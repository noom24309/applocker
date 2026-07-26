package app.lock.photo.valut.features.auth.pin

import androidx.lifecycle.ViewModel
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.security.PinSetupSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class CreatePinViewModel @Inject constructor(
    private val session: PinSetupSession
) : ViewModel() {

    sealed interface Event {
        data object Proceed : Event
    }

    private val _length = MutableStateFlow(Constants.DEFAULT_PIN_LENGTH)
    val length: StateFlow<Int> = _length.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun setLength(length: Int) {
        _length.value = length
    }

    /** Any PIN is accepted — no strength screening. */
    fun submitPin(pin: String) {
        session.store(pin, _length.value)
        events.trySend(Event.Proceed)
    }
}
