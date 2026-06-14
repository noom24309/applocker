package app.lock.photo.valut.features.applock.model

import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.domain.model.UnlockMethod

/** Render state for the lock overlay shown over a protected app. */
data class AppLockOverlayUiState(
    val lockedPackageName: String = "",
    val lockedAppName: String = "",
    val unlockMethod: UnlockMethod = UnlockMethod.PIN,
    val expectedPinLength: Int = 4,
    val biometricEnabled: Boolean = false,
    val fakeMode: FakeMode = FakeMode.NONE,
    val theme: LockTheme = LockTheme.DEFAULT,
    val hideAppName: Boolean = false,
    val requireBiometricOnly: Boolean = false,
    val isLockedOut: Boolean = false,
    val remainingMillis: Long = 0L,
    val attemptCount: Int = 0,
    /** Set once the effective settings have been resolved (drives first render). */
    val resolved: Boolean = false
)
