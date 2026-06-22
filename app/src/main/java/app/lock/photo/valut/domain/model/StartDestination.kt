package app.lock.photo.valut.domain.model

/**
 * Where the splash screen should send the user, based on persisted state.
 */
enum class StartDestination {
    /** First launch — show onboarding. */
    ONBOARDING,

    /**
     * Onboarding finished — send the user to the App Lock permission gate. The gate passes
     * straight through to home once protection is active, otherwise it shows the permission
     * setup (with a Skip-to-home option). This is the post-onboarding entry on every relaunch.
     */
    PERMISSION_GATE,

    /** Onboarding done but no master credential yet — let the user choose PIN or pattern. */
    SETUP_CREDENTIAL,

    /** A credential exists — require unlocking (PIN or pattern, per [UnlockMethod]). */
    LOCKED
}
