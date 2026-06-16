package app.lock.photo.valut.features.applock.perapp
import app.lock.photo.valut.features.applock.AppLockLabels
import app.lock.photo.valut.features.applock.settings.AppLockSettingsViewModel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.BottomsheetPerAppLockBinding
import app.lock.photo.valut.domain.model.AppLockDelayMode
import app.lock.photo.valut.domain.model.FakeMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PerAppLockSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetPerAppLockBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PerAppLockSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetPerAppLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rowUseCustom.setOnClickListener {
            viewModel.setUseCustom(!binding.switchUseCustom.isChecked)
        }
        binding.rowUnlockMethod.setOnClickListener { showUnlockMethodPicker() }
        binding.rowDelay.setOnClickListener { showDelayPicker() }
        binding.rowFakeMode.setOnClickListener { showFakeModePicker() }
        binding.rowHideName.setOnClickListener { viewModel.setHideName(!binding.switchHideName.isChecked) }
        binding.rowBiometricOnly.setOnClickListener { viewModel.setBiometricOnly(!binding.switchBiometricOnly.isChecked) }
        binding.btnSave.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.savedFlow.collect {
                        Toast.makeText(requireContext(), R.string.perapp_saved, Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
            }
        }
    }

    private fun render(state: PerAppLockUiState) {
        if (!state.loaded) return
        binding.tvAppName.text = state.appName
        binding.switchUseCustom.isChecked = state.useCustom
        binding.customGroup.isVisible = state.useCustom
        binding.switchHideName.isChecked = state.hideName
        binding.switchBiometricOnly.isChecked = state.biometricOnly
        binding.tvUnlockMethod.setText(unlockMethodLabel(state.unlockMethod))
        binding.tvDelay.text = state.delayMode?.let { getString(AppLockLabels.delay(AppLockDelayMode.fromStorage(it))) }
            ?: getString(R.string.applock_use_global)
        binding.tvFakeMode.setText(AppLockLabels.fakeMode(FakeMode.fromStorage(state.fakeMode)))
    }

    private fun unlockMethodLabel(value: String): Int = when (value) {
        "PIN" -> R.string.unlock_method_pin
        "PATTERN" -> R.string.unlock_method_pattern
        "BIOMETRIC" -> R.string.perapp_method_biometric
        else -> R.string.applock_use_default
    }

    private fun showUnlockMethodPicker() {
        val values = listOf("DEFAULT", "PIN", "PATTERN", "BIOMETRIC")
        val labels = values.map { getString(unlockMethodLabel(it)) }.toTypedArray()
        val checked = values.indexOf(viewModel.state.value.unlockMethod).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perapp_unlock_method)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setUnlockMethod(values[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDelayPicker() {
        val options = listOf<AppLockDelayMode?>(null) + AppLockLabels.GLOBAL_DELAY_OPTIONS
        val labels = options.map {
            if (it == null) getString(R.string.applock_use_global) else getString(AppLockLabels.delay(it))
        }.toTypedArray()
        val current = viewModel.state.value.delayMode?.let { AppLockDelayMode.fromStorage(it) }
        val checked = options.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perapp_lock_delay)
            .setSingleChoiceItems(labels, checked) { d, which ->
                d.dismiss(); viewModel.setDelayMode(options[which]?.name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFakeModePicker() {
        val values = listOf(FakeMode.USE_GLOBAL) + AppLockLabels.GLOBAL_FAKE_OPTIONS
        val labels = values.map { getString(AppLockLabels.fakeMode(it)) }.toTypedArray()
        val checked = values.indexOf(FakeMode.fromStorage(viewModel.state.value.fakeMode)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perapp_fake_mode)
            .setSingleChoiceItems(labels, checked) { d, which -> d.dismiss(); viewModel.setFakeMode(values[which].name) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(packageName: String, appName: String): PerAppLockSettingsBottomSheet =
            PerAppLockSettingsBottomSheet().apply {
                arguments = bundleOf(
                    PerAppLockSettingsViewModel.ARG_PACKAGE to packageName,
                    PerAppLockSettingsViewModel.ARG_APP_NAME to appName
                )
            }
    }
}
