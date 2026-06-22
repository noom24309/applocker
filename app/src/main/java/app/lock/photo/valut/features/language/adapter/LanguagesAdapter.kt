package com.wastickers.romantic.stickers.loveromance.ui.language.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language

class LanguagesAdapter(
    private val languages: List<Language>,
    private val onClick: (Language) -> Unit
) : RecyclerView.Adapter<LanguagesAdapter.LanguageViewHolder>() {

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadge: TextView = itemView.findViewById(R.id.tvLangBadge)
        private val tvNative: TextView = itemView.findViewById(R.id.tvLangNative)
        private val tvEnglish: TextView = itemView.findViewById(R.id.tvLangEnglish)
        private val ivRadio: ImageView = itemView.findViewById(R.id.ivLangRadio)

        fun bind(language: Language) {
            tvNative.text = language.name
            tvEnglish.text = language.englishName
            // Leading badge: language code (no flags).
            tvBadge.text = language.languageCode.take(2).uppercase()
            ivRadio.setImageResource(
                if (language.isChecked) R.drawable.ic_check_circle else R.drawable.bg_radio_ring
            )
            itemView.isSelected = language.isChecked
            itemView.setOnClickListener { onClick(language) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.language_items_layout, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position])
    }

    override fun getItemCount(): Int = languages.size
}
