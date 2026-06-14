package app.lock.photo.valut.domain.model

/**
 * Where the splash screen should send the user, based on persisted state.
 */
enum class StartDestination {
    /** First launch — show onboarding. */
    ONBOARDING,

    /** Onboarding done but no master credential yet. */
    CREATE_PIN,

    /** A credential exists — require unlocking (PIN or pattern, per [UnlockMethod]). */
    LOCKED
}
