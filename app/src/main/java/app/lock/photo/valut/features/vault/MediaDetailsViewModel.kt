package app.lock.photo.valut.features.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Read-only details for a vault item, plus display-name rename. */
data class MediaDetails(
    val id: Long,
    val displayName: String,
    val typeLabel: MediaType,
    val sizeText: String,
    val resolutionText: String?,
    val durationText: String?,
    val importedDateText: String,
    val albumId: Long?,
    val isFavorite: Boolean,
    val isEncrypted: Boolean
)

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    private val mediaId: Long = savedStateHandle[ARG_MEDIA_ID] ?: -1L

    private val _details = MutableStateFlow<MediaDetails?>(null)
    val details: StateFlow<MediaDetails?> = _details.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val item = repository.getMediaById(mediaId) ?: return@launch
            val type = MediaType.fromStorage(item.mediaType)
            _details.value = MediaDetails(
                id = item.id,
                displayName = item.displayName,
                typeLabel = type,
                sizeText = Formatters.formatSize(item.sizeBytes),
                resolutionText = Formatters.formatResolution(item.width, item.height),
                durationText = if (type == MediaType.VIDEO) Formatters.formatDuration(item.durationMillis) else null,
                importedDateText = Formatters.formatDate(item.dateImported),
                albumId = item.albumId,
                isFavorite = item.isFavorite,
                isEncrypted = item.isEncrypted
            )
        }
    }

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameMedia(mediaId, newName)
            load()
        }
    }

    companion object {
        const val ARG_MEDIA_ID = "arg_media_id"
    }
}
