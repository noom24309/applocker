package app.lock.photo.valut.features.documents.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemDocumentCardBinding
import app.lock.photo.valut.domain.model.DocumentCardColors
import app.lock.photo.valut.domain.model.DocumentCardUiModel

class DocumentCardsAdapter(
    private val onClick: (DocumentCardUiModel) -> Unit,
    private val onFavorite: (DocumentCardUiModel) -> Unit,
    private val onLongClick: (DocumentCardUiModel) -> Unit
) : ListAdapter<DocumentCardUiModel, DocumentCardsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDocumentCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DocumentCardUiModel) = with(binding) {
            val context = root.context
            gradientLayer.setBackgroundResource(DocumentCardColors.gradientFor(item.colorKey))
            cardTypeIcon.setImageResource(item.type.iconRes)
            cardWatermark.setImageResource(item.type.iconRes)
            cardTypeLabel.text = context.getString(item.type.displayNameRes).uppercase()
            cardHolderName.text = item.holderName.ifBlank { context.getString(item.type.displayNameRes) }
            cardNumber.text = item.maskedNumber
            cardNumber.isVisible = item.maskedNumber.isNotEmpty()
            cardExpiry.text = item.expiryText
            cardExpiry.isVisible = item.expiryText.isNotEmpty()
            cardFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            cardRoot.setOnClickListener { onClick(item) }
            cardRoot.setOnLongClickListener { onLongClick(item); true }
            cardFavorite.setOnClickListener { onFavorite(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDocumentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DocumentCardUiModel>() {
            override fun areItemsTheSame(old: DocumentCardUiModel, new: DocumentCardUiModel) = old.id == new.id
            override fun areContentsTheSame(old: DocumentCardUiModel, new: DocumentCardUiModel) = old == new
        }
    }
}
