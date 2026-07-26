package app.lock.photo.valut.features.auth.pin

import android.view.View
import app.lock.photo.valut.core.ui.showToast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.databinding.ActivityChangePinBinding
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Multi-step Change-PIN: verify current PIN → enter new PIN → confirm. New PINs are
 * always 4 digits; the verify step follows whatever length the stored PIN has. In
 * recovery reset mode the verify step is skipped.
 */
@AndroidEntryPoint
class ChangePinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_change_pin
    private lateinit var binding: ActivityChangePinBinding

    private val viewModel: ChangePinViewModel by viewModels()

    private var lastStep: ChangePinViewModel.Step? = null

    override fun createContentView(): View {
        binding = ActivityChangePinBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewReady() {
        // Drawn on the splash background, so the system bars need light icons.
        useLightSystemBarIcons()

        val resetMode = intent.getBooleanExtra(Constants.EXTRA_RESET_MODE, false)
        viewModel.configure(resetMode)

        loadNativeAd()
        observeStep()
        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        when (viewModel.step.value) {
            ChangePinViewModel.Step.VERIFY_OLD -> viewModel.verifyOld(pin)
            ChangePinViewModel.Step.ENTER_NEW -> viewModel.submitNew(pin)
            ChangePinViewModel.Step.CONFIRM_NEW -> viewModel.confirmNew(pin)
        }
    }

    private fun loadNativeAd() {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.NativeChangePin),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativeChangePin),
            canReloadAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            logTag = "ChangePin"
        )
    }

    private fun observeStep() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.step.collect { step ->
                    if (step == lastStep) return@collect
                    lastStep = step
                    renderStep(step)
                }
            }
        }
    }

    private fun renderStep(step: ChangePinViewModel.Step) {
        when (step) {
            ChangePinViewModel.Step.VERIFY_OLD -> {
                titleView.setText(R.string.change_pin_verify_title)
                subtitleView.setText(R.string.change_pin_verify_subtitle)
                // The current PIN may still be an older 6-digit one.
                lifecycleScope.launch { applyPinLength(viewModel.oldPinLength()) }
            }
            ChangePinViewModel.Step.ENTER_NEW -> {
                titleView.setText(R.string.change_pin_new_title)
                subtitleView.setText(R.string.change_pin_new_subtitle)
                viewModel.setNewLength(Constants.PIN_LENGTH_4)
                applyPinLength(Constants.PIN_LENGTH_4)
            }
            ChangePinViewModel.Step.CONFIRM_NEW -> {
                titleView.setText(R.string.change_pin_confirm_title)
                subtitleView.setText(R.string.change_pin_confirm_subtitle)
                applyPinLength(Constants.PIN_LENGTH_4)
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        is ChangePinViewModel.Event.WrongOld ->
                            showError(getString(R.string.unlock_wrong_pin, event.attemptCount))
                        ChangePinViewModel.Event.SameAsOld ->
                            showError(getString(R.string.change_pin_same))
                        ChangePinViewModel.Event.WeakNew ->
                            showError(getString(R.string.pin_save_failed))
                        ChangePinViewModel.Event.Mismatch ->
                            showError(getString(R.string.pin_mismatch))
                        ChangePinViewModel.Event.Error ->
                            showError(getString(R.string.pin_save_failed))
                        ChangePinViewModel.Event.Saved -> {
                            showToast(R.string.change_pin_success)
                            finish()
                        }
                        ChangePinViewModel.Event.OldVerified,
                        ChangePinViewModel.Event.ProceedConfirm -> Unit // handled by step flow
                    }
                }
            }
        }
    }
}
