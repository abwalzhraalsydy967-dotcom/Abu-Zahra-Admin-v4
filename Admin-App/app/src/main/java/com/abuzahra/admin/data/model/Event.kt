package com.abuzahra.admin.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Event(
    @SerializedName("id") val id: String = "",
    @SerializedName("time") val time: String? = null,
    @SerializedName("event") val event: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("level") val level: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("details") val details: String? = null,
    @SerializedName("device_name") val deviceName: String = "",
    @SerializedName("created_at") val createdAt: String? = null
) {

    val timestamp: String
        get() = time ?: createdAt ?: ""

    val displayEvent: String
        get() {
            // Return a user-friendly Arabic event description
            return when {
                event.contains("device_online", ignoreCase = true) -> "الجهاز متصل"
                event.contains("device_offline", ignoreCase = true) -> "الجهاز غير متصل"
                event.contains("battery_low", ignoreCase = true) -> "بطارية منخفضة"
                event.contains("command_sent", ignoreCase = true) -> "تم إرسال أمر"
                event.contains("command_completed", ignoreCase = true) -> "تم تنفيذ أمر"
                event.contains("command_failed", ignoreCase = true) -> "فشل تنفيذ أمر"
                event.contains("screenshot", ignoreCase = true) -> "لقطة شاشة"
                event.contains("location", ignoreCase = true) -> "تحديد موقع"
                event.contains("file_uploaded", ignoreCase = true) -> "تم رفع ملف"
                event.contains("file_downloaded", ignoreCase = true) -> "تم تحميل ملف"
                event.contains("login", ignoreCase = true) -> "تسجيل دخول"
                event.contains("error", ignoreCase = true) -> "خطأ"
                event.contains("new_device", ignoreCase = true) -> "جهاز جديد"
                else -> event
            }
        }

    val eventTypeCategory: String
        get() {
            return when {
                event.contains("online", ignoreCase = true) ||
                    event.contains("offline", ignoreCase = true) ||
                    event.contains("connect", ignoreCase = true) ||
                    event.contains("disconnect", ignoreCase = true) -> "اتصال"

                event.contains("command", ignoreCase = true) ||
                    event.contains("screenshot", ignoreCase = true) ||
                    event.contains("location", ignoreCase = true) -> "أوامر"

                else -> "تنبيهات"
            }
        }

    val relativeTime: String
        get() {
            val timeStr = time ?: createdAt ?: return ""
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(timeStr) ?: return timeStr
                val now = System.currentTimeMillis()
                val diff = now - date.time
                when {
                    diff < TimeUnit.MINUTES.toMillis(1) -> "الآن"
                    diff < TimeUnit.HOURS.toMillis(1) -> "منذ ${TimeUnit.MILLISECONDS.toMinutes(diff)} دقيقة"
                    diff < TimeUnit.DAYS.toMillis(1) -> "منذ ${TimeUnit.MILLISECONDS.toHours(diff)} ساعة"
                    diff < TimeUnit.DAYS.toMillis(7) -> "منذ ${TimeUnit.MILLISECONDS.toDays(diff)} يوم"
                    else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                timeStr
            }
        }
}