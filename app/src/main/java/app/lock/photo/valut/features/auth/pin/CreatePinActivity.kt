package app.lock.photo.valut.features.auth.pin

import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.databinding.ActivityCreatePinBinding
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.features.auth.pattern.PatternSetupActivity
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First step of PIN setup, and the first screen after the splash for a fresh install:
 * enter a 4-digit PIN. The PIN is held only in an in-memory session (never an Intent
 * extra) and confirmed next. Users who'd rather draw a pattern switch from here — that
 * inline shortcut replaced the old unlock-method picker screen.
 */
@AndroidEntryPoint
class CreatePinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_create_pin
    private lateinit var binding: ActivityCreatePinBinding
    private val viewModel: CreatePinViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun createContentView(): View {
        binding = ActivityCreatePinBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewReady() {
        // Drawn on the splash background, so the system bars need light icons.
        useLightSystemBarIcons()

        // Reaching credential setup means onboarding is finished — persist it so relaunches
        // route here (or onward) instead of repeating onboarding.
        lifecycleScope.launch { settingsRepository.completeOnboarding() }

        binding.btnUsePattern.setOnClickListener {
            startActivity(PatternSetupActivity.firstRunIntent(this))
        }

        // Setup is always a 4-digit PIN — no length picker on this screen.
        viewModel.setLength(Constants.PIN_LENGTH_4)
        applyPinLength(Constants.PIN_LENGTH_4)

        loadNativeAd()

        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        viewModel.submitPin(pin)
    }

    private fun loadNativeAd() {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.NativePassCode),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativePassCode),
            canReloadAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            logTag = "CreatePin"
        )
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        CreatePinViewModel.Event.Proceed -> {
                            startActivity(Intent(this@CreatePinActivity, ConfirmPinActivity::class.java))
                        }
                    }
                }
            }
        }
    }
}
