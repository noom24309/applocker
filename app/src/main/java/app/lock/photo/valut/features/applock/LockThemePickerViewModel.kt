package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.LockTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockThemePickerViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore
) : ViewModel() {

    val selectedTheme: StateFlow<LockTheme> = dataStore.globalLockTheme
        .map { LockTheme.fromStorage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockTheme.GLOBAL_DEFAULT)

    val themes: List<LockTheme> = AppLockLabels.GLOBAL_THEME_OPTIONS

    fun select(theme: LockTheme) {
        viewModelScope.launch { dataStore.setGlobalLockTheme(theme.name) }
    }
}
