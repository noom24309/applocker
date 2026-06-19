package app.lock.photo.valut.features.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import app.lock.photo.valut.features.vault.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    /** Which folder set this screen shows ("PHOTO"/"VIDEO"), or null for all. */
    private val mediaType: String? = savedStateHandle[AlbumsActivity.EXTRA_MEDIA_FILTER]

    val albums: StateFlow<List<AlbumUiModel>> = repository.getAlbumsFlow(mediaType)
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createAlbum(name, mediaType) }
    }

    fun renameAlbum(albumId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameAlbum(albumId, newName) }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { repository.deleteAlbum(albumId) }
    }
}
