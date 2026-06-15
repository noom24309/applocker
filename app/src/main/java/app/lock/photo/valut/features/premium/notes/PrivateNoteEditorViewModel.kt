package app.lock.photo.valut.features.premium.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.NoteContent
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.PrivateNotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val id: Long,
    val title: String = "",
    val content: String = "",
    val loaded: Boolean = false
)

sealed interface NoteEditorEvent {
    data class Saved(val id: Long) : NoteEditorEvent
    data object SaveFailed : NoteEditorEvent
    data class Exported(val text: String) : NoteEditorEvent
}

@HiltViewModel
class PrivateNoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PrivateNotesRepository
) : ViewModel() {

    private val vaultMode = VaultMode.REAL
    private val initialId: Long = savedStateHandle[ARG_NOTE_ID] ?: -1L

    private val _uiState = MutableStateFlow(NoteEditorUiState(id = initialId))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<NoteEditorEvent>(Channel.BUFFERED)
    val events: Flow<NoteEditorEvent> = _events.receiveAsFlow()

    init {
        if (initialId > 0) load(initialId) else _uiState.value = _uiState.value.copy(loaded = true)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val note: NoteContent? = repository.getNote(id)
            _uiState.value = NoteEditorUiState(
                id = id,
                title = note?.title.orEmpty(),
                content = note?.content.orEmpty(),
                loaded = true
            )
        }
    }

    /** Saves the note; no-op if both title and content are blank. */
    fun save(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) {
            viewModelScope.launch { _events.send(NoteEditorEvent.Saved(_uiState.value.id)) }
            return
        }
        viewModelScope.launch {
            val id = repository.saveNote(_uiState.value.id.takeIf { it > 0 }, title, content, vaultMode)
            if (id > 0) {
                _uiState.value = _uiState.value.copy(id = id)
                _events.send(NoteEditorEvent.Saved(id))
            } else {
                _events.send(NoteEditorEvent.SaveFailed)
            }
        }
    }

    fun requestExport() {
        val id = _uiState.value.id
        if (id <= 0) return
        viewModelScope.launch {
            val note = repository.getNoteForExport(id) ?: return@launch
            _events.send(NoteEditorEvent.Exported(note.content))
        }
    }

    companion object {
        const val ARG_NOTE_ID = "arg_note_id"
    }
}
