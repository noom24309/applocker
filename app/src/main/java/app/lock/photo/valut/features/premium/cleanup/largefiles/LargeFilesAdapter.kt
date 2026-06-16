package app.lock.photo.valut.features.premium.cleanup.largefiles

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.databinding.ItemCleanupMediaBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.model.toUiModel

/** Row model so selection changes are picked up by DiffUtil. */
data class LargeFileRow(val entity: VaultMediaEntity, val selected: Boolean)

/** Flat list of large vault files with a per-row selection checkbox. */
class LargeFilesAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onToggle: (Long) -> Unit
) : ListAdapter<LargeFileRow, LargeFilesAdapter.RowHolder>(DIFF) {

    inner class RowHolder(
        private val binding: ItemCleanupMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: LargeFileRow) = with(binding) {
            val item = row.entity
            thumbnailLoader.loadThumbnail(thumbnail, item.toUiModel())
            playIcon.isVisible = MediaType.fromStorage(item.mediaType) == MediaType.VIDEO
            name.text = item.displayName
            val date = Formatters.formatDate(item.dateImported)
            meta.text = "${Formatters.formatSize(item.sizeBytes)} · $date"
            recommendedTag.isVisible = false
            checkbox.isChecked = row.selected
            root.setOnClickListener { onToggle(item.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val binding = ItemCleanupMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowHolder(binding)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LargeFileRow>() {
            override fun areItemsTheSame(old: LargeFileRow, new: LargeFileRow) =
                old.entity.id == new.entity.id

            override fun areContentsTheSame(old: LargeFileRow, new: LargeFileRow) = old == new
        }
    }
}
