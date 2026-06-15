package app.lock.photo.valut.domain.repository

import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.model.CleanupSuggestion
import app.lock.photo.valut.domain.model.DuplicateGroup
import app.lock.photo.valut.domain.model.StorageBreakdown
import app.lock.photo.valut.domain.model.VaultHealth
import kotlinx.coroutines.flow.Flow

/**
 * Local-only premium analysis tools (duplicate finder, large files, storage analyzer,
 * vault health, smart cleanup). All work runs on Dispatchers.IO; nothing is uploaded.
 * REAL vault only until Phase 8 (decoy) exists.
 */
interface PremiumToolsRepository {

    /**
     * Finds byte-identical photo groups, computing+persisting any missing checksums first.
     * [onProgress] reports (processed, total) for the checksum pass.
     */
    suspend fun scanDuplicatePhotos(onProgress: (processed: Int, total: Int) -> Unit): List<DuplicateGroup>

    /** Live list of media at/above [minSizeBytes], largest first. */
    fun observeLargeFiles(minSizeBytes: Long): Flow<List<VaultMediaEntity>>

    suspend fun getStorageBreakdown(): StorageBreakdown

    suspend fun getVaultHealth(): VaultHealth

    suspend fun getCleanupSuggestions(): List<CleanupSuggestion>

    /** Soft-deletes media to the Recycle Bin (shared with the vault flow). */
    suspend fun moveToRecycleBin(ids: List<Long>)

    /** Clears decrypted temp + private-camera temp working files. */
    suspend fun clearTempCache()

    /** Records "now" as the last vault scan time (drives the health dashboard). */
    suspend fun markScanned()
}
