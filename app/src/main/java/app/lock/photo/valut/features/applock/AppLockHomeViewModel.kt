package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.features.applock.model.AppLockHomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockHomeViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val serviceManager: AppLockServiceManager,
    private val permissionChecker: AppLockPermissionChecker,
    appLockRepository: AppLockRepository
) : ViewModel() {

    private val permissionsGranted = MutableStateFlow(false)

    val uiState: StateFlow<AppLockHomeUiState> = combine(
        dataStore.appLockFeatureEnabled,
        appLockRepository.observeLockedCount(),
        serviceManager.observeServiceState(),
        permissionsGranted
    ) { enabled, count, running, perms ->
        AppLockHomeUiState(
            isAppLockEnabled = enabled,
            lockedAppsCount = count,
            isServiceRunning = running,
            permissionsGranted = perms,
            canStartProtection = enabled && perms && count > 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockHomeUiState())

    /** Re-reads live permission status (permissions can change outside the app). */
    fun refreshPermissions() {
        permissionsGranted.value = permissionChecker.hasAllRequiredAppLockPermissions()
        // Resume protection the user previously turned on once permissions are granted
        // again (e.g. they just came back from the permission screen).
        viewModelScope.launch {
            val intendedOn = dataStore.appLockServiceEnabled.first()
            if (intendedOn && serviceManager.canStartProtection() && !serviceManager.isServiceRunning()) {
                serviceManager.startProtection()
            }
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAppLockFeatureEnabled(enabled)
            if (enabled) {
                if (serviceManager.canStartProtection()) serviceManager.startProtection()
            } else {
                serviceManager.stopProtection()
            }
        }
    }

    fun startProtection() {
        viewModelScope.launch { serviceManager.startProtection() }
    }

    fun stopProtection() {
        viewModelScope.launch { serviceManager.stopProtection() }
    }
}
