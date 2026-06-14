package app.lock.photo.valut.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.security.PatternSecurityManager
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

@HiltViewModel
class PatternSetupViewModel @Inject constructor(
    private val patternSecurityManager: PatternSecurityManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    enum class Phase { DRAW, CONFIRM }

    sealed interface Event {
        data object TooShort : Event
        data object ProceedToConfirm : Event
        data object Mismatch : Event
        data object Saved : Event
    }

    private val _phase = MutableStateFlow(Phase.DRAW)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private var firstNodes: List<Int> = emptyList()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun submit(nodes: List<Int>) {
        if (nodes.size < Constants.MIN_PATTERN_NODES) {
            events.trySend(Event.TooShort)
            return
        }
        when (_phase.value) {
            Phase.DRAW -> {
                firstNodes = nodes
                _phase.value = Phase.CONFIRM
                events.trySend(Event.ProceedToConfirm)
            }
            Phase.CONFIRM -> {
                if (nodes != firstNodes) {
                    _phase.value = Phase.DRAW
                    firstNodes = emptyList()
                    events.trySend(Event.Mismatch)
                } else {
                    save(nodes)
                }
            }
        }
    }

    private fun save(nodes: List<Int>) {
        viewModelScope.launch {
            if (patternSecurityManager.createPattern(nodes) == SecurityResult.Success) {
                val biometric = settingsRepository.biometricEnabled.first()
                settingsRepository.setUnlockMethod(
                    UnlockMethod.combine(UnlockMethod.PATTERN, biometric)
                )
                events.trySend(Event.Saved)
            } else {
                _phase.value = Phase.DRAW
                firstNodes = emptyList()
                events.trySend(Event.TooShort)
            }
        }
    }
}
