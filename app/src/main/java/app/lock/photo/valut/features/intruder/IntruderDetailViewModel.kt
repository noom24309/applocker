package app.lock.photo.valut.features.intruder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.repository.IntruderRepository
import app.lock.photo.valut.features.intruder.model.IntruderDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntruderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: IntruderRepository
) : ViewModel() {

    sealed interface Event {
        data class Exported(val success: Boolean) : Event
        data object Deleted : Event
        data object Missing : Event
    }

    val attemptId: Long = savedStateHandle[ARG_ID] ?: -1L

    private val _state = MutableStateFlow(IntruderDetailUiState())
    val state: StateFlow<IntruderDetailUiState> = _state.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val entity = repository.getAttempt(attemptId)
            if (entity == null) {
                events.trySend(Event.Missing)
                return@launch
            }
            _state.value = IntruderDetailUiState(
                id = entity.id,
                triggerSource = entity.triggerSource,
                appName = entity.lockedAppName,
                unlockMethod = entity.attemptedUnlockMethod,
                timestamp = entity.timestamp,
                wrongAttemptCount = entity.wrongAttemptCount,
                captureSuccess = entity.captureSuccess,
                isEncrypted = entity.isEncrypted,
                failureReason = entity.failureReason,
                loaded = true
            )
        }
    }

    /** Decrypts the full photo into memory for display (caller shows the bytes). */
    suspend fun loadPhoto(): ByteArray? = repository.loadPhotoBytes(attemptId)

    fun export() {
        viewModelScope.launch { events.trySend(Event.Exported(repository.exportAttemptPhoto(attemptId))) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deleteAttempt(attemptId)
            events.trySend(Event.Deleted)
        }
    }

    companion object {
        const val ARG_ID = "arg_intruder_id"
    }
}
