package app.lock.photo.valut.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.domain.repository.PrivateDocumentsRepository
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.VaultHomeUiState
import app.lock.photo.valut.features.vault.model.toUiModel
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
    private val fileManager: VaultFileManager,
    private val appLockRepository: AppLockRepository,
    private val documentsRepository: PrivateDocumentsRepository
) : ViewModel() {

    private val events = Channel<Unit>(Channel.BUFFERED)
    val albumCreatedFlow = events.receiveAsFlow()

    init {
        // Ensure vault directories + .nomedia exist up front.
        viewModelScope.launch { runCatching { fileManager.createVaultDirectories() } }
    }

    val uiState: StateFlow<VaultHomeUiState> = combine(
        repository.observeVaultCounts(),
        repository.getAlbumsFlow(),
        appLockRepository.observeLockedPackageNames(),
        documentsRepository.observeDocuments(VaultMode.REAL),
        repository.getRecentlyImportedFlow(limit = 12)
    ) { counts, albums, lockedApps, documents, recent ->
        VaultHomeUiState(
            photoCount = counts.photoCount,
            videoCount = counts.videoCount,
            appCount = lockedApps.size,
            documentCount = documents.size,
            albumCount = albums.size,
            favoriteCount = counts.favoriteCount,
            recycleBinCount = counts.recycleBinCount,
            storageUsedText = Formatters.formatSize(counts.storageUsedBytes),
            storagePercent = deviceStoragePercent(),
            encryptionActive = repository.hasVaultKey(),
            albums = albums.map { it.toUiModel() },
            recentMedia = recent.map { it.toUiModel() },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultHomeUiState()
    )

    /** Device storage used %, shown by the "Vault is Safe" ring. */
    private fun deviceStoragePercent(): Int = runCatching {
        val stat = android.os.StatFs(fileManager.encryptedPhotosDir.path)
        val total = stat.totalBytes
        if (total <= 0L) 0 else (((total - stat.availableBytes) * 100) / total).toInt()
    }.getOrDefault(0)

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createAlbum(name)
            events.trySend(Unit)
        }
    }
}
