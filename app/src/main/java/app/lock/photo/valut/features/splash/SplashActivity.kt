package app.lock.photo.valut.features.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.wastickers.romantic.stickers.loveromance.ui.language.LanguageActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Branded splash. Shows the brand for a fixed duration while [SplashViewModel] resolves the
 * start destination (onboarding / setup-credential / locked), then navigates once both the
 * timer and the resolved route are ready.
 */
@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    // Full-bleed branded splash: the gradient must reach behind the status/nav bars.
    override val applyEdgeToEdgeInsets: Boolean = false

    private val handler = Handler(Looper.getMainLooper())

    // Navigation source of truth — held until the splash timer completes.
    private var pendingRoute: SplashRoute? = null
    private var readyToNavigate = false
    private var isNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeRoute()
        viewModel.resolveStartDestination()

        handler.postDelayed({
            readyToNavigate = true
            tryNavigate()
        }, SPLASH_DURATION)
    }

    private fun observeRoute() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.route.collect { route ->
                    route?.let {
                        pendingRoute = it
                        tryNavigate()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /** Navigates only once BOTH the splash timer is done and [SplashViewModel] has a route. */
    private fun tryNavigate() {
        if (!readyToNavigate || isNavigated) return
        val route = pendingRoute ?: return
        isNavigated = true
        val intent = when (route.destination) {
            StartDestination.ONBOARDING -> Intent(this, LanguageActivity::class.java)
            StartDestination.PERMISSION_GATE -> AppLockPermissionActivity.gateIntent(this)
            StartDestination.SETUP_CREDENTIAL -> Intent(this, ChooseUnlockMethodActivity::class.java)
            StartDestination.LOCKED -> LockRouter.lockIntent(this, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }

    private companion object {
        const val SPLASH_DURATION = 2500L
    }
}
