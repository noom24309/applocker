package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.applock.VerifySessionManager
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.AppLockDelayMode
import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.features.applock.model.AppLockSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val serviceManager: AppLockServiceManager,
    private val verifySessionManager: VerifySessionManager,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val relockGroup = combine(
        dataStore.relockAfterScreenOff,
        dataStore.relockAfterAppSwitch,
        dataStore.relockAfterDeviceLock
    ) { screenOff, appSwitch, deviceLock -> Triple(screenOff, appSwitch, deviceLock) }

    private val toggleGroup = combine(
        dataStore.lockNewAppsAutomatically,
        dataStore.appLockNotificationEnabled,
        dataStore.localStatsEnabled,
        dataStore.hideRecentPreview
    ) { lockNew, notif, stats, hideRecent -> arrayOf<Any?>(lockNew, notif, stats, hideRecent) }

    private val disguiseGroup = combine(
        dataStore.globalLockTheme,
        dataStore.defaultFakeMode,
        dataStore.hideAppNameOnLock,
        dataStore.restartProtectionAfterBoot,
        settingsRepository.unlockMethod
    ) { theme, fake, hideName, restartBoot, method ->
        arrayOf<Any?>(theme, fake, hideName, restartBoot, method)
    }

    val uiState: StateFlow<AppLockSettingsUiState> = combine(
        dataStore.appLockFeatureEnabled,
        dataStore.appLockDelayMode,
        relockGroup,
        toggleGroup,
        disguiseGroup
    ) { enabled, delayStr, relock, toggles, disguise ->
        AppLockSettingsUiState(
            appLockEnabled = enabled,
            delayMode = AppLockDelayMode.fromStorage(delayStr),
            relockAfterScreenOff = relock.first,
            relockAfterAppSwitch = relock.second,
            relockAfterDeviceLock = relock.third,
            lockNewAppsAutomatically = toggles[0] as Boolean,
            notificationEnabled = toggles[1] as Boolean,
            localStatsEnabled = toggles[2] as Boolean,
            hideRecentPreview = toggles[3] as Boolean,
            theme = LockTheme.fromStorage(disguise[0] as String?),
            defaultFakeMode = FakeMode.fromStorage(disguise[1] as String?),
            hideAppName = disguise[2] as Boolean,
            restartAfterBoot = disguise[3] as Boolean,
            usesPattern = (disguise[4] as UnlockMethod).usesPattern
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettingsUiState())

    // --- verification gate (2-minute verified session) ---

    suspend fun needsVerification(): Boolean = !verifySessionManager.isVerificationStillValid()

    fun markVerified() {
        viewModelScope.launch { verifySessionManager.markVerified() }
    }

    // --- setters ---

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAppLockFeatureEnabled(enabled)
            if (enabled) {
                if (serviceManager.canStartProtection()) serviceManager.startProtection()
            } else {
                serviceManager.stopProtection()
            }
        }
    }

    fun setDelayMode(mode: AppLockDelayMode) {
        viewModelScope.launch { dataStore.setAppLockDelayMode(mode.name) }
    }

    fun setTheme(theme: LockTheme) {
        viewModelScope.launch { dataStore.setGlobalLockTheme(theme.name) }
    }

    fun setDefaultFakeMode(mode: FakeMode) {
        viewModelScope.launch { dataStore.setDefaultFakeMode(mode.name) }
    }

    fun setRelockAfterScreenOff(value: Boolean) {
        viewModelScope.launch { dataStore.setRelockAfterScreenOff(value) }
    }

    fun setRelockAfterAppSwitch(value: Boolean) {
        viewModelScope.launch { dataStore.setRelockAfterAppSwitch(value) }
    }

    fun setRelockAfterDeviceLock(value: Boolean) {
        viewModelScope.launch { dataStore.setRelockAfterDeviceLock(value) }
    }

    fun setLockNewAppsAutomatically(value: Boolean) {
        viewModelScope.launch { dataStore.setLockNewAppsAutomatically(value) }
    }

    fun setNotificationEnabled(value: Boolean) {
        viewModelScope.launch {
            dataStore.setAppLockNotificationEnabled(value)
            if (serviceManager.isServiceRunning()) serviceManager.restartProtection()
        }
    }

    fun setLocalStatsEnabled(value: Boolean) {
        viewModelScope.launch { dataStore.setLocalStatsEnabled(value) }
    }

    fun setHideRecentPreview(value: Boolean) {
        viewModelScope.launch { dataStore.setHideRecentPreview(value) }
    }

    fun setRestartAfterBoot(value: Boolean) {
        viewModelScope.launch { dataStore.setRestartProtectionAfterBoot(value) }
    }

    fun setHideAppName(value: Boolean) {
        viewModelScope.launch { dataStore.setHideAppNameOnLock(value) }
    }
}
