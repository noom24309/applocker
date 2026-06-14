package app.lock.photo.valut.data.local.relation

/** Aggregate counts/usage for the vault dashboard (single-row query result). */
data class VaultCounts(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val favoriteCount: Int = 0,
    val recycleBinCount: Int = 0,
    val storageUsedBytes: Long = 0
)
