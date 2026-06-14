package app.lock.photo.valut.domain.model

/** Current wrong-attempt / lockout snapshot. */
data class LockoutStatus(
    val attemptCount: Int,
    val lockedOut: Boolean,
    val remainingMillis: Long
)
