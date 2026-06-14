package app.lock.photo.valut.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.security.PatternSecurityManager
import app.lock.photo.valut.core.security.PinSecurityManager
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Verifies the current master credential (PIN or pattern) before allowing
 * sensitive security-setting changes.
 */
@HiltViewModel
class VerifyMasterViewModel @Inject constructor(
    private val pinSecurityManager: PinSecurityManager,
    private val patternSecurityManager: PatternSecurityManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    sealed interface Event {
        data object Verified : Event
        data object Wrong : Event
    }

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    suspend fun unlockMethod(): UnlockMethod = settingsRepository.unlockMethod.first()

    suspend fun pinLength(): Int = settingsRepository.pinLength.first()

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            emit(pinSecurityManager.verifyPin(pin))
        }
    }

    fun verifyPattern(nodes: List<Int>) {
        viewModelScope.launch {
            emit(patternSecurityManager.verifyPattern(nodes))
        }
    }

    private fun emit(success: Boolean) {
        events.trySend(if (success) Event.Verified else Event.Wrong)
    }
}
