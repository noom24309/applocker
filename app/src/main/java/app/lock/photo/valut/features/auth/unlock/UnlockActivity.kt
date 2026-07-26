package app.lock.photo.valut.features.auth.unlock

import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.lock.LockScreen
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.features.auth.pin.BasePinActivity
import app.lock.photo.valut.features.auth.recovery.ForgotPinActivity
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.nextgen.ads.appopen.NextGenAppOpen
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PIN unlock screen. Enforces the wrong-attempt lockout (with live countdown),
 * supports biometric unlock when enabled, and offers recovery via "Forgot PIN?".
 */
@AndroidEntryPoint
class UnlockActivity : BasePinActivity(), LockScreen {

    override val layoutRes: Int = R.layout.activity_unlock

    private val viewModel: UnlockViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    @Inject
    lateinit var permissionChecker: AppLockPermissionChecker

    private lateinit var lockoutTimer: TextView
    private var appliedLength = -1
    private var biometricReady = false
    private var biometricPrompted = false

    override fun onViewReady() {
        lockoutTimer = findViewById(R.id.tvLockoutTimer)
        findViewById<View>(R.id.btnForgotPin).setOnClickListener {
            startActivity(Intent(this, ForgotPinActivity::class.java))
        }

        Log.e("TAG**********", "onCreate: bbbbbbbbbbbbbb", )

        observeState()
        observeEvents()
        setupBiometric()
        loadNativeAd()
    }

    /** Native ad above the keypad — this is the screen every relaunch lands on. */
    private fun loadNativeAd() {
        val container = findViewById<FrameLayout>(R.id.flAdNative) ?: return
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = container,
            nativeId = getString(R.string.NativePassCode),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativePattern && RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativePassCode),
            canReloadAds = RemoteConfig.nativePattern && RemoteConfig.enableAllAds,
            logTag = "Pin_Unlock"
        )
    }

    override fun onPinEntered(pin: String) {
        viewModel.verifyPin(pin)
    }

    override fun onBiometricClicked() {
        if (biometricReady) showBiometricPrompt()
    }

    private fun setupBiometric() {
        lifecycleScope.launch {
            biometricReady = viewModel.isBiometricEnabled() &&
                biometricHelper.isBiometricAvailable(this@UnlockActivity)
            setBiometricVisible(biometricReady)
            if (biometricReady && !biometricPrompted) {
                biometricPrompted = true
                showBiometricPrompt()
            }
        }
    }

    private fun showBiometricPrompt() {
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = { viewModel.onBiometricSuccess() },
            onError = { /* Fall back to PIN entry; PIN counter is untouched. */ }
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.pinLength != appliedLength) {
                        appliedLength = state.pinLength
                        applyPinLength(state.pinLength)
                    }
                    renderLockout(state.lockedOut, state.remainingMillis)
                }
            }
        }
    }

    private fun renderLockout(lockedOut: Boolean, remainingMillis: Long) {
        setKeypadEnabled(!lockedOut)
        lockoutTimer.isVisible = lockedOut
        if (lockedOut) {
            lockoutTimer.text = getString(R.string.lockout_try_again, formatRemaining(remainingMillis))
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        UnlockViewModel.Event.Success -> goToMain()
                        is UnlockViewModel.Event.Wrong -> {
                            if (!event.lockedOut) {
                                showError(getString(R.string.unlock_wrong_pin, event.attemptCount))
                            } else {
                                clearPin()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Straight to home, unless not a single App Lock permission is granted — then the
     * permission gate comes first, so a relaunch always offers protection setup again.
     */
    private fun goToMain() {
        val next = if (permissionChecker.hasNoAppLockPermissions()) {
            AppLockPermissionActivity.gateIntent(this)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
