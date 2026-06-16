package app.lock.photo.valut.features.vault

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityVideoPlayerBinding
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class VideoPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private val viewModel: VideoPlayerViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var preparedPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnExport.setOnClickListener { showExportDialog() }
        binding.btnDetails.setOnClickListener {
            MediaDetailsBottomSheet.newInstance(viewModel.mediaId).show(supportFragmentManager, "details")
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.media.collect(::render) }
                launch { viewModel.playback.collect(::onPlayback) }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            VideoPlayerViewModel.Event.Deleted -> finish()
                            is VideoPlayerViewModel.Event.ExportFinished -> toastExport(event.result)
                        }
                    }
                }
            }
        }
    }

    private fun render(item: VaultMediaUiModel?) {
        if (item == null) return
        binding.tvTitle.text = item.displayName
        binding.btnFavorite.setImageResource(
            if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    private fun onPlayback(state: VideoPlayerViewModel.Playback) {
        when (state) {
            VideoPlayerViewModel.Playback.Loading -> Unit
            VideoPlayerViewModel.Playback.Error -> showError()
            is VideoPlayerViewModel.Playback.Ready -> prepare(state.file)
        }
    }

    private fun prepare(file: File) {
        if (preparedPath == file.absolutePath) return
        if (!file.exists()) {
            showError()
            return
        }
        binding.tvError.isVisible = false
        preparedPath = file.absolutePath
        player?.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player?.prepare()
    }

    private fun initPlayer() {
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = showError()
        })
        binding.playerView.player = exo
        exo.playWhenReady = true
        player = exo
        preparedPath = null
        // Decrypt-to-temp then play; re-prepare if the temp file is already available.
        when (val state = viewModel.playback.value) {
            is VideoPlayerViewModel.Playback.Ready -> prepare(state.file)
            else -> viewModel.preparePlayback()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        binding.playerView.player = null
        preparedPath = null
        // Drop the decrypted temp file as soon as playback stops.
        viewModel.clearPlayback()
    }

    private fun showError() {
        binding.tvError.isVisible = true
        Toast.makeText(this, R.string.error_video_playback, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportDialog() {
        val labels = arrayOf(getString(R.string.export_copy_only), getString(R.string.export_and_remove))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setItems(labels) { _, which -> viewModel.export(removeFromVault = which == 1) }
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

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    companion object {
        private const val EXTRA_MEDIA_ID = VideoPlayerViewModel.ARG_MEDIA_ID

        fun intent(context: Context, mediaId: Long): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_MEDIA_ID, mediaId)
    }
}
