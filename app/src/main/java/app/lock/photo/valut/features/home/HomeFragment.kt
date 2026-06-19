package app.lock.photo.valut.features.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.databinding.FragmentHomeBinding
import app.lock.photo.valut.features.applock.apps.AppLockAppsAdapter
import app.lock.photo.valut.features.applock.apps.AppLockAppsViewModel
import app.lock.photo.valut.features.applock.home.AppLockHomeViewModel
import app.lock.photo.valut.features.applock.model.AppFilter
import app.lock.photo.valut.features.applock.model.AppLockHomeUiState
import app.lock.photo.valut.features.applock.model.AppLockStatsUiState
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel
import app.lock.photo.valut.features.applock.perapp.PerAppLockSettingsBottomSheet
import app.lock.photo.valut.features.applock.stats.AppLockStatsViewModel
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import app.lock.photo.valut.features.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Home tab (Lock & Vault): the installed-apps list with a per-app lock toggle, filter
 * chips, a stats card and a "complete setup" card. App data is live from
 * [AppLockAppsViewModel]; the stats values are illustrative for now.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppLockAppsViewModel by viewModels()
    private val homeViewModel: AppLockHomeViewModel by viewModels()
    private val statsViewModel: AppLockStatsViewModel by viewModels()

    @Inject
    lateinit var iconCacheManager: AppIconCacheManager

    @Inject
    lateinit var permissionChecker: AppLockPermissionChecker

    private lateinit var adapter: AppLockAppsAdapter

    private var sortAsc = true
    private var currentApps: List<InstalledAppUiModel> = emptyList()

    /** Permission setup is only requested when the user actually locks an app. */
    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { viewModel.ensureProtectionRunning() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        setupControls()
        selectChip(AppFilter.ALL)
        observe()
    }

    override fun onResume() {
        super.onResume()
        // Re-read permission state (it can change in system settings); the dashboard +
        // setup-card visibility react to the result.
        homeViewModel.refreshPermissions()
    }

    private fun setupRecycler() {
        adapter = AppLockAppsAdapter(
            onToggle = { app, locked -> handleToggle(app, locked) },
            onConfigure = { app ->
                PerAppLockSettingsBottomSheet.newInstance(app.packageName, app.appName)
                    .show(childFragmentManager, "perAppLock")
            },
            loadIcon = ::loadIcon
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupControls() {
        binding.btnSettings.setOnClickListener {
            startActivity(SettingsActivity.intent(requireContext()))
        }
        binding.btnFilter.setOnClickListener {
            binding.searchInput.requestFocus()
        }
        binding.searchInput.addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }

        binding.chipAll.setOnClickListener { onChip(AppFilter.ALL) }
        binding.chipLocked.setOnClickListener { onChip(AppFilter.LOCKED) }
        binding.chipUnlocked.setOnClickListener { onChip(AppFilter.UNLOCKED) }
        binding.chipSystem.setOnClickListener { onChip(AppFilter.SYSTEM) }

        binding.btnSort.setOnClickListener {
            sortAsc = !sortAsc
            binding.tvSortLabel.text = getString(if (sortAsc) R.string.lv_sort_az else R.string.lv_sort_za)
            renderList(currentApps)
        }

        binding.btnFinishSetup.setOnClickListener {
            permissionLauncher.launch(Intent(requireContext(), AppLockPermissionActivity::class.java))
        }
    }

    private fun onChip(filter: AppFilter) {
        viewModel.setFilter(filter)
        selectChip(filter)
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.apps.collect { apps ->
                        currentApps = apps
                        renderList(apps)
                    }
                }
                launch {
                    viewModel.loading.collect { binding.progress.isVisible = it }
                }
                launch { homeViewModel.uiState.collect(::renderDashboard) }
                launch { statsViewModel.uiState.collect(::renderStats) }
            }
        }
    }

    /** Locked-apps count, protection status, and the setup-card visibility — all live. */
    private fun renderDashboard(state: AppLockHomeUiState) {
        binding.tvStatLocked.text = state.lockedAppsCount.toString()
        binding.tvStatProtection.setText(
            if (state.isServiceRunning) R.string.lv_stat_protection_value
            else R.string.lv_stat_protection_off
        )
        // Setup card only matters while permissions are missing.
        binding.setupCard.isVisible = !state.permissionsGranted
    }

    /** Today's blocked attempts and protection time. */
    private fun renderStats(stats: AppLockStatsUiState) {
        binding.tvStatBlocked.text = stats.failedUnlocks.toString()
        binding.tvStatTime.text = formatDuration(stats.protectionActiveMillis)
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) getString(R.string.duration_hm, hours, minutes)
        else getString(R.string.duration_m, minutes)
    }

    private fun renderList(apps: List<InstalledAppUiModel>) {
        val sorted = if (sortAsc) apps.sortedBy { it.appName.lowercase() }
        else apps.sortedByDescending { it.appName.lowercase() }
        adapter.submitList(sorted)
        binding.tvAppCount.text = getString(R.string.lv_app_count, sorted.size)
        binding.emptyText.isVisible = sorted.isEmpty() && !viewModel.loading.value
    }

    /**
     * Locks/unlocks an app. The first time the user locks one without the App Lock
     * permissions, we mark it locked and open the permission screen so protection can
     * actually start — that is the only place the permission flow is triggered.
     */
    private fun handleToggle(app: InstalledAppUiModel, locked: Boolean) {
        viewModel.setLocked(app, locked)
        if (locked && !permissionChecker.hasAllRequiredAppLockPermissions()) {
            permissionLauncher.launch(Intent(requireContext(), AppLockPermissionActivity::class.java))
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { iconCacheManager.getIcon(packageName) }
            if (imageView.tag == packageName && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    // ---- chip selection styling ----

    private fun selectChip(selected: AppFilter) {
        styleChip(binding.chipAll, binding.chipAllText, null, selected == AppFilter.ALL, R.color.home_primary)
        styleChip(binding.chipLocked, binding.chipLockedText, binding.chipLockedIcon, selected == AppFilter.LOCKED, R.color.home_primary)
        styleChip(binding.chipUnlocked, binding.chipUnlockedText, binding.chipUnlockedIcon, selected == AppFilter.UNLOCKED, R.color.home_success)
        styleChip(binding.chipSystem, binding.chipSystemText, binding.chipSystemIcon, selected == AppFilter.SYSTEM, R.color.home_system_icon)
    }

    private fun styleChip(
        chip: View,
        text: TextView,
        icon: ImageView?,
        selected: Boolean,
        iconColorRes: Int
    ) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_lv_chip_selected else R.drawable.bg_lv_chip_unselected
        )
        text.setTextColor(ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.home_chip_text))
        icon?.setColorFilter(ContextCompat.getColor(requireContext(), if (selected) R.color.white else iconColorRes))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
