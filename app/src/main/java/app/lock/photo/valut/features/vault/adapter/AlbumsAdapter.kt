package app.lock.photo.valut.features.vault.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemAlbumBinding
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import com.bumptech.glide.Glide

class AlbumsAdapter(
    private val onClick: (AlbumUiModel) -> Unit,
    private val onOptions: (AlbumUiModel, View) -> Unit
) : ListAdapter<AlbumUiModel, AlbumsAdapter.AlbumViewHolder>(DIFF) {

    inner class AlbumViewHolder(
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlbumUiModel) = with(binding) {
            albumName.text = item.name
            albumCount.text = root.context.getString(R.string.items_count, item.itemCount)
            Glide.with(cover).load(item.coverPath).centerCrop().into(cover)
            root.setOnClickListener { onClick(item) }
            albumMore.setOnClickListener { onOptions(item, it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlbumUiModel>() {
            override fun areItemsTheSame(old: AlbumUiModel, new: AlbumUiModel) = old.id == new.id
            override fun areContentsTheSame(old: AlbumUiModel, new: AlbumUiModel) = old == new
        }
    }
}
