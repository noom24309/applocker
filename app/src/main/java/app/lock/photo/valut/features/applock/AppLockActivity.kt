package app.lock.photo.valut.features.applock

import app.lock.photo.valut.core.ui.BaseActivity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentAppLockHomeBinding
import app.lock.photo.valut.features.applock.apps.AppLockAppsActivity
import app.lock.photo.valut.features.applock.home.AppLockHomeViewModel
import app.lock.photo.valut.features.applock.model.AppLockHomeUiState
import app.lock.photo.valut.features.applock.model.AppLockStatsUiState
import app.lock.photo.valut.features.applock.settings.AppLockSettingsActivity
import app.lock.photo.valut.features.applock.stats.AppLockStatsViewModel
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** App Lock dashboard: master switch, status, stats and entry points to the apps list/settings. */
@AndroidEntryPoint
class AppLockActivity : BaseActivity() {

    private lateinit var binding: FragmentAppLockHomeBinding
    private val viewModel: AppLockHomeViewModel by viewModels()
    private val statsViewModel: AppLockStatsViewModel by viewModels()

    /** Latest live permission status, mirrored from [render] to gate the lock actions. */
    private var permissionsGranted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshPermissions()
        // Setup wizard finished with everything granted → turn the feature on so the
        // user lands straight on "choose apps to lock".
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setAppLockEnabled(true)
            Toast.makeText(this, R.string.applock_setup_done_lock_apps, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAppLockHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.masterSwitch.setOnClickListener {
            val wantOn = binding.masterSwitch.isChecked
            // Enabling the feature requires the permission setup to be done first.
            if (wantOn && !permissionsGranted) {
                binding.masterSwitch.isChecked = false
                requireSetupThen()
            } else {
                viewModel.setAppLockEnabled(wantOn)
            }
        }
        binding.btnCompleteSetup.setOnClickListener { openPermissions() }
        binding.btnStart.setOnClickListener { viewModel.startProtection() }
        binding.btnStop.setOnClickListener { viewModel.stopProtection() }
        // Open the apps list freely; the permission prompt happens only when the user
        // actually toggles a lock on an app (handled in the list itself).
        binding.cardManageApps.setOnClickListener { startActivity(AppLockAppsActivity.intent(this)) }
        binding.cardSettings.setOnClickListener { startActivity(AppLockSettingsActivity.intent(this)) }
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { statsViewModel.uiState.collect(::renderStats) }
            }
        }
    }

    private fun renderStats(stats: AppLockStatsUiState) {
        binding.tvStatUnlocks.text = getString(R.string.applock_stat_unlocks, stats.successfulUnlocks)
        binding.tvStatFailed.text = getString(R.string.applock_stat_failed, stats.failedUnlocks)
        binding.tvStatOpens.text = getString(R.string.applock_stat_opens, stats.lockedAppOpens)
        binding.tvStatProtection.text =
            getString(R.string.applock_stat_protection, formatDuration(stats.protectionActiveMillis))
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) getString(R.string.duration_hm, hours, minutes)
        else getString(R.string.duration_m, minutes)
    }

    private fun render(state: AppLockHomeUiState) {
        permissionsGranted = state.permissionsGranted
        binding.masterSwitch.isChecked = state.isAppLockEnabled

        val countText = resources.getQuantityString(
            R.plurals.applock_locked_count, state.lockedAppsCount, state.lockedAppsCount
        )
        binding.tvLockedCount.text = countText

        when {
            !state.permissionsGranted -> {
                binding.tvStatus.setText(R.string.applock_status_setup_required)
                binding.tvStatusDesc.setText(R.string.applock_status_setup_desc)
            }
            state.isServiceRunning -> {
                binding.tvStatus.setText(R.string.applock_status_active)
                binding.tvStatusDesc.text = countText
            }
            else -> {
                binding.tvStatus.setText(R.string.applock_status_inactive)
                binding.tvStatusDesc.text = countText
            }
        }

        binding.btnCompleteSetup.isVisible = !state.permissionsGranted
        // Start shown when we can run but aren't; Stop shown when running.
        binding.btnStart.isVisible = state.permissionsGranted && state.isAppLockEnabled &&
            !state.isServiceRunning && state.lockedAppsCount > 0
        binding.btnStop.isVisible = state.permissionsGranted && state.isServiceRunning
    }

    /** Tells the user setup comes first, then routes into the permission wizard. */
    private fun requireSetupThen() {
        Toast.makeText(this, R.string.applock_setup_first, Toast.LENGTH_SHORT).show()
        openPermissions()
    }

    private fun openPermissions() {
        permissionLauncher.launch(Intent(this, AppLockPermissionActivity::class.java))
    }

    companion object {
        fun intent(context: Context) = Intent(context, AppLockActivity::class.java)
    }
}
