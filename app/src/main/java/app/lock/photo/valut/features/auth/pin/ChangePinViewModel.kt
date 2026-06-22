package app.lock.photo.valut.features.auth.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.security.PatternSecurityManager
import app.lock.photo.valut.core.security.PinSecurityManager
import app.lock.photo.valut.core.security.WrongAttemptManager
import app.lock.photo.valut.domain.model.SecurityResult
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the multi-step Change-PIN flow. In recovery [resetMode] the "verify old
 * PIN" step is skipped (the recovery key has already authorized the reset).
 */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val pinSecurityManager: PinSecurityManager,
    private val patternSecurityManager: PatternSecurityManager,
    private val wrongAttemptManager: WrongAttemptManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    enum class Step { VERIFY_OLD, ENTER_NEW, CONFIRM_NEW }

    sealed interface Event {
        data class WrongOld(val attemptCount: Int, val lockedOut: Boolean) : Event
        data object OldVerified : Event
        data object WeakNew : Event
        data object SameAsOld : Event
        data object ProceedConfirm : Event
        data object Mismatch : Event
        data object Saved : Event
        data object Error : Event
    }

    private var resetMode = false

    private val _step = MutableStateFlow(Step.VERIFY_OLD)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _newLength = MutableStateFlow(Constants.DEFAULT_PIN_LENGTH)
    val newLength: StateFlow<Int> = _newLength.asStateFlow()

    private var tempNewPin: String? = null

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun configure(resetMode: Boolean) {
        this.resetMode = resetMode
        _step.value = if (resetMode) Step.ENTER_NEW else Step.VERIFY_OLD
    }

    suspend fun oldPinLength(): Int = settingsRepository.pinLength.first()

    fun verifyOld(pin: String) {
        viewModelScope.launch {
            if (pinSecurityManager.verifyPin(pin)) {
                _step.value = Step.ENTER_NEW
                events.trySend(Event.OldVerified)
            } else {
                val status = wrongAttemptManager.recordWrongAttempt()
                events.trySend(Event.WrongOld(status.attemptCount, status.lockedOut))
            }
        }
    }

    fun setNewLength(length: Int) {
        _newLength.value = length
    }

    fun submitNew(pin: String) {
        viewModelScope.launch {
            if (!resetMode && pinSecurityManager.verifyPin(pin)) {
                events.trySend(Event.SameAsOld)
                return@launch
            }
            // Weak check happens at save time via createPin; pre-check for a friendly message.
            tempNewPin = pin
            _step.value = Step.CONFIRM_NEW
            events.trySend(Event.ProceedConfirm)
        }
    }

    fun confirmNew(pin: String) {
        if (pin != tempNewPin) {
            _step.value = Step.ENTER_NEW
            tempNewPin = null
            events.trySend(Event.Mismatch)
            return
        }
        viewModelScope.launch {
            when (pinSecurityManager.createPin(pin, _newLength.value)) {
                SecurityResult.Success -> {
                    wrongAttemptManager.resetAttempts()
                    if (resetMode) {
                        // Recovery sets a fresh PIN and reverts the method to PIN-based.
                        patternSecurityManager.clearPattern()
                        val biometric = settingsRepository.biometricEnabled.first()
                        settingsRepository.setUnlockMethod(UnlockMethod.combine(UnlockMethod.PIN, biometric))
                    }
                    tempNewPin = null
                    events.trySend(Event.Saved)
                }
                SecurityResult.WeakCredential -> {
                    _step.value = Step.ENTER_NEW
                    tempNewPin = null
                    events.trySend(Event.WeakNew)
                }
                else -> events.trySend(Event.Error)
            }
        }
    }
}
