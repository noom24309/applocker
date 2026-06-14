package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.features.applock.model.AppLockPermissionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppLockPermissionViewModel @Inject constructor(
    private val permissionChecker: AppLockPermissionChecker
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockPermissionUiState())
    val state: StateFlow<AppLockPermissionUiState> = _state.asStateFlow()

    /** Re-reads the live permission state (call on resume + "Check again"). */
    fun refresh() {
        _state.value = AppLockPermissionUiState(
            hasUsageAccess = permissionChecker.hasUsageAccess(),
            hasOverlayPermission = permissionChecker.hasOverlayPermission(),
            hasNotificationPermission = permissionChecker.hasNotificationPermission(),
            isLoading = false
        )
    }
}
