package com.abuzahra.admin.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.data.api.NotificationEntry
import com.abuzahra.admin.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for the notifications list shown in NotificationsActivity.
 * Each row displays: app icon, app name, time, title, text.
 */
class NotificationAdapter :
    ListAdapter<NotificationEntry, NotificationAdapter.NotificationVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NotificationEntry>() {
            override fun areItemsTheSame(
                oldItem: NotificationEntry,
                newItem: NotificationEntry
            ): Boolean {
                // Use the composite key (app+title+text+timestamp) since
                // NotificationEntry has no stable id.
                return oldItem.app == newItem.app &&
                        oldItem.title == newItem.title &&
                        oldItem.text == newItem.text &&
                        oldItem.timestamp == newItem.timestamp
            }

            override fun areContentsTheSame(
                oldItem: NotificationEntry,
                newItem: NotificationEntry
            ): Boolean = oldItem == newItem
        }

        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val DATE_FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }

    inner class NotificationVH(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationVH {
        return NotificationVH(
            ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: NotificationVH, position: Int) {
        val n = getItem(position)
        with(holder.binding) {
            tvApp.text = n.app.ifEmpty { n.package_name.ifEmpty { "تطبيق غير معروف" } }
            tvTitle.text = n.title.ifEmpty { "—" }
            tvText.text = n.text.ifEmpty { n.ticker.ifEmpty { "" } }

            tvTime.text = if (n.timestamp > 0) {
                val ts = n.timestamp
                // Treat timestamps > 1e12 as milliseconds.
                val ms = if (ts > 1_000_000_000_000L) ts else ts * 1000
                val now = System.currentTimeMillis()
                val delta = (now - ms) / 1000
                when {
                    delta < 60 -> "الآن"
                    delta < 3600 -> "منذ ${delta / 60} دقيقة"
                    delta < 86400 -> "منذ ${delta / 3600} ساعة"
                    else -> DATE_FMT.format(Date(ms))
                }
            } else if (n.date.isNotEmpty()) {
                n.date
            } else {
                TIME_FMT.format(Date())
            }
        }
    }
}
