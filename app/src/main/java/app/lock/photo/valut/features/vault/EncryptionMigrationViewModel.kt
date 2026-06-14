package app.lock.photo.valut.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.storage.VaultEncryptionMigrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EncryptionMigrationViewModel @Inject constructor(
    private val migrationManager: VaultEncryptionMigrationManager
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val total: Int = 0,
        val processed: Int = 0,
        val succeeded: Int = 0,
        val failed: Int = 0,
        val finished: Boolean = false
    ) {
        val percent: Int get() = if (total == 0) 100 else (processed * 100) / total
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var started = false

    /** Starts (or resumes) encrypting the vault. Idempotent across config changes. */
    fun start() {
        if (started) return
        started = true
        _state.update { it.copy(running = true) }
        viewModelScope.launch {
            migrationManager.migrateAll { progress ->
                _state.update {
                    it.copy(
                        running = !progress.finished,
                        total = progress.total,
                        processed = progress.processed,
                        succeeded = progress.succeeded,
                        failed = progress.failed,
                        finished = progress.finished
                    )
                }
            }
        }
    }

    /** Retries any items that failed in the previous pass. */
    fun retryFailed() {
        started = false
        _state.value = UiState()
        start()
    }
}
