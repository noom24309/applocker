package app.lock.photo.valut.features.premium

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.databinding.ActivityPremiumToolsBinding
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.DocumentCardsRepository
import app.lock.photo.valut.domain.repository.PrivateDocumentsRepository
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import app.lock.photo.valut.features.premium.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.premium.cleanup.largefiles.LargeFilesActivity
import app.lock.photo.valut.features.premium.cleanup.smartcleanup.SmartCleanupActivity
import app.lock.photo.valut.features.premium.cleanup.storage.StorageAnalyzerActivity
import app.lock.photo.valut.features.premium.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.premium.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.premium.notes.PrivateNotesActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Phase 11 — Premium Tools dashboard. Only the implemented tools are shown (no fake buttons). */
@AndroidEntryPoint
class PremiumToolsActivity : BaseActivity() {

    private lateinit var binding: ActivityPremiumToolsBinding

    @Inject lateinit var appLockStateManager: AppLockStateManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var documentCardsRepository: DocumentCardsRepository
    @Inject lateinit var privateDocumentsRepository: PrivateDocumentsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.cardNotes.setOnClickListener { startActivity(PrivateNotesActivity.intent(this)) }
        binding.cardDocuments.setOnClickListener { startActivity(PrivateDocumentsActivity.intent(this)) }
        binding.cardDuplicates.setOnClickListener { startActivity(DuplicateFinderActivity.intent(this)) }
        binding.cardLargeFiles.setOnClickListener { startActivity(LargeFilesActivity.intent(this)) }
        binding.cardSmartCleanup.setOnClickListener { startActivity(SmartCleanupActivity.intent(this)) }
        binding.cardStorage.setOnClickListener { startActivity(StorageAnalyzerActivity.intent(this)) }
        binding.cardHealth.setOnClickListener { startActivity(VaultHealthActivity.intent(this)) }

        observeDocumentCounts()
    }

    /** Shows live "%d cards · %d files" on the Private Documents tile. */
    private fun observeDocumentCounts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    documentCardsRepository.observeActiveCount(VaultMode.REAL),
                    privateDocumentsRepository.observeDocuments(VaultMode.REAL)
                ) { cardCount, files -> cardCount to files.size }
                    .collect { (cardCount, fileCount) ->
                        binding.documentsSubtitle.text =
                            getString(R.string.documents_tools_summary, cardCount, fileCount)
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(
                        this@PremiumToolsActivity,
                        settingsRepository.unlockMethod.first(),
                        IntruderTrigger.VAULT_UNLOCK
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
