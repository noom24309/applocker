package app.lock.photo.valut.features.intruder.model

import app.lock.photo.valut.domain.model.IntruderAutoDeleteMode

/** One row in the intruder alerts list. Display text is resolved in the adapter. */
data class IntruderAttemptUiModel(
    val id: Long,
    val triggerSource: String,
    val appName: String?,
    val unlockMethod: String,
    val timestamp: Long,
    val wrongAttemptCount: Int,
    val captureSuccess: Boolean,
    val isSelected: Boolean = false
)

/** State for the Intruder Alert settings screen. */
data class IntruderSettingsUiState(
    val enabled: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val cameraAvailable: Boolean = true,
    val captureAfterAttempts: Int = 2,
    val captureOnAppUnlock: Boolean = true,
    val captureOnAppLockOverlay: Boolean = true,
    val captureOnVaultUnlock: Boolean = true,
    val saveEncrypted: Boolean = true,
    val showNotification: Boolean = true,
    val hideNotificationContent: Boolean = false,
    val autoDeleteMode: IntruderAutoDeleteMode = IntruderAutoDeleteMode.DEFAULT,
    val maxRecords: Int = 100
)

/** State for the intruder detail screen. */
data class IntruderDetailUiState(
    val id: Long = 0,
    val triggerSource: String = "",
    val appName: String? = null,
    val unlockMethod: String = "",
    val timestamp: Long = 0,
    val wrongAttemptCount: Int = 0,
    val captureSuccess: Boolean = false,
    val isEncrypted: Boolean = true,
    val failureReason: String? = null,
    val loaded: Boolean = false
)
