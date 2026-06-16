package app.lock.photo.valut.features.intruder

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.VerifySessionManager
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.databinding.ActivityIntruderDetailBinding
import app.lock.photo.valut.features.auth.VerifyMasterActivity
import app.lock.photo.valut.features.intruder.model.IntruderDetailUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Full-screen intruder photo + details. FLAG_SECURE; decrypted photo cleared on close. */
@AndroidEntryPoint
class IntruderDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityIntruderDetailBinding
    private val viewModel: IntruderDetailViewModel by viewModels()

    @Inject lateinit var secureCacheManager: SecureCacheManager
    @Inject lateinit var verifySessionManager: VerifySessionManager

    private var pendingAction: (() -> Unit)? = null
    private val verifyLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch { verifySessionManager.markVerified() }
            action?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIntruderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnExport.setOnClickListener { confirmExport() }

        observe()
        loadPhoto()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { if (it.loaded) render(it) } }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is IntruderDetailViewModel.Event.Exported -> {
                                val msg = if (event.success) R.string.intruder_export_done else R.string.intruder_export_failed
                                Toast.makeText(this@IntruderDetailActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                            IntruderDetailViewModel.Event.Deleted -> finish()
                            IntruderDetailViewModel.Event.Missing -> {
                                Toast.makeText(this@IntruderDetailActivity, R.string.intruder_record_missing, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(state: IntruderDetailUiState) {
        val none = getString(R.string.details_none)
        val source = getString(IntruderLabels.source(state.triggerSource))
        val time = DateUtils.getRelativeDateTimeString(
            this, state.timestamp, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
        )
        val lines = buildList {
            add("${getString(R.string.intruder_detail_source)}: $source")
            add("${getString(R.string.intruder_detail_app)}: ${state.appName ?: none}")
            add("${getString(R.string.intruder_detail_method)}: ${state.unlockMethod}")
            add("${getString(R.string.intruder_detail_attempts)}: ${state.wrongAttemptCount}")
            add("${getString(R.string.intruder_detail_time)}: $time")
            add("${getString(R.string.intruder_detail_status)}: " + getString(
                if (state.captureSuccess) R.string.intruder_status_captured else R.string.intruder_capture_failed
            ))
            if (!state.captureSuccess && state.failureReason != null) {
                add("${getString(R.string.intruder_detail_reason)}: ${state.failureReason}")
            }
        }
        binding.tvDetails.text = lines.joinToString("\n")
        binding.btnExport.isEnabled = state.captureSuccess
    }

    private fun loadPhoto() {
        lifecycleScope.launch {
            val bytes = viewModel.loadPhoto()
            if (bytes == null) {
                binding.tvNoPhoto.isVisible = true
                return@launch
            }
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
            if (bitmap != null) binding.photo.setImageBitmap(bitmap) else binding.tvNoPhoto.isVisible = true
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.intruder_delete_title)
            .setMessage(R.string.intruder_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmExport() = runVerified {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.intruder_export_title)
            .setMessage(R.string.intruder_export_message)
            .setPositiveButton(R.string.intruder_export) { _, _ -> viewModel.export() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runVerified(action: () -> Unit) {
        lifecycleScope.launch {
            if (!verifySessionManager.isVerificationStillValid()) {
                pendingAction = action
                verifyLauncher.launch(Intent(this@IntruderDetailActivity, VerifyMasterActivity::class.java))
            } else {
                action()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear any decrypted intruder temp file created for viewing.
        runCatching { secureCacheManager.clearIntruderTempFiles() }
    }

    companion object {
        fun intent(context: Context, id: Long): Intent =
            Intent(context, IntruderDetailActivity::class.java)
                .putExtra(IntruderDetailViewModel.ARG_ID, id)
    }
}
