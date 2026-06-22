package app.lock.photo.valut.features.permissions

import androidx.lifecycle.ViewModel
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class AppLockPermissionViewModel @Inject constructor(
    private val permissionChecker: AppLockPermissionChecker,
    private val dataStore: AppSettingsDataStore,
    private val serviceManager: AppLockServiceManager
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockPermissionUiState())
    val state: StateFlow<AppLockPermissionUiState> = _state.asStateFlow()

    /** Live single-permission checks, used to poll for a grant while the user is in Settings. */
    fun hasUsageAccess(): Boolean = permissionChecker.hasUsageAccess()
    fun hasOverlayPermission(): Boolean = permissionChecker.hasOverlayPermission()

    /** Re-reads the live permission state (call on resume + "Check again"). */
    fun refresh() {
        _state.value = AppLockPermissionUiState(
            hasUsageAccess = permissionChecker.hasUsageAccess(),
            hasOverlayPermission = permissionChecker.hasOverlayPermission(),
            hasNotificationPermission = permissionChecker.hasNotificationPermission(),
            isLoading = false
        )
    }

    /**
     * Protection is considered active once all permissions are granted AND the App Lock
     * feature is on. The pre-home gate uses this to pass straight through when there is
     * nothing left to set up.
     */
    suspend fun isProtectionActive(): Boolean =
        permissionChecker.hasAllRequiredAppLockPermissions() &&
            dataStore.appLockFeatureEnabled.first()

    /**
     * Persists that onboarding is finished. Called when the gate is first reached at the end of
     * the onboarding flow, so subsequent launches route straight back here (not into onboarding).
     */
    suspend fun markOnboardingComplete() {
        dataStore.setOnboardingCompleted(true)
    }

    /**
     * Turns App Lock on and starts the monitor service when it can run (≥1 locked app).
     * Called from the gate's "Activate protection" action once permissions are granted.
     */
    suspend fun activateProtection() {
        dataStore.setAppLockFeatureEnabled(true)
        if (serviceManager.canStartProtection()) serviceManager.startProtection()
    }
}
