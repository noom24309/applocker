package app.lock.photo.valut.domain.model

/**
 * Outcome of a security operation (create / verify / change credential).
 */
sealed interface SecurityResult {
    data object Success : SecurityResult

    /** Supplied credential was incorrect. [attemptCount] is the running wrong-attempt total. */
    data class WrongCredential(val attemptCount: Int) : SecurityResult

    /** Action blocked by an active lockout. [remainingMillis] until it ends. */
    data class LockedOut(val remainingMillis: Long) : SecurityResult

    /** Credential is too easy to guess. */
    data object WeakCredential : SecurityResult

    /** Confirmation did not match the original entry. */
    data object Mismatch : SecurityResult

    /** New credential is identical to the current one (not allowed when changing). */
    data object SameAsOld : SecurityResult

    /** Unexpected failure. */
    data object Error : SecurityResult
}
