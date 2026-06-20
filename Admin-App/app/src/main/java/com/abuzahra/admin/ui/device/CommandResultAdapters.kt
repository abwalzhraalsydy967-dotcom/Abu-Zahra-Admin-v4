package com.abuzahra.admin.ui.device

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.databinding.ItemCallBinding
import com.abuzahra.admin.databinding.ItemContactBinding
import com.abuzahra.admin.databinding.ItemGenericKeyValueBinding
import com.abuzahra.admin.databinding.ItemSmsBinding

// ═══════════════════════════════════════════════════════════════════
// Result list adapters — one per parsed result list type.
//
// All four follow the same ListAdapter pattern (DiffUtil + ViewBinding)
// used elsewhere in the app (EventAdapter, RequestedFileAdapter, …) so
// they recycle efficiently and animate item changes.
// ═══════════════════════════════════════════════════════════════════

/**
 * Adapter for [CommandResultParser.SmsItem] — renders the SMS list result
 * (sender / body / date) using [R.layout.item_sms].
 */
class SmsAdapter : ListAdapter<CommandResultParser.SmsItem, SmsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSmsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemSmsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.SmsItem) {
            binding.tvSender.text = item.sender
            binding.tvBody.text = item.body
            binding.tvDate.text = item.date
            // Incoming SMS = green bar, sent = secondary, unknown = primary
            val colorRes = when ((item.type ?: "").lowercase()) {
                "inbox", "received", "incoming", "1" -> R.color.secondary
                "sent", "outgoing", "2" -> R.color.primary_variant
                "draft", "3" -> R.color.warning
                else -> R.color.secondary
            }
            binding.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
            binding.root.visibility = if (item.body.isEmpty() && item.sender == "غير معروف") {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.SmsItem>() {
        override fun areItemsTheSame(o: CommandResultParser.SmsItem, n: CommandResultParser.SmsItem) =
            o.sender == n.sender && o.date == n.date && o.body == n.body
        override fun areContentsTheSame(o: CommandResultParser.SmsItem, n: CommandResultParser.SmsItem) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.ContactItem] — renders the contacts list
 * (name / phone / email) using [R.layout.item_contact].
 */
class ContactAdapter : ListAdapter<CommandResultParser.ContactItem, ContactAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.ContactItem) {
            binding.tvName.text = item.name
            binding.tvPhone.text = item.phone
            if (item.email.isNullOrEmpty()) {
                binding.tvEmail.visibility = View.GONE
            } else {
                binding.tvEmail.text = item.email
                binding.tvEmail.visibility = View.VISIBLE
            }
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.ContactItem>() {
        override fun areItemsTheSame(o: CommandResultParser.ContactItem, n: CommandResultParser.ContactItem) =
            o.phone == n.phone && o.name == n.name
        override fun areContentsTheSame(o: CommandResultParser.ContactItem, n: CommandResultParser.ContactItem) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.CallItem] — renders the call log
 * (number / type chip / duration / date) using [R.layout.item_call].
 */
class CallAdapter : ListAdapter<CommandResultParser.CallItem, CallAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.CallItem) {
            binding.tvNumber.text = item.number
            val ctx = binding.root.context
            val (labelRes, colorRes) = when (item.type) {
                "incoming" -> R.string.call_incoming to R.color.secondary
                "outgoing" -> R.string.call_outgoing to R.color.primary_variant
                "missed" -> R.string.call_missed to R.color.error
                "rejected" -> R.string.call_rejected to R.color.warning
                else -> R.string.call_unknown to R.color.text_hint
            }
            binding.chipCallType.text = ctx.getString(labelRes)
            binding.chipCallType.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, colorRes)
            )
            binding.chipCallType.setTextColor(ContextCompat.getColor(ctx, R.color.on_primary))
            binding.ivCallType.setColorFilter(ContextCompat.getColor(ctx, colorRes))

            val metaParts = mutableListOf<String>()
            if (item.durationSeconds != null && item.durationSeconds > 0) {
                metaParts.add(formatDuration(item.durationSeconds))
            }
            binding.tvCallMeta.text = metaParts.joinToString(" • ")
            binding.tvCallDate.text = item.date
        }

        private fun formatDuration(seconds: Long): String {
            val m = seconds / 60
            val s = seconds % 60
            return String.format("%02d:%02d", m, s)
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.CallItem>() {
        override fun areItemsTheSame(o: CommandResultParser.CallItem, n: CommandResultParser.CallItem) =
            o.number == n.number && o.date == n.date
        override fun areContentsTheSame(o: CommandResultParser.CallItem, n: CommandResultParser.CallItem) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.KeyValuePair] — renders a generic
 * key-value list (device_info, battery, wifi_info, notifications, apps, …)
 * using [R.layout.item_generic_key_value].
 */
class KeyValueAdapter : ListAdapter<CommandResultParser.KeyValuePair, KeyValueAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGenericKeyValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemGenericKeyValueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.KeyValuePair) {
            binding.tvKey.text = item.key
            binding.tvValue.text = item.value
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.KeyValuePair>() {
        override fun areItemsTheSame(o: CommandResultParser.KeyValuePair, n: CommandResultParser.KeyValuePair) =
            o.key == n.key
        override fun areContentsTheSame(o: CommandResultParser.KeyValuePair, n: CommandResultParser.KeyValuePair) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.FileItem] — reuses the key-value card
 * layout to display file listings (name, size, modified date).
 */
class FileListAdapter : ListAdapter<CommandResultParser.FileItem, FileListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGenericKeyValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemGenericKeyValueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.FileItem) {
            val icon = if (item.isDirectory) "📁" else "📄"
            binding.tvKey.text = "$icon ${item.name}"
            val parts = mutableListOf<String>()
            if (item.size > 0) parts.add(formatSize(item.size))
            if (!item.modified.isNullOrEmpty()) parts.add(item.modified)
            binding.tvValue.text = parts.joinToString(" • ").ifEmpty { item.path }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "${bytes} B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.FileItem>() {
        override fun areItemsTheSame(o: CommandResultParser.FileItem, n: CommandResultParser.FileItem) =
            o.path == n.path
        override fun areContentsTheSame(o: CommandResultParser.FileItem, n: CommandResultParser.FileItem) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.NotificationItem] — reuses the key-value
 * card layout to display notification list entries.
 */
class NotificationAdapter : ListAdapter<CommandResultParser.NotificationItem, NotificationAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGenericKeyValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemGenericKeyValueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.NotificationItem) {
            binding.tvKey.text = "${item.app} — ${item.title}"
            binding.tvValue.text = item.text.ifEmpty { item.time }
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.NotificationItem>() {
        override fun areItemsTheSame(o: CommandResultParser.NotificationItem, n: CommandResultParser.NotificationItem) =
            o.app == n.app && o.title == n.title && o.time == n.time
        override fun areContentsTheSame(o: CommandResultParser.NotificationItem, n: CommandResultParser.NotificationItem) = o == n
    }
}

/**
 * Adapter for [CommandResultParser.AppItem] — reuses the key-value card
 * layout to display installed-apps list entries.
 */
class AppListAdapter : ListAdapter<CommandResultParser.AppItem, AppListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGenericKeyValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemGenericKeyValueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandResultParser.AppItem) {
            val systemTag = if (item.isSystem) " [نظام]" else ""
            binding.tvKey.text = "${item.name}${systemTag}"
            val parts = mutableListOf<String>()
            if (!item.packageName.isNullOrEmpty()) parts.add(item.packageName)
            if (!item.version.isNullOrEmpty()) parts.add("v${item.version}")
            binding.tvValue.text = parts.joinToString(" • ")
        }
    }

    private object DIFF : DiffUtil.ItemCallback<CommandResultParser.AppItem>() {
        override fun areItemsTheSame(o: CommandResultParser.AppItem, n: CommandResultParser.AppItem) =
            o.packageName == n.packageName
        override fun areContentsTheSame(o: CommandResultParser.AppItem, n: CommandResultParser.AppItem) = o == n
    }
}
