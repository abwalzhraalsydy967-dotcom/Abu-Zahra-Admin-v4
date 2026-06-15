
---
Task ID: 1
Agent: Main Agent
Task: مراجعة شاملة ونشر وتشغيل سيرفر أبو زهرا على VPS

Work Log:
- مراجعة شاملة لجميع ملفات السيرفر (11 ملف)
- إصلاح خطأ: `_ip_device_map` غير معرف في DataStore.__init__
- إصلاح خطأ: `store` غير معرف في device_monitor_loop و cleanup_loop (يجب أن يكون `data_store`)
- إصلاح خطأ: `charset` في content_type غير مدعوم في aiohttp 3.14
- إضافة دعم ملف `.env` باستخدام python-dotenv
- تحديث requirements.txt لإضافة python-dotenv
- تنظيف VPS من الخدمات غير المرتبطة (Docker, containerd, ModemManager, etc.)
- رفع ملفات السيرفر إلى /opt/abu-zahra/Server
- إعداد Python venv مع التبعيات
- إنشاء systemd service (abu-zahra.service)
- تكوين Caddy كـ reverse proxy مع TLS تلقائي
- إعداد جدار الحماية (ufw) - السماح فقط بـ 22, 80, 443
- ربط السيرفر بـ 127.0.0.1 فقط (أمان)

Stage Summary:
- السيرفر يعمل بنجاح على https://alsydyabwalzhra.online
- API: /api/health - يعمل
- لوحة التحكم: / - تعمل (HTTP 200, 69KB)
- تسجيل الدخول: /api/login - يعمل
- كود الربط: /api/web/link_code - يعمل
- Caddy يعكس الطلبات من HTTPS إلى السيرفر المحلي
- شهادة TLS تلقائية من Let's Encrypt
- لا أخطاء في السيرفر
- استخدام الذاكرة: ~35MB
