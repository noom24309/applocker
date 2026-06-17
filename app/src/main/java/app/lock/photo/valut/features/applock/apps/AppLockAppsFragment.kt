package app.lock.photo.valut.features.applock.apps
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.applock.perapp.PerAppLockSettingsBottomSheet
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.databinding.FragmentAppLockAppsBinding
import app.lock.photo.valut.features.applock.model.AppFilter
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AppLockAppsFragment : Fragment() {

    private var _binding: FragmentAppLockAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppLockAppsViewModel by viewModels()

    @Inject
    lateinit var iconCacheManager: AppIconCacheManager

    @Inject
    lateinit var permissionChecker: AppLockPermissionChecker

    private lateinit var adapter: AppLockAppsAdapter

    /** Permission setup is only requested when the user actually locks an app. */
    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { viewModel.ensureProtectionRunning() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppLockAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        setupRecycler()
        setupControls()
        applyInitialFilter(savedInstanceState)
        observe()
    }

    /** Apply a one-time starting filter passed via [newInstance] (e.g. LOCKED from Home). */
    private fun applyInitialFilter(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val filterName = arguments?.getString(ARG_INITIAL_FILTER) ?: return
        val filter = runCatching { AppFilter.valueOf(filterName) }.getOrNull() ?: return
        viewModel.setFilter(filter)
        when (filter) {
            AppFilter.ALL -> binding.chipAll.isChecked = true
            AppFilter.LOCKED -> binding.chipLocked.isChecked = true
            AppFilter.UNLOCKED -> binding.chipUnlocked.isChecked = true
            AppFilter.SYSTEM -> binding.chipSystem.isChecked = true
        }
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
        binding.searchInput.addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.chipAll.setOnClickListener { viewModel.setFilter(AppFilter.ALL) }
        binding.chipLocked.setOnClickListener { viewModel.setFilter(AppFilter.LOCKED) }
        binding.chipUnlocked.setOnClickListener { viewModel.setFilter(AppFilter.UNLOCKED) }
        binding.chipSystem.setOnClickListener { viewModel.toggleSystemApps() }
        binding.btnSuggestions.setOnClickListener { (requireActivity() as AppLockActivity).openSuggestions() }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.apps.collect { apps ->
                        adapter.submitList(apps)
                        binding.emptyText.isVisible = apps.isEmpty() && !viewModel.loading.value
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        binding.progress.isVisible = loading
                    }
                }
                launch {
                    viewModel.showSystemApps.collect { binding.chipSystem.isChecked = it }
                }
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_FILTER = "arg_initial_filter"

        fun newInstance(initialFilter: AppFilter? = null) = AppLockAppsFragment().apply {
            if (initialFilter != null) {
                arguments = Bundle().apply { putString(ARG_INITIAL_FILTER, initialFilter.name) }
            }
        }
    }
}
