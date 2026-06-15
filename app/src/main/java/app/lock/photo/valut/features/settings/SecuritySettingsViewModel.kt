package app.lock.photo.valut.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.security.PatternSecurityManager
import app.lock.photo.valut.domain.model.AppearanceMode
import app.lock.photo.valut.domain.model.AutoLockMode
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockStateManager: AppLockStateManager,
    private val patternSecurityManager: PatternSecurityManager
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val biometricEnabled: StateFlow<Boolean> =
        settingsRepository.biometricEnabled.stateIn(viewModelScope, started, false)

    val patternEnabled: StateFlow<Boolean> =
        settingsRepository.patternEnabled.stateIn(viewModelScope, started, false)

    val unlockMethod: StateFlow<UnlockMethod> =
        settingsRepository.unlockMethod.stateIn(viewModelScope, started, UnlockMethod.DEFAULT)

    val autoLockMode: StateFlow<AutoLockMode> =
        settingsRepository.autoLockMode.stateIn(viewModelScope, started, AutoLockMode.DEFAULT)

    val appearanceMode: StateFlow<AppearanceMode> =
        settingsRepository.appearanceMode.stateIn(viewModelScope, started, AppearanceMode.DEFAULT)

    private val lockNowEvents = Channel<Unit>(Channel.BUFFERED)
    val lockNowFlow = lockNowEvents.receiveAsFlow()

    /** Toggles biometric and folds it into the active unlock method. */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricEnabled(enabled)
            val base = settingsRepository.unlockMethod.first()
            settingsRepository.setUnlockMethod(UnlockMethod.combine(base, enabled))
        }
    }

    fun setAutoLockMode(mode: AutoLockMode) {
        viewModelScope.launch { settingsRepository.setAutoLockMode(mode) }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch { settingsRepository.setAppearanceMode(mode) }
    }

    /** Switches to PIN-only as the primary method, removing any stored pattern. */
    fun selectPinMethod() {
        viewModelScope.launch {
            patternSecurityManager.clearPattern()
            val biometric = settingsRepository.biometricEnabled.first()
            settingsRepository.setUnlockMethod(UnlockMethod.combine(UnlockMethod.PIN, biometric))
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            appLockStateManager.markLocked()
            lockNowEvents.trySend(Unit)
        }
    }
}
