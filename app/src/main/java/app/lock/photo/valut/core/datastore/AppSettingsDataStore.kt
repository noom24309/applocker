package app.lock.photo.valut.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lock.photo.valut.core.common.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore delegate scoped to the application context. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SETTINGS_DATASTORE_NAME
)

/**
 * Typed wrapper around the Preferences DataStore. Holds only non-sensitive flags
 * and *hashed* credential material (never raw PINs/patterns/recovery keys).
 * Provided as a singleton by [app.lock.photo.valut.di.DataStoreModule].
 */
class AppSettingsDataStore(
    private val context: Context
) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        val PIN_CREATED = booleanPreferencesKey("pin_created")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")

        val PATTERN_ENABLED = booleanPreferencesKey("pattern_enabled")
        val PATTERN_HASH = stringPreferencesKey("pattern_hash")
        val PATTERN_SALT = stringPreferencesKey("pattern_salt")

        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val UNLOCK_METHOD = stringPreferencesKey("unlock_method")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val AUTO_LOCK_MODE = stringPreferencesKey("auto_lock_mode")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")

        val LAST_UNLOCK_TIME = longPreferencesKey("last_unlock_time")
        val LAST_BACKGROUND_TIME = longPreferencesKey("last_background_time")
        val IS_APP_UNLOCKED = booleanPreferencesKey("is_app_unlocked")

        val WRONG_ATTEMPT_COUNT = intPreferencesKey("wrong_attempt_count")
        val LOCKOUT_END_TIME = longPreferencesKey("lockout_end_time")

        val RECOVERY_KEY_HASH = stringPreferencesKey("recovery_key_hash")
        val RECOVERY_KEY_SALT = stringPreferencesKey("recovery_key_salt")
        val RECOVERY_KEY_CREATED = booleanPreferencesKey("recovery_key_created")

        // --- Phase 5: App Lock feature (distinct from the vault app's own auto-lock) ---
        val APP_LOCK_FEATURE_ENABLED = booleanPreferencesKey("app_lock_feature_enabled")
        val APP_LOCK_SERVICE_ENABLED = booleanPreferencesKey("app_lock_service_enabled")
        val APP_LOCK_DELAY_MODE = stringPreferencesKey("app_lock_delay_mode")
        val RELOCK_AFTER_SCREEN_OFF = booleanPreferencesKey("relock_after_screen_off")
        val RELOCK_AFTER_APP_SWITCH = booleanPreferencesKey("relock_after_app_switch")
        val LOCK_NEW_APPS_AUTOMATICALLY = booleanPreferencesKey("lock_new_apps_automatically")
        val APP_LOCK_NOTIFICATION_ENABLED = booleanPreferencesKey("app_lock_notification_enabled")

        // --- Phase 6: Advanced App Lock ---
        val GLOBAL_LOCK_THEME = stringPreferencesKey("global_lock_theme")
        val DEFAULT_FAKE_MODE = stringPreferencesKey("default_fake_mode")
        val HIDE_APP_NAME_ON_LOCK = booleanPreferencesKey("hide_app_name_on_lock")
        val RELOCK_AFTER_DEVICE_LOCK = booleanPreferencesKey("relock_after_device_lock")
        val LAST_SECURITY_VERIFICATION_TIME = longPreferencesKey("last_security_verification_time")
        val VERIFY_SESSION_TIMEOUT_MILLIS = longPreferencesKey("verify_session_timeout_millis")
        val SHOW_NEW_APP_LOCK_PROMPT = booleanPreferencesKey("show_new_app_lock_prompt")
        val HIDE_RECENT_PREVIEW = booleanPreferencesKey("hide_recent_preview")
        val RESTART_PROTECTION_AFTER_BOOT = booleanPreferencesKey("restart_protection_after_boot")
        val LOCAL_STATS_ENABLED = booleanPreferencesKey("local_stats_enabled")
        val BATTERY_HELP_SHOWN = booleanPreferencesKey("battery_help_shown")
        val LAST_HANDLED_PACKAGE_TIME = longPreferencesKey("last_handled_package_time")

        // --- Phase 7: Intruder Alert ---
        val INTRUDER_ALERT_ENABLED = booleanPreferencesKey("intruder_alert_enabled")
        val INTRUDER_CAPTURE_AFTER_ATTEMPTS = intPreferencesKey("intruder_capture_after_attempts")
        val INTRUDER_CAPTURE_ON_APP_UNLOCK = booleanPreferencesKey("intruder_capture_on_app_unlock")
        val INTRUDER_CAPTURE_ON_APP_LOCK_OVERLAY = booleanPreferencesKey("intruder_capture_on_app_lock_overlay")
        val INTRUDER_CAPTURE_ON_VAULT_UNLOCK = booleanPreferencesKey("intruder_capture_on_vault_unlock")
        val INTRUDER_SAVE_ENCRYPTED = booleanPreferencesKey("intruder_save_encrypted")
        val INTRUDER_SHOW_NOTIFICATION = booleanPreferencesKey("intruder_show_notification")
        val INTRUDER_HIDE_NOTIFICATION_CONTENT = booleanPreferencesKey("intruder_hide_notification_content")
        val INTRUDER_AUTO_DELETE_MODE = stringPreferencesKey("intruder_auto_delete_mode")
        val INTRUDER_MAX_RECORDS = intPreferencesKey("intruder_max_records")

        // --- Phase 9: Private Camera ---
        val PRIVATE_CAMERA_DEFAULT_FACING = stringPreferencesKey("private_camera_default_facing")
        val PRIVATE_CAMERA_DEFAULT_MODE = stringPreferencesKey("private_camera_default_mode")
        val PRIVATE_CAMERA_RECORD_AUDIO = booleanPreferencesKey("private_camera_record_audio")
        val PRIVATE_CAMERA_VIDEO_QUALITY = stringPreferencesKey("private_camera_video_quality")
        val PRIVATE_CAMERA_PHOTO_QUALITY = stringPreferencesKey("private_camera_photo_quality")
        val PRIVATE_CAMERA_SHOW_CAPTURE_PREVIEW = booleanPreferencesKey("private_camera_show_capture_preview")
        val PRIVATE_CAMERA_KEEP_SCREEN_AWAKE = booleanPreferencesKey("private_camera_keep_screen_awake")
        val PRIVATE_CAMERA_DEFAULT_ALBUM_REAL = longPreferencesKey("private_camera_default_album_real")

        // --- Phase 11: Premium Tools ---
        val PREMIUM_UNLOCKED_LOCAL = booleanPreferencesKey("premium_unlocked_local")
        val PREMIUM_TRIAL_USED = booleanPreferencesKey("premium_trial_used")
        val LAST_VAULT_SCAN_TIME = longPreferencesKey("last_vault_scan_time")
    }

    // --- Onboarding ---
    val onboardingCompleted: Flow<Boolean> = read(Keys.ONBOARDING_COMPLETED, false)
    suspend fun setOnboardingCompleted(value: Boolean) = write(Keys.ONBOARDING_COMPLETED, value)

    // --- PIN ---
    val pinCreated: Flow<Boolean> = read(Keys.PIN_CREATED, false)
    val pinLength: Flow<Int> = read(Keys.PIN_LENGTH, Constants.DEFAULT_PIN_LENGTH)
    val pinHash: Flow<String?> = readNullable(Keys.PIN_HASH)
    val pinSalt: Flow<String?> = readNullable(Keys.PIN_SALT)

    suspend fun savePin(hash: String, salt: String, length: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_LENGTH] = length
            prefs[Keys.PIN_CREATED] = true
        }
    }

    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs[Keys.PIN_CREATED] = false
        }
    }

    // --- Pattern ---
    val patternEnabled: Flow<Boolean> = read(Keys.PATTERN_ENABLED, false)
    val patternHash: Flow<String?> = readNullable(Keys.PATTERN_HASH)
    val patternSalt: Flow<String?> = readNullable(Keys.PATTERN_SALT)

    suspend fun savePattern(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PATTERN_HASH] = hash
            prefs[Keys.PATTERN_SALT] = salt
            prefs[Keys.PATTERN_ENABLED] = true
        }
    }

    suspend fun clearPattern() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PATTERN_HASH)
            prefs.remove(Keys.PATTERN_SALT)
            prefs[Keys.PATTERN_ENABLED] = false
        }
    }

    // --- Method / biometric / auto-lock ---
    val biometricEnabled: Flow<Boolean> = read(Keys.BIOMETRIC_ENABLED, false)
    suspend fun setBiometricEnabled(value: Boolean) = write(Keys.BIOMETRIC_ENABLED, value)

    val unlockMethod: Flow<String?> = readNullable(Keys.UNLOCK_METHOD)
    suspend fun setUnlockMethod(value: String) = write(Keys.UNLOCK_METHOD, value)

    val appLockEnabled: Flow<Boolean> = read(Keys.APP_LOCK_ENABLED, true)
    suspend fun setAppLockEnabled(value: Boolean) = write(Keys.APP_LOCK_ENABLED, value)

    val autoLockMode: Flow<String?> = readNullable(Keys.AUTO_LOCK_MODE)
    suspend fun setAutoLockMode(value: String) = write(Keys.AUTO_LOCK_MODE, value)

    val appearanceMode: Flow<String?> = readNullable(Keys.APPEARANCE_MODE)
    suspend fun setAppearanceMode(value: String) = write(Keys.APPEARANCE_MODE, value)

    // --- Session / timestamps ---
    val lastUnlockTime: Flow<Long> = read(Keys.LAST_UNLOCK_TIME, 0L)
    suspend fun setLastUnlockTime(value: Long) = write(Keys.LAST_UNLOCK_TIME, value)

    val lastBackgroundTime: Flow<Long> = read(Keys.LAST_BACKGROUND_TIME, 0L)
    suspend fun setLastBackgroundTime(value: Long) = write(Keys.LAST_BACKGROUND_TIME, value)

    val isAppUnlocked: Flow<Boolean> = read(Keys.IS_APP_UNLOCKED, false)
    suspend fun setAppUnlocked(value: Boolean) = write(Keys.IS_APP_UNLOCKED, value)

    // --- Wrong attempts / lockout ---
    val wrongAttemptCount: Flow<Int> = read(Keys.WRONG_ATTEMPT_COUNT, 0)
    suspend fun setWrongAttemptCount(value: Int) = write(Keys.WRONG_ATTEMPT_COUNT, value)

    val lockoutEndTime: Flow<Long> = read(Keys.LOCKOUT_END_TIME, 0L)
    suspend fun setLockoutEndTime(value: Long) = write(Keys.LOCKOUT_END_TIME, value)

    suspend fun resetAttempts() {
        context.dataStore.edit { prefs ->
            prefs[Keys.WRONG_ATTEMPT_COUNT] = 0
            prefs[Keys.LOCKOUT_END_TIME] = 0L
        }
    }

    // --- Recovery key ---
    val recoveryKeyCreated: Flow<Boolean> = read(Keys.RECOVERY_KEY_CREATED, false)
    val recoveryKeyHash: Flow<String?> = readNullable(Keys.RECOVERY_KEY_HASH)
    val recoveryKeySalt: Flow<String?> = readNullable(Keys.RECOVERY_KEY_SALT)

    suspend fun saveRecoveryKey(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECOVERY_KEY_HASH] = hash
            prefs[Keys.RECOVERY_KEY_SALT] = salt
            prefs[Keys.RECOVERY_KEY_CREATED] = true
        }
    }

    // --- App Lock feature (Phase 5) ---
    val appLockFeatureEnabled: Flow<Boolean> = read(Keys.APP_LOCK_FEATURE_ENABLED, false)
    suspend fun setAppLockFeatureEnabled(value: Boolean) = write(Keys.APP_LOCK_FEATURE_ENABLED, value)

    val appLockServiceEnabled: Flow<Boolean> = read(Keys.APP_LOCK_SERVICE_ENABLED, false)
    suspend fun setAppLockServiceEnabled(value: Boolean) = write(Keys.APP_LOCK_SERVICE_ENABLED, value)

    val appLockDelayMode: Flow<String?> = readNullable(Keys.APP_LOCK_DELAY_MODE)
    suspend fun setAppLockDelayMode(value: String) = write(Keys.APP_LOCK_DELAY_MODE, value)

    val relockAfterScreenOff: Flow<Boolean> = read(Keys.RELOCK_AFTER_SCREEN_OFF, true)
    suspend fun setRelockAfterScreenOff(value: Boolean) = write(Keys.RELOCK_AFTER_SCREEN_OFF, value)

    val relockAfterAppSwitch: Flow<Boolean> = read(Keys.RELOCK_AFTER_APP_SWITCH, true)
    suspend fun setRelockAfterAppSwitch(value: Boolean) = write(Keys.RELOCK_AFTER_APP_SWITCH, value)

    val lockNewAppsAutomatically: Flow<Boolean> = read(Keys.LOCK_NEW_APPS_AUTOMATICALLY, false)
    suspend fun setLockNewAppsAutomatically(value: Boolean) = write(Keys.LOCK_NEW_APPS_AUTOMATICALLY, value)

    val appLockNotificationEnabled: Flow<Boolean> = read(Keys.APP_LOCK_NOTIFICATION_ENABLED, true)
    suspend fun setAppLockNotificationEnabled(value: Boolean) = write(Keys.APP_LOCK_NOTIFICATION_ENABLED, value)

    // --- Phase 6: Advanced App Lock ---
    val globalLockTheme: Flow<String?> = readNullable(Keys.GLOBAL_LOCK_THEME)
    suspend fun setGlobalLockTheme(value: String) = write(Keys.GLOBAL_LOCK_THEME, value)

    val defaultFakeMode: Flow<String?> = readNullable(Keys.DEFAULT_FAKE_MODE)
    suspend fun setDefaultFakeMode(value: String) = write(Keys.DEFAULT_FAKE_MODE, value)

    val hideAppNameOnLock: Flow<Boolean> = read(Keys.HIDE_APP_NAME_ON_LOCK, false)
    suspend fun setHideAppNameOnLock(value: Boolean) = write(Keys.HIDE_APP_NAME_ON_LOCK, value)

    val relockAfterDeviceLock: Flow<Boolean> = read(Keys.RELOCK_AFTER_DEVICE_LOCK, true)
    suspend fun setRelockAfterDeviceLock(value: Boolean) = write(Keys.RELOCK_AFTER_DEVICE_LOCK, value)

    val lastSecurityVerificationTime: Flow<Long> = read(Keys.LAST_SECURITY_VERIFICATION_TIME, 0L)
    suspend fun setLastSecurityVerificationTime(value: Long) = write(Keys.LAST_SECURITY_VERIFICATION_TIME, value)

    val verifySessionTimeoutMillis: Flow<Long> = read(Keys.VERIFY_SESSION_TIMEOUT_MILLIS, DEFAULT_VERIFY_TIMEOUT)
    suspend fun setVerifySessionTimeoutMillis(value: Long) = write(Keys.VERIFY_SESSION_TIMEOUT_MILLIS, value)

    val showNewAppLockPrompt: Flow<Boolean> = read(Keys.SHOW_NEW_APP_LOCK_PROMPT, true)
    suspend fun setShowNewAppLockPrompt(value: Boolean) = write(Keys.SHOW_NEW_APP_LOCK_PROMPT, value)

    val hideRecentPreview: Flow<Boolean> = read(Keys.HIDE_RECENT_PREVIEW, true)
    suspend fun setHideRecentPreview(value: Boolean) = write(Keys.HIDE_RECENT_PREVIEW, value)

    val restartProtectionAfterBoot: Flow<Boolean> = read(Keys.RESTART_PROTECTION_AFTER_BOOT, true)
    suspend fun setRestartProtectionAfterBoot(value: Boolean) = write(Keys.RESTART_PROTECTION_AFTER_BOOT, value)

    val localStatsEnabled: Flow<Boolean> = read(Keys.LOCAL_STATS_ENABLED, true)
    suspend fun setLocalStatsEnabled(value: Boolean) = write(Keys.LOCAL_STATS_ENABLED, value)

    val batteryHelpShown: Flow<Boolean> = read(Keys.BATTERY_HELP_SHOWN, false)
    suspend fun setBatteryHelpShown(value: Boolean) = write(Keys.BATTERY_HELP_SHOWN, value)

    val lastHandledPackageTime: Flow<Long> = read(Keys.LAST_HANDLED_PACKAGE_TIME, 0L)
    suspend fun setLastHandledPackageTime(value: Long) = write(Keys.LAST_HANDLED_PACKAGE_TIME, value)

    // --- Phase 7: Intruder Alert ---
    val intruderAlertEnabled: Flow<Boolean> = read(Keys.INTRUDER_ALERT_ENABLED, false)
    suspend fun setIntruderAlertEnabled(value: Boolean) = write(Keys.INTRUDER_ALERT_ENABLED, value)

    val intruderCaptureAfterAttempts: Flow<Int> = read(Keys.INTRUDER_CAPTURE_AFTER_ATTEMPTS, 2)
    suspend fun setIntruderCaptureAfterAttempts(value: Int) = write(Keys.INTRUDER_CAPTURE_AFTER_ATTEMPTS, value)

    val intruderCaptureOnAppUnlock: Flow<Boolean> = read(Keys.INTRUDER_CAPTURE_ON_APP_UNLOCK, true)
    suspend fun setIntruderCaptureOnAppUnlock(value: Boolean) = write(Keys.INTRUDER_CAPTURE_ON_APP_UNLOCK, value)

    val intruderCaptureOnAppLockOverlay: Flow<Boolean> = read(Keys.INTRUDER_CAPTURE_ON_APP_LOCK_OVERLAY, true)
    suspend fun setIntruderCaptureOnAppLockOverlay(value: Boolean) = write(Keys.INTRUDER_CAPTURE_ON_APP_LOCK_OVERLAY, value)

    val intruderCaptureOnVaultUnlock: Flow<Boolean> = read(Keys.INTRUDER_CAPTURE_ON_VAULT_UNLOCK, true)
    suspend fun setIntruderCaptureOnVaultUnlock(value: Boolean) = write(Keys.INTRUDER_CAPTURE_ON_VAULT_UNLOCK, value)

    val intruderSaveEncrypted: Flow<Boolean> = read(Keys.INTRUDER_SAVE_ENCRYPTED, true)
    suspend fun setIntruderSaveEncrypted(value: Boolean) = write(Keys.INTRUDER_SAVE_ENCRYPTED, value)

    val intruderShowNotification: Flow<Boolean> = read(Keys.INTRUDER_SHOW_NOTIFICATION, true)
    suspend fun setIntruderShowNotification(value: Boolean) = write(Keys.INTRUDER_SHOW_NOTIFICATION, value)

    val intruderHideNotificationContent: Flow<Boolean> = read(Keys.INTRUDER_HIDE_NOTIFICATION_CONTENT, false)
    suspend fun setIntruderHideNotificationContent(value: Boolean) = write(Keys.INTRUDER_HIDE_NOTIFICATION_CONTENT, value)

    val intruderAutoDeleteMode: Flow<String?> = readNullable(Keys.INTRUDER_AUTO_DELETE_MODE)
    suspend fun setIntruderAutoDeleteMode(value: String) = write(Keys.INTRUDER_AUTO_DELETE_MODE, value)

    val intruderMaxRecords: Flow<Int> = read(Keys.INTRUDER_MAX_RECORDS, 100)
    suspend fun setIntruderMaxRecords(value: Int) = write(Keys.INTRUDER_MAX_RECORDS, value)

    // --- Phase 9: Private Camera ---
    val privateCameraDefaultFacing: Flow<String?> = readNullable(Keys.PRIVATE_CAMERA_DEFAULT_FACING)
    suspend fun setPrivateCameraDefaultFacing(value: String) = write(Keys.PRIVATE_CAMERA_DEFAULT_FACING, value)

    val privateCameraDefaultMode: Flow<String?> = readNullable(Keys.PRIVATE_CAMERA_DEFAULT_MODE)
    suspend fun setPrivateCameraDefaultMode(value: String) = write(Keys.PRIVATE_CAMERA_DEFAULT_MODE, value)

    val privateCameraRecordAudioEnabled: Flow<Boolean> = read(Keys.PRIVATE_CAMERA_RECORD_AUDIO, false)
    suspend fun setPrivateCameraRecordAudioEnabled(value: Boolean) = write(Keys.PRIVATE_CAMERA_RECORD_AUDIO, value)

    val privateCameraVideoQuality: Flow<String?> = readNullable(Keys.PRIVATE_CAMERA_VIDEO_QUALITY)
    suspend fun setPrivateCameraVideoQuality(value: String) = write(Keys.PRIVATE_CAMERA_VIDEO_QUALITY, value)

    val privateCameraPhotoQuality: Flow<String?> = readNullable(Keys.PRIVATE_CAMERA_PHOTO_QUALITY)
    suspend fun setPrivateCameraPhotoQuality(value: String) = write(Keys.PRIVATE_CAMERA_PHOTO_QUALITY, value)

    val privateCameraShowCapturePreview: Flow<Boolean> = read(Keys.PRIVATE_CAMERA_SHOW_CAPTURE_PREVIEW, true)
    suspend fun setPrivateCameraShowCapturePreview(value: Boolean) = write(Keys.PRIVATE_CAMERA_SHOW_CAPTURE_PREVIEW, value)

    val privateCameraKeepScreenAwake: Flow<Boolean> = read(Keys.PRIVATE_CAMERA_KEEP_SCREEN_AWAKE, true)
    suspend fun setPrivateCameraKeepScreenAwake(value: Boolean) = write(Keys.PRIVATE_CAMERA_KEEP_SCREEN_AWAKE, value)

    val privateCameraDefaultAlbumReal: Flow<Long> = read(Keys.PRIVATE_CAMERA_DEFAULT_ALBUM_REAL, -1L)
    suspend fun setPrivateCameraDefaultAlbumReal(value: Long) = write(Keys.PRIVATE_CAMERA_DEFAULT_ALBUM_REAL, value)

    // --- Phase 11: Premium Tools ---
    // Defaults to true so the implemented tools are usable now; Phase 12 will gate via Billing.
    val premiumUnlockedLocal: Flow<Boolean> = read(Keys.PREMIUM_UNLOCKED_LOCAL, true)
    suspend fun setPremiumUnlockedLocal(value: Boolean) = write(Keys.PREMIUM_UNLOCKED_LOCAL, value)

    val premiumTrialUsed: Flow<Boolean> = read(Keys.PREMIUM_TRIAL_USED, false)
    suspend fun setPremiumTrialUsed(value: Boolean) = write(Keys.PREMIUM_TRIAL_USED, value)

    val lastVaultScanTime: Flow<Long> = read(Keys.LAST_VAULT_SCAN_TIME, 0L)
    suspend fun setLastVaultScanTime(value: Long) = write(Keys.LAST_VAULT_SCAN_TIME, value)

    // --- helpers ---
    private fun read(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        context.dataStore.data.map { it[key] ?: default }

    private fun read(key: Preferences.Key<Int>, default: Int): Flow<Int> =
        context.dataStore.data.map { it[key] ?: default }

    private fun read(key: Preferences.Key<Long>, default: Long): Flow<Long> =
        context.dataStore.data.map { it[key] ?: default }

    private fun readNullable(key: Preferences.Key<String>): Flow<String?> =
        context.dataStore.data.map { it[key] }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private companion object {
        /** Default verified-session window for sensitive App Lock changes (2 minutes). */
        const val DEFAULT_VERIFY_TIMEOUT = 2L * 60L * 1000L
    }
}
