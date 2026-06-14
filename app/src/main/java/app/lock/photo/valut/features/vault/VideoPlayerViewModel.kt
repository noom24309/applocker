package app.lock.photo.valut.features.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import app.lock.photo.valut.features.vault.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    sealed interface Event {
        data class ExportFinished(val result: ExportResult) : Event
        data object Deleted : Event
    }

    /** Where the decrypted, playable video currently lives (a secure cache temp file). */
    sealed interface Playback {
        data object Loading : Playback
        data class Ready(val file: File) : Playback
        data object Error : Playback
    }

    val mediaId: Long = savedStateHandle[ARG_MEDIA_ID] ?: -1L

    val media: StateFlow<VaultMediaUiModel?> = repository.observeMediaById(mediaId)
        .map { it?.toUiModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _playback = MutableStateFlow<Playback>(Playback.Loading)
    val playback: StateFlow<Playback> = _playback.asStateFlow()

    private var tempFile: File? = null
    private var preparing = false

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Decrypts the video into a temp file once; safe to call repeatedly. */
    fun preparePlayback() {
        if (tempFile?.exists() == true || preparing) return
        preparing = true
        viewModelScope.launch {
            _playback.value = Playback.Loading
            val file = repository.decryptVideoToTemp(mediaId)
            preparing = false
            tempFile = file
            _playback.value = if (file != null) Playback.Ready(file) else Playback.Error
        }
    }

    /** Deletes the decrypted temp file (called when playback stops / screen leaves). */
    fun clearPlayback() {
        val file = tempFile
        tempFile = null
        _playback.value = Playback.Loading
        if (file != null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { if (file.exists()) file.delete() } }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { repository.toggleFavorite(mediaId) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.softDeleteMedia(listOf(mediaId))
            events.trySend(Event.Deleted)
        }
    }

    fun export(removeFromVault: Boolean) {
        viewModelScope.launch {
            val result = repository.exportMedia(listOf(mediaId), removeFromVault)
            events.trySend(Event.ExportFinished(result))
            if (removeFromVault && result.exportedCount > 0) events.trySend(Event.Deleted)
        }
    }

    override fun onCleared() {
        super.onCleared()
        val file = tempFile
        tempFile = null
        if (file != null) {
            // Best-effort synchronous delete on teardown.
            runCatching { if (file.exists()) file.delete() }
        }
    }

    companion object {
        const val ARG_MEDIA_ID = "arg_media_id"
    }
}
