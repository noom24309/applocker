package app.lock.photo.valut.core.intruder

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import app.lock.photo.valut.domain.repository.IntruderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a wrong PIN/Pattern attempt should trigger an intruder capture, then
 * runs it off the UI thread. Gating: feature enabled, the trigger source is enabled, the
 * wrong-attempt count reached the threshold, and a debounce window has passed (so one
 * wrong-attempt burst never produces multiple photos). Never blocks the unlock UI.
 */
@Singleton
class IntruderAttemptHandler @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val cameraManager: IntruderCameraManager,
    private val repository: IntruderRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastCaptureAt = 0L

    /** Fire-and-forget: call right after recording a wrong PIN/pattern attempt. */
    fun onWrongAttempt(context: IntruderTriggerContext) {
        scope.launch {
            if (!dataStore.intruderAlertEnabled.first()) return@launch
            if (!cameraManager.hasCameraPermission()) return@launch
            if (!isSourceEnabled(context.trigger)) return@launch

            val threshold = dataStore.intruderCaptureAfterAttempts.first()
            if (context.wrongAttemptCount < threshold) return@launch

            val now = System.currentTimeMillis()
            if (now - lastCaptureAt < DEBOUNCE_MILLIS) return@launch
            lastCaptureAt = now

            runCatching { repository.captureAndSaveIntruder(context) }
        }
    }

    private suspend fun isSourceEnabled(trigger: IntruderTrigger): Boolean = when (trigger) {
        IntruderTrigger.APP_UNLOCK,
        IntruderTrigger.PATTERN_UNLOCK -> dataStore.intruderCaptureOnAppUnlock.first()
        IntruderTrigger.VAULT_UNLOCK -> dataStore.intruderCaptureOnVaultUnlock.first()
        IntruderTrigger.APP_LOCK_OVERLAY,
        IntruderTrigger.FAKE_CALCULATOR,
        IntruderTrigger.FAKE_CRASH -> dataStore.intruderCaptureOnAppLockOverlay.first()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 3_000L
    }
}
