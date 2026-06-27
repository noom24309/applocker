package app.lock.photo.valut.features.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.FragmentSettingsBinding
import app.lock.photo.valut.domain.model.AutoLockMode
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.auth.pattern.PatternSetupActivity
import app.lock.photo.valut.features.auth.pin.ChangePinActivity
import app.lock.photo.valut.features.auth.verify.VerifyMasterActivity
import app.lock.photo.valut.features.language.LanguageActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wastickers.romantic.stickers.loveromance.ui.settings.SettingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    private lateinit var binding: FragmentSettingsBinding

    private val viewModel: SecuritySettingsViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    private var appVersion: String = ""

    /** Action to run once the user passes the master-verification gate. */
    private var pendingAction: (() -> Unit)? = null

    private val verifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == RESULT_OK) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupVersion()
        setupStaticValues()
        setupActions()
        observeState()
    }

    private fun setupVersion() {
        appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }

    private fun setupStaticValues() {
        binding.tvWrongAttemptValue.text =
            getString(R.string.settings_attempts_value, Constants.ATTEMPTS_SHORT_LOCK)
    }

    private fun setupActions() {
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.rowChangePin.setOnClickListener { requireVerification { openChangePin() } }
        binding.rowChangePattern.setOnClickListener { requireVerification { openPatternSetup() } }
        binding.rowBiometric.setOnClickListener { toggleBiometric() }
        binding.rowAutoLock.setOnClickListener { showAutoLockPicker() }
        binding.rowWrongAttempt.setOnClickListener { showWrongAttemptInfo() }
        binding.rowAppLockSettings.setOnClickListener { openAppLock() }
        binding.rowLockOverlay.setOnClickListener { openAppLock() }
        binding.rowNotifications.setOnClickListener { openNotificationSettings() }
        binding.rowLanguage.setOnClickListener { openLanguageSettings() }
        binding.rowPrivacy.setOnClickListener { showPrivacyPolicy() }
        binding.rowAbout.setOnClickListener { showAbout() }
        binding.rowRate.setOnClickListener { openPlayStoreListing() }
        binding.rowShare.setOnClickListener { shareApp() }
        binding.rowMoreApps.setOnClickListener { openMoreApps() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.biometricEnabled.collect { binding.switchBiometric.isChecked = it }
                }
                launch {
                    viewModel.autoLockMode.collect {
                        binding.tvAutoLockValue.setText(autoLockValueLabel(it))
                    }
                }
            }
        }
    }

    private fun requireVerification(action: () -> Unit) {
        pendingAction = action
        verifyLauncher.launch(Intent(this, VerifyMasterActivity::class.java))
    }

    private fun openChangePin() {
        startActivity(Intent(this, ChangePinActivity::class.java))
    }

    private fun openPatternSetup() {
        startActivity(Intent(this, PatternSetupActivity::class.java))
    }

    private fun openAppLock() {
        startActivity(AppLockActivity.intent(this))
    }

    private fun toggleBiometric() {
        if (viewModel.biometricEnabled.value) {
            viewModel.setBiometricEnabled(false)
            return
        }
        if (!biometricHelper.isBiometricAvailable(this)) {
            Toast.makeText(this, R.string.biometric_unavailable_subtitle, Toast.LENGTH_SHORT).show()
            return
        }
        requireVerification { viewModel.setBiometricEnabled(true) }
    }

    private fun showAutoLockPicker() {
        val modes = AutoLockMode.entries.toTypedArray()
        val labels = modes.map { getString(autoLockLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(viewModel.autoLockMode.value)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_lock_picker_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                viewModel.setAutoLockMode(modes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startSystemSettings(intent)
    }

    private fun openLanguageSettings() {
        // Came from in-app settings: after the user picks a language the ported
        // language flow routes back to MainActivity (home) instead of onboarding.
        SettingActivity.comeFromLangauge = true
        startActivity(Intent(this, LanguageActivity::class.java))
    }

    /** Launches a system settings screen, falling back to a toast if unavailable. */
    private fun startSystemSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_open_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyPolicy() {
        startActivity(
            WebViewActivity.intent(
                this,
                PRIVACY_POLICY_URL,
                getString(R.string.privacy_policy_title)
            )
        )
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_about_app)
            .setMessage(getString(R.string.settings_about_message, appVersion))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Opens this app's Play Store listing (falls back to the web URL if Play isn't installed). */
    private fun openPlayStoreListing() {
        openExternal(
            Uri.parse("market://details?id=$packageName"),
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )
    }

    /** Shares the Play Store link via the system share sheet. */
    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_share_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.settings_share_text, packageName))
        }
        runCatching { startActivity(Intent.createChooser(intent, null)) }
            .onFailure { toastOpenUnavailable() }
    }

    /** Opens the developer's other apps on the Play Store. */
    private fun openMoreApps() {
        val publisher = Uri.encode(PLAY_DEVELOPER_NAME)
        openExternal(
            Uri.parse("market://search?q=pub:$publisher"),
            Uri.parse("https://play.google.com/store/apps/developer?id=$publisher")
        )
    }

    /** Launches [primary] (e.g. a market:// intent); on failure tries [fallback] (web), else toasts. */
    private fun openExternal(primary: Uri, fallback: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, primary)) }
            .recoverCatching { startActivity(Intent(Intent.ACTION_VIEW, fallback)) }
            .onFailure { toastOpenUnavailable() }
    }

    private fun toastOpenUnavailable() {
        Toast.makeText(this, R.string.settings_open_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun showSecurityStatusInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_security_status)
            .setMessage(R.string.settings_security_status_active)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showWrongAttemptInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_wrong_attempt)
            .setMessage(R.string.settings_wrong_attempt_info)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun autoLockLabel(mode: AutoLockMode): Int = when (mode) {
        AutoLockMode.IMMEDIATE -> R.string.auto_lock_immediate
        AutoLockMode.SECONDS_15 -> R.string.auto_lock_15s
        AutoLockMode.SECONDS_30 -> R.string.auto_lock_30s
        AutoLockMode.MINUTE_1 -> R.string.auto_lock_1m
        AutoLockMode.MINUTES_5 -> R.string.auto_lock_5m
        AutoLockMode.NEVER_IN_MEMORY -> R.string.auto_lock_never
    }

    private fun autoLockValueLabel(mode: AutoLockMode): Int = when (mode) {
        AutoLockMode.IMMEDIATE -> R.string.auto_lock_immediate_short
        AutoLockMode.SECONDS_15 -> R.string.auto_lock_15s_short
        AutoLockMode.SECONDS_30 -> R.string.auto_lock_30s_short
        AutoLockMode.MINUTE_1 -> R.string.auto_lock_1m_short
        AutoLockMode.MINUTES_5 -> R.string.auto_lock_5m_short
        AutoLockMode.NEVER_IN_MEMORY -> R.string.auto_lock_never_short
    }

    companion object {
        private const val PRIVACY_POLICY_URL =
            "https://sites.google.com/view/applock-privatevault/home"

        /** Play Store publisher name used by the "More Apps" row. */
        private const val PLAY_DEVELOPER_NAME = "A2ZTechnologies"

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
