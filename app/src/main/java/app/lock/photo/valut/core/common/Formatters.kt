package app.lock.photo.valut.core.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Pure, context-free formatting helpers for sizes, durations and dates. */
object Formatters {

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /** Formats milliseconds as m:ss or h:mm:ss. */
    fun formatDuration(millis: Long?): String {
        if (millis == null || millis <= 0) return "0:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun formatResolution(width: Int?, height: Int?): String? =
        if (width != null && height != null && width > 0 && height > 0) "${width} × ${height}" else null
}
