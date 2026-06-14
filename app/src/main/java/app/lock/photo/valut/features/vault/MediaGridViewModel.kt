package app.lock.photo.valut.features.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.domain.model.SortOrder
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    sealed interface Event {
        data class ExportFinished(val result: ExportResult) : Event
        data object ActionDone : Event
    }

    val source: GridSource =
        GridSource.valueOf(savedStateHandle[ARG_SOURCE] ?: GridSource.PHOTOS.name)
    private val albumId: Long = savedStateHandle[ARG_ALBUM_ID] ?: -1L

    private val sourceFlow = when (source) {
        GridSource.PHOTOS -> repository.getPhotosFlow()
        GridSource.VIDEOS -> repository.getVideosFlow()
        GridSource.FAVORITES -> repository.getFavoritesFlow()
        GridSource.RECENT -> repository.getRecentlyImportedFlow()
        GridSource.RECYCLE_BIN -> repository.getRecycleBinFlow()
        GridSource.ALBUM -> repository.getMediaByAlbumFlow(albumId)
    }

    private val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val items: StateFlow<List<VaultMediaUiModel>> = combine(
        sourceFlow, sortOrder, selectedIds
    ) { entities, order, selected ->
        entities.sortedWith(comparatorFor(order)).map { it.toUiModel(isSelected = it.id in selected) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectionCount: StateFlow<Int> =
        selectedIds.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val albums: StateFlow<List<AlbumUiModel>> = repository.getAlbumsFlow()
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentSort: StateFlow<SortOrder> = sortOrder.asStateFlow()

    fun setSort(order: SortOrder) {
        sortOrder.value = order
    }

    fun toggleSelection(id: Long) {
        selectedIds.value = selectedIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAll() {
        selectedIds.value = items.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun favoriteSelected() = withSelection { ids ->
        ids.forEach { repository.toggleFavorite(it) }
    }

    fun deleteSelected() = withSelection { ids -> repository.softDeleteMedia(ids) }

    fun restoreSelected() = withSelection { ids -> repository.restoreMedia(ids) }

    fun permanentlyDeleteSelected() = withSelection { ids -> repository.permanentlyDeleteMedia(ids) }

    fun moveSelectedToAlbum(albumId: Long?) = withSelection { ids -> repository.moveToAlbum(ids, albumId) }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
            clearSelection()
            events.trySend(Event.ActionDone)
        }
    }

    fun exportSelected(removeFromVault: Boolean) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = repository.exportMedia(ids, removeFromVault)
            clearSelection()
            events.trySend(Event.ExportFinished(result))
        }
    }

    private inline fun withSelection(crossinline action: suspend (List<Long>) -> Unit) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            action(ids)
            clearSelection()
            events.trySend(Event.ActionDone)
        }
    }

    private fun comparatorFor(order: SortOrder): Comparator<VaultMediaEntity> = when (order) {
        SortOrder.NEWEST -> compareByDescending { it.dateImported }
        SortOrder.OLDEST -> compareBy { it.dateImported }
        SortOrder.NAME -> compareBy { it.displayName.lowercase() }
        SortOrder.SIZE -> compareByDescending { it.sizeBytes }
    }

    companion object {
        const val ARG_SOURCE = "arg_source"
        const val ARG_ALBUM_ID = "arg_album_id"
    }
}
