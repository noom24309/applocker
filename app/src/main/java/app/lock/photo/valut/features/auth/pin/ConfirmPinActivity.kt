package app.lock.photo.valut.features.auth.pin
import app.lock.photo.valut.features.auth.recovery.RecoveryKeyActivity

import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityConfirmPinBinding
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Second step of PIN setup: re-enter the PIN to confirm it, persist it, then move
 * on to the one-time recovery key.
 */
@AndroidEntryPoint
class ConfirmPinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_confirm_pin
    private lateinit var binding: ActivityConfirmPinBinding

    private val viewModel: ConfirmPinViewModel by viewModels()

    override fun createContentView(): View {
        binding = ActivityConfirmPinBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewReady() {
        // Drawn on the splash background, so the system bars need light icons.
        useLightSystemBarIcons()
        applyPinLength(viewModel.expectedLength)
        observeEvents()

        loadNativeAd()
    }

    override fun onPinEntered(pin: String) {
        viewModel.confirm(pin)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        ConfirmPinViewModel.Event.Mismatch ->
                            showError(getString(R.string.pin_mismatch))
                        ConfirmPinViewModel.Event.Error ->
                            showError(getString(R.string.pin_save_failed))
                        ConfirmPinViewModel.Event.Saved -> {
                            startActivity(Intent(this@ConfirmPinActivity, RecoveryKeyActivity::class.java))
                            finishAffinity()
                        }
                    }
                }
            }
        }
    }



    private fun loadNativeAd() {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.NativePassCodeConfirm),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativePassCodeConfirm),
            canReloadAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            logTag = "ConfirmPin"
        )
    }
}
