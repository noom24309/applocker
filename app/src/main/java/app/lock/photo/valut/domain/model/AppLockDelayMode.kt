package app.lock.photo.valut.domain.model

/** Practical "stays unlocked" window for event-driven delay modes (24h). */
private const val LONG_GRACE = 24L * 60L * 60L * 1000L

/**
 * How long a protected app stays unlocked after a successful unlock before the lock
 * screen is shown again.
 *
 * Fixed delays use [durationMillis]. The "until" modes use a long grace and rely on
 * events to re-lock: [UNTIL_APP_SWITCH] re-locks when you leave the app,
 * [UNTIL_SCREEN_OFF]/[UNTIL_DEVICE_LOCKED] re-lock when the screen turns off.
 */
enum class AppLockDelayMode(val durationMillis: Long) {
    IMMEDIATE(0L),
    SECONDS_5(5_000L),
    SECONDS_15(15_000L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_5(5 * 60_000L),
    UNTIL_SCREEN_OFF(LONG_GRACE),
    UNTIL_APP_SWITCH(0L),
    UNTIL_DEVICE_LOCKED(LONG_GRACE);

    /** Cleared by a screen-off / device-lock event rather than by elapsed time. */
    val clearedByScreenOff: Boolean
        get() = this == UNTIL_SCREEN_OFF || this == UNTIL_DEVICE_LOCKED

    companion object {
        val DEFAULT = IMMEDIATE

        fun fromStorage(value: String?): AppLockDelayMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}