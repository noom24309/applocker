package app.lock.photo.valut.features.intruder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.VerifySessionManager
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.intruder.IntruderCameraManager
import app.lock.photo.valut.domain.model.IntruderAutoDeleteMode
import app.lock.photo.valut.features.intruder.model.IntruderSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntruderSettingsViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val cameraManager: IntruderCameraManager,
    private val verifySessionManager: VerifySessionManager
) : ViewModel() {

    private val cameraPermission = MutableStateFlow(false)

    private val captureGroup = combine(
        dataStore.intruderCaptureOnAppUnlock,
        dataStore.intruderCaptureOnAppLockOverlay,
        dataStore.intruderCaptureOnVaultUnlock,
        dataStore.intruderCaptureAfterAttempts
    ) { appUnlock, overlay, vault, attempts -> arrayOf<Any?>(appUnlock, overlay, vault, attempts) }

    private val storageGroup = combine(
        dataStore.intruderSaveEncrypted,
        dataStore.intruderShowNotification,
        dataStore.intruderHideNotificationContent,
        dataStore.intruderAutoDeleteMode,
        dataStore.intruderMaxRecords
    ) { encrypted, notif, hide, autoDelete, maxRecords ->
        arrayOf<Any?>(encrypted, notif, hide, autoDelete, maxRecords)
    }

    val uiState: StateFlow<IntruderSettingsUiState> = combine(
        dataStore.intruderAlertEnabled,
        cameraPermission,
        captureGroup,
        storageGroup
    ) { enabled, permission, capture, storage ->
        IntruderSettingsUiState(
            enabled = enabled,
            cameraPermissionGranted = permission,
            cameraAvailable = cameraManager.isCameraAvailable(),
            captureOnAppUnlock = capture[0] as Boolean,
            captureOnAppLockOverlay = capture[1] as Boolean,
            captureOnVaultUnlock = capture[2] as Boolean,
            captureAfterAttempts = capture[3] as Int,
            saveEncrypted = storage[0] as Boolean,
            showNotification = storage[1] as Boolean,
            hideNotificationContent = storage[2] as Boolean,
            autoDeleteMode = IntruderAutoDeleteMode.fromStorage(storage[3] as String?),
            maxRecords = storage[4] as Int
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntruderSettingsUiState())

    fun refreshPermission() { cameraPermission.value = cameraManager.hasCameraPermission() }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Only allow enabling when camera permission is granted; otherwise force off.
            val granted = cameraManager.hasCameraPermission()
            dataStore.setIntruderAlertEnabled(enabled && granted)
        }
    }

    fun setCaptureAfterAttempts(value: Int) {
        viewModelScope.launch { dataStore.setIntruderCaptureAfterAttempts(value) }
    }

    fun setCaptureOnAppUnlock(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderCaptureOnAppUnlock(value) }
    }

    fun setCaptureOnAppLockOverlay(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderCaptureOnAppLockOverlay(value) }
    }

    fun setCaptureOnVaultUnlock(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderCaptureOnVaultUnlock(value) }
    }

    fun setSaveEncrypted(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderSaveEncrypted(value) }
    }

    fun setShowNotification(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderShowNotification(value) }
    }

    fun setHideNotificationContent(value: Boolean) {
        viewModelScope.launch { dataStore.setIntruderHideNotificationContent(value) }
    }

    fun setAutoDeleteMode(mode: IntruderAutoDeleteMode) {
        viewModelScope.launch { dataStore.setIntruderAutoDeleteMode(mode.name) }
    }

    fun setMaxRecords(value: Int) {
        viewModelScope.launch { dataStore.setIntruderMaxRecords(value) }
    }

    suspend fun needsVerification(): Boolean = !verifySessionManager.isVerificationStillValid()
    fun markVerified() { viewModelScope.launch { verifySessionManager.markVerified() } }
}
