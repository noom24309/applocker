package app.lock.photo.valut.features.intruder

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentIntruderSettingsBinding
import app.lock.photo.valut.features.auth.VerifyMasterActivity
import app.lock.photo.valut.features.intruder.model.IntruderSettingsUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IntruderSettingsFragment : Fragment() {

    private var _binding: FragmentIntruderSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IntruderSettingsViewModel by viewModels()
    private var current = IntruderSettingsUiState()

    private var pendingAction: (() -> Unit)? = null
    private val verifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.markVerified()
            action?.invoke()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermission()
        if (granted) viewModel.setEnabled(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntruderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.rowEnable.setOnClickListener { onToggleEnable() }
        binding.rowPermission.setOnClickListener { requestCameraPermission() }
        binding.rowAttempts.setOnClickListener { runVerified { showAttemptsPicker() } }
        binding.rowAppUnlock.setOnClickListener { viewModel.setCaptureOnAppUnlock(!current.captureOnAppUnlock) }
        binding.rowOverlay.setOnClickListener { viewModel.setCaptureOnAppLockOverlay(!current.captureOnAppLockOverlay) }
        binding.rowVault.setOnClickListener { viewModel.setCaptureOnVaultUnlock(!current.captureOnVaultUnlock) }
        binding.rowEncrypted.setOnClickListener { viewModel.setSaveEncrypted(!current.saveEncrypted) }
        binding.rowNotification.setOnClickListener { viewModel.setShowNotification(!current.showNotification) }
        binding.rowHideContent.setOnClickListener { viewModel.setHideNotificationContent(!current.hideNotificationContent) }
        binding.rowAutoDelete.setOnClickListener { showAutoDeletePicker() }
        binding.rowMaxRecords.setOnClickListener { showMaxRecordsPicker() }
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: IntruderSettingsUiState) {
        current = state
        binding.switchEnable.isChecked = state.enabled
        binding.switchAppUnlock.isChecked = state.captureOnAppUnlock
        binding.switchOverlay.isChecked = state.captureOnAppLockOverlay
        binding.switchVault.isChecked = state.captureOnVaultUnlock
        binding.switchEncrypted.isChecked = state.saveEncrypted
        binding.switchNotification.isChecked = state.showNotification
        binding.switchHideContent.isChecked = state.hideNotificationContent
        binding.tvAttempts.text = state.captureAfterAttempts.toString()
        binding.tvMaxRecords.text = state.maxRecords.toString()
        binding.tvAutoDelete.setText(IntruderLabels.autoDelete(state.autoDeleteMode))
        binding.tvPermissionStatus.setText(
            when {
                !state.cameraAvailable -> R.string.intruder_no_camera
                state.cameraPermissionGranted -> R.string.applock_perm_granted
                else -> R.string.intruder_permission_needed
            }
        )
    }

    private fun onToggleEnable() {
        if (current.enabled) {
            // Disabling is sensitive.
            runVerified { viewModel.setEnabled(false) }
            return
        }
        if (!current.cameraAvailable) {
            toast(R.string.intruder_no_camera)
            return
        }
        if (current.cameraPermissionGranted) {
            viewModel.setEnabled(true)
        } else {
            explainThenRequestPermission()
        }
    }

    private fun requestCameraPermission() {
        if (current.cameraPermissionGranted) return
        explainThenRequestPermission()
    }

    private fun explainThenRequestPermission() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.intruder_permission_title)
            .setMessage(R.string.intruder_permission_explanation)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAttemptsPicker() {
        val options = IntruderLabels.ATTEMPT_OPTIONS
        val labels = options.map { it.toString() }.toTypedArray()
        val checked = options.indexOf(current.captureAfterAttempts).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.intruder_after_attempts)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setCaptureAfterAttempts(options[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAutoDeletePicker() {
        val options = IntruderLabels.AUTO_DELETE_OPTIONS
        val labels = options.map { getString(IntruderLabels.autoDelete(it)) }.toTypedArray()
        val checked = options.indexOf(current.autoDeleteMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.intruder_auto_delete)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setAutoDeleteMode(options[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMaxRecordsPicker() {
        val options = IntruderLabels.MAX_RECORD_OPTIONS
        val labels = options.map { it.toString() }.toTypedArray()
        val checked = options.indexOf(current.maxRecords).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.intruder_max_records)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setMaxRecords(options[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runVerified(action: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.needsVerification()) {
                pendingAction = action
                verifyLauncher.launch(Intent(requireContext(), VerifyMasterActivity::class.java))
            } else {
                action()
            }
        }
    }

    private fun toast(resId: Int) =
        android.widget.Toast.makeText(requireContext(), resId, android.widget.Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
