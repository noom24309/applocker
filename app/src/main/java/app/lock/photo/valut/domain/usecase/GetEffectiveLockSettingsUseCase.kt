package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.domain.model.AppLockDelayMode
import app.lock.photo.valut.domain.model.EffectiveLockSettings
import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves the effective lock behaviour for a package: per-app overrides (when the app
 * has [useCustomSettings]) layered on top of the global App Lock settings.
 */
class GetEffectiveLockSettingsUseCase @Inject constructor(
    private val lockedAppDao: LockedAppDao,
    private val dataStore: AppSettingsDataStore,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(packageName: String, fallbackAppName: String): EffectiveLockSettings {
        val entity = lockedAppDao.getLockedApp(packageName)
        val useCustom = entity?.useCustomSettings == true

        val globalMethod = settingsRepository.unlockMethod.first()
        val globalDelay = AppLockDelayMode.fromStorage(dataStore.appLockDelayMode.first())
        val globalFake = FakeMode.fromStorage(dataStore.defaultFakeMode.first()).resolveGlobal()
        val globalTheme = LockTheme.fromStorage(dataStore.globalLockTheme.first()).resolveGlobal()
        val globalHideName = dataStore.hideAppNameOnLock.first()

        val method = when {
            useCustom && entity?.customUnlockMethod != null &&
                entity.customUnlockMethod != "DEFAULT" &&
                entity.customUnlockMethod != "BIOMETRIC" ->
                UnlockMethod.fromStorage(entity.customUnlockMethod)
            else -> globalMethod
        }

        val delay = if (useCustom && entity?.customLockDelayMode != null) {
            AppLockDelayMode.fromStorage(entity.customLockDelayMode)
        } else {
            globalDelay
        }

        val grace = if (useCustom && entity?.customTemporaryUnlockMillis != null) {
            entity.customTemporaryUnlockMillis
        } else {
            delay.durationMillis
        }

        val appFake = FakeMode.fromStorage(entity?.fakeMode)
        val fakeMode = if (appFake == FakeMode.USE_GLOBAL) globalFake else appFake

        val appTheme = LockTheme.fromStorage(entity?.customLockTheme)
        val theme = if (appTheme == LockTheme.USE_GLOBAL) globalTheme else appTheme

        val hideName = globalHideName || (useCustom && entity?.hideAppNameOnLock == true)
        val biometricOnly = useCustom &&
            (entity?.requireBiometricOnly == true || entity?.customUnlockMethod == "BIOMETRIC")

        return EffectiveLockSettings(
            packageName = packageName,
            appName = entity?.appName ?: fallbackAppName,
            unlockMethod = method,
            delayMode = delay,
            graceMillis = grace,
            fakeMode = fakeMode,
            theme = theme,
            hideAppName = hideName,
            requireBiometricOnly = biometricOnly
        )
    }

    private fun FakeMode.resolveGlobal(): FakeMode =
        if (this == FakeMode.USE_GLOBAL) FakeMode.NONE else this

    private fun LockTheme.resolveGlobal(): LockTheme =
        if (this == LockTheme.USE_GLOBAL) LockTheme.GLOBAL_DEFAULT else this
}
