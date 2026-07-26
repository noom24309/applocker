package app.lock.photo.valut.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rejects trivially guessable patterns. Pure logic, no Android deps, so it is
 * fully unit-testable.
 *
 * PINs are deliberately not screened: any digits the user picks are accepted.
 */
@Singleton
class WeakCredentialChecker @Inject constructor() {

    /** A pattern is weak if it is a straight ascending/descending row scan of the grid. */
    fun isWeakPattern(nodes: List<Int>): Boolean {
        if (nodes.size < 4) return true
        if (nodes == (0..8).toList()) return true
        if (nodes == (0..8).toList().reversed()) return true
        return false
    }
}
