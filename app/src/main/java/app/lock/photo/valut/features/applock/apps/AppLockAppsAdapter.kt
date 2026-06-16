package app.lock.photo.valut.features.applock.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.databinding.ItemInstalledAppBinding
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel

/**
 * Installed-apps list with a per-app lock toggle. Icons are loaded off the main thread
 * via [loadIcon] (cancellation-safe by tagging the ImageView with the package name).
 */
class AppLockAppsAdapter(
    private val onToggle: (InstalledAppUiModel, Boolean) -> Unit,
    private val onConfigure: (InstalledAppUiModel) -> Unit,
    private val loadIcon: (String, ImageView) -> Unit
) : ListAdapter<InstalledAppUiModel, AppLockAppsAdapter.AppViewHolder>(DIFF) {

    inner class AppViewHolder(
        private val binding: ItemInstalledAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InstalledAppUiModel) = with(binding) {
            appName.text = item.appName
            appPackage.text = item.packageName
            appIcon.setImageDrawable(null)
            appIcon.tag = item.packageName
            loadIcon(item.packageName, appIcon)

            lockSwitch.setOnCheckedChangeListener(null)
            lockSwitch.isChecked = item.isLocked
            // Tapping a locked app opens its per-app settings; tapping an unlocked app locks it.
            root.setOnClickListener {
                if (item.isLocked) onConfigure(item) else onToggle(item, true)
            }
            lockSwitch.setOnClickListener { onToggle(item, lockSwitch.isChecked) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemInstalledAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<InstalledAppUiModel>() {
            override fun areItemsTheSame(old: InstalledAppUiModel, new: InstalledAppUiModel) =
                old.packageName == new.packageName

            override fun areContentsTheSame(old: InstalledAppUiModel, new: InstalledAppUiModel) =
                old == new
        }
    }
}
