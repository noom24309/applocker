package app.lock.photo.valut.features.premium.cleanup.storage

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
import app.lock.photo.valut.databinding.ActivityStorageAnalyzerBinding
import app.lock.photo.valut.databinding.ViewStorageRowBinding
import app.lock.photo.valut.domain.model.StorageBreakdown
import app.lock.photo.valut.features.premium.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.premium.cleanup.largefiles.LargeFilesActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StorageAnalyzerActivity : BaseActivity() {

    private lateinit var binding: ActivityStorageAnalyzerBinding
    private val viewModel: StorageAnalyzerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnClearTemp.setOnClickListener { viewModel.clearTempCache() }
        binding.btnDuplicates.setOnClickListener { startActivity(DuplicateFinderActivity.intent(this)) }
        binding.btnLargeFiles.setOnClickListener { startActivity(LargeFilesActivity.intent(this)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.breakdown.collect { it?.let(::render) } }
                launch { viewModel.messages.collect { Toast.makeText(this@StorageAnalyzerActivity, it, Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun render(b: StorageBreakdown) {
        binding.totalText.text = Formatters.formatSize(b.totalBytes)
        binding.rowsContainer.removeAllViews()
        val total = b.totalBytes.coerceAtLeast(1)
        addRow(getString(R.string.home_photos), b.photosBytes, total)
        addRow(getString(R.string.home_videos), b.videosBytes, total)
        addRow(getString(R.string.documents_title), b.documentsBytes, total)
        addRow(getString(R.string.storage_private_camera), b.privateCameraBytes, total)
        addRow(getString(R.string.storage_thumbnails), b.thumbnailsBytes, total)
        addRow(getString(R.string.storage_intruder), b.intruderBytes, total)
        addRow(getString(R.string.storage_recycle_bin), b.recycleBinBytes, total)
        addRow(getString(R.string.storage_temp_cache), b.tempCacheBytes, total)
    }

    private fun addRow(label: String, bytes: Long, total: Long) {
        val row = ViewStorageRowBinding.inflate(layoutInflater, binding.rowsContainer, false)
        row.rowLabel.text = label
        row.rowSize.text = Formatters.formatSize(bytes)
        row.rowBar.progress = ((bytes * 100) / total).toInt()
        binding.rowsContainer.addView(row.root)
    }

    companion object {
        fun intent(context: Context) = Intent(context, StorageAnalyzerActivity::class.java)
    }
}
