package app.lock.photo.valut.core.lock

/**
 * Marker for the actual unlock screens (PIN / pattern). They are [LockExempt] and
 * additionally signal to the lifecycle observer that a lock screen is on top, so
 * no duplicate unlock screen is launched.
 */
interface LockScreen : LockExempt
