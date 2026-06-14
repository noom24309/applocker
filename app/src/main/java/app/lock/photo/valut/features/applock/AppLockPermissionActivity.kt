package app.lock.photo.valut.features.applock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.databinding.ActivityAppLockPermissionBinding
import app.lock.photo.valut.features.applock.model.AppLockPermissionUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Explains and routes the user to grant the three App Lock permissions. Nothing is
 * forced — each card shows status and a single action; Continue unlocks once granted.
 */
@AndroidEntryPoint
class AppLockPermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppLockPermissionBinding
    private val viewModel: AppLockPermissionViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLockPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnUsage.setOnClickListener { openUsageAccessSettings() }
        binding.btnOverlay.setOnClickListener { openOverlaySettings() }
        binding.btnNotification.setOnClickListener { requestNotificationPermission() }
        binding.btnCheckAgain.setOnClickListener { viewModel.refresh() }
        binding.btnContinue.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
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
}
