package app.lock.photo.valut.features.intruder.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemIntruderAlertBinding
import app.lock.photo.valut.features.intruder.IntruderLabels
import app.lock.photo.valut.features.intruder.model.IntruderAttemptUiModel

/**
 * Intruder alerts list. Encrypted thumbnails are decoded off the main thread by
 * [loadThumbnail] (cancellation-safe via the ImageView tag).
 */
class IntruderAlertsAdapter(
    private val onClick: (IntruderAttemptUiModel) -> Unit,
    private val onLongClick: (IntruderAttemptUiModel) -> Unit,
    private val loadThumbnail: (Long, ImageView) -> Unit
) : ListAdapter<IntruderAttemptUiModel, IntruderAlertsAdapter.AlertViewHolder>(DIFF) {

    inner class AlertViewHolder(
        private val binding: ItemIntruderAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: IntruderAttemptUiModel) = with(binding) {
            val context = root.context
            val source = context.getString(IntruderLabels.source(item.triggerSource))
            tvSource.text = if (item.appName != null) "$source · ${item.appName}" else source

            val time = DateUtils.getRelativeDateTimeString(
                context, item.timestamp, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
            )
            tvTime.text = context.getString(R.string.intruder_time_attempts, time, item.wrongAttemptCount)

            tvStatus.isVisible = !item.captureSuccess
            tvStatus.setText(R.string.intruder_capture_failed)

            thumbnail.setImageDrawable(null)
            thumbnail.tag = item.id
            if (item.captureSuccess) loadThumbnail(item.id, thumbnail)

            selectionCheck.isVisible = item.isSelected
            root.isChecked = item.isSelected
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener { onLongClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemIntruderAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<IntruderAttemptUiModel>() {
            override fun areItemsTheSame(old: IntruderAttemptUiModel, new: IntruderAttemptUiModel) =
                old.id == new.id
            override fun areContentsTheSame(old: IntruderAttemptUiModel, new: IntruderAttemptUiModel) =
                old == new
        }
    }
}
