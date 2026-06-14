package app.lock.photo.valut.features.applock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockSessionManager
import app.lock.photo.valut.core.security.PatternSecurityManager
import app.lock.photo.valut.core.security.PinSecurityManager
import app.lock.photo.valut.core.security.WrongAttemptManager
import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.usecase.GetEffectiveLockSettingsUseCase
import app.lock.photo.valut.domain.usecase.HandleIntruderWrongAttemptUseCase
import app.lock.photo.valut.domain.usecase.RecordLocalAppLockStatsUseCase
import app.lock.photo.valut.features.applock.model.AppLockOverlayUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the App Lock overlay. Reuses the same master credential managers as the vault
 * app — there is no separate App Lock PIN — and the same wrong-attempt lockout policy.
 */
@HiltViewModel
class AppLockOverlayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pinSecurityManager: PinSecurityManager,
    private val patternSecurityManager: PatternSecurityManager,
    private val wrongAttemptManager: WrongAttemptManager,
    private val sessionManager: AppLockSessionManager,
    private val appLockRepository: AppLockRepository,
    private val settingsRepository: SettingsRepository,
    private val getEffectiveLockSettings: GetEffectiveLockSettingsUseCase,
    private val recordStats: RecordLocalAppLockStatsUseCase,
    private val handleIntruderWrongAttempt: HandleIntruderWrongAttemptUseCase
) : ViewModel() {

    sealed interface Event {
        data object Success : Event
        data class Wrong(val attemptCount: Int, val lockedOut: Boolean) : Event
    }

    private val packageName: String = savedStateHandle[ARG_PACKAGE] ?: ""

    private val _state = MutableStateFlow(
        AppLockOverlayUiState(
            lockedPackageName = packageName,
            lockedAppName = savedStateHandle[ARG_APP_NAME] ?: packageName
        )
    )
    val state: StateFlow<AppLockOverlayUiState> = _state.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var graceMillis = 0L

    init {
        viewModelScope.launch {
            val effective = getEffectiveLockSettings(packageName, _state.value.lockedAppName)
            graceMillis = effective.graceMillis
            _state.update {
                it.copy(
                    lockedAppName = effective.appName,
                    unlockMethod = effective.unlockMethod,
                    expectedPinLength = settingsRepository.pinLength.first(),
                    biometricEnabled = settingsRepository.biometricEnabled.first(),
                    fakeMode = effective.fakeMode,
                    theme = effective.theme,
                    hideAppName = effective.hideAppName,
                    requireBiometricOnly = effective.requireBiometricOnly,
                    resolved = true
                )
            }
            refreshLockout()
            startCountdown()
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            if (wrongAttemptManager.isLockedOut()) {
                refreshLockout()
                return@launch
            }
            if (pinSecurityManager.verifyPin(pin)) onAuthenticated() else onWrong()
        }
    }

    fun verifyPattern(nodes: List<Int>) {
        viewModelScope.launch {
            if (wrongAttemptManager.isLockedOut()) {
                refreshLockout()
                return@launch
            }
            if (patternSecurityManager.verifyPattern(nodes)) onAuthenticated() else onWrong()
        }
    }

    /** Biometric success never affects the wrong-attempt counter. */
    fun onBiometricSuccess() {
        viewModelScope.launch { onAuthenticated() }
    }

    private suspend fun onAuthenticated() {
        sessionManager.onUnlockSuccess(packageName, graceMillis)
        appLockRepository.markUnlockedNow(packageName, graceMillis)
        appLockRepository.recordPerAppUnlock(packageName)
        recordStats(RecordLocalAppLockStatsUseCase.Event.SUCCESS)
        wrongAttemptManager.resetAttempts()
        _state.update { it.copy(isLockedOut = false, remainingMillis = 0L, attemptCount = 0) }
        events.trySend(Event.Success)
    }

    private suspend fun onWrong() {
        appLockRepository.recordPerAppFailedUnlock(packageName)
        recordStats(RecordLocalAppLockStatsUseCase.Event.FAILURE)
        val status = wrongAttemptManager.recordWrongAttempt()
        handleIntruderWrongAttempt(
            IntruderTriggerContext(
                trigger = intruderTrigger(),
                lockedPackageName = packageName,
                lockedAppName = _state.value.lockedAppName,
                unlockMethod = if (_state.value.unlockMethod.usesPattern) "PATTERN" else "PIN",
                wrongAttemptCount = status.attemptCount
            )
        )
        _state.update {
            it.copy(
                attemptCount = status.attemptCount,
                isLockedOut = status.lockedOut,
                remainingMillis = status.remainingMillis
            )
        }
        events.trySend(Event.Wrong(status.attemptCount, status.lockedOut))
    }

    private fun intruderTrigger(): IntruderTrigger {
        val s = _state.value
        return when {
            s.fakeMode == FakeMode.FAKE_CALCULATOR || s.theme == LockTheme.CALCULATOR -> IntruderTrigger.FAKE_CALCULATOR
            s.fakeMode == FakeMode.FAKE_CRASH || s.theme == LockTheme.FAKE_CRASH -> IntruderTrigger.FAKE_CRASH
            else -> IntruderTrigger.APP_LOCK_OVERLAY
        }
    }

    private suspend fun refreshLockout() {
        val status = wrongAttemptManager.currentStatus()
        _state.update {
            it.copy(
                attemptCount = status.attemptCount,
                isLockedOut = status.lockedOut,
                remainingMillis = status.remainingMillis
            )
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (isActive) {
                if (_state.value.isLockedOut) {
                    val remaining = wrongAttemptManager.getLockoutRemainingMillis()
                    _state.update { it.copy(isLockedOut = remaining > 0L, remainingMillis = remaining) }
                }
                delay(500L)
            }
        }
    }

    companion object {
        const val ARG_PACKAGE = "arg_locked_package"
        const val ARG_APP_NAME = "arg_locked_app_name"
    }
}
