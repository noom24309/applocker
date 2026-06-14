package app.lock.photo.valut.features.applock.model

/** Render state for the App Lock permission setup screen. */
data class AppLockPermissionUiState(
    val hasUsageAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isLoading: Boolean = true
) {
    val canContinue: Boolean
        get() = hasUsageAccess && hasOverlayPermission && hasNotificationPermission
}
