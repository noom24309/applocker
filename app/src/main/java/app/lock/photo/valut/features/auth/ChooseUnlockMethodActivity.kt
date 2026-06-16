package app.lock.photo.valut.features.auth

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isInvisible
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityChooseUnlockMethodBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * First-run credential picker. Lets the user choose Pattern (the default) or PIN as
 * the master unlock method before setting it up. Fingerprint unlock is offered later
 * as an optional add-on in [BiometricSetupActivity].
 */
@AndroidEntryPoint
class ChooseUnlockMethodActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityChooseUnlockMethodBinding

    private var patternSelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseUnlockMethodBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
