package app.lock.photo.valut.features.vault.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ItemVaultRecentBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel

/** Recent-media grid cell on the vault home (thumbnail + play badge for videos). */
class VaultRecentAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onClick: (VaultMediaUiModel) -> Unit
) : RecyclerView.Adapter<VaultRecentAdapter.RecentVH>() {

    private val items = mutableListOf<VaultMediaUiModel>()

    fun submit(newItems: List<VaultMediaUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentVH {
        val binding = ItemVaultRecentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentVH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecentVH, position: Int) = holder.bind(items[position])

    inner class RecentVH(
        private val binding: ItemVaultRecentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VaultMediaUiModel) = with(binding) {
            thumbnailLoader.loadThumbnail(thumb, item)
            playBadge.visibility = if (item.mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
            root.setOnClickListener { onClick(item) }
        }
    }
}
