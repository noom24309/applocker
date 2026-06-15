package app.lock.photo.valut.features.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import app.lock.photo.valut.features.vault.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    sealed interface Event {
        data class ExportFinished(val result: ExportResult) : Event
        data class RestoredToGallery(val success: Boolean) : Event
        data object Empty : Event
        data object ActionDone : Event
    }

    private val ids: List<Long> =
        (savedStateHandle.get<LongArray>(ARG_IDS) ?: LongArray(0)).toList()
    val startIndex: Int = savedStateHandle[ARG_INDEX] ?: 0

    private val _items = MutableStateFlow<List<VaultMediaUiModel>>(emptyList())
    val items: StateFlow<List<VaultMediaUiModel>> = _items.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val albums: StateFlow<List<AlbumUiModel>> = repository.getAlbumsFlow()
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _items.value = ids.mapNotNull { repository.getMediaById(it)?.toUiModel() }
            if (_items.value.isEmpty()) events.trySend(Event.Empty)
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
            val updated = repository.getMediaById(id)?.toUiModel() ?: return@launch
            _items.value = _items.value.map { if (it.id == id) updated else it }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.softDeleteMedia(listOf(id))
            removeLocal(id)
        }
    }

    fun moveToAlbum(id: Long, albumId: Long?) {
        viewModelScope.launch {
            repository.moveToAlbum(listOf(id), albumId)
            events.trySend(Event.ActionDone)
        }
    }

    /** Unhide: move the original back to the gallery and remove it from the vault. */
    fun restoreToGallery(id: Long) {
        viewModelScope.launch {
            val restored = repository.restoreToGallery(listOf(id))
            removeLocal(id)
            events.trySend(Event.RestoredToGallery(restored > 0))
        }
    }

    fun export(id: Long, removeFromVault: Boolean) {
        viewModelScope.launch {
            val result = repository.exportMedia(listOf(id), removeFromVault)
            if (removeFromVault && result.exportedCount > 0) removeLocal(id)
            events.trySend(Event.ExportFinished(result))
        }
    }

    private fun removeLocal(id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
        if (_items.value.isEmpty()) events.trySend(Event.Empty)
    }

    companion object {
        const val ARG_IDS = "arg_ids"
        const val ARG_INDEX = "arg_index"
    }
}
