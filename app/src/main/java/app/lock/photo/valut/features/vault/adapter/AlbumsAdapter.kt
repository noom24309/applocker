package app.lock.photo.valut.features.vault.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ItemAlbumAddBinding
import app.lock.photo.valut.databinding.ItemAlbumBinding
import app.lock.photo.valut.features.vault.model.AlbumUiModel

/** One cell in the albums grid: either an existing album or the trailing "create folder" tile. */
sealed interface AlbumListItem {
    data class Album(val album: AlbumUiModel) : AlbumListItem
    data object AddTile : AlbumListItem
}

class AlbumsAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onClick: (AlbumUiModel) -> Unit,
    private val onOptions: (AlbumUiModel, View) -> Unit,
    private val onCreate: () -> Unit
) : ListAdapter<AlbumListItem, RecyclerView.ViewHolder>(DIFF) {

    inner class AlbumViewHolder(
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlbumUiModel) = with(binding) {
            albumName.text = item.name
            albumCount.text = root.context.getString(R.string.items_count, item.itemCount)
            thumbnailLoader.loadCover(
                cover,
                item.coverEncrypted,
                item.coverEncryptedThumbPath,
                item.coverThumbPath ?: item.coverPath
            )
            root.setOnClickListener { onClick(item) }
            albumMore.setOnClickListener { onOptions(item, it) }
        }
    }

    inner class AddTileViewHolder(
        binding: ItemAlbumAddBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { onCreate() }
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AlbumListItem.Album -> TYPE_ALBUM
        AlbumListItem.AddTile -> TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ADD) {
            AddTileViewHolder(ItemAlbumAddBinding.inflate(inflater, parent, false))
        } else {
            AlbumViewHolder(ItemAlbumBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is AlbumViewHolder && item is AlbumListItem.Album) holder.bind(item.album)
    }

    private companion object {
        const val TYPE_ALBUM = 0
        const val TYPE_ADD = 1

        val DIFF = object : DiffUtil.ItemCallback<AlbumListItem>() {
            override fun areItemsTheSame(old: AlbumListItem, new: AlbumListItem): Boolean = when {
                old is AlbumListItem.Album && new is AlbumListItem.Album -> old.album.id == new.album.id
                else -> old == new
            }

            override fun areContentsTheSame(old: AlbumListItem, new: AlbumListItem) = old == new
        }
    }
}
