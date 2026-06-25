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
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityAppLockPermissionBinding
import app.lock.photo.valut.features.home.MainActivity
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

        binding.ivBack.isVisible = !gateMode
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnUsage.setOnClickListener { openUsageAccessSettings() }
        binding.btnOverlay.setOnClickListener { openOverlaySettings() }
        binding.btnNotification.setOnClickListener { requestNotificationPermission() }
        binding.btnCheckAgain.setOnClickListener { viewModel.refresh() }

        if (gateMode) {
            binding.btnContinue.setText(R.string.applock_activate_protection)
            binding.btnSkip.isVisible = true
            binding.btnSkip.setOnClickListener { goToHome() }
            binding.btnContinue.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.activateProtection()
                    goToHome()
                }
            }
        } else {
            binding.btnContinue.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        }

        observeState()
        viewModel.refresh()
    }

    private fun goToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
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
        bindCard(state.hasUsageAccess, binding.checkUsage, binding.btnUsage)
        bindCard(state.hasOverlayPermission, binding.checkOverlay, binding.btnOverlay)
        bindCard(state.hasNotificationPermission, binding.checkNotification, binding.btnNotification)
        binding.btnContinue.isEnabled = state.canContinue
    }

    private fun bindCard(
        granted: Boolean,
        check: android.view.View,
        button: com.google.android.material.button.MaterialButton
    ) {
        check.isVisible = granted
        button.isVisible = !granted
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
