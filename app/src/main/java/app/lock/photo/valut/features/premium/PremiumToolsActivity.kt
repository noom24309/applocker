package app.lock.photo.valut.features.premium

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.databinding.ActivityPremiumToolsBinding
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.features.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.cleanup.largefiles.LargeFilesActivity
import app.lock.photo.valut.features.cleanup.smartcleanup.SmartCleanupActivity
import app.lock.photo.valut.features.cleanup.storage.StorageAnalyzerActivity
import app.lock.photo.valut.features.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.vault.VaultActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tools dashboard — cleanup + security tools, matching the home design language. */
@AndroidEntryPoint
class PremiumToolsActivity : BaseActivity() {

    private lateinit var binding: ActivityPremiumToolsBinding

    @Inject lateinit var appLockStateManager: AppLockStateManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettings.setOnClickListener {
            startActivity(MainActivity.settingsIntent(this))
            finish()
        }

        // Cleanup tools.
        binding.cardDuplicates.setOnClickListener { startActivity(DuplicateFinderActivity.intent(this)) }
        binding.cardLargeFiles.setOnClickListener { startActivity(LargeFilesActivity.intent(this)) }
        binding.cardSmartCleanup.setOnClickListener { startActivity(SmartCleanupActivity.intent(this)) }
        binding.cardStorage.setOnClickListener { startActivity(StorageAnalyzerActivity.intent(this)) }

        // Security & privacy.
        binding.cardHealth.setOnClickListener { startActivity(VaultHealthActivity.intent(this)) }
        binding.cardPrivacyScan.setOnClickListener { comingSoon() }
        binding.cardPermissions.setOnClickListener { comingSoon() }
        binding.cardSecureDelete.setOnClickListener { comingSoon() }

        binding.btnRunScan.setOnClickListener { startActivity(SmartCleanupActivity.intent(this)) }

        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_tools
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()
                    true
                }
                R.id.nav_vault -> {
                    startActivity(VaultActivity.intent(this))
                    false
                }
                else -> true // already on Tools
            }
        }
    }

    private fun comingSoon() {
        Toast.makeText(this, R.string.tools_coming_soon, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_tools
        lifecycleScope.launch {
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(
                        this@PremiumToolsActivity,
                        settingsRepository.unlockMethod.first()
                    )
                )
                finish()
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, PremiumToolsActivity::class.java)
    }
}
