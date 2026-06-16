package app.lock.photo.valut.features.vault.photo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ItemPhotoPageBinding
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel

/** Full-screen swipeable photo pages with zoom. Images are decrypted into memory only. */
class PhotoPagerAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onZoomChanged: (Boolean) -> Unit
) : ListAdapter<VaultMediaUiModel, PhotoPagerAdapter.PageViewHolder>(DIFF) {

    inner class PageViewHolder(
        private val binding: ItemPhotoPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VaultMediaUiModel) {
            binding.photoView.onZoomChanged = onZoomChanged
            thumbnailLoader.loadFullImage(binding.photoView, item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<VaultMediaUiModel>() {
            override fun areItemsTheSame(old: VaultMediaUiModel, new: VaultMediaUiModel) = old.id == new.id
            override fun areContentsTheSame(old: VaultMediaUiModel, new: VaultMediaUiModel) = old == new
        }
    }
}
