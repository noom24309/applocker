package app.lock.photo.valut.domain.model

/** Encryption/migration state of a vault item, persisted as a String. */
object MigrationStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val ENCRYPTING = "ENCRYPTING"
    const val ENCRYPTED = "ENCRYPTED"
    const val FAILED = "FAILED"
    const val NEEDS_REPAIR = "NEEDS_REPAIR"
}

/** Current encryption scheme version. */
const val ENCRYPTION_VERSION = 1
