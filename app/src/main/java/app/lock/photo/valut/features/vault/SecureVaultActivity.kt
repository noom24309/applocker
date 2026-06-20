package app.lock.photo.valut.features.vault

import app.lock.photo.valut.core.ui.BaseActivity

import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Base for the vault browse screens. Applies FLAG_SECURE so vault content is excluded from
 * screenshots/recents, re-checks the session lock on resume (routing to the lock screen if the
 * session expired), and clears any decrypted temp files left by a closed viewer/player.
 *
 * Subclasses must be annotated with @AndroidEntryPoint for the injected fields to be supplied.
 */
abstract class SecureVaultActivity : BaseActivity() {

    @Inject lateinit var appLockStateManager: AppLockStateManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var secureCacheManager: SecureCacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        // Use the session-locked check (not the full auto-lock policy) so opening a photo/video
        // and returning doesn't re-lock; real background transitions go through AppLifecycleObserver.
        lifecycleScope.launch {
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(this@SecureVaultActivity, settingsRepository.unlockMethod.first())
                )
                finish()
                return@launch
            }
            runCatching { secureCacheManager.clearAllDecryptedTempFiles() }
        }
    }
}
