package com.abuzahra.admin.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.databinding.ItemEventCardBinding

/**
 * Adapter for the Events fragment. Mirrors the web's EventsView.
 * Each row shows: level icon + level chip + event message + meta (time + type + device).
 */
class EventAdapter :
    ListAdapter<Event, EventAdapter.EventViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(
        private val binding: ItemEventCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            val level = event.level.lowercase()
            val (label, icon, color) = when (level) {
                "info" -> Triple("معلومة", "ℹ️", R.color.info)
                "success" -> Triple("نجاح", "✅", R.color.online_color)
                "warning" -> Triple("تحذير", "⚠️", R.color.warning)
                "error" -> Triple("خطأ", "❌", R.color.error)
                "critical" -> Triple("حرج", "🔥", R.color.error)
                else -> Triple(event.level.ifEmpty { "حدث" }, "🔔", R.color.text_secondary)
            }
            binding.tvLevel.text = label
            binding.tvLevelIcon.text = icon
            binding.tvLevel.setTextColor(itemView.context.getColor(color))
            binding.tvEventType.text = event.eventTypeCategory
            binding.tvEventMessage.text = event.displayEvent.ifEmpty { event.event }
            binding.tvEventMeta.text = buildString {
                append(event.relativeTime)
                if (event.deviceName.isNotEmpty()) {
                    append(" • ")
                    append(event.deviceName)
                }
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean =
            oldItem == newItem
    }
}
