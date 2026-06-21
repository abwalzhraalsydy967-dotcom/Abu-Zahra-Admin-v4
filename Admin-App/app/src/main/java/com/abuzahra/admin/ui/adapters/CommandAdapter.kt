package com.abuzahra.admin.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.data.model.CommandDefinitions
import com.abuzahra.admin.databinding.ItemCommandCardBinding

/**
 * Adapter for the Commands fragment's command grid.
 * Renders a flat list of [CommandDefinitions.CommandDef] items.
 * Category chips + search are applied before submitList() by the fragment.
 */
class CommandAdapter(
    private val onItemClick: (CommandDefinitions.CommandDef) -> Unit
) : ListAdapter<CommandDefinitions.CommandDef, CommandAdapter.CommandViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandViewHolder {
        val binding = ItemCommandCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommandViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommandViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommandViewHolder(
        private val binding: ItemCommandCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardCommand.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(pos))
                }
            }
        }

        fun bind(def: CommandDefinitions.CommandDef) {
            binding.tvCommandName.text = def.name
            binding.tvCommandDesc.text = def.description
            // Pick a representative emoji per category — server doesn't ship icons,
            // so we derive them client-side (matches web's commands.ts icons).
            binding.tvCommandIcon.text = emojiFor(def)
            binding.tvCommandParamBadge.visibility = if (hasParams(def)) android.view.View.VISIBLE else android.view.View.GONE
        }

        /**
         * Map a [CommandDefinitions.CommandDef] to an emoji icon — mirrors the
         * web's `commands.ts` icon assignments.
         */
        private fun emojiFor(def: CommandDefinitions.CommandDef): String {
            return when (def.key) {
                // data
                "sms" -> "💬"
                "calls" -> "📞"
                "contacts" -> "👤"
                "location" -> "📍"
                "notifications" -> "🔔"
                "apps" -> "📱"
                "info" -> "ℹ️"
                "battery" -> "🔋"
                "gallery" -> "🖼️"
                "clipboard" -> "📋"
                "all_data" -> "📦"
                "wifi_info" -> "📶"
                "network_info" -> "🌐"
                "sim_info" -> "📲"
                "storage_info" -> "💾"
                "installed_apps" -> "📱"
                "running_apps" -> "⚡"
                "calendar" -> "📅"
                "browser_history" -> "🌍"
                "app_usage" -> "⏱️"
                // social
                "whatsapp" -> "💬"
                "telegram_app" -> "✈️"
                "instagram" -> "📷"
                "messenger" -> "💭"
                "snapchat" -> "👻"
                "tiktok" -> "🎵"
                "twitter" -> "🐦"
                "viber" -> "📞"
                "signal" -> "🔒"
                "facebook" -> "👤"
                "youtube" -> "▶️"
                // control
                "ping" -> "🏓"
                "vibrate" -> "📳"
                "ring" -> "🔔"
                "screenshot" -> "📸"
                "front_camera" -> "🤳"
                "back_camera" -> "📷"
                "record_audio" -> "🎙️"
                "record_screen" -> "🎬"
                "lock_phone" -> "🔒"
                "unlock_phone" -> "🔓"
                "reboot" -> "🔄"
                "shutdown" -> "⛔"
                "set_volume" -> "🔊"
                "set_brightness" -> "☀️"
                "set_ringtone" -> "🎵"
                "enable_wifi" -> "📶"
                "disable_wifi" -> "📵"
                "enable_bluetooth" -> "🔵"
                "disable_bluetooth" -> "❌"
                "enable_mobile_data" -> "📱"
                "disable_mobile_data" -> "📵"
                "enable_hotspot" -> "📡"
                "disable_hotspot" -> "📵"
                "airplane_on" -> "✈️"
                "airplane_off" -> "🛬"
                "torch_on" -> "🔦"
                "torch_off" -> "🔦"
                "play_sound" -> "🔊"
                "speak_text" -> "🗣️"
                "show_notification" -> "🔔"
                "open_url" -> "🔗"
                "send_sms" -> "💬"
                "make_call" -> "📞"
                // apps
                "open_app" -> "📱"
                "close_app" -> "❌"
                "install_app" -> "⬇️"
                "uninstall_app" -> "🗑️"
                "block_app" -> "🚫"
                "unblock_app" -> "✅"
                "clear_app_data" -> "🧹"
                "force_stop_app" -> "⛔"
                // files
                "list_files" -> "📁"
                "list_downloads" -> "📥"
                "list_dcim" -> "📷"
                "list_music" -> "🎵"
                "list_videos" -> "🎬"
                "list_documents" -> "📄"
                "list_whatsapp" -> "💬"
                "list_telegram_files" -> "✈️"
                "recent_files" -> "🕐"
                "search_files" -> "🔍"
                "get_file" -> "⬇️"
                "delete_file" -> "🗑️"
                "send_full_backup" -> "💾"
                // security
                "wipe_data" -> "💣"
                "factory_reset" -> "⚠️"
                "show_app" -> "👁️"
                "hide_app" -> "🙈"
                "change_passcode" -> "🔑"
                "enable_biometric" -> "☝️"
                "disable_biometric" -> "🚫"
                "anti_uninstall_on" -> "🛡️"
                "anti_uninstall_off" -> "❌"
                "device_admin_status" -> "👑"
                "check_root" -> "🔍"
                // monitor
                "keylogger_start" -> "⌨️"
                "keylogger_stop" -> "⏹️"
                "get_keylogger" -> "📋"
                "screen_record_start" -> "🎬"
                "screen_record_stop" -> "⏹️"
                "location_live" -> "📍"
                "location_stop" -> "⛔"
                "clipboard_monitor_start" -> "📋"
                "clipboard_monitor_stop" -> "⏹️"
                "sms_monitor" -> "💬"
                "call_monitor" -> "📞"
                // streaming
                "start_screen_stream" -> "🖥️"
                "stop_screen_stream" -> "⏹️"
                "start_camera_stream" -> "📷"
                "stop_camera_stream" -> "⏹️"
                "start_audio_stream" -> "🎙️"
                "stop_audio_stream" -> "⏹️"
                "switch_camera" -> "🔄"
                "set_stream_quality" -> "⚙️"
                "stop_all_streams" -> "⛔"
                else -> "⚡"
            }
        }

        /**
         * True for commands that take parameters (so we show the "معلمات" badge).
         * Mirrors the web's `hasParams` field on commands.ts entries.
         */
        private fun hasParams(def: CommandDefinitions.CommandDef): Boolean = when (def.key) {
            "set_volume", "set_brightness", "set_ringtone", "speak_text",
            "show_notification", "open_url", "send_sms", "make_call",
            "open_app", "close_app", "install_app", "uninstall_app",
            "block_app", "unblock_app", "clear_app_data", "force_stop_app",
            "list_files", "search_files", "get_file", "delete_file",
            "change_passcode", "set_stream_quality" -> true
            else -> false
        }
    }

    object Diff : DiffUtil.ItemCallback<CommandDefinitions.CommandDef>() {
        override fun areItemsTheSame(
            oldItem: CommandDefinitions.CommandDef,
            newItem: CommandDefinitions.CommandDef
        ): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: CommandDefinitions.CommandDef,
            newItem: CommandDefinitions.CommandDef
        ): Boolean = oldItem == newItem
    }
}
