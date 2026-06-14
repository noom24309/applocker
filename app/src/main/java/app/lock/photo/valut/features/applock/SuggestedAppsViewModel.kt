package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.domain.model.InstalledAppInfo
import app.lock.photo.valut.domain.model.SuggestedApp
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.domain.usecase.GetSuggestedAppsToLockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A suggested app plus its current selection state. */
data class SuggestedAppUi(val app: SuggestedApp, val selected: Boolean)

@HiltViewModel
class SuggestedAppsViewModel @Inject constructor(
    private val getSuggestions: GetSuggestedAppsToLockUseCase,
    private val repository: AppLockRepository,
    private val serviceManager: AppLockServiceManager
) : ViewModel() {

    private val selected = mutableSetOf<String>()

    private val _items = MutableStateFlow<List<SuggestedAppUi>>(emptyList())
    val items: StateFlow<List<SuggestedAppUi>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val locked = Channel<Int>(Channel.BUFFERED)
    val lockedFlow = locked.receiveAsFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            val suggestions = getSuggestions()
            selected.addAll(suggestions.map { it.packageName }) // all selected by default
            _items.value = suggestions.map { SuggestedAppUi(it, true) }
            _loading.value = false
        }
    }

    fun toggle(packageName: String) {
        if (!selected.add(packageName)) selected.remove(packageName)
        _items.value = _items.value.map {
            if (it.app.packageName == packageName) it.copy(selected = packageName in selected) else it
        }
    }

    fun lockSelected() {
        viewModelScope.launch {
            val toLock = _items.value.filter { it.selected }
            toLock.forEach {
                repository.setAppLocked(
                    InstalledAppInfo(it.app.packageName, it.app.appName, it.app.isSystemApp),
                    locked = true
                )
            }
            if (serviceManager.canStartProtection() && !serviceManager.isServiceRunning()) {
                serviceManager.startProtection()
            }
            locked.trySend(toLock.size)
        }
    }
}
