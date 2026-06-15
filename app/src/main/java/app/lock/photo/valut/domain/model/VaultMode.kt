package app.lock.photo.valut.domain.model

/**
 * Which vault a record belongs to. Phase 8 (Decoy Vault) is not implemented yet, so
 * everything is [REAL] today; the column exists so decoy can be plugged in later without
 * another migration. Persisted as a string.
 */
object VaultMode {
    const val REAL = "REAL"
    const val DECOY = "DECOY"
}
