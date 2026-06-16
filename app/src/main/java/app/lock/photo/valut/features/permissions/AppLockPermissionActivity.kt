package app.lock.photo.valut.features.permissions

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

/**
 * Explains and routes the user to grant the three App Lock permissions. Nothing is
 * forced — each card shows status and a single action; Continue unlocks once granted.
 *
 * In **gate mode** ([EXTRA_GATE_MODE], used as the pre-home step after unlock/first-run)
 * the screen shows before the home dashboard until App Lock protection is active: the
 * user can either activate protection (Continue, once permissions are granted) or skip
 * for now. Once protection is active the gate passes straight through to home.
 *
 * It is [LockExempt] because it is part of the unlock/setup flow — granting the usage
 * and overlay permissions sends the user out to system Settings, and returning must not
 * trigger the app's own auto-lock over this screen.
 */
@AndroidEntryPoint
class AppLockPermissionActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityAppLockPermissionBinding
    private val viewModel: AppLockPermissionViewModel by viewModels()

    /** When true this is the pre-home gate; otherwise a sub-screen that returns RESULT_OK. */
    private var gateMode = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)

        if (gateMode) {
            // Decide before drawing: if protection is already set up, skip straight to home.
            lifecycleScope.launch {
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
        // No binding yet while the gate is still deciding whether to pass through.
        if (::binding.isInitialized) viewModel.refresh()
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
        check: android.widget.ImageView,
        button: com.google.android.material.button.MaterialButton
    ) {
        check.isVisible = granted
        button.isVisible = !granted
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) } }
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
