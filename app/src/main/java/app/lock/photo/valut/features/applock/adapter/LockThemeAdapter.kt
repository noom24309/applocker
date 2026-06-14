package app.lock.photo.valut.features.applock.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemLockThemeBinding
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.features.applock.AppLockLabels

/** Simple theme list with a preview swatch + selected check. */
class LockThemeAdapter(
    private val themes: List<LockTheme>,
    private val onSelect: (LockTheme) -> Unit
) : RecyclerView.Adapter<LockThemeAdapter.ThemeViewHolder>() {

    private var selected: LockTheme = LockTheme.DEFAULT

    fun setSelected(theme: LockTheme) {
        val old = selected
        selected = theme
        notifyItemChanged(themes.indexOf(old))
        notifyItemChanged(themes.indexOf(theme))
    }

    inner class ThemeViewHolder(
        private val binding: ItemLockThemeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(theme: LockTheme) = with(binding) {
            themeName.setText(AppLockLabels.theme(theme))
            themeSelected.isVisible = theme == selected
            themePreview.setBackgroundColor(
                ContextCompat.getColor(root.context, previewColor(theme))
            )
            root.setOnClickListener { onSelect(theme) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemLockThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThemeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) = holder.bind(themes[position])

    override fun getItemCount(): Int = themes.size

    private fun previewColor(theme: LockTheme): Int = when (theme) {
        LockTheme.DARK -> R.color.overlay_dark_bg
        LockTheme.GLASS -> R.color.overlay_glass_bg
        LockTheme.MINIMAL -> R.color.white
        LockTheme.CALCULATOR -> R.color.calculator_bg
        LockTheme.FAKE_CRASH -> R.color.fake_crash_bg
        LockTheme.FAKE_LOADING -> R.color.background
        else -> R.color.primary
    }
}
