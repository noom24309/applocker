package app.lock.photo.valut.features.intruder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.VerifySessionManager
import app.lock.photo.valut.domain.repository.IntruderRepository
import app.lock.photo.valut.features.intruder.model.IntruderAttemptUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntruderAlertsViewModel @Inject constructor(
    private val repository: IntruderRepository,
    private val verifySessionManager: VerifySessionManager
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val items: StateFlow<List<IntruderAttemptUiModel>> = combine(
        repository.observeAttempts(),
        selection
    ) { attempts, selected ->
        attempts.map { e ->
            IntruderAttemptUiModel(
                id = e.id,
                triggerSource = e.triggerSource,
                appName = e.lockedAppName,
                unlockMethod = e.attemptedUnlockMethod,
                timestamp = e.timestamp,
                wrongAttemptCount = e.wrongAttemptCount,
                captureSuccess = e.captureSuccess,
                isSelected = e.id in selected
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectionCount: StateFlow<Int> =
        selection.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val selectionMode: Boolean get() = selection.value.isNotEmpty()

    fun toggleSelection(id: Long) {
        val current = selection.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        selection.value = current
    }

    fun clearSelection() { selection.value = emptySet() }

    fun deleteSelected() {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteAttempts(ids)
            clearSelection()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllAttempts()
            clearSelection()
        }
    }

    suspend fun loadThumbnail(id: Long): ByteArray? = repository.loadThumbnailBytes(id)

    suspend fun needsVerification(): Boolean = !verifySessionManager.isVerificationStillValid()
    fun markVerified() { viewModelScope.launch { verifySessionManager.markVerified() } }
}
