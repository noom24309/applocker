package app.lock.photo.valut.domain.model

/**
 * Fully-resolved lock behaviour for one package: per-app overrides applied on top of the
 * global App Lock settings. [fakeMode] and [theme] are concrete (never USE_GLOBAL).
 */
data class EffectiveLockSettings(
    val packageName: String,
    val appName: String,
    val unlockMethod: UnlockMethod,
    val delayMode: AppLockDelayMode,
    val graceMillis: Long,
    val fakeMode: FakeMode,
    val theme: LockTheme,
    val hideAppName: Boolean,
    val requireBiometricOnly: Boolean
)
