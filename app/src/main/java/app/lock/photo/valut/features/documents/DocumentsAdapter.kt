package app.lock.photo.valut.features.documents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.databinding.ItemDocumentBinding

class DocumentsAdapter(
    private val onClick: (PrivateDocumentEntity) -> Unit,
    private val onFavorite: (PrivateDocumentEntity) -> Unit,
    private val onLongClick: (PrivateDocumentEntity) -> Unit
) : ListAdapter<PrivateDocumentEntity, DocumentsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PrivateDocumentEntity) = with(binding) {
            docName.text = item.displayName
            val type = item.displayName.substringAfterLast('.', "").uppercase()
                .ifEmpty { item.mimeType.substringAfterLast('/').uppercase() }
            docMeta.text = root.context.getString(
                R.string.documents_meta,
                type,
                Formatters.formatSize(item.sizeBytes),
                Formatters.formatDate(item.dateImported)
            )
            docFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener { onLongClick(item); true }
            docFavorite.setOnClickListener { onFavorite(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PrivateDocumentEntity>() {
            override fun areItemsTheSame(old: PrivateDocumentEntity, new: PrivateDocumentEntity) = old.id == new.id
            override fun areContentsTheSame(old: PrivateDocumentEntity, new: PrivateDocumentEntity) = old == new
        }
    }
}
