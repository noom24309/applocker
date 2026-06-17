package app.lock.photo.valut.features.splash

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.features.auth.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    // Full-bleed branded splash: the gradient must reach behind the status/nav bars.
    override val applyEdgeToEdgeInsets: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeRoute()
        viewModel.resolveStartDestination()
    }

    private fun observeRoute() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.route.collect { route ->
                    route?.let(::navigateTo)
                }
            }
        }
    }

    private fun navigateTo(route: SplashRoute) {
        val intent = when (route.destination) {
            StartDestination.ONBOARDING -> Intent(this, OnboardingActivity::class.java)
            StartDestination.SETUP_CREDENTIAL -> Intent(this, ChooseUnlockMethodActivity::class.java)
            StartDestination.LOCKED -> LockRouter.lockIntent(this, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }
}
