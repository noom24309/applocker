package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.security.KeystoreManager
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves the initial screen for the splash flow from persisted state. A cold
 * start always requires unlocking when a credential exists (the session is not
 * carried across process death).
 */
class GetStartDestinationUseCase @Inject constructor(
    private val repository: SettingsRepository,
    private val dataStore: AppSettingsDataStore,
    private val keystoreManager: KeystoreManager
) {
    suspend operator fun invoke(): StartDestination {
        val hasCredential = repository.pinCreated.first() || repository.patternEnabled.first()
        if (!hasCredential) return StartDestination.SETUP_CREDENTIAL
        // Credential flags without the Keystore pepper = data restored from a backup /
        // device transfer. Those hashes can never verify (the pepper died with the old
        // install), so locking would brick the app — reset and run setup again.
        if (!keystoreManager.hasPepperKey()) {
            dataStore.clearPin()
            dataStore.clearPattern()
            return StartDestination.SETUP_CREDENTIAL
        }
        return StartDestination.LOCKED
    }
}
