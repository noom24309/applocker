package app.lock.photo.valut.features.premium.notes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.databinding.ItemNoteBinding
import app.lock.photo.valut.domain.model.NoteListItem

class NotesAdapter(
    private val onClick: (NoteListItem) -> Unit,
    private val onFavorite: (NoteListItem) -> Unit,
    private val onLongClick: (NoteListItem) -> Unit
) : ListAdapter<NoteListItem, NotesAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NoteListItem) = with(binding) {
            noteTitle.text = item.title
            notePreview.text = item.preview
            noteDate.text = Formatters.formatDate(item.updatedAt)
            noteFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener { onLongClick(item); true }
            noteFavorite.setOnClickListener { onFavorite(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteListItem>() {
            override fun areItemsTheSame(old: NoteListItem, new: NoteListItem) = old.id == new.id
            override fun areContentsTheSame(old: NoteListItem, new: NoteListItem) = old == new
        }
    }
}
