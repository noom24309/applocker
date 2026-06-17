package app.lock.photo.valut.features.vault.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ItemVaultAlbumBinding
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import java.text.NumberFormat

/** Horizontal album row on the vault home (cover + name + count). */
class VaultAlbumRowAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onClick: (AlbumUiModel) -> Unit
) : RecyclerView.Adapter<VaultAlbumRowAdapter.AlbumVH>() {

    private val items = mutableListOf<AlbumUiModel>()

    fun submit(newItems: List<AlbumUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumVH {
        val binding = ItemVaultAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumVH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AlbumVH, position: Int) = holder.bind(items[position])

    inner class AlbumVH(
        private val binding: ItemVaultAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AlbumUiModel) = with(binding) {
            albumName.text = item.name
            albumCount.text = NumberFormat.getInstance().format(item.itemCount)
            thumbnailLoader.loadCover(
                cover,
                item.coverEncrypted,
                item.coverEncryptedThumbPath,
                item.coverThumbPath ?: item.coverPath
            )
            root.setOnClickListener { onClick(item) }
        }
    }
}
