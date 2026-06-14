package app.lock.photo.valut.core.security

import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.LockoutStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks wrong unlock attempts and the resulting temporary lockouts. State is
 * persisted in DataStore so lockouts survive app restarts.
 *
 * Policy: 3 → warning, 5 → 30s lock, 10 → 5min lock.
 */
@Singleton
class WrongAttemptManager @Inject constructor(
    private val dataStore: AppSettingsDataStore
) {

    /** Records a wrong attempt, applies any lockout, and returns the new status. */
    suspend fun recordWrongAttempt(): LockoutStatus {
        val count = dataStore.wrongAttemptCount.first() + 1
        dataStore.setWrongAttemptCount(count)

        val now = System.currentTimeMillis()
        val lockEnd = when {
            count >= Constants.ATTEMPTS_LONG_LOCK -> now + Constants.LONG_LOCK_MILLIS
            count >= Constants.ATTEMPTS_SHORT_LOCK -> now + Constants.SHORT_LOCK_MILLIS
            else -> 0L
        }
        if (lockEnd > 0L) dataStore.setLockoutEndTime(lockEnd)

        return LockoutStatus(
            attemptCount = count,
            lockedOut = lockEnd > 0L,
            remainingMillis = if (lockEnd > 0L) lockEnd - now else 0L
        )
    }

    suspend fun resetAttempts() = dataStore.resetAttempts()

    suspend fun isLockedOut(): Boolean = getLockoutRemainingMillis() > 0L

    suspend fun getLockoutRemainingMillis(): Long {
        val end = dataStore.lockoutEndTime.first()
        val remaining = end - System.currentTimeMillis()
        return if (remaining > 0L) remaining else 0L
    }

    suspend fun getAttemptCount(): Int = dataStore.wrongAttemptCount.first()

    /** Current status without recording a new attempt. */
    suspend fun currentStatus(): LockoutStatus {
        val remaining = getLockoutRemainingMillis()
        return LockoutStatus(
            attemptCount = getAttemptCount(),
            lockedOut = remaining > 0L,
            remainingMillis = remaining
        )
    }
}
