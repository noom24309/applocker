package app.lock.photo.valut.features.applock

import androidx.annotation.StringRes
import app.lock.photo.valut.R
import app.lock.photo.valut.domain.model.AppLockDelayMode
import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.LockTheme

/** Central string-resource mapping for App Lock enums (used across settings + pickers). */
object AppLockLabels {

    @StringRes
    fun delay(mode: AppLockDelayMode): Int = when (mode) {
        AppLockDelayMode.IMMEDIATE -> R.string.auto_lock_immediate
        AppLockDelayMode.SECONDS_5 -> R.string.delay_5s
        AppLockDelayMode.SECONDS_15 -> R.string.auto_lock_15s
        AppLockDelayMode.SECONDS_30 -> R.string.auto_lock_30s
        AppLockDelayMode.MINUTE_1 -> R.string.auto_lock_1m
        AppLockDelayMode.MINUTES_5 -> R.string.auto_lock_5m
        AppLockDelayMode.UNTIL_SCREEN_OFF -> R.string.delay_until_screen_off
        AppLockDelayMode.UNTIL_APP_SWITCH -> R.string.delay_until_app_switch
        AppLockDelayMode.UNTIL_DEVICE_LOCKED -> R.string.delay_until_device_locked
    }

    @StringRes
    fun theme(theme: LockTheme): Int = when (theme) {
        LockTheme.DEFAULT -> R.string.theme_default
        LockTheme.DARK -> R.string.theme_dark
        LockTheme.GLASS -> R.string.theme_glass
        LockTheme.MINIMAL -> R.string.theme_minimal
        LockTheme.CALCULATOR -> R.string.theme_calculator
        LockTheme.FAKE_CRASH -> R.string.theme_fake_crash
        LockTheme.FAKE_LOADING -> R.string.theme_fake_loading
        LockTheme.USE_GLOBAL -> R.string.applock_use_global
    }

    @StringRes
    fun fakeMode(mode: FakeMode): Int = when (mode) {
        FakeMode.NONE -> R.string.fake_mode_none
        FakeMode.FAKE_CRASH -> R.string.fake_mode_crash
        FakeMode.FAKE_LOADING -> R.string.fake_mode_loading
        FakeMode.FAKE_CALCULATOR -> R.string.fake_mode_calculator
        FakeMode.USE_GLOBAL -> R.string.applock_use_global
    }

    /** Delay options shown in the global settings picker (excludes per-app-only entries). */
    val GLOBAL_DELAY_OPTIONS = listOf(
        AppLockDelayMode.IMMEDIATE,
        AppLockDelayMode.SECONDS_5,
        AppLockDelayMode.SECONDS_15,
        AppLockDelayMode.SECONDS_30,
        AppLockDelayMode.MINUTE_1,
        AppLockDelayMode.MINUTES_5,
        AppLockDelayMode.UNTIL_SCREEN_OFF,
        AppLockDelayMode.UNTIL_APP_SWITCH,
        AppLockDelayMode.UNTIL_DEVICE_LOCKED
    )

    /** Global theme options (excludes USE_GLOBAL, which is per-app only). */
    val GLOBAL_THEME_OPTIONS = listOf(
        LockTheme.DEFAULT,
        LockTheme.DARK,
        LockTheme.GLASS,
        LockTheme.MINIMAL,
        LockTheme.CALCULATOR,
        LockTheme.FAKE_CRASH,
        LockTheme.FAKE_LOADING
    )

    /** Global fake-mode options (excludes USE_GLOBAL, which is per-app only). */
    val GLOBAL_FAKE_OPTIONS = listOf(
        FakeMode.NONE,
        FakeMode.FAKE_CRASH,
        FakeMode.FAKE_LOADING,
        FakeMode.FAKE_CALCULATOR
    )
}
