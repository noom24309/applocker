package app.lock.photo.valut.features.settings

import android.content.Intent
import android.os.Bundle
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
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.permissions.BiometricHelper
import androidx.appcompat.app.AppCompatDelegate
import app.lock.photo.valut.databinding.FragmentSettingsBinding
import app.lock.photo.valut.domain.model.AppearanceMode
import app.lock.photo.valut.domain.model.AutoLockMode
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
        setupActions()
        observeState()
    }

    private fun setupVersion() {
        val pm = requireContext().packageManager
        binding.tvAppVersion.text =
            pm.getPackageInfo(requireContext().packageName, 0).versionName.orEmpty()
    }

    private fun setupActions() {
        binding.rowChangePin.setOnClickListener {
            requireVerification { openChangePin() }
        }
        binding.rowChangeUnlockMethod.setOnClickListener {
            requireVerification { showUnlockMethodPicker() }
        }
        binding.rowBiometric.setOnClickListener { toggleBiometric() }
        binding.rowAutoLock.setOnClickListener { showAutoLockPicker() }
        binding.rowWrongAttempt.setOnClickListener { showWrongAttemptInfo() }
        binding.rowLockNow.setOnClickListener { viewModel.lockNow() }
        binding.rowAppearance.setOnClickListener { showAppearancePicker() }
        binding.rowPrivacy.setOnClickListener { showPrivacyPolicy() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.biometricEnabled.collect { binding.switchBiometric.isChecked = it }
                }
                launch {
                    viewModel.unlockMethod.collect {
                        binding.tvUnlockMethodValue.setText(
                            if (it.usesPattern) R.string.unlock_method_pattern
                            else R.string.unlock_method_pin
                        )
                    }
                }
                launch {
                    viewModel.autoLockMode.collect {
                        binding.tvAutoLockValue.setText(autoLockLabel(it))
                    }
                }
                launch {
                    viewModel.appearanceMode.collect {
                        binding.tvAppearanceValue.setText(appearanceLabel(it))
                    }
                }
                launch {
                    viewModel.lockNowFlow.collect {
                        startActivity(
                            LockRouter.lockIntent(requireContext(), viewModel.unlockMethod.value)
                        )
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

    private fun showUnlockMethodPicker() {
        val labels = arrayOf(
            getString(R.string.unlock_method_pin),
            getString(R.string.unlock_method_pattern)
        )
        val checked = if (viewModel.unlockMethod.value.usesPattern) 1 else 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.unlock_method_picker_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                if (which == 1) {
                    startActivity(Intent(requireContext(), PatternSetupActivity::class.java))
                } else {
                    viewModel.selectPinMethod()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun showAppearancePicker() {
        val modes = AppearanceMode.entries.toTypedArray()
        val labels = modes.map { getString(appearanceLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(viewModel.appearanceMode.value)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appearance_picker_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                val mode = modes[which]
                viewModel.setAppearanceMode(mode)
                // Apply immediately; AppCompat recreates activities to the new mode.
                AppCompatDelegate.setDefaultNightMode(mode.nightMode)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun appearanceLabel(mode: AppearanceMode): Int = when (mode) {
        AppearanceMode.LIGHT -> R.string.appearance_light
        AppearanceMode.DARK -> R.string.appearance_dark
        AppearanceMode.SYSTEM -> R.string.appearance_system
    }

    private fun showPrivacyPolicy() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy_policy_title)
            .setMessage(R.string.privacy_policy_body)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
