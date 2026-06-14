package app.lock.photo.valut.domain.model

/** A launchable installed app the user may choose to protect. */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)
