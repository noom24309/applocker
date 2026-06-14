package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.domain.usecase.AppLockHealth
import app.lock.photo.valut.domain.usecase.CheckAppLockHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockTroubleshootingViewModel @Inject constructor(
    private val checkHealth: CheckAppLockHealthUseCase,
    private val serviceManager: AppLockServiceManager
) : ViewModel() {

    private val _health = MutableStateFlow<AppLockHealth?>(null)
    val health: StateFlow<AppLockHealth?> = _health.asStateFlow()

    init {
        checkAgain()
    }

    fun checkAgain() {
        viewModelScope.launch { _health.value = checkHealth() }
    }

    fun restartProtection() {
        viewModelScope.launch {
            serviceManager.restartProtection()
            checkAgain()
        }
    }
}
