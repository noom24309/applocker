package app.lock.photo.valut.features.premium.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.NoteListItem
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.PrivateNotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivateNotesUiState(
    val notes: List<NoteListItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class PrivateNotesViewModel @Inject constructor(
    private val repository: PrivateNotesRepository
) : ViewModel() {

    // REAL only until Phase 8 (decoy) exists.
    private val vaultMode = VaultMode.REAL
    private val query = MutableStateFlow("")

    val uiState: StateFlow<PrivateNotesUiState> =
        combine(repository.observeNotes(vaultMode), query) { notes, q ->
            val filtered = if (q.isBlank()) notes else notes.filter {
                it.title.contains(q, ignoreCase = true) || it.preview.contains(q, ignoreCase = true)
            }
            PrivateNotesUiState(notes = filtered, query = q, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivateNotesUiState())

    fun setQuery(value: String) { query.value = value }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.softDeleteNotes(listOf(id)) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }
}
