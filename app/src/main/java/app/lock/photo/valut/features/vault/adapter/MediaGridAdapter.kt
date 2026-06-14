package app.lock.photo.valut.features.vault.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ItemMediaGridBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel

/**
 * Reusable grid adapter for photos/videos. Thumbnails are decrypted into memory by
 * [SecureThumbnailLoader]; favorite, video play + duration, and selection state shown.
 */
class MediaGridAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onClick: (VaultMediaUiModel) -> Unit,
    private val onLongClick: (VaultMediaUiModel) -> Unit
) : ListAdapter<VaultMediaUiModel, MediaGridAdapter.MediaViewHolder>(DIFF) {

    inner class MediaViewHolder(
        private val binding: ItemMediaGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VaultMediaUiModel) = with(binding) {
            thumbnailLoader.loadThumbnail(thumbnail, item)

            val isVideo = item.mediaType == MediaType.VIDEO
            playIcon.isVisible = isVideo
            durationBadge.isVisible = isVideo && item.durationText != null
            durationBadge.text = item.durationText

            favoriteBadge.isVisible = item.isFavorite && !item.isSelected
            selectionOverlay.isVisible = item.isSelected
            selectionCheck.isVisible = item.isSelected

            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<VaultMediaUiModel>() {
            override fun areItemsTheSame(old: VaultMediaUiModel, new: VaultMediaUiModel) =
                old.id == new.id

            override fun areContentsTheSame(old: VaultMediaUiModel, new: VaultMediaUiModel) =
                old == new
        }
    }
}
