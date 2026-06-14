package app.lock.photo.valut.core.security

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.SecurityResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master PIN authority: creation, verification and change. Stores only a salted,
 * peppered hash — never the raw PIN. The PIN is never logged or persisted in clear.
 */
@Singleton
class PinSecurityManager @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val hasher: CredentialHasher,
    private val weakChecker: WeakCredentialChecker
) {

    suspend fun isPinCreated(): Boolean = dataStore.pinCreated.first()

    /** Creates a new PIN. Rejects weak PINs. */
    suspend fun createPin(pin: String, length: Int): SecurityResult {
        if (weakChecker.isWeakPin(pin)) return SecurityResult.WeakCredential
        persist(pin, length)
        return SecurityResult.Success
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = dataStore.pinHash.first()
        val salt = dataStore.pinSalt.first()
        return hasher.verify(pin.toCharArray(), hash, salt)
    }

    /**
     * Changes the PIN after verifying [oldPin]. Disallows reusing the old PIN and
     * weak PINs. Returns a [SecurityResult] describing the outcome.
     */
    suspend fun changePin(oldPin: String, newPin: String, newLength: Int): SecurityResult {
        if (!verifyPin(oldPin)) {
            return SecurityResult.WrongCredential(attemptCount = 0)
        }
        if (oldPin == newPin) return SecurityResult.SameAsOld
        if (weakChecker.isWeakPin(newPin)) return SecurityResult.WeakCredential
        persist(newPin, newLength)
        return SecurityResult.Success
    }

    /** Used by the recovery flow only. No-op unless [allowed] is true. */
    suspend fun clearPinForResetOnlyIfAllowed(allowed: Boolean) {
        if (allowed) dataStore.clearPin()
    }

    private suspend fun persist(pin: String, length: Int) {
        val hashed = hasher.hash(pin.toCharArray())
        dataStore.savePin(hash = hashed.hash, salt = hashed.salt, length = length)
    }
}
