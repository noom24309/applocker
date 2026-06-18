package app.lock.photo.valut.features.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.databinding.FragmentSettingsBinding
import app.lock.photo.valut.domain.model.AutoLockMode
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.auth.ChangePinActivity
import app.lock.photo.valut.features.auth.PatternSetupActivity
import app.lock.photo.valut.features.auth.VerifyMasterActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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
        if (result.resultCode == android.app.Activity.RESULT_OK) action?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVersion()
        setupStaticValues()
        setupActions()
        observeState()
    }

    private fun setupVersion() {
        val pm = requireContext().packageManager
        appVersion = pm.getPackageInfo(requireContext().packageName, 0).versionName.orEmpty()
    }

    private fun setupStaticValues() {
        binding.tvWrongAttemptValue.text =
            getString(R.string.settings_attempts_value, Constants.ATTEMPTS_SHORT_LOCK)
    }

    private fun setupActions() {
        binding.cardSecurityStatus.setOnClickListener { showSecurityStatusInfo() }
        binding.rowChangePin.setOnClickListener { requireVerification { openChangePin() } }
        binding.rowChangePattern.setOnClickListener { requireVerification { openPatternSetup() } }
        binding.rowBiometric.setOnClickListener { toggleBiometric() }
        binding.rowAutoLock.setOnClickListener { showAutoLockPicker() }
        binding.rowWrongAttempt.setOnClickListener { showWrongAttemptInfo() }
        binding.rowAppLockSettings.setOnClickListener { openAppLock() }
        binding.rowLockOverlay.setOnClickListener { openAppLock() }
        binding.rowDisguise.setOnClickListener { comingSoon() }
        binding.rowNotifications.setOnClickListener { openNotificationSettings() }
        binding.rowLanguage.setOnClickListener { openLanguageSettings() }
        binding.rowPrivacy.setOnClickListener { showPrivacyPolicy() }
        binding.rowAbout.setOnClickListener { showAbout() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        verifyLauncher.launch(Intent(requireContext(), VerifyMasterActivity::class.java))
    }

    private fun openChangePin() {
        startActivity(Intent(requireContext(), ChangePinActivity::class.java))
    }

    private fun openPatternSetup() {
        startActivity(Intent(requireContext(), PatternSetupActivity::class.java))
    }

    private fun openAppLock() {
        startActivity(AppLockActivity.intent(requireContext()))
    }

    private fun toggleBiometric() {
        if (viewModel.biometricEnabled.value) {
            viewModel.setBiometricEnabled(false)
            return
        }
        if (!biometricHelper.isBiometricAvailable(requireContext())) {
            Toast.makeText(
                requireContext(),
                R.string.biometric_unavailable_subtitle,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        requireVerification { viewModel.setBiometricEnabled(true) }
    }

    private fun showAutoLockPicker() {
        val modes = AutoLockMode.entries.toTypedArray()
        val labels = modes.map { getString(autoLockLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(viewModel.autoLockMode.value)
        MaterialAlertDialogBuilder(requireContext())
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
            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        startSystemSettings(intent)
    }

    private fun openLanguageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(
                Settings.ACTION_APP_LOCALE_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null)
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null)
            )
        }
        startSystemSettings(intent)
    }

    /** Launches a system settings screen, falling back to a toast if unavailable. */
    private fun startSystemSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            comingSoon()
        }
    }

    private fun comingSoon() {
        Toast.makeText(requireContext(), R.string.tools_coming_soon, Toast.LENGTH_SHORT).show()
    }

    private fun showPrivacyPolicy() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy_policy_title)
            .setMessage(R.string.privacy_policy_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_about_app)
            .setMessage(getString(R.string.settings_about_message, appVersion))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showSecurityStatusInfo() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_security_status)
            .setMessage(R.string.settings_security_status_active)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showWrongAttemptInfo() {
        MaterialAlertDialogBuilder(requireContext())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
