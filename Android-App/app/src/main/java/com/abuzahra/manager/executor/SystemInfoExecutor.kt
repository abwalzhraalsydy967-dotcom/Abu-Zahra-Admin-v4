package com.abuzahra.manager.executor

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * SystemInfoExecutor — system-level info + settings shortcuts + media + calls.
 *
 * Most data-retrieval commands here are READ-only and safe.
 * Settings shortcuts open the corresponding system Settings activity
 * (requires no special permission — they are user-facing activities).
 */
object SystemInfoExecutor {

    private const val TAG = "SystemInfoExecutor"

    // ===== SYSTEM PROPERTIES =====
    fun getSystemProperties(): Map<String, Any> {
        return try {
            val keys = listOf(
                "ro.build.version.release",
                "ro.build.version.sdk",
                "ro.build.version.incremental",
                "ro.product.model",
                "ro.product.brand",
                "ro.product.manufacturer",
                "ro.product.device",
                "ro.product.board",
                "ro.bootloader",
                "ro.hardware",
                "ro.kernel.qemu",
                "ro.debuggable",
                "ro.secure",
                "ro.build.type",
                "ro.build.tags",
                "ro.build.fingerprint",
                "ro.product.cpu.abi",
                "ro.product.cpu.abilist",
                "ro.boot.verifiedbootstate",
                "ro.boot.flash.locked",
                "ro.boot.vbmeta.device_state",
                "gsm.version.baseband",
                "gsm.version.ril-impl",
                "init.svc.adbd",
                "init.svc.surfaceflinger",
                "persist.sys.timezone",
                "persist.sys.locale",
                "ro.serialno",
                "ro.boot.serialno"
            )
            val map = mutableMapOf<String, Any>()
            for (key in keys) {
                val value = try {
                    readSystemProperty(key)
                } catch (e: Exception) { "" }
                if (value.isNotBlank()) map[key] = value
            }
            mapOf("count" to map.size, "properties" to map)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "system_properties error"))
        }
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val method = cls.getMethod("get", String::class.java)
            method.invoke(null, key) as? String ?: ""
        } catch (e: Exception) {
            // Fallback: try ProcessBuilder "getprop"
            try {
                val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText().trim()
                p.waitFor()
                out
            } catch (_: Exception) { "" }
        }
    }

    // ===== BUILD INFO =====
    fun getBuildInfo(): Map<String, Any> {
        return try {
            mapOf(
                "model" to Build.MODEL,
                "brand" to Build.BRAND,
                "manufacturer" to Build.MANUFACTURER,
                "device" to Build.DEVICE,
                "product" to Build.PRODUCT,
                "board" to Build.BOARD,
                "hardware" to Build.HARDWARE,
                "host" to Build.HOST,
                "user" to Build.USER,
                "display" to Build.DISPLAY,
                "bootloader" to Build.BOOTLOADER,
                "fingerprint" to Build.FINGERPRINT,
                "id" to Build.ID,
                "type" to Build.TYPE,
                "tags" to Build.TAGS,
                "time" to Build.TIME,
                "time_str" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(Build.TIME)),
                "release" to Build.VERSION.RELEASE,
                "sdk_int" to Build.VERSION.SDK_INT,
                "codename" to Build.VERSION.CODENAME,
                "incremental" to Build.VERSION.INCREMENTAL,
                "security_patch" to Build.VERSION.SECURITY_PATCH,
                "abi" to Build.SUPPORTED_ABIS.joinToString(",")
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "build info error"))
        }
    }

    // ===== UPTIME =====
    fun getUptime(): Map<String, Any> {
        return try {
            val ms = SystemClock.elapsedRealtime()
            val secs = ms / 1000
            val days = secs / 86400
            val hours = (secs % 86400) / 3600
            val minutes = (secs % 3600) / 60
            val seconds = secs % 60
            mapOf(
                "uptime_ms" to ms,
                "uptime_seconds" to secs,
                "uptime_human" to "${days}d ${hours}h ${minutes}m ${seconds}s",
                "deep_sleep_ms" to SystemClock.elapsedRealtimeNanos() / 1_000_000
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "uptime error"))
        }
    }

    // ===== BOOT TIME =====
    fun getBootTime(): Map<String, Any> {
        return try {
            val now = System.currentTimeMillis()
            val uptime = SystemClock.elapsedRealtime()
            val bootTime = now - uptime
            mapOf(
                "boot_time_ms" to bootTime,
                "boot_time_str" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(bootTime)),
                "current_time_ms" to now
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "boot time error"))
        }
    }

    // ===== CURRENT TIME =====
    fun getCurrentTime(): Map<String, Any> {
        return try {
            val now = System.currentTimeMillis()
            val tz = TimeZone.getDefault()
            mapOf(
                "epoch_ms" to now,
                "iso_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now)),
                "iso_utc" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(now)),
                "timezone_id" to tz.id,
                "timezone_offset_ms" to tz.getOffset(now)
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "current time error"))
        }
    }

    // ===== SET CURRENT TIME (requires system) =====
    fun setCurrentTime(): Map<String, Any> {
        return mapOf(
            "error" to "Setting system time requires SET_TIME permission (system-signature only).",
            "hint" to "Use 'set_timezone' instead; time sync is automatic via NTP."
        )
    }

    // ===== TIMEZONE =====
    fun getTimezone(): Map<String, Any> {
        return try {
            val tz = TimeZone.getDefault()
            val available = TimeZone.getAvailableIDs()
            mapOf(
                "id" to tz.id,
                "display_name" to tz.displayName,
                "short_name" to tz.getDisplayName(false, TimeZone.SHORT),
                "dst_short_name" to tz.getDisplayName(true, TimeZone.SHORT),
                "offset_hours" to (tz.getOffset(System.currentTimeMillis()) / (1000 * 60 * 60)),
                "uses_dst" to tz.useDaylightTime(),
                "available_count" to available.size,
                "sample_ids" to available.take(20)
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "timezone error"))
        }
    }

    // ===== AVAILABLE LOCALES =====
    fun getAvailableLocales(): Map<String, Any> {
        return try {
            val locales = Locale.getAvailableLocales().distinctBy { it.toString() }
            val list = locales.map { loc ->
                mapOf(
                    "language" to loc.language,
                    "country" to loc.country,
                    "variant" to loc.variant,
                    "display_name" to loc.displayName,
                    "display_language" to loc.displayLanguage,
                    "iso3_language" to try { loc.isO3Language } catch (_: Exception) { "" },
                    "iso3_country" to try { loc.isO3Country } catch (_: Exception) { "" },
                    "to_string" to loc.toString()
                )
            }
            mapOf(
                "count" to list.size,
                "current" to mapOf(
                    "language" to Locale.getDefault().language,
                    "country" to Locale.getDefault().country,
                    "display_name" to Locale.getDefault().displayName
                ),
                "locales" to list
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "available locales error"))
        }
    }

    // ===== SETTINGS SHORTCUTS =====
    private fun openActivity(context: Context, action: String, label: String): Map<String, Any> {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            mapOf("status" to "ok", "action" to action, "label" to label)
        } catch (e: Exception) {
            mapOf("error" to "Failed to open $label: ${e.message}")
        }
    }

    fun openSettings(context: Context) = openActivity(context, Settings.ACTION_SETTINGS, "Settings")
    fun openWifiSettings(context: Context) = openActivity(context, Settings.ACTION_WIFI_SETTINGS, "WiFi Settings")
    fun openBluetoothSettings(context: Context) = openActivity(context, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
    fun openLocationSettings(context: Context) = openActivity(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location Settings")
    fun openAppSettings(context: Context) = openActivity(context, Settings.ACTION_APPLICATION_SETTINGS, "App Settings")
    fun openSecuritySettings(context: Context) = openActivity(context, Settings.ACTION_SECURITY_SETTINGS, "Security Settings")
    fun openDeveloperOptions(context: Context) = openActivity(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "Developer Options")
    fun openAccessibilitySettings(context: Context) = openActivity(context, Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Settings")
    fun openNotificationSettings(context: Context) = openActivity(context, android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, "Notification Settings")

    // ===== CALLS =====
    fun answerCall(context: Context): Map<String, Any> {
        // On Android 8+ the only way to answer a call programmatically is
        // via TelecomManager.acceptCall() which requires MODIFY_PHONE_STATE
        // (system-signature only) — not implementable without system perms.
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val state = try {
                tm?.callState
            } catch (_: Exception) { android.telephony.TelephonyManager.CALL_STATE_IDLE }
            if (state != android.telephony.TelephonyManager.CALL_STATE_RINGING) {
                return mapOf("error" to "No incoming call to answer (state=$state)")
            }
            mapOf(
                "error" to "Answering calls requires MODIFY_PHONE_STATE (system-signature) on Android 8+",
                "hint" to "Pre-Android 8: use BluetoothHeadset.actionAnswerCancelledViaBluetooth trick. Modern Android: requires system app."
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "answer_call error"))
        }
    }

    fun endCall(context: Context): Map<String, Any> {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (telecomManager == null) {
                return mapOf("error" to "TelecomManager unavailable")
            }
            // endCall() requires ANSWER_PHONE_CALLS on Android 9+ (or MODIFY_PHONE_STATE pre-9)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val ended = telecomManager.endCall()
                    mapOf("status" to if (ended) "ok" else "failed", "ended" to ended)
                } else {
                    @Suppress("DEPRECATION")
                    telecomManager.endCall()
                    mapOf("status" to "ok", "ended" to true)
                }
            } catch (e: SecurityException) {
                mapOf(
                    "error" to "endCall requires ANSWER_PHONE_CALLS permission (Android 9+)",
                    "hint" to "Add android.permission.ANSWER_PHONE_CALLS to manifest and request runtime grant"
                )
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "end_call error"))
        }
    }

    fun sendUssd(context: Context, params: Map<String, Any>): Map<String, Any> {
        val code = params["code"]?.toString() ?: params["arg"]?.toString() ?: ""
        if (code.isBlank()) return mapOf("error" to "code param required (e.g. *#06# for IMEI)")
        return try {
            // Dial the USSD code (does not require CALL_PHONE for the dialer)
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.fromParts("tel", code, "#")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            mapOf("status" to "ok", "code" to code, "message" to "Opened dialer with USSD code (user must press call)")
        } catch (e: Exception) {
            mapOf("error" to "USSD dial failed: ${e.message}")
        }
    }

    // ===== SMS =====
    fun sendSmsTo(context: Context, params: Map<String, Any>): Map<String, Any> {
        val number = params["number"]?.toString() ?: params["arg"]?.toString() ?: ""
        val message = params["message"]?.toString() ?: params["text"]?.toString() ?: ""
        if (number.isBlank()) return mapOf("error" to "number param required")
        if (message.isBlank()) return mapOf("error" to "message param required")
        return try {
            val sm = context.getSystemService("sms") as? android.telephony.SmsManager
                ?: return mapOf("error" to "SmsManager unavailable")
            val parts = sm.divideMessage(message)
            if (parts.size == 1) {
                sm.sendTextMessage(number, null, message, null, null)
            } else {
                sm.sendMultipartTextMessage(number, null, parts, null, null)
            }
            mapOf("status" to "ok", "number" to number, "message_length" to message.length, "parts" to parts.size)
        } catch (e: SecurityException) {
            mapOf("error" to "SEND_SMS permission required: ${e.message}")
        } catch (e: Exception) {
            mapOf("error" to "send_sms_to failed: ${e.message}")
        }
    }

    fun sendSmsBroadcast(context: Context, params: Map<String, Any>): Map<String, Any> {
        val numbersParam = params["numbers"]
        val numbers: List<String> = when (numbersParam) {
            is List<*> -> numbersParam.mapNotNull { it?.toString() }
            is String -> numbersParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
        val message = params["message"]?.toString() ?: params["text"]?.toString() ?: ""
        if (numbers.isEmpty()) return mapOf("error" to "numbers param required (array or comma-separated string)")
        if (message.isBlank()) return mapOf("error" to "message param required")
        if (numbers.size > 50) return mapOf("error" to "Max 50 recipients per broadcast")
        return try {
            val sm = context.getSystemService("sms") as? android.telephony.SmsManager
                ?: return mapOf("error" to "SmsManager unavailable")
            var sent = 0
            val failed = mutableListOf<String>()
            for (num in numbers) {
                try {
                    sm.sendTextMessage(num, null, message, null, null)
                    sent++
                } catch (e: Exception) {
                    Log.w(TAG, "SMS to $num failed: ${e.message}")
                    failed.add(num)
                }
            }
            mapOf(
                "status" to if (sent > 0) "partial" else "failed",
                "sent_count" to sent,
                "failed_count" to failed.size,
                "total" to numbers.size,
                "failed_numbers" to failed
            )
        } catch (e: SecurityException) {
            mapOf("error" to "SEND_SMS permission required: ${e.message}")
        }
    }

    // ===== NOTIFICATIONS =====
    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(1000)

    fun postNotification(context: Context, params: Map<String, Any>): Map<String, Any> {
        val title = params["title"]?.toString() ?: params["arg"]?.toString() ?: "Abu-Zahra"
        val text = params["text"]?.toString() ?: params["message"]?.toString() ?: ""
        val id = (params["id"] as? Number)?.toInt() ?: notificationIdCounter.incrementAndGet()
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return mapOf("error" to "NotificationManager unavailable")
            // Ensure channel exists (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "abu_zahra_remote",
                    "Abu-Zahra Remote",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
            }
            val builder = androidx.core.app.NotificationCompat.Builder(context, "abu_zahra_remote")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
            nm.notify(id, builder.build())
            mapOf("status" to "ok", "id" to id, "title" to title)
        } catch (e: Exception) {
            mapOf("error" to "post_notification failed: ${e.message}")
        }
    }

    fun cancelNotification(context: Context, params: Map<String, Any>): Map<String, Any> {
        val id = (params["id"] as? Number)?.toInt()
            ?: params["arg"]?.toString()?.toIntOrNull()
            ?: return mapOf("error" to "id param required (notification id)")
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return mapOf("error" to "NotificationManager unavailable")
            nm.cancel(id)
            mapOf("status" to "ok", "cancelled_id" to id)
        } catch (e: Exception) {
            mapOf("error" to "cancel_notification failed: ${e.message}")
        }
    }

    fun cancelAllNotifications(context: Context): Map<String, Any> {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return mapOf("error" to "NotificationManager unavailable")
            nm.cancelAll()
            mapOf("status" to "ok", "message" to "All notifications posted by this app cancelled (cannot cancel other apps' notifications)")
        } catch (e: Exception) {
            mapOf("error" to "cancel_all_notifications failed: ${e.message}")
        }
    }

    // ===== MEDIA PLAYBACK (uses AudioManager dispatch) =====
    fun playMedia(context: Context): Map<String, Any> {
        return dispatchMediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
    }
    fun pauseMedia(context: Context): Map<String, Any> {
        return dispatchMediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
    }
    fun stopMedia(context: Context): Map<String, Any> {
        return dispatchMediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_STOP)
    }
    fun nextTrack(context: Context): Map<String, Any> {
        return dispatchMediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
    }
    fun previousTrack(context: Context): Map<String, Any> {
        return dispatchMediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun dispatchMediaKey(context: Context, keycode: Int): Map<String, Any> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return mapOf("error" to "AudioManager unavailable")
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keycode))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keycode))
            mapOf("status" to "ok", "keycode" to keycode)
        } catch (e: Exception) {
            mapOf("error" to "Media key dispatch failed: ${e.message}")
        }
    }

    fun setMediaVolume(context: Context, params: Map<String, Any>): Map<String, Any> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return mapOf("error" to "AudioManager unavailable")
            val vol = (params["volume"] as? Number)?.toInt()
                ?: params["arg"]?.toString()?.toIntOrNull()
                ?: return mapOf("error" to "volume param required (0-${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)})")
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val clamped = vol.coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
            mapOf("status" to "ok", "volume" to clamped, "max" to max, "current" to am.getStreamVolume(AudioManager.STREAM_MUSIC))
        } catch (e: Exception) {
            mapOf("error" to "set_media_volume failed: ${e.message}")
        }
    }

    fun getNowPlaying(): Map<String, Any> {
        // Reading the active media session requires a NotificationListenerService
        // (android.permission.BIND_NOTIFICATION_LISTENER_SERVICE). We return a
        // friendly hint instead of crashing.
        return mapOf(
            "error" to "Reading the currently-playing track requires an active MediaSessionManager + NotificationListenerService.",
            "hint" to "Enable ${"notification listener"} in Settings > Notification access. Then we can use MediaSessionManager to read the active session's metadata."
        )
    }
}
