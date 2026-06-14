package app.lock.photo.valut.domain.repository

import app.lock.photo.valut.domain.model.AutoLockMode
import app.lock.photo.valut.domain.model.UnlockMethod
import kotlinx.coroutines.flow.Flow

/**
 * Facade over non-credential security settings and flags. Credential crypto lives
 * in the dedicated security managers; this exposes user-facing preferences.
 */
interface SettingsRepository {

    val onboardingCompleted: Flow<Boolean>
    val pinCreated: Flow<Boolean>
    val patternEnabled: Flow<Boolean>
    val biometricEnabled: Flow<Boolean>
    val appLockEnabled: Flow<Boolean>
    val pinLength: Flow<Int>
    val unlockMethod: Flow<UnlockMethod>
    val autoLockMode: Flow<AutoLockMode>

    suspend fun completeOnboarding()
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setUnlockMethod(method: UnlockMethod)
    suspend fun setAutoLockMode(mode: AutoLockMode)
}
