package app.lock.photo.valut.features.applock.apps

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.databinding.FragmentAppLockAppsBinding
import app.lock.photo.valut.features.applock.model.AppFilter
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel
import app.lock.photo.valut.features.applock.perapp.PerAppLockSettingsBottomSheet
import app.lock.photo.valut.features.applock.suggested.SuggestedAppsActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AppLockAppsActivity : BaseActivity() {

    private lateinit var binding: FragmentAppLockAppsBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAppLockAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        setupRecycler()
        setupControls()
        applyInitialFilter(savedInstanceState)
        observe()
    }

    /** Apply a one-time starting filter passed via [intent] (e.g. LOCKED from Home). */
    private fun applyInitialFilter(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val filterName = intent.getStringExtra(EXTRA_INITIAL_FILTER) ?: return
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
                    .show(supportFragmentManager, "perAppLock")
            },
            loadIcon = ::loadIcon
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupControls() {
        binding.searchInput.addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.chipAll.setOnClickListener { viewModel.setFilter(AppFilter.ALL) }
        binding.chipLocked.setOnClickListener { viewModel.setFilter(AppFilter.LOCKED) }
        binding.chipUnlocked.setOnClickListener { viewModel.setFilter(AppFilter.UNLOCKED) }
        binding.chipSystem.setOnClickListener { viewModel.toggleSystemApps() }
        binding.btnSuggestions.setOnClickListener { startActivity(SuggestedAppsActivity.intent(this)) }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
            permissionLauncher.launch(Intent(this, AppLockPermissionActivity::class.java))
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { iconCacheManager.getIcon(packageName) }
            if (imageView.tag == packageName && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private const val EXTRA_INITIAL_FILTER = "extra_initial_filter"

        fun intent(context: Context, initialFilter: AppFilter? = null) =
            Intent(context, AppLockAppsActivity::class.java).apply {
                if (initialFilter != null) putExtra(EXTRA_INITIAL_FILTER, initialFilter.name)
            }
    }
}
