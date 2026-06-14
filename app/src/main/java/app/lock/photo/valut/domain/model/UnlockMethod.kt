package app.lock.photo.valut.domain.model

/**
 * How the user unlocks the app.
 */
enum class UnlockMethod {
    PIN,
    PATTERN,
    PIN_BIOMETRIC,
    PATTERN_BIOMETRIC;

    val usesPattern: Boolean
        get() = this == PATTERN || this == PATTERN_BIOMETRIC

    val usesPin: Boolean
        get() = this == PIN || this == PIN_BIOMETRIC

    val usesBiometric: Boolean
        get() = this == PIN_BIOMETRIC || this == PATTERN_BIOMETRIC

    companion object {
        val DEFAULT = PIN

        fun fromStorage(value: String?): UnlockMethod =
            entries.firstOrNull { it.name == value } ?: DEFAULT

        /** Pairs the primary method [base] with a biometric toggle. */
        fun combine(base: UnlockMethod, biometric: Boolean): UnlockMethod = when {
            base.usesPattern && biometric -> PATTERN_BIOMETRIC
            base.usesPattern -> PATTERN
            biometric -> PIN_BIOMETRIC
            else -> PIN
        }
    }
}
