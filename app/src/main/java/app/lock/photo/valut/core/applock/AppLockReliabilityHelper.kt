package app.lock.photo.valut.core.applock

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Best-effort helpers that keep the App Lock monitor service alive on aggressive OEMs.
 *
 * Two levers the OS gives an app to survive background kills:
 *  1. **Battery-optimization exemption** — a standard one-tap "Allow" dialog on every device.
 *     Without it, Doze and OEM battery managers kill the foreground service and can also
 *     block the watchdog alarm from restarting it.
 *  2. **OEM auto-start / background-start whitelist** — a manufacturer-specific screen
 *     (MIUI "Autostart", ColorOS/FuntouchOS "Startup manager", etc.). Not exposed through
 *     any public API, so we deep-link into the known Settings components and fall back to
 *     the app's system App-Info page when the exact screen can't be resolved.
 *
 * None of this is required to *start* protection; it's what makes it *stay* running.
 */
object AppLockReliabilityHelper {

    /** One-tap system dialog that asks the user to exempt us from battery optimization. */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /** Fallback: the global battery-optimization list, if the direct dialog is unavailable. */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * True on manufacturers known to ship an auto-start / background-start manager that
     * kills apps by default. Used to decide whether to surface the auto-start prompt at all.
     */
    fun hasKnownAutoStartManager(): Boolean =
        manufacturer() in AUTO_START_MANUFACTURERS

    /**
     * Opens the OEM auto-start / startup-manager screen. Tries each known component for the
     * current manufacturer in turn; if none resolve (OEM renamed it, or on a manufacturer
     * without one), falls back to this app's system App-Info page so the user can still reach
     * the relevant toggles. Never throws — returns false only if even App-Info can't open.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        for (component in autoStartComponents()) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (canResolve(context, intent) && runCatching { context.startActivity(intent) }.isSuccess) {
                return true
            }
        }
        return openAppInfo(context)
    }

    /** The app's own system settings page — a universal fallback that always exists. */
    fun openAppInfo(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun canResolve(context: Context, intent: Intent): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, 0)
            } != null
        }.getOrDefault(false)

    private fun manufacturer(): String = Build.MANUFACTURER?.lowercase(Locale.ROOT).orEmpty()

    /** Candidate auto-start Settings components for the current manufacturer. */
    private fun autoStartComponents(): List<ComponentName> = when (manufacturer()) {
        "xiaomi", "redmi", "poco" -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        )
        "oppo", "realme" -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
        )
        "vivo", "iqoo" -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        )
        "huawei", "honor" -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
        )
        "samsung" -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        "letv" -> listOf(
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        )
        "asus" -> listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")
        )
        "oneplus" -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )
        else -> emptyList()
    }

    private val AUTO_START_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
        "huawei", "honor", "samsung", "letv", "asus", "oneplus"
    )
}
