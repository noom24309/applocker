package app.lock.photo.valut.features.auth.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.security.RecoveryKeyManager
import app.lock.photo.valut.core.security.WrongAttemptManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPinViewModel @Inject constructor(
    private val recoveryKeyManager: RecoveryKeyManager,
    private val wrongAttemptManager: WrongAttemptManager
) : ViewModel() {

    sealed interface Event {
        data object Verified : Event
        data object Wrong : Event
    }

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun submitRecoveryKey(key: String) {
        viewModelScope.launch {
            if (recoveryKeyManager.verifyRecoveryKey(key)) {
                wrongAttemptManager.resetAttempts()
                events.trySend(Event.Verified)
            } else {
                events.trySend(Event.Wrong)
            }
        }
    }
}
