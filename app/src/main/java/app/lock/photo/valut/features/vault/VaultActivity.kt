package app.lock.photo.valut.features.vault

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.databinding.ActivityVaultBinding
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.repository.VaultRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host for all vault fragments. Applies FLAG_SECURE so the whole vault is excluded
 * from screenshots/recents, and re-checks the lock state on resume.
 */
@AndroidEntryPoint
class VaultActivity : BaseActivity() {

    private lateinit var binding: ActivityVaultBinding

    @Inject
    lateinit var appLockStateManager: AppLockStateManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var secureCacheManager: SecureCacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            replace(PrivateVaultFragment(), addToBackStack = false)
            maybeStartMigration()
        }
    }

    override fun onResume() {
        super.onResume()
        // Never expose the vault while locked. Use the session-locked check (not the full
        // auto-lock policy) so opening a photo/video and returning doesn't re-lock the vault;
        // real background transitions are handled by AppLifecycleObserver.
        lifecycleScope.launch {
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(
                        this@VaultActivity,
                        settingsRepository.unlockMethod.first(),
                        app.lock.photo.valut.domain.model.IntruderTrigger.VAULT_UNLOCK
                    )
                )
                finish()
                return@launch
            }
            // Any decrypted temp left over from a closed viewer/player is cleared on return.
            runCatching { secureCacheManager.clearAllDecryptedTempFiles() }
        }
    }

    /** If pre-Phase-4 plain files remain, route to the encryption screen before browsing. */
    private fun maybeStartMigration() {
        lifecycleScope.launch {
            val unencrypted = vaultRepository.observeUnencryptedCount().first()
            if (unencrypted > 0) {
                startActivity(EncryptionMigrationActivity.intent(this@VaultActivity))
            }
        }
    }

    fun openGrid(source: GridSource, albumId: Long = -1L, title: String? = null) {
        replace(MediaGridFragment.newInstance(source, albumId, title))
    }

    fun openAlbums() = replace(AlbumsFragment())

    fun openAlbumDetail(albumId: Long, name: String) {
        replace(MediaGridFragment.newInstance(GridSource.ALBUM, albumId, name))
    }

    private fun replace(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.commit {
            replace(binding.vaultContainer.id, fragment)
            if (addToBackStack) addToBackStack(null)
        }
    }

    companion object {
        fun intent(context: android.content.Context) = Intent(context, VaultActivity::class.java)
    }
}
