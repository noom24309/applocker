package app.lock.photo.valut.features.vault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.FragmentAlbumsBinding
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.adapter.AlbumListItem
import app.lock.photo.valut.features.vault.adapter.AlbumsAdapter
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlbumsActivity : SecureVaultActivity() {

    private lateinit var binding: FragmentAlbumsBinding
    private val viewModel: AlbumsViewModel by viewModels()
    private lateinit var adapter: AlbumsAdapter

    @Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    /** Photos-only / videos-only entry point, or null for all media. */
    private val mediaFilter: MediaType? by lazy {
        intent.getStringExtra(EXTRA_MEDIA_FILTER)?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAlbumsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(
            when (mediaFilter) {
                MediaType.PHOTO -> R.string.vault_cat_pictures
                MediaType.VIDEO -> R.string.vault_cat_videos
                else -> R.string.vault_albums
            }
        )
        // The grid carries its own "+" create tile, so the FAB is hidden here.
        binding.fabCreateAlbum.isVisible = false

        adapter = AlbumsAdapter(
            thumbnailLoader,
            onClick = ::openAlbum,
            onOptions = ::showOptions,
            onCreate = ::showCreateDialog
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.albums.collect { albums ->
                    adapter.submitList(albums.map { AlbumListItem.Album(it) } + AlbumListItem.AddTile)
                }
            }
        }
    }

    private fun openAlbum(album: AlbumUiModel) {
        startActivity(MediaGridActivity.intent(this, GridSource.ALBUM, album.id, album.name, mediaFilter))
    }

    private fun showOptions(album: AlbumUiModel, anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.rename)
            menu.add(0, 2, 1, R.string.delete_label)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameDialog(album)
                    2 -> confirmDelete(album)
                }
                true
            }
            show()
        }
    }

    private fun showCreateDialog() {
        promptName(R.string.album_create_title, R.string.create, "") { viewModel.createAlbum(it) }
    }

    private fun showRenameDialog(album: AlbumUiModel) {
        promptName(R.string.album_rename_title, R.string.rename, album.name) {
            viewModel.renameAlbum(album.id, it)
        }
    }

    private fun confirmDelete(album: AlbumUiModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.album_delete_title)
            .setMessage(R.string.album_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> viewModel.deleteAlbum(album.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptName(titleRes: Int, positiveRes: Int, initial: String, onConfirm: (String) -> Unit) {
        val container = FrameLayout(this)
        val input = EditText(this).apply {
            hint = getString(R.string.album_name_hint)
            setText(initial)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.space_l)
        container.setPadding(pad, 0, pad, 0)
        container.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(positiveRes) { _, _ -> onConfirm(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_MEDIA_FILTER = "arg_media_filter"

        fun intent(context: Context, mediaFilter: MediaType? = null) =
            Intent(context, AlbumsActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_FILTER, mediaFilter?.name)
            }
    }
}
