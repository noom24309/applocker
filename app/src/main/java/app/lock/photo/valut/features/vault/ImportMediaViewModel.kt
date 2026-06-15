package app.lock.photo.valut.features.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.storage.HiddenGalleryManager
import app.lock.photo.valut.domain.model.ImportItemResult
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.ImportProgressUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportMediaViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val hiddenGalleryManager: HiddenGalleryManager
) : ViewModel() {

    private val _state = MutableStateFlow(ImportProgressUiState())
    val state: StateFlow<ImportProgressUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    fun startImport(uris: List<Uri>, albumId: Long = NO_ALBUM) {
        if (importJob?.isActive == true || _state.value.isFinished) return
        if (uris.isEmpty()) {
            _state.value = ImportProgressUiState(isFinished = true)
            return
        }
        _state.value = ImportProgressUiState(totalCount = uris.size, isImporting = true)
        importJob = viewModelScope.launch {
            var completed = 0
            var failed = 0
            var photos = 0
            var videos = 0
            val originalsToRemove = mutableListOf<Uri>()
            val importedIds = mutableListOf<Long>()
            for ((index, uri) in uris.withIndex()) {
                if (!isActive) break
                _state.update { it.copy(currentFileName = "${index + 1} / ${uris.size}") }
                when (val result = repository.importSingleMedia(uri)) {
                    is ImportItemResult.Success -> {
                        completed++
                        importedIds.add(result.mediaId)
                        if (result.mediaType == MediaType.VIDEO) videos++ else photos++
                        // Copy the original into the hidden shared folder (survives uninstall).
                        // Only when that copy succeeds do we queue the original for removal,
                        // so a photo is never lost. We queue the *resolved* MediaStore URI
                        // (picker URIs can't be deleted); null means we skip removal safely.
                        val hidden = hiddenGalleryManager.copyToHidden(uri, result.mediaType)
                        if (hidden != null) {
                            // Link the hidden copy to the vault item so it can be restored later.
                            repository.setHiddenUri(result.mediaId, hidden.toString())
                            hiddenGalleryManager.resolveDeletableUri(uri, result.mediaType)
                                ?.let { originalsToRemove.add(it) }
                        }
                    }
                    is ImportItemResult.Failed -> failed++
                }
                _state.update {
                    it.copy(
                        completedCount = completed,
                        failedCount = failed,
                        importedPhotos = photos,
                        importedVideos = videos
                    )
                }
            }
            // File freshly-imported items into the target folder, if one was given.
            if (albumId != NO_ALBUM && importedIds.isNotEmpty()) {
                repository.moveToAlbum(importedIds, albumId)
            }
            _state.update {
                it.copy(
                    isImporting = false,
                    isFinished = true,
                    originalsToRemove = originalsToRemove.toList()
                )
            }
        }
    }

    fun cancel() {
        importJob?.cancel()
        _state.update { it.copy(isImporting = false, isCancelled = true, isFinished = true) }
    }

    companion object {
        const val NO_ALBUM = -1L
    }
}
