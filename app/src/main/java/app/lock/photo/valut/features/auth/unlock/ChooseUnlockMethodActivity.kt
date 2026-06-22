package app.lock.photo.valut.features.auth.unlock
import app.lock.photo.valut.features.auth.biometric.BiometricSetupActivity
import app.lock.photo.valut.features.auth.pin.CreatePinActivity
import app.lock.photo.valut.features.auth.pattern.PatternSetupActivity

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityChooseUnlockMethodBinding
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-run credential picker. Lets the user choose Pattern (the default) or PIN as
 * the master unlock method before setting it up. Fingerprint unlock is offered later
 * as an optional add-on in [BiometricSetupActivity].
 */
@AndroidEntryPoint
class ChooseUnlockMethodActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityChooseUnlockMethodBinding

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var patternSelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseUnlockMethodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reaching the credential picker means onboarding is finished — persist it so relaunches
        // route here (or onward) instead of repeating onboarding.
        lifecycleScope.launch { settingsRepository.completeOnboarding() }

        binding.cardPattern.setOnClickListener { select(pattern = true) }
        binding.cardPin.setOnClickListener { select(pattern = false) }
        binding.btnContinue.setOnClickListener { proceed() }

        select(pattern = true)
    }

    private fun select(pattern: Boolean) {
        patternSelected = pattern
        binding.checkPattern.isInvisible = !pattern
        binding.checkPin.isInvisible = pattern
    }

    private fun proceed() {
        val intent = if (patternSelected) {
            PatternSetupActivity.firstRunIntent(this)
        } else {
            Intent(this, CreatePinActivity::class.java)
        }
        startActivity(intent)
        // Keep this screen so the user can come back and switch method before finishing.
    }
}
