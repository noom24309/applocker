package app.lock.photo.valut.domain.model

import app.lock.photo.valut.data.local.entity.VaultMediaEntity

/** A set of byte-identical photos (same checksum). */
data class DuplicateGroup(
    val checksum: String,
    val items: List<VaultMediaEntity>,
    val totalSizeBytes: Long,
    val recommendedKeepId: Long
) {
    /** Bytes recoverable if every item except the recommended keep is removed. */
    val recoverableBytes: Long
        get() = items.filter { it.id != recommendedKeepId }.sumOf { it.sizeBytes }
}

/** On-disk / logical storage split shown by the Storage Analyzer. */
data class StorageBreakdown(
    val photosBytes: Long = 0,
    val videosBytes: Long = 0,
    val documentsBytes: Long = 0,
    val privateCameraBytes: Long = 0,
    val recycleBinBytes: Long = 0,
    val thumbnailsBytes: Long = 0,
    val tempCacheBytes: Long = 0
) {
    val totalBytes: Long
        get() = photosBytes + videosBytes + documentsBytes + recycleBinBytes +
            thumbnailsBytes + tempCacheBytes
}

/** Vault health snapshot. */
data class VaultHealth(
    val encryptionActive: Boolean,
    val unencryptedCount: Int,
    val failedRepairCount: Int,
    val recycleBinCount: Int,
    val tempCacheBytes: Long,
    val appLockReady: Boolean,
    val lastScanAt: Long
) {
    /** 0–100 score: starts at 100 and deducts for each open issue. */
    val score: Int
        get() {
            var s = 100
            if (!encryptionActive) s -= 40
            if (unencryptedCount > 0) s -= 15
            if (failedRepairCount > 0) s -= 15
            if (tempCacheBytes > 20L * 1024 * 1024) s -= 10
            if (!appLockReady) s -= 10
            return s.coerceIn(0, 100)
        }
}

/** A single actionable cleanup suggestion. */
data class CleanupSuggestion(
    val type: Type,
    val estimatedBytes: Long,
    val count: Int
) {
    enum class Type { LARGE_VIDEOS, DUPLICATE_PHOTOS, OLD_RECYCLE_BIN, TEMP_CACHE, FAILED_REPAIR }
}
