package app.lock.photo.valut.features.applock.suggested

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.databinding.ItemInstalledAppBinding

/** Suggested-apps list (reuses the installed-app row); the switch reflects selection. */
class SuggestedAppsAdapter(
    private val categoryLabel: (SuggestedAppUi) -> String,
    private val onToggle: (String) -> Unit,
    private val loadIcon: (String, ImageView) -> Unit
) : ListAdapter<SuggestedAppUi, SuggestedAppsAdapter.SuggestionViewHolder>(DIFF) {

    inner class SuggestionViewHolder(
        private val binding: ItemInstalledAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SuggestedAppUi) = with(binding) {
            appName.text = item.app.appName
            appPackage.text = categoryLabel(item)
            appIcon.setImageDrawable(null)
            appIcon.tag = item.app.packageName
            loadIcon(item.app.packageName, appIcon)
            lockSwitch.setOnCheckedChangeListener(null)
            lockSwitch.isChecked = item.selected
            root.setOnClickListener { onToggle(item.app.packageName) }
            lockSwitch.setOnClickListener { onToggle(item.app.packageName) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemInstalledAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SuggestedAppUi>() {
            override fun areItemsTheSame(old: SuggestedAppUi, new: SuggestedAppUi) =
                old.app.packageName == new.app.packageName
            override fun areContentsTheSame(old: SuggestedAppUi, new: SuggestedAppUi) = old == new
        }
    }
}
