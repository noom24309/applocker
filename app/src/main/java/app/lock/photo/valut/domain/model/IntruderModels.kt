package app.lock.photo.valut.domain.model

/** Where a wrong-unlock attempt that may trigger an intruder capture came from. */
enum class IntruderTrigger {
    APP_UNLOCK,
    VAULT_UNLOCK,
    APP_LOCK_OVERLAY,
    FAKE_CALCULATOR,
    FAKE_CRASH,
    PATTERN_UNLOCK;

    companion object {
        fun fromStorage(value: String?): IntruderTrigger =
            entries.firstOrNull { it.name == value } ?: APP_UNLOCK
    }
}

/** How long intruder records are kept before the cleanup worker removes them. */
enum class IntruderAutoDeleteMode(val days: Int) {
    NEVER(0),
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90);

    companion object {
        val DEFAULT = DAYS_30

        fun fromStorage(value: String?): IntruderAutoDeleteMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Context passed to a capture so the saved record is meaningful. */
data class IntruderTriggerContext(
    val trigger: IntruderTrigger,
    val lockedPackageName: String? = null,
    val lockedAppName: String? = null,
    val unlockMethod: String = "PIN",
    val wrongAttemptCount: Int = 0
)

/** Outcome of an intruder capture attempt. */
sealed interface IntruderCaptureResult {
    data class Success(val attemptId: Long) : IntruderCaptureResult
    data class Failure(val reason: Reason) : IntruderCaptureResult {
        enum class Reason {
            DISABLED, NO_PERMISSION, NO_CAMERA, CAMERA_IN_USE, CAPTURE_FAILED,
            ENCRYPT_FAILED, STORAGE_FULL, UNKNOWN
        }
    }
}

/** Aggregate, local-only intruder stats. */
data class IntruderStats(
    val totalAlerts: Int,
    val lastAlertAt: Long?
)
