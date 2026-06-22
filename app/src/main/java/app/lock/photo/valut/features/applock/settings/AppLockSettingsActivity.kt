package app.lock.photo.valut.features.applock.settings

import app.lock.photo.valut.core.ui.BaseActivity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentAppLockSettingsBinding
import app.lock.photo.valut.features.applock.AppLockLabels
import app.lock.photo.valut.features.applock.model.AppLockSettingsUiState
import app.lock.photo.valut.features.applock.theme.LockThemePickerActivity
import app.lock.photo.valut.features.applock.troubleshooting.AppLockTroubleshootingActivity
import app.lock.photo.valut.features.auth.verify.VerifyMasterActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppLockSettingsActivity : BaseActivity() {

    private lateinit var binding: FragmentAppLockSettingsBinding
    private val viewModel: AppLockSettingsViewModel by viewModels()

    private var pendingAction: (() -> Unit)? = null
    private var current = AppLockSettingsUiState()

    private val verifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.markVerified()
            action?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAppLockSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        // Sensitive changes are verification-gated.
        binding.rowMaster.setOnClickListener {
            runVerified { viewModel.setAppLockEnabled(!current.appLockEnabled) }
        }
        binding.rowDelay.setOnClickListener { runVerified { showDelayPicker() } }
        binding.rowFakeMode.setOnClickListener { runVerified { showFakeModePicker() } }
        binding.rowHideName.setOnClickListener { runVerified { viewModel.setHideAppName(!current.hideAppName) } }
        binding.rowTheme.setOnClickListener { startActivity(LockThemePickerActivity.intent(this)) }
        // Non-sensitive toggles.
        binding.rowLockNew.setOnClickListener { viewModel.setLockNewAppsAutomatically(!current.lockNewAppsAutomatically) }
        binding.rowScreenOff.setOnClickListener { viewModel.setRelockAfterScreenOff(!current.relockAfterScreenOff) }
        binding.rowAppSwitch.setOnClickListener { viewModel.setRelockAfterAppSwitch(!current.relockAfterAppSwitch) }
        binding.rowDeviceLock.setOnClickListener { viewModel.setRelockAfterDeviceLock(!current.relockAfterDeviceLock) }
        binding.rowHideRecent.setOnClickListener { viewModel.setHideRecentPreview(!current.hideRecentPreview) }
        binding.rowLocalStats.setOnClickListener { viewModel.setLocalStatsEnabled(!current.localStatsEnabled) }
        binding.rowRestartBoot.setOnClickListener { viewModel.setRestartAfterBoot(!current.restartAfterBoot) }
        binding.rowPermissions.setOnClickListener { openPermissions() }
        binding.rowBattery.setOnClickListener { openBatterySettings() }
        binding.rowTroubleshoot.setOnClickListener { startActivity(AppLockTroubleshootingActivity.intent(this)) }
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: AppLockSettingsUiState) {
        current = state
        binding.switchMaster.isChecked = state.appLockEnabled
        binding.switchLockNew.isChecked = state.lockNewAppsAutomatically
        binding.switchScreenOff.isChecked = state.relockAfterScreenOff
        binding.switchAppSwitch.isChecked = state.relockAfterAppSwitch
        binding.switchDeviceLock.isChecked = state.relockAfterDeviceLock
        binding.switchHideName.isChecked = state.hideAppName
        binding.switchHideRecent.isChecked = state.hideRecentPreview
        binding.switchLocalStats.isChecked = state.localStatsEnabled
        binding.switchRestartBoot.isChecked = state.restartAfterBoot
        binding.tvDelayValue.setText(AppLockLabels.delay(state.delayMode))
        binding.tvThemeValue.setText(AppLockLabels.theme(state.theme))
        binding.tvFakeValue.setText(AppLockLabels.fakeMode(state.defaultFakeMode))
        binding.tvUnlockMethod.setText(
            if (state.usesPattern) R.string.unlock_method_pattern else R.string.unlock_method_pin
        )
    }

    /** Runs [action] now if the verified session is still valid, else asks to verify first. */
    private fun runVerified(action: () -> Unit) {
        lifecycleScope.launch {
            if (viewModel.needsVerification()) {
                pendingAction = action
                verifyLauncher.launch(Intent(this@AppLockSettingsActivity, VerifyMasterActivity::class.java))
            } else {
                action()
            }
        }
    }

    private fun showDelayPicker() {
        val modes = AppLockLabels.GLOBAL_DELAY_OPTIONS
        val labels = modes.map { getString(AppLockLabels.delay(it)) }.toTypedArray()
        val checked = modes.indexOf(current.delayMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.applock_setting_delay)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setDelayMode(modes[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFakeModePicker() {
        val modes = AppLockLabels.GLOBAL_FAKE_OPTIONS
        val labels = modes.map { getString(AppLockLabels.fakeMode(it)) }.toTypedArray()
        val checked = modes.indexOf(current.defaultFakeMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.applock_setting_fake_mode)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setDefaultFakeMode(modes[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPermissions() {
        startActivity(Intent(this, AppLockPermissionActivity::class.java))
    }

    private fun openBatterySettings() {
        // General battery-optimization list (no exemption is requested) — user guidance only.
        runCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, AppLockSettingsActivity::class.java)
    }
}
