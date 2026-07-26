package app.lock.photo.valut.features.vault.video

import android.net.Uri
import android.os.SystemClock
import android.util.Log
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

    /**
     * What the player should read.
     *
     * Vault video is decrypted into the secure cache first and played from there. Streaming the
     * decryption was tried and does not work for these files: AES/GCM only releases plaintext
     * once the trailing auth tag verifies, so Android's CipherInputStream hands back no bytes
     * until the whole stream has been consumed — the player just buffers forever. Decrypting
     * up front is correct and, at typical vault sizes, still starts in well under a second.
     */
    sealed interface Playback {
        data object Loading : Playback
        data class Ready(val uri: Uri) : Playback
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

    /** MIME type of the item, handed to the player so it never has to guess the container. */
    val mimeType: String? get() = media.value?.mimeType

    /** Decrypts the video into the secure cache once; safe to call repeatedly. */
    fun preparePlayback() {
        val existing = tempFile
        if (existing?.exists() == true && existing.length() > 0L) {
            Log.d(TAG, "reusing decrypted file (${existing.length()} bytes)")
            _playback.value = Playback.Ready(Uri.fromFile(existing))
            return
        }
        if (preparing) {
            Log.d(TAG, "decrypt already in flight")
            return
        }
        preparing = true
        viewModelScope.launch {
            _playback.value = Playback.Loading
            val startedAt = SystemClock.elapsedRealtime()
            val file = repository.decryptVideoToTemp(mediaId)
            val tookMs = SystemClock.elapsedRealtime() - startedAt
            preparing = false
            tempFile = file
            val size = file?.length() ?: 0L
            Log.d(TAG, "decrypt finished in ${tookMs}ms, file=${file != null}, bytes=$size")
            _playback.value = if (file != null && size > 0L) {
                Playback.Ready(Uri.fromFile(file))
            } else {
                Playback.Error
            }
            // Items imported before the fast key format are re-encrypted once, in the background,
            // right after playback starts — so the next open doesn't pay the slow decrypt again.
            if (file != null && size > 0L) upgradeStorageFormat(file)
        }
    }

    private fun upgradeStorageFormat(plainFile: File) {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val upgraded = runCatching {
                repository.upgradeEncryptionIfNeeded(mediaId, plainFile)
            }.getOrDefault(false)
            if (upgraded) {
                Log.d(TAG, "re-encrypted to fast format in ${SystemClock.elapsedRealtime() - startedAt}ms")
            }
        }
    }

    /**
     * Forgets the current decrypted file and prepares again. Used when playback fails because
     * the temp file went away underneath us — the vault screens clear that cache on resume, so a
     * stale path is a real possibility rather than a theoretical one.
     */
    fun invalidateAndReprepare() {
        Log.d(TAG, "invalidating decrypted file and preparing again")
        tempFile = null
        preparing = false
        _playback.value = Playback.Loading
        preparePlayback()
    }

    /** Drops the decrypted temp file (screen closing / playback stopped). */
    fun clearPlayback() {
        val file = tempFile
        tempFile = null
        _playback.value = Playback.Loading
        if (file != null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { if (file.exists()) file.delete() } }
        }
    }

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

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
        // Best-effort synchronous delete on teardown: no plaintext is left behind.
        if (file != null) runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        const val ARG_MEDIA_ID = "arg_media_id"
        private const val TAG = "VaultVideoPlayer"
    }
}
