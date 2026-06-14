package app.lock.photo.valut.core.applock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the package currently in the foreground using [UsageStatsManager] events.
 *
 * Privacy: this reads only the *latest* foreground transition in a short trailing
 * window to answer "what is on screen right now". It never stores usage history,
 * never logs package names, and the result never leaves the device.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** The package on top right now, or null if it can't be determined. */
    fun getCurrentForegroundPackage(): String? {
        val usm = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MILLIS, now)
        var latestPackage: String? = null
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    private companion object {
        /** Short trailing window — long enough to catch the latest switch, not history. */
        const val LOOKBACK_MILLIS = 10_000L
    }
}
