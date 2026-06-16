package app.lock.photo.valut.features.cleanup.largefiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LargeFilesType { ALL, PHOTOS, VIDEOS }
enum class LargeFilesSort { LARGEST, NEWEST, OLDEST }

data class LargeFilesUiState(
    val files: List<VaultMediaEntity> = emptyList(),
    val minSizeBytes: Long = 10L * 1024 * 1024,
    val type: LargeFilesType = LargeFilesType.ALL,
    val sort: LargeFilesSort = LargeFilesSort.LARGEST,
    val selectedIds: Set<Long> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LargeFilesViewModel @Inject constructor(
    private val repository: PremiumToolsRepository
) : ViewModel() {

    private val minSize = MutableStateFlow(10L * 1024 * 1024)
    private val type = MutableStateFlow(LargeFilesType.ALL)
    private val sort = MutableStateFlow(LargeFilesSort.LARGEST)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    private val filesFlow = minSize.flatMapLatest { repository.observeLargeFiles(it) }

    val uiState: StateFlow<LargeFilesUiState> =
        combine(filesFlow, type, sort, minSize, selected) { files, t, s, min, sel ->
            val filtered = files.filter {
                when (t) {
                    LargeFilesType.ALL -> true
                    LargeFilesType.PHOTOS -> it.mediaType == "PHOTO"
                    LargeFilesType.VIDEOS -> it.mediaType == "VIDEO"
                }
            }
            val sorted = when (s) {
                LargeFilesSort.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
                LargeFilesSort.NEWEST -> filtered.sortedByDescending { it.dateImported }
                LargeFilesSort.OLDEST -> filtered.sortedBy { it.dateImported }
            }
            LargeFilesUiState(sorted, min, t, s, sel.intersect(sorted.map { it.id }.toSet()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LargeFilesUiState())

    fun setMinSize(bytes: Long) { minSize.value = bytes }
    fun setType(value: LargeFilesType) { type.value = value }
    fun setSort(value: LargeFilesSort) { sort.value = value }

    fun toggle(id: Long) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun selectedSizeBytes(): Long {
        val ids = selected.value
        return uiState.value.files.filter { it.id in ids }.sumOf { it.sizeBytes }
    }

    fun deleteSelected() {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToRecycleBin(ids)
            selected.value = emptySet()
        }
    }
}
