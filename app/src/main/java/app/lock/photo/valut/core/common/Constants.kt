package app.lock.photo.valut.core.common

/**
 * App-wide constants. Kept minimal and centralized so values aren't duplicated across modules.
 */
object Constants {

    /** Name of the Preferences DataStore file. */
    const val SETTINGS_DATASTORE_NAME = "app_settings"

    /** Name of the Room database file. */
    const val DATABASE_NAME = "private_lock_vault.db"

    /** Supported PIN lengths. */
    const val PIN_LENGTH_4 = 4
    const val PIN_LENGTH_6 = 6

    /** Default PIN length used until the user picks one. */
    const val DEFAULT_PIN_LENGTH = PIN_LENGTH_4

    /** Minimum number of nodes a pattern must connect. */
    const val MIN_PATTERN_NODES = 4

    // --- Lockout policy ---
    /** Attempt count at which a soft warning is shown (no lockout yet). */
    const val ATTEMPTS_WARNING = 3
    /** Attempt count at which the first (short) lockout triggers. */
    const val ATTEMPTS_SHORT_LOCK = 5
    /** Attempt count at which the long lockout triggers. */
    const val ATTEMPTS_LONG_LOCK = 10
    /** Short lockout duration (30 seconds). */
    const val SHORT_LOCK_MILLIS = 30_000L
    /** Long lockout duration (5 minutes). */
    const val LONG_LOCK_MILLIS = 5 * 60_000L

    // --- Intent extras (never carries a raw credential) ---
    /** Boolean extra signalling Change-PIN should run as a recovery reset (no old PIN required). */
    const val EXTRA_RESET_MODE = "extra_reset_mode"
}
