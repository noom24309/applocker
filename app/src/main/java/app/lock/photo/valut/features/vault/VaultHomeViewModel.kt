package app.lock.photo.valut.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.VaultHomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultHomeViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val fileManager: VaultFileManager
) : ViewModel() {

    private val events = Channel<Unit>(Channel.BUFFERED)
    val albumCreatedFlow = events.receiveAsFlow()

    init {
        // Ensure vault directories + .nomedia exist up front.
        viewModelScope.launch { runCatching { fileManager.createVaultDirectories() } }
    }

    val uiState: StateFlow<VaultHomeUiState> = combine(
        repository.observeVaultCounts(),
        repository.getAlbumsFlow()
    ) { counts, albums ->
        VaultHomeUiState(
            photoCount = counts.photoCount,
            videoCount = counts.videoCount,
            albumCount = albums.size,
            favoriteCount = counts.favoriteCount,
            recycleBinCount = counts.recycleBinCount,
            storageUsedText = Formatters.formatSize(counts.storageUsedBytes),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultHomeUiState()
    )

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createAlbum(name)
            events.trySend(Unit)
        }
    }
}
