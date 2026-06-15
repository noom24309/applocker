package app.lock.photo.valut.features.vault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ActivityPhotoViewerBinding
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.features.vault.adapter.PhotoPagerAdapter
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.widget.Toast

@AndroidEntryPoint
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val viewModel: PhotoViewerViewModel by viewModels()
    private lateinit var pagerAdapter: PhotoPagerAdapter
    private var initialPositionApplied = false

    @javax.inject.Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        pagerAdapter = PhotoPagerAdapter(thumbnailLoader) { zoomed -> binding.viewPager.isUserInputEnabled = !zoomed }
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateBarsForCurrent()
        })

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFavorite.setOnClickListener { current()?.let { viewModel.toggleFavorite(it.id) } }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnExport.setOnClickListener { showExportDialog() }
        binding.btnMove.setOnClickListener { showMoveDialog() }
        binding.btnDetails.setOnClickListener {
            current()?.let { MediaDetailsBottomSheet.newInstance(it.id).show(supportFragmentManager, "details") }
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        pagerAdapter.submitList(items) {
                            if (!initialPositionApplied && items.isNotEmpty()) {
                                initialPositionApplied = true
                                binding.viewPager.setCurrentItem(viewModel.startIndex.coerceIn(0, items.lastIndex), false)
                            }
                            updateBarsForCurrent()
                        }
                    }
                }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            PhotoViewerViewModel.Event.Empty -> finish()
                            is PhotoViewerViewModel.Event.ExportFinished -> toastExport(event.result)
                            is PhotoViewerViewModel.Event.RestoredToGallery -> {
                                val msg = if (event.success) R.string.restore_done else R.string.restore_failed
                                Toast.makeText(this@PhotoViewerActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                            PhotoViewerViewModel.Event.ActionDone -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun current(): VaultMediaUiModel? = viewModel.items.value.getOrNull(binding.viewPager.currentItem)

    private fun updateBarsForCurrent() {
        val item = current() ?: return
        binding.tvTitle.text = item.displayName
        binding.btnFavorite.setImageResource(
            if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    private fun confirmDelete() {
        val item = current() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportDialog() {
        val item = current() ?: return
        val labels = arrayOf(
            getString(R.string.export_copy_only),
            getString(R.string.export_and_remove),
            getString(R.string.restore_to_gallery)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> viewModel.export(item.id, removeFromVault = false)
                    1 -> viewModel.export(item.id, removeFromVault = true)
                    2 -> viewModel.restoreToGallery(item.id)
                }
            }
            .show()
    }

    private fun showMoveDialog() {
        val item = current() ?: return
        val albums = viewModel.albums.value
        val labels = (listOf(getString(R.string.move_main_vault)) + albums.map { it.name }).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to_album_title)
            .setItems(labels) { _, which ->
                val albumId = if (which == 0) null else albums[which - 1].id
                viewModel.moveToAlbum(item.id, albumId)
            }
            .show()
    }

    private fun toastExport(result: ExportResult) {
        val msg = when {
            !result.supported -> getString(R.string.export_unsupported)
            result.exportedCount > 0 -> getString(R.string.export_done, result.exportedCount)
            else -> getString(R.string.export_failed)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val EXTRA_IDS = PhotoViewerViewModel.ARG_IDS
        private const val EXTRA_INDEX = PhotoViewerViewModel.ARG_INDEX

        fun intent(context: Context, ids: LongArray, index: Int): Intent =
            Intent(context, PhotoViewerActivity::class.java).apply {
                putExtra(EXTRA_IDS, ids)
                putExtra(EXTRA_INDEX, index)
            }
    }
}
