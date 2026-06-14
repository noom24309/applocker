package app.lock.photo.valut.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds an in-progress PIN in memory while the user moves from the create screen
 * to the confirm screen. This avoids passing the raw PIN through Intent extras.
 * Always [clear] once setup completes or is abandoned.
 */
@Singleton
class PinSetupSession @Inject constructor() {

    private var pin: String? = null
    var length: Int = 0
        private set

    fun store(pin: String, length: Int) {
        this.pin = pin
        this.length = length
    }

    fun matches(candidate: String): Boolean = pin != null && pin == candidate

    fun current(): String? = pin

    fun clear() {
        pin = null
        length = 0
    }
}
