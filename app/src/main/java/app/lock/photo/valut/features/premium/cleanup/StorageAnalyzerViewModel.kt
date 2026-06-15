package app.lock.photo.valut.features.premium.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.StorageBreakdown
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageAnalyzerViewModel @Inject constructor(
    private val repository: PremiumToolsRepository
) : ViewModel() {

    private val _breakdown = MutableStateFlow<StorageBreakdown?>(null)
    val breakdown: StateFlow<StorageBreakdown?> = _breakdown.asStateFlow()

    private val _events = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _events.receiveAsFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _breakdown.value = repository.getStorageBreakdown() }
    }

    fun clearTempCache() {
        viewModelScope.launch {
            repository.clearTempCache()
            _events.send(app.lock.photo.valut.R.string.cleanup_temp_cleared)
            refresh()
        }
    }
}
