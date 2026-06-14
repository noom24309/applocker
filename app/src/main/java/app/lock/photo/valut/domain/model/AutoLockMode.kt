package app.lock.photo.valut.domain.model

/**
 * When the app should require re-authentication after going to the background.
 *
 * [delayMillis] is the grace period before a lock is required. [NEVER_IN_MEMORY]
 * uses [NEVER] sentinel meaning "stay unlocked while the process is alive".
 */
enum class AutoLockMode(val delayMillis: Long) {
    IMMEDIATE(0L),
    SECONDS_15(15_000L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_5(5 * 60_000L),
    NEVER_IN_MEMORY(-1L);

    companion object {
        /** Sentinel for "never auto-lock while in memory". */
        const val NEVER = -1L

        val DEFAULT = IMMEDIATE

        fun fromStorage(value: String?): AutoLockMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
