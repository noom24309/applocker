package app.lock.photo.valut.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rejects trivially guessable PINs and patterns. Pure logic, no Android deps,
 * so it is fully unit-testable.
 */
@Singleton
class WeakCredentialChecker @Inject constructor() {

    /**
     * A PIN is weak if every digit is the same, or the digits form a simple
     * ascending or descending run, or an alternating 2-digit pattern (e.g. 1212).
     */
    fun isWeakPin(pin: String): Boolean {
        if (pin.isEmpty()) return true
        if (allSameDigit(pin)) return true
        if (isSequential(pin, ascending = true)) return true
        if (isSequential(pin, ascending = false)) return true
        if (isAlternating(pin)) return true
        return false
    }

    /** A pattern is weak if it is a straight ascending/descending row scan of the grid. */
    fun isWeakPattern(nodes: List<Int>): Boolean {
        if (nodes.size < 4) return true
        if (nodes == (0..8).toList()) return true
        if (nodes == (0..8).toList().reversed()) return true
        return false
    }

    private fun allSameDigit(pin: String): Boolean = pin.all { it == pin[0] }

    private fun isSequential(pin: String, ascending: Boolean): Boolean {
        val step = if (ascending) 1 else -1
        for (i in 1 until pin.length) {
            if (pin[i] - pin[i - 1] != step) return false
        }
        return true
    }

    private fun isAlternating(pin: String): Boolean {
        if (pin.length < 4) return false
        if (pin[0] == pin[1]) return false
        for (i in pin.indices) {
            val expected = if (i % 2 == 0) pin[0] else pin[1]
            if (pin[i] != expected) return false
        }
        return true
    }
}
