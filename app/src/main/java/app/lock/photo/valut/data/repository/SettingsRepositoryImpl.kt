package app.lock.photo.valut.data.repository

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.AppearanceMode
import app.lock.photo.valut.domain.model.AutoLockMode
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppSettingsDataStore
) : SettingsRepository {

    override val onboardingCompleted: Flow<Boolean> = dataStore.onboardingCompleted
    override val pinCreated: Flow<Boolean> = dataStore.pinCreated
    override val patternEnabled: Flow<Boolean> = dataStore.patternEnabled
    override val biometricEnabled: Flow<Boolean> = dataStore.biometricEnabled
    override val appLockEnabled: Flow<Boolean> = dataStore.appLockEnabled
    override val pinLength: Flow<Int> = dataStore.pinLength

    override val unlockMethod: Flow<UnlockMethod> =
        dataStore.unlockMethod.map { UnlockMethod.fromStorage(it) }

    override val autoLockMode: Flow<AutoLockMode> =
        dataStore.autoLockMode.map { AutoLockMode.fromStorage(it) }

    override val appearanceMode: Flow<AppearanceMode> =
        dataStore.appearanceMode.map { AppearanceMode.fromStorage(it) }

    override suspend fun completeOnboarding() = dataStore.setOnboardingCompleted(true)

    override suspend fun setBiometricEnabled(enabled: Boolean) =
        dataStore.setBiometricEnabled(enabled)

    override suspend fun setAppLockEnabled(enabled: Boolean) =
        dataStore.setAppLockEnabled(enabled)

    override suspend fun setUnlockMethod(method: UnlockMethod) =
        dataStore.setUnlockMethod(method.name)

    override suspend fun setAutoLockMode(mode: AutoLockMode) =
        dataStore.setAutoLockMode(mode.name)

    override suspend fun setAppearanceMode(mode: AppearanceMode) =
        dataStore.setAppearanceMode(mode.name)
}
