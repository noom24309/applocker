package app.lock.photo.valut.features.applock.apps

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemInstalledAppBinding
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel
import com.nextgen.ads.nativead.NextGenNativeListHelper

/**
 * Installed-apps list with a custom per-app lock toggle. Icons are loaded off the main
 * thread via [loadIcon] (cancellation-safe by tagging the ImageView with the package name).
 *
 * Native-ad rows are interleaved into the list, so an adapter position is *not* an index
 * into [apps]. That rules out diffing (`ListAdapter`): the differ's granular notifications
 * describe app indices, while the ad rows sit at combined positions that shift as the app
 * count changes — RecyclerView then offsets view holders by the wrong amounts and dies
 * with "Inconsistency detected. Invalid view holder adapter position". So every data
 * change here goes through [redraw]: the visible state (apps + ad rows) is swapped in one
 * step and always followed by a full invalidation, never inside a layout pass.
 */
class AppLockAppsAdapter(
    private val onToggle: (InstalledAppUiModel, Boolean) -> Unit,
    private val onConfigure: (InstalledAppUiModel) -> Unit,
    private val loadIcon: (String, ImageView) -> Unit,
    /** Native-ad rows interleaved into the list; null = plain list, no ads. */
    private val listAd: NextGenNativeListHelper? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** What is on screen right now — only ever replaced by [redraw]. */
    private var apps: List<InstalledAppUiModel> = emptyList()

    /**
     * Snapshot of the ad-row positions. The helper's own view of them changes whenever an
     * ad loads or finally fails — possibly from inside a bind — so it is read only in
     * [redraw]. Deriving item count/view types from live helper state instead makes them
     * shift mid-layout, which RecyclerView reports as an inconsistency.
     */
    private var adRows: List<Int> = emptyList()

    /** Set while a change is waiting for the current layout/scroll pass to finish. */
    private var pendingApps: List<InstalledAppUiModel>? = null

    private var recyclerView: RecyclerView? = null

    override fun getItemCount(): Int = apps.size + adRows.size

    override fun getItemViewType(position: Int): Int =
        if (adRows.contains(position)) TYPE_AD else TYPE_APP

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    /** New app list (search/filter result, lock toggled, …). */
    fun submitList(list: List<InstalledAppUiModel>) {
        pendingApps = list
        redraw()
    }

    /** Ad rows appeared or disappeared — re-read them and redraw. */
    fun syncAdRows() {
        redraw()
    }

    /**
     * Swaps in the pending list plus the current ad-row layout and invalidates. Deferred
     * while the RecyclerView is laying out or scrolling: changing the item count in that
     * window is exactly what RecyclerView reports as an inconsistency, and `pendingApps`
     * keeps the visible state untouched until the notify actually goes out.
     */
    private fun redraw() {
        val recycler = recyclerView
        if (recycler != null && recycler.isComputingLayout) {
            recycler.post { redraw() }
            return
        }
        pendingApps?.let {
            apps = it
            pendingApps = null
        }
        adRows = listAd?.adRowPositions(apps.size) ?: emptyList()
        notifyDataSetChanged()
    }

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

    /** Ad row: just the container the SDK fills with the loaded native (or a skeleton). */
    class AdViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_AD) {
            AdViewHolder(
                inflater.inflate(R.layout.item_app_list_native_ad, parent, false) as FrameLayout
            )
        } else {
            AppViewHolder(ItemInstalledAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val helper = listAd
        if (holder is AdViewHolder && helper != null) {
            helper.bindAdRow(holder.container, adRows.indexOf(position).coerceAtLeast(0))
            return
        }
        val item = apps.getOrNull(position - adRows.count { it < position }) ?: return
        (holder as AppViewHolder).bind(item, isLast = position == itemCount - 1)
    }

    private companion object {
        const val TYPE_APP = 0
        const val TYPE_AD = 1
    }
}
