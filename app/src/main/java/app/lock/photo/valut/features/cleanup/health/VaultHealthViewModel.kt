package app.lock.photo.valut.features.cleanup.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.R
import app.lock.photo.valut.domain.model.VaultHealth
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
class VaultHealthViewModel @Inject constructor(
    private val repository: PremiumToolsRepository
) : ViewModel() {

    private val _health = MutableStateFlow<VaultHealth?>(null)
    val health: StateFlow<VaultHealth?> = _health.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _events = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _events.receiveAsFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.markScanned()
            _health.value = repository.getVaultHealth()
            _isScanning.value = false
        }
    }

    fun clearTempCache() {
        viewModelScope.launch {
            repository.clearTempCache()
            _events.send(R.string.cleanup_temp_cleared)
            scan()
        }
    }
}
