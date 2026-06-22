package app.lock.photo.valut.features.auth.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.security.PinSecurityManager
import app.lock.photo.valut.core.security.PinSetupSession
import app.lock.photo.valut.domain.model.SecurityResult
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmPinViewModel @Inject constructor(
    private val session: PinSetupSession,
    private val pinSecurityManager: PinSecurityManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    sealed interface Event {
        data object Mismatch : Event
        data object Saved : Event
        data object Error : Event
    }

    val expectedLength: Int get() = session.length

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun confirm(pin: String) {
        if (!session.matches(pin)) {
            events.trySend(Event.Mismatch)
            return
        }
        viewModelScope.launch {
            when (pinSecurityManager.createPin(pin, session.length)) {
                SecurityResult.Success -> {
                    settingsRepository.setUnlockMethod(UnlockMethod.PIN)
                    session.clear()
                    events.trySend(Event.Saved)
                }
                else -> events.trySend(Event.Error)
            }
        }
    }
}
