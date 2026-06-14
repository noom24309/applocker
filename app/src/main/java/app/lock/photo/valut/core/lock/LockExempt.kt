package app.lock.photo.valut.core.lock

/**
 * Marker for activities that must never be covered by the auto-lock screen:
 * splash, onboarding, all PIN/pattern setup and unlock screens, recovery, etc.
 * The lifecycle observer skips locking while one of these is in the foreground.
 */
interface LockExempt
