package app.lock.photo.valut.core.applock

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks a short "recently verified" session so the user isn't asked to re-authenticate
 * for every sensitive App Lock change in a row. Only a timestamp is stored — never the
 * credential. Default window is 2 minutes.
 */
@Singleton
class VerifySessionManager @Inject constructor(
    private val dataStore: AppSettingsDataStore
) {

    suspend fun markVerified() {
        dataStore.setLastSecurityVerificationTime(System.currentTimeMillis())
    }

    suspend fun isVerificationStillValid(): Boolean {
        val last = dataStore.lastSecurityVerificationTime.first()
        if (last <= 0L) return false
        val timeout = dataStore.verifySessionTimeoutMillis.first()
        return System.currentTimeMillis() - last < timeout
    }

    suspend fun clearVerification() {
        dataStore.setLastSecurityVerificationTime(0L)
    }
}
