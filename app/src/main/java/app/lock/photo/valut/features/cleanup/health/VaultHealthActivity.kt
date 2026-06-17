package app.lock.photo.valut.features.cleanup.health

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.databinding.ActivityVaultHealthBinding
import app.lock.photo.valut.databinding.ViewHealthRowBinding
import app.lock.photo.valut.domain.model.VaultHealth
import app.lock.photo.valut.features.applock.AppLockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VaultHealthActivity : BaseActivity() {

    private lateinit var binding: ActivityVaultHealthBinding
    private val viewModel: VaultHealthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultHealthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnScan.setOnClickListener { viewModel.scan() }
        binding.btnClearTemp.setOnClickListener { viewModel.clearTempCache() }
        binding.btnAppLock.setOnClickListener { startActivity(AppLockActivity.intent(this)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.health.collect { it?.let(::render) } }
                launch { viewModel.messages.collect { Toast.makeText(this@VaultHealthActivity, it, Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    private fun render(h: VaultHealth) {
        binding.scoreText.text = h.score.toString()
        binding.lastScanText.text = getString(
            R.string.health_last_scan,
            if (h.lastScanAt > 0) Formatters.formatDate(h.lastScanAt) else getString(R.string.health_never)
        )
        binding.healthContainer.removeAllViews()
        addRow(getString(R.string.health_encryption), yesNo(h.encryptionActive, R.string.health_active, R.string.health_inactive))
        addRow(getString(R.string.health_app_lock), yesNo(h.appLockReady, R.string.health_ready, R.string.health_needs_setup))
        addRow(getString(R.string.health_unencrypted), h.unencryptedCount.toString())
        addRow(getString(R.string.health_failed_repairs), h.failedRepairCount.toString())
        addRow(getString(R.string.storage_recycle_bin), h.recycleBinCount.toString())
        addRow(getString(R.string.storage_temp_cache), Formatters.formatSize(h.tempCacheBytes))
    }

    private fun yesNo(value: Boolean, yes: Int, no: Int): String = getString(if (value) yes else no)

    private fun addRow(label: String, value: String) {
        val row = ViewHealthRowBinding.inflate(layoutInflater, binding.healthContainer, false)
        row.healthLabel.text = label
        row.healthValue.text = value
        binding.healthContainer.addView(row.root)
    }

    companion object {
        fun intent(context: Context) = Intent(context, VaultHealthActivity::class.java)
    }
}
