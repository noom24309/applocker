package app.lock.photo.valut.features.vault.video

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import app.lock.photo.valut.core.ui.showToast
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.lock.photo.valut.R
import app.lock.photo.valut.core.ads.AppAds
import app.lock.photo.valut.databinding.ActivityVideoPlayerBinding
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.features.vault.MediaDetailsBottomSheet
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private val viewModel: VideoPlayerViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var preparedUri: Uri? = null

    /** Survives the stop/start cycle so returning to the screen resumes where it left off. */
    private var resumePosition = 0L
    private var resumeWhenReady = true

    /** One retry budget for "the decrypted file vanished" errors. */
    private var retriedAfterError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        useLightSystemBarIcons()

        resumePosition = savedInstanceState?.getLong(STATE_POSITION) ?: 0L

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnExport.setOnClickListener { showExportDialog() }
        binding.btnDetails.setOnClickListener {
            MediaDetailsBottomSheet.newInstance(viewModel.mediaId).show(supportFragmentManager, "details")
        }

        binding.btnFullscreen.setOnClickListener { toggleOrientation() }
        // Landscape hides the header, so these are the way back out of fullscreen.
        binding.btnExitFullscreen.setOnClickListener { toggleOrientation() }
        binding.btnBackLandscape.setOnClickListener { finish() }
        keepLandscapeControlsWithController()

        observe()
        AppAds.loadBottomBanner(this, binding.frAdsBottom, R.string.BannerVideoPlayer, "VideoPlayer")
        applyOrientation(resources.configuration.orientation)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_POSITION, player?.currentPosition ?: resumePosition)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig.orientation)
    }

    private fun applyOrientation(orientation: Int) {
        val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.topBar.isVisible = !landscape
        binding.bottomBar.isVisible = !landscape
        binding.frAdsBottom.isVisible = !landscape
        // In fullscreen the only chrome left is this floating pair; start it visible so the way
        // back is obvious, then it follows the player controls.
        binding.landscapeControls.isVisible = landscape
        binding.btnFullscreen.setImageResource(
            if (landscape) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
        binding.btnFullscreen.contentDescription =
            getString(if (landscape) R.string.player_exit_fullscreen else R.string.player_fullscreen)
        setSystemBarsHidden(landscape)
    }

    /** Landscape plays edge to edge; the bars come back (swipe-revealable) in portrait. */
    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * The floating landscape controls appear and disappear with the player's own controller, so
     * tapping the video brings back "exit fullscreen" instead of leaving the user stuck.
     */
    @OptIn(UnstableApi::class)
    private fun keepLandscapeControlsWithController() {
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                val landscape =
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                binding.landscapeControls.isVisible = landscape && visibility == View.VISIBLE
            }
        )
    }

    /** Header button: flip orientation without needing the device rotation to be unlocked. */
    private fun toggleOrientation() {
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
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
        binding.tvSubtitle.text = listOfNotNull(item.durationText, item.sizeText).joinToString(" · ")
        binding.btnFavorite.setImageResource(
            if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    private fun onPlayback(state: VideoPlayerViewModel.Playback) {
        Log.d(TAG, "playback state = ${state::class.simpleName}, player=${player != null}")
        when (state) {
            VideoPlayerViewModel.Playback.Loading -> Unit
            VideoPlayerViewModel.Playback.Error -> showError()
            is VideoPlayerViewModel.Playback.Ready -> prepare(state.uri)
        }
    }

    /** Plays the decrypted file the view model prepared. */
    private fun prepare(uri: Uri) {
        if (preparedUri == uri) {
            Log.d(TAG, "already prepared for this uri")
            return
        }
        val exo = player
        if (exo == null) {
            // The file became ready before the player existed (or after it was released):
            // remember it so initPlayer() can pick it up instead of waiting forever.
            Log.d(TAG, "player not ready yet — will prepare on init")
            return
        }
        binding.tvError.isVisible = false
        binding.loading.isVisible = true
        preparedUri = uri
        // Declaring the MIME type lets the extractor skip container sniffing.
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply { viewModel.mimeType?.let { setMimeType(it) } }
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        if (resumePosition > 0L) exo.seekTo(resumePosition)
        exo.playWhenReady = resumeWhenReady
    }

    private fun initPlayer() {
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback error (${error.errorCodeName}): ${error.message}")
                // Most likely cause: the decrypted temp file was cleared by another vault
                // screen. Decrypt again once before giving up.
                if (!retriedAfterError) {
                    retriedAfterError = true
                    preparedUri = null
                    binding.loading.isVisible = true
                    viewModel.invalidateAndReprepare()
                } else {
                    showError()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                binding.loading.isVisible = playbackState == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Keep the screen awake while the video actually plays.
                binding.playerView.keepScreenOn = isPlaying
            }
        })
        binding.playerView.player = exo
        player = exo
        preparedUri = null
        binding.loading.isVisible = true
        // Always ask the view model: it reuses a still-valid decrypted file and re-decrypts when
        // the file is gone, so a stale "ready" state can never leave the screen stuck loading.
        viewModel.preparePlayback()
        (viewModel.playback.value as? VideoPlayerViewModel.Playback.Ready)?.let { prepare(it.uri) }
    }

    private fun releasePlayer() {
        player?.let {
            // Remember where we were so onStart resumes instead of restarting.
            resumePosition = it.currentPosition
            resumeWhenReady = it.playWhenReady
            it.release()
        }
        player = null
        binding.playerView.player = null
        binding.playerView.keepScreenOn = false
        preparedUri = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Never leave a decrypted copy behind.
        viewModel.clearPlayback()
    }

    private fun showError() {
        binding.loading.isVisible = false
        binding.tvError.isVisible = true
        showToast(R.string.error_video_playback)
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
        showToast(msg)
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
        private const val STATE_POSITION = "state_position"
        private const val TAG = "VaultVideoPlayer"

        fun intent(context: Context, mediaId: Long): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_MEDIA_ID, mediaId)
    }
}
