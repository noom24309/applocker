package app.lock.photo.valut.features.applock.troubleshooting

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentAppLockTroubleshootingBinding
import app.lock.photo.valut.domain.usecase.AppLockHealth
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppLockTroubleshootingActivity : BaseActivity() {

    private lateinit var binding: FragmentAppLockTroubleshootingBinding
    private val viewModel: AppLockTroubleshootingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAppLockTroubleshootingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnCheckAgain.setOnClickListener { viewModel.checkAgain() }
        binding.btnRestart.setOnClickListener { viewModel.restartProtection() }
        binding.btnPermissions.setOnClickListener {
            startActivity(Intent(this, AppLockPermissionActivity::class.java))
        }
        binding.btnBattery.setOnClickListener { openBattery() }
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAgain()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.health.collect { it?.let(::render) }
            }
        }
    }

    private fun render(health: AppLockHealth) {
        mark(binding.checkUsage, health.usageAccess)
        mark(binding.checkOverlay, health.overlay)
        mark(binding.checkNotification, health.notification)
        mark(binding.checkService, health.serviceRunning)
        mark(binding.checkApps, health.hasLockedApps)
        binding.tvLastCheck.text = getString(R.string.applock_troubleshoot_last_check)
    }

    private fun mark(view: TextView, ok: Boolean) {
        val icon = if (ok) R.drawable.ic_check_circle else R.drawable.ic_close
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        view.compoundDrawableTintList = ContextCompat.getColorStateList(
            this, if (ok) R.color.accent_green else R.color.accent_red
        )
    }

    private fun openBattery() {
        runCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, AppLockTroubleshootingActivity::class.java)
    }
}
