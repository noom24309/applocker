package app.lock.photo.valut.features.applock.apps

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemInstalledAppBinding
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel

/**
 * Installed-apps list with a custom per-app lock toggle. Icons are loaded off the main
 * thread via [loadIcon] (cancellation-safe by tagging the ImageView with the package name).
 */
class AppLockAppsAdapter(
    private val onToggle: (InstalledAppUiModel, Boolean) -> Unit,
    private val onConfigure: (InstalledAppUiModel) -> Unit,
    private val loadIcon: (String, ImageView) -> Unit
) : ListAdapter<InstalledAppUiModel, AppLockAppsAdapter.AppViewHolder>(DIFF) {

    inner class AppViewHolder(
        private val binding: ItemInstalledAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InstalledAppUiModel, isLast: Boolean) = with(binding) {
            appName.text = item.appName
            appPackage.text = item.packageName
            appIcon.setImageDrawable(null)
            appIcon.tag = item.packageName
            loadIcon(item.packageName, appIcon)

            rowDivider.visibility = if (isLast) View.GONE else View.VISIBLE
            renderSwitch(item.isLocked)

            // Tapping a locked app opens its per-app settings; tapping an unlocked app locks it.
            rowRoot.setOnClickListener {
                if (item.isLocked) onConfigure(item) else onToggle(item, true)
            }
            switchTrack.setOnClickListener { onToggle(item, !item.isLocked) }
        }

        private fun renderSwitch(locked: Boolean) {
            val ctx = binding.root.context
            val trackColor = ContextCompat.getColor(
                ctx, if (locked) R.color.home_primary else R.color.home_toggle_off
            )
            val iconColor = ContextCompat.getColor(
                ctx, if (locked) R.color.home_primary else R.color.home_hint
            )
            binding.switchTrack.backgroundTintList = ColorStateList.valueOf(trackColor)
            binding.switchIcon.setColorFilter(iconColor)

            val lp = binding.switchThumb.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.CENTER_VERTICAL or (if (locked) Gravity.END else Gravity.START)
            binding.switchThumb.layoutParams = lp
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemInstalledAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), isLast = position == itemCount - 1)
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
