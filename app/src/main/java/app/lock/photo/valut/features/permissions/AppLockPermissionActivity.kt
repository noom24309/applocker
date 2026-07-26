package app.lock.photo.valut.features.permissions

import app.lock.photo.valut.core.ui.BaseActivity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppLockReliabilityHelper
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityAppLockPermissionBinding
import app.lock.photo.valut.features.home.MainActivity
import com.nextgen.ads.nativead.NextGenNativeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppLockPermissionActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityAppLockPermissionBinding
    private val viewModel: AppLockPermissionViewModel by viewModels()

    /** When true this is the pre-home gate; otherwise a sub-screen that returns RESULT_OK. */
    private var gateMode = false

    // Usage-access and overlay grants happen in system Settings with no callback. While the user
    // is over there we poll for the grant and pull our task back to the front the moment it flips.
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    // The battery-exemption dialog returns here; continue the reliability flow either way
    // (grant or deny) so the user is never stuck on this screen.
    private val batteryResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { maybeShowAutoStartThenHome() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)

        if (gateMode) {
            // Reaching the gate means onboarding is finished — persist it so relaunches route
            // straight back here instead of repeating onboarding.
            lifecycleScope.launch {
                viewModel.markOnboardingComplete()
                // Decide before drawing: if protection is already set up, skip straight to home.
                if (viewModel.isProtectionActive()) {
                    goToHome()
                } else {
                    showSetupUi()
                }
            }
        } else {
            showSetupUi()
        }
    }

    private fun showSetupUi() {
        binding = ActivityAppLockPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Drawn on the splash background, so the system bars need light icons.
        useLightSystemBarIcons()

        binding.ivBack.isVisible = !gateMode
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.rowUsage.setOnClickListener { requestUsageAccess() }
        binding.rowOverlay.setOnClickListener { requestOverlay() }
        binding.rowNotification.setOnClickListener { requestNotifications() }
        binding.btnCheckAgain.setOnClickListener { viewModel.refresh() }

        if (gateMode) {
            binding.btnSkip.isVisible = true
            binding.btnSkip.setOnClickListener { goToHome() }
        }
        binding.btnContinue.setOnClickListener { onPrimaryAction() }

        loadNativeAd()
        observeState()
        viewModel.refresh()
    }

    private fun loadNativeAd() {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.NativePermissions),
            // Same native layout the splash uses.
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeHome && RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativePermissions),
            canReloadAds = RemoteConfig.nativeHome && RemoteConfig.enableAllAds,
            logTag = "AppLockPermission"
        )
    }

    /**
     * The bottom action doubles as the setup driver: while permissions are missing it opens
     * the next one, and only once they are all granted does it activate protection / return.
     */
    private fun onPrimaryAction() {
        val state = viewModel.state.value
        when {
            !state.hasUsageAccess -> requestUsageAccess()
            !state.hasOverlayPermission -> requestOverlay()
            !state.hasNotificationPermission -> requestNotifications()
            gateMode -> activateAndFinishGate()
            else -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun requestUsageAccess() {
        showPermissionDisclosure(
            R.string.applock_disclosure_usage_title,
            getString(R.string.applock_disclosure_usage_message, getString(R.string.app_name))
        ) { openUsageAccessSettings() }
    }

    private fun requestOverlay() {
        showPermissionDisclosure(
            R.string.applock_disclosure_overlay_title,
            getString(R.string.applock_disclosure_overlay_message)
        ) { openOverlaySettings() }
    }

    private fun requestNotifications() {
        showPermissionDisclosure(
            R.string.applock_disclosure_notification_title,
            getString(R.string.applock_disclosure_notification_message)
        ) { requestNotificationPermission() }
    }

    private fun goToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    /**
     * Gate "Activate protection": turn App Lock on, then — before landing on home — walk the
     * user through the OEM survival settings that keep the monitor service alive (battery
     * exemption, then auto-start on manufacturers that need it). Each prompt is shown at most
     * once; if none are needed we go straight home.
     */
    private fun activateAndFinishGate() {
        lifecycleScope.launch {
            viewModel.activateProtection()
            if (viewModel.shouldPromptBatteryExemption()) {
                viewModel.markBatteryHelpShown()
                showBatteryDialog()
            } else {
                maybeShowAutoStartThenHome()
            }
        }
    }

    /**
     * Prominent, in-context disclosure required by Google Play before requesting the
     * sensitive Usage Access / overlay (and notification) permissions. Explains why the
     * permission is needed and how the data is used, entirely on-device. Only when the user
     * taps "Continue" does [onProceed] start the actual system permission flow.
     */
    private fun showPermissionDisclosure(
        titleRes: Int,
        message: String,
        onProceed: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(R.string.applock_disclosure_continue) { _, _ -> onProceed() }
            .setNegativeButton(R.string.applock_disclosure_cancel, null)
            .show()
    }

    private fun showBatteryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.applock_battery_prompt_title)
            .setMessage(R.string.applock_battery_prompt_message)
            .setCancelable(false)
            .setPositiveButton(R.string.applock_battery_prompt_allow) { _, _ ->
                runCatching {
                    batteryResultLauncher.launch(
                        AppLockReliabilityHelper.batteryExemptionIntent(this)
                    )
                }.onFailure {
                    runCatching { startActivity(AppLockReliabilityHelper.batterySettingsIntent()) }
                    maybeShowAutoStartThenHome()
                }
            }
            .setNegativeButton(R.string.applock_reliability_later) { _, _ ->
                maybeShowAutoStartThenHome()
            }
            .show()
    }

    private fun maybeShowAutoStartThenHome() {
        lifecycleScope.launch {
            if (viewModel.shouldPromptAutoStart()) {
                viewModel.markAutostartHelpShown()
                showAutoStartDialog()
            } else {
                goToHome()
            }
        }
    }

    private fun showAutoStartDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.applock_autostart_prompt_title)
            .setMessage(R.string.applock_autostart_prompt_message)
            .setCancelable(false)
            .setPositiveButton(R.string.applock_autostart_prompt_open) { _, _ ->
                AppLockReliabilityHelper.openAutoStartSettings(this)
                goToHome()
            }
            .setNegativeButton(R.string.applock_reliability_later) { _, _ -> goToHome() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Back in the foreground (auto-returned or the user came back) — stop polling and re-read.
        stopWatchingForGrant()
        // No binding yet while the gate is still deciding whether to pass through.
        if (::binding.isInitialized) viewModel.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchingForGrant()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: AppLockPermissionUiState) {
        bindRow(state.hasUsageAccess, binding.checkUsage, binding.badgeUsage)
        bindRow(state.hasOverlayPermission, binding.checkOverlay, binding.badgeOverlay)
        bindRow(state.hasNotificationPermission, binding.checkNotification, binding.badgeNotification)

        val granted = listOf(
            state.hasUsageAccess,
            state.hasOverlayPermission,
            state.hasNotificationPermission
        ).count { it }
        binding.tvProgress.text = getString(R.string.applock_perm_progress, granted, PERMISSION_COUNT)

        binding.btnContinue.setText(
            when {
                !state.canContinue -> R.string.applock_perm_setup_cta
                gateMode -> R.string.applock_activate_protection
                else -> R.string.continue_label
            }
        )
    }

    /** Granted rows swap their step number for a green check. */
    private fun bindRow(granted: Boolean, check: android.view.View, badge: android.view.View) {
        check.isVisible = granted
        badge.isVisible = !granted
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            watchForGrant { viewModel.hasUsageAccess() }
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) } }
            .onSuccess { watchForGrant { viewModel.hasOverlayPermission() } }
    }

    /**
     * Polls [granted] while the user is in system Settings; once it returns true, pulls this
     * activity's task back to the front so the user lands straight back in the app. The poll keeps
     * running while the activity is in the background (it stops in [onResume]/[onDestroy]).
     */
    private fun watchForGrant(granted: () -> Boolean) {
        stopWatchingForGrant()
        pollRunnable = object : Runnable {
            override fun run() {
                if (granted()) {
                    bringTaskToFront()
                } else {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }.also { pollHandler.postDelayed(it, POLL_INTERVAL_MS) }
    }

    private fun stopWatchingForGrant() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun bringTaskToFront() {
        stopWatchingForGrant()
        // The app is in the background (user is in system Settings). Use BOTH mechanisms because
        // each covers a different permission:
        //  - moveToFront(): pulls our own task back for the usage-access grant, where the app has no
        //    background-activity-launch exemption (plain startActivity is blocked on Android 10+).
        //  - startActivity(REORDER_TO_FRONT): works once the overlay (SYSTEM_ALERT_WINDOW) permission
        //    is granted, which exempts us from the background-launch restriction.
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        runCatching { activityManager?.appTasks?.firstOrNull()?.moveToFront() }
        runCatching {
            startActivity(
                Intent(this, AppLockPermissionActivity::class.java)
                    .putExtra(EXTRA_GATE_MODE, gateMode)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-13: notifications are controlled in system settings for the app.
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
    }

    companion object {
        private const val EXTRA_GATE_MODE = "extra_gate_mode"
        private const val POLL_INTERVAL_MS = 500L
        private const val PERMISSION_COUNT = 3

        /**
         * Intent for the pre-home gate shown after unlock/first-run. The gate passes
         * straight through to home once App Lock protection is active, so callers can
         * route here unconditionally instead of starting [MainActivity] directly.
         */
        fun gateIntent(context: Context): Intent =
            Intent(context, AppLockPermissionActivity::class.java)
                .putExtra(EXTRA_GATE_MODE, true)
    }
}
