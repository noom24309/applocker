package app.lock.photo.valut.features.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.security.PinSecurityManager
import app.lock.photo.valut.core.security.WrongAttemptManager
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.usecase.HandleIntruderWrongAttemptUseCase
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

data class UnlockUiState(
    val pinLength: Int = 4,
    val lockedOut: Boolean = false,
    val remainingMillis: Long = 0L,
    val attemptCount: Int = 0
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pinSecurityManager: PinSecurityManager,
    private val wrongAttemptManager: WrongAttemptManager,
    private val appLockStateManager: AppLockStateManager,
    private val settingsRepository: SettingsRepository,
    private val handleIntruderWrongAttempt: HandleIntruderWrongAttemptUseCase
) : ViewModel() {

    private val triggerSource: IntruderTrigger =
        IntruderTrigger.fromStorage(savedStateHandle[LockRouter.EXTRA_TRIGGER_SOURCE])

    sealed interface Event {
        data object Success : Event
        data class Wrong(val attemptCount: Int, val lockedOut: Boolean) : Event
    }

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(pinLength = settingsRepository.pinLength.first()) }
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
            if (pinSecurityManager.verifyPin(pin)) {
                onAuthenticated()
            } else {
                val status = wrongAttemptManager.recordWrongAttempt()
                handleIntruderWrongAttempt(
                    IntruderTriggerContext(
                        trigger = triggerSource,
                        unlockMethod = "PIN",
                        wrongAttemptCount = status.attemptCount
                    )
                )
                _state.update {
                    it.copy(
                        attemptCount = status.attemptCount,
                        lockedOut = status.lockedOut,
                        remainingMillis = status.remainingMillis
                    )
                }
                events.trySend(Event.Wrong(status.attemptCount, status.lockedOut))
            }
        }
    }

    /** Biometric success never affects the wrong-PIN counter. */
    fun onBiometricSuccess() {
        viewModelScope.launch { onAuthenticated() }
    }

    suspend fun isBiometricEnabled(): Boolean = settingsRepository.biometricEnabled.first()

    private suspend fun onAuthenticated() {
        wrongAttemptManager.resetAttempts()
        appLockStateManager.markUnlocked()
        _state.update { it.copy(lockedOut = false, remainingMillis = 0L, attemptCount = 0) }
        events.trySend(Event.Success)
    }

    private suspend fun refreshLockout() {
        val status = wrongAttemptManager.currentStatus()
        _state.update {
            it.copy(
                attemptCount = status.attemptCount,
                lockedOut = status.lockedOut,
                remainingMillis = status.remainingMillis
            )
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (isActive) {
                if (_state.value.lockedOut) {
                    val remaining = wrongAttemptManager.getLockoutRemainingMillis()
                    _state.update { it.copy(lockedOut = remaining > 0L, remainingMillis = remaining) }
                }
                delay(500L)
            }
        }
    }
}
