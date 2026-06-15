package app.lock.photo.valut.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import app.lock.photo.valut.features.vault.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the combined private-vault home: folders (albums) on top, loose photos/videos
 * below. The FAB creates a folder or adds media (handled by the fragment).
 */
@HiltViewModel
class PrivateVaultViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    val folders: StateFlow<List<AlbumUiModel>> = repository.getAlbumsFlow()
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val media: StateFlow<List<VaultMediaUiModel>> = repository.getUnsortedMediaFlow()
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.createAlbum(trimmed) }
    }
}
