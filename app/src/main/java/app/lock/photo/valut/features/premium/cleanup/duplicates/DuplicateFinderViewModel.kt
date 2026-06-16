package app.lock.photo.valut.features.premium.cleanup.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.DuplicateGroup
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicateFinderUiState(
    val hasScanned: Boolean = false,
    val isScanning: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedIds: Set<Long> = emptySet()
) {
    val selectedCount: Int get() = selectedIds.size
}

@HiltViewModel
class DuplicateFinderViewModel @Inject constructor(
    private val repository: PremiumToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateFinderUiState())
    val uiState: StateFlow<DuplicateFinderUiState> = _uiState.asStateFlow()

    fun scan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, progress = 0 to 0, selectedIds = emptySet()) }
        viewModelScope.launch {
            val groups = repository.scanDuplicatePhotos { done, total ->
                _uiState.update { it.copy(progress = done to total) }
            }
            // Pre-select everything except the recommended keep in each group.
            val preselect = groups.flatMap { g -> g.items.map { it.id } - g.recommendedKeepId }.toSet()
            _uiState.update {
                it.copy(isScanning = false, hasScanned = true, groups = groups, selectedIds = preselect)
            }
        }
    }

    fun toggle(id: Long) = _uiState.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    fun keepNewest() = selectAllExcept { group -> group.items.maxByOrNull { it.dateImported }?.id }

    fun keepOldest() = selectAllExcept { group -> group.items.minByOrNull { it.dateImported }?.id }

    /** Selects every duplicate except the recommended best in each group. */
    fun selectAllExceptBest() = selectAllExcept { it.recommendedKeepId }

    fun selectedSizeBytes(): Long {
        val ids = _uiState.value.selectedIds
        return _uiState.value.groups.flatMap { it.items }.filter { it.id in ids }.sumOf { it.sizeBytes }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToRecycleBin(ids)
            scan()
        }
    }

    private fun selectAllExcept(keepSelector: (DuplicateGroup) -> Long?) = _uiState.update { state ->
        val selected = state.groups.flatMap { group ->
            val keep = keepSelector(group)
            group.items.map { it.id } - setOfNotNull(keep)
        }.toSet()
        state.copy(selectedIds = selected)
    }
}
