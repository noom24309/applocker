package app.lock.photo.valut.features.home

/** Snapshot of the dashboard counters and security state. */
data class HomeUiState(
    val lockedApps: Int = 0,
    val photos: Int = 0,
    val videos: Int = 0,
    val documents: Int = 0,
    val albums: Int = 0,
    val favorites: Int = 0,
    val recycleBin: Int = 0,
    val intruderAlerts: Int = 0,
    val storageUsedBytes: Long = 0,
    val appLockEnabled: Boolean = true
)
