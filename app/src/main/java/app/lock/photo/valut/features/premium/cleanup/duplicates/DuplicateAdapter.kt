package app.lock.photo.valut.features.premium.cleanup.duplicates

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
import app.lock.photo.valut.databinding.ItemDuplicateHeaderBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.model.toUiModel

/** Flat rows the duplicate list renders: a group header followed by its photos. */
sealed interface DuplicateRow {
    data class Header(val checksum: String, val copies: Int, val recoverableBytes: Long) : DuplicateRow
    data class Item(
        val entity: VaultMediaEntity,
        val selected: Boolean,
        val isRecommended: Boolean
    ) : DuplicateRow
}

/** Grouped duplicate photos: a header per checksum group, then each copy with a checkbox. */
class DuplicateAdapter(
    private val thumbnailLoader: SecureThumbnailLoader,
    private val onToggle: (Long) -> Unit
) : ListAdapter<DuplicateRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) =
        if (getItem(position) is DuplicateRow.Header) TYPE_HEADER else TYPE_ITEM

    inner class HeaderHolder(
        private val binding: ItemDuplicateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DuplicateRow.Header) {
            binding.groupHeader.text = binding.root.context.getString(
                app.lock.photo.valut.R.string.duplicates_group_header,
                row.copies,
                Formatters.formatSize(row.recoverableBytes)
            )
        }
    }

    inner class ItemHolder(
        private val binding: ItemCleanupMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DuplicateRow.Item) = with(binding) {
            val item = row.entity
            thumbnailLoader.loadThumbnail(thumbnail, item.toUiModel())
            playIcon.isVisible = MediaType.fromStorage(item.mediaType) == MediaType.VIDEO
            name.text = item.displayName
            meta.text = "${Formatters.formatSize(item.sizeBytes)} · ${Formatters.formatDate(item.dateImported)}"
            recommendedTag.isVisible = row.isRecommended
            checkbox.isChecked = row.selected
            root.setOnClickListener { onToggle(item.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemDuplicateHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemCleanupMediaBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is DuplicateRow.Header -> (holder as HeaderHolder).bind(row)
            is DuplicateRow.Item -> (holder as ItemHolder).bind(row)
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1

        val DIFF = object : DiffUtil.ItemCallback<DuplicateRow>() {
            override fun areItemsTheSame(old: DuplicateRow, new: DuplicateRow): Boolean = when {
                old is DuplicateRow.Header && new is DuplicateRow.Header -> old.checksum == new.checksum
                old is DuplicateRow.Item && new is DuplicateRow.Item -> old.entity.id == new.entity.id
                else -> false
            }

            override fun areContentsTheSame(old: DuplicateRow, new: DuplicateRow) = old == new
        }
    }
}
