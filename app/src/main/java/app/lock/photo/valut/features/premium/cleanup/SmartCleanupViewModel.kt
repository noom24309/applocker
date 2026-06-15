package app.lock.photo.valut.features.premium.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.CleanupSuggestion
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartCleanupUiState(
    val isLoading: Boolean = true,
    val suggestions: List<CleanupSuggestion> = emptyList(),
    val ignored: Set<CleanupSuggestion.Type> = emptySet()
) {
    val visible: List<CleanupSuggestion> get() = suggestions.filter { it.type !in ignored }
}

@HiltViewModel
class SmartCleanupViewModel @Inject constructor(
    private val repository: PremiumToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartCleanupUiState())
    val uiState: StateFlow<SmartCleanupUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val suggestions = repository.getCleanupSuggestions()
            _uiState.update { it.copy(isLoading = false, suggestions = suggestions) }
        }
    }

    fun ignore(type: CleanupSuggestion.Type) = _uiState.update { it.copy(ignored = it.ignored + type) }

    fun clearTempCache() {
        viewModelScope.launch {
            repository.clearTempCache()
            refresh()
        }
    }
}
