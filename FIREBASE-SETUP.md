# 📘 دليل إعداد Firebase Console الشامل — مشروع أبو زهرة

> **المستند:** دليل خطوة بخطوة لإعداد مشروع Firebase وربطه بالخادم وتطبيقات Android ولوحة التحكم Web.
> **المشروع الحالي:** `abwalzhraalsydy-62ccf`
> **نطاق الخادم:** `https://alsydyabwalzhra.online`
> **الإصدار:** 4.0.0
> **التاريخ:** 2025

---

## 📑 فهرس المحتويات

1. [نظرة عامة على البنية والمفاتيح المطلوبة](#1-نظرة-عامة-على-البنية-والمفاتيح-المطلوبة)
2. [مفاتيح Firebase المطلوبة وموقع كل منها](#2-مفاتيح-firebase-المطلوبة-وموقع-كل-منها)
3. [إعداد Firebase Console خطوة بخطوة](#3-إعداد-firebase-console-خطوة-بخطوة)
4. [قواعد أمان Firebase RTDB (Security Rules)](#4-قواعد-أمان-firebase-rtdb-security-rules)
5. [ملفات .env المطلوبة للخادم ولوحة التحكم](#5-ملفات-env-المطلوبة-للخادم-ولوحة-التحكم)
6. [خطوات التحقق (Verification)](#6-خطوات-التحقق-verification)
7. [استكشاف الأخطاء وإصلاحها (Troubleshooting)](#7-استكشاف-الأخطاء-وإصلاحها-troubleshooting)
8. [قائمة التحقق النهائية (Checklist)](#8-قائمة-التحقق-النهائية-checklist)

---

## 1. نظرة عامة على البنية والمفاتيح المطلوبة

### 1.1 معمارية المشروع

```
┌─────────────────────────────────────────────────────────────────┐
│                      Firebase Console                            │
│  Project: abwalzhraalsydy-62ccf                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐   │
│  │ Authentication │  │ Realtime DB    │  │ Storage (unused) │   │
│  │ Email/Password │  │ (RTDB)         │  │ — فقط نص لا ملفات│   │
│  │ Google Sign-In │  │                │  └──────────────────┘   │
│  └────────┬───────┘  └────────┬───────┘                          │
└───────────┼───────────────────┼──────────────────────────────────┘
            │                   │
            │ auth.idToken      │ REST API (DB Secret)
            │                   │
   ┌────────▼────────┐  ┌───────▼──────────────────┐
   │   Web Dashboard │  │   Abu-Zahra Server       │
   │   (Next.js)     │  │   (Python aiohttp)       │
   │                 │  │                          │
   │ Firebase Client │◄─┤ وسيط التحقق والكتابة     │
   │ SDK (Web)       │  │ firebase_client.py       │
   │                 │  │ RTDB = نص فقط            │
   │ Admin SDK       │  │ Files = التخزين المؤقت   │
   │ (verify tokens) │  │ على السيرفر              │
   └────────┬────────┘  └────────┬─────────────────┘
            │                    │
            │ REST API           │ REST API + Firebase RTDB
            │                    │
   ┌────────▼────────────────────▼─────────┐
   │        تطبيقات Android                 │
   │  ┌────────────────┐ ┌──────────────┐  │
   │  │ Admin-App      │ │ Android-App  │  │
   │  │ com.abuzahra.  │ │ com.abuzahra.│  │
   │  │ admin          │ │ manager      │  │
   │  └────────────────┘ └──────────────┘  │
   └────────────────────────────────────────┘
```

### 1.2 مسارات RTDB المستخدمة في المشروع

تم استخراج هذه المسارات من `Server/modules/firebase_client.py`:

| المسار | الوصف | الكاتب |
|--------|------|--------|
| `sms/$device_id` | رسائل SMS | السيرفر (وسيط) |
| `contacts/$device_id` | جهات الاتصال | السيرفر |
| `calls/$device_id` | سجل المكالمات | السيرفر |
| `notifications/$device_id` | الإشعارات | السيرفر |
| `device_info/$device_id` | معلومات الجهاز | السيرفر |
| `logs/$device_id` | سجلات الجهاز | السيرفر |
| `location/$device_id` | الموقع الجغرافي | السيرفر |
| `commands/$device_id/$command_id` | أوامر من السيرفر للجهاز | السيرفر |
| `results/$device_id/$command_id` | نتائج تنفيذ الأوامر | السيرفر |
| `permanent_codes/$safe_email` | الرمز الدائم لكل بريد | السيرفر |
| `code_to_email/$code` | عكس الرمز إلى بريد | السيرفر |
| `link_codes/$safe_code` | أكواد الربط المؤقتة (قديمة) | السيرفر |

> **ملاحظة مهمة:** كل الكتابة تتم بواسطة السيرفر باستخدام **Database Secret**. الأجهزة لا تكتب مباشرة في Firebase — السيرفر هو الوسيط المسؤول عن العزل بين المستخدمين (per-user isolation).

---

## 2. مفاتيح Firebase المطلوبة وموقع كل منها

### 2.1 جدول المفاتيح الكامل

| # | المفتاح | القيمة الحالية (للمشروع) | مكان الحصول عليه في Firebase Console |
|---|---------|--------------------------|--------------------------------------|
| 1 | **Project ID** | `abwalzhraalsydy-62ccf` | Project Settings → General → Project ID |
| 2 | **Web API Key** | `AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk` | Project Settings → General → Web API Key |
| 3 | **Auth Domain** | `abwalzhraalsydy-62ccf.firebaseapp.com` | Project Settings → General → Auth Domain |
| 4 | **Database URL (RTDB)** | `https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com` | Project Settings → General → Realtime Database URL |
| 5 | **Storage Bucket** | `abwalzhraalsydy-62ccf.firebasestorage.app` | Project Settings → General → Storage Bucket |
| 6 | **Messaging Sender ID** | `159319780620` | Project Settings → General → Cloud Messaging → Sender ID |
| 7 | **Web App ID** | `1:159319780620:web:ec599a59dfefd278f52d29` | Project Settings → General → Your apps → Web app |
| 8 | **Android Admin App ID** | `1:159319780620:android:680395e3035cac75f52d29` | Project Settings → Your apps → Admin-App |
| 9 | **Android Manager App ID** | `1:159319780620:android:8876fc8e03865b3bf52d29` | Project Settings → Your apps → Android-App |
| 10 | **Web Client ID (OAuth 2.0)** | `159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com` | Project Settings → General → Your apps → Web app → OAuth 2.0 Client ID |
| 11 | **Database Secret** | `<مفقود — يجب إنشاؤه>` | Project Settings → Service Accounts → Database Secrets → Add Secret |
| 12 | **Service Account JSON** | `<مفقود — يجب تنزيله>` | Project Settings → Service Accounts → Generate new private key |

### 2.2 أماكن استخدام كل مفتاح داخل المشروع

| المفتاح | يُستخدم في | الملف |
|---------|-----------|------|
| `FIREBASE_PROJECT` | الخادم | `Server/modules/config.py:37` |
| `FIREBASE_RTDB_URL` | الخادم (يُشتق تلقائياً) | `Server/modules/config.py:38` |
| `FIREBASE_DB_SECRET` | الخادم (للـ REST API على RTDB) | `Server/modules/config.py:39` |
| `FIREBASE_WEB_API_KEY` | الخادم (لتحقق ID Token) | `Server/modules/config.py:42` |
| `NEXT_PUBLIC_FIREBASE_*` | لوحة التحكم Web | `src/lib/firebase.ts:7-12` |
| `firebase-admin-sdk.json` | لوحة التحكم Web (Admin SDK) | `src/lib/firebase-admin.ts:23` |
| `google-services.json` | تطبيق Android | `Android-App/app/` و `Admin-App/app/` |

### 2.3 المفاتيح المفقودة حالياً ✋

القيمتان التاليتان **غير موجودتين** ويجب إنشاؤهما من Firebase Console قبل تشغيل النظام:

1. **`FIREBASE_DB_SECRET`** — يُستخدم للوصول الكامل لـ RTDB عبر REST API.
2. **`firebase-admin-sdk.json`** — يُستخدم للوصول إلى Firebase Auth (إنشاء مستخدمين، التحقق من ID Tokens، إلخ) من لوحة التحكم Web.

> ⚠️ **بدون هذين المفتاحين:**
> - الخادم لن يستطيع الكتابة في RTDB → لن تظهر بيانات الأجهزة (SMS، جهات الاتصال، إلخ).
> - لوحة التحكم Web لن تستطيع التحقق من رموز Firebase Auth → Google Sign-In سيفشل.

---

## 3. إعداد Firebase Console خطوة بخطوة

### 3.1 الوصول إلى Firebase Console

1. افتح المتصفح على: **https://console.firebase.google.com/**
2. سجّل الدخول بحساب Google (يُفضّل حساب مخصص للإدارة).
3. يجب أن تجد المشروع الحالي: **abwalzhraalsydy-62ccf** في قائمة المشاريع.

> 📸 **وصف لقطة الشاشة المتوقعة:** قائمة المشاريع تعرض بطاقة بالاسم `abwalzhraalsydy-62ccf` مع زر "Go to console".

---

### 3.2 إنشاء مشروع Firebase جديد (فقط إذا لم يكن موجوداً)

> ⚠️ **تخطَّ هذه الخطوة** إذا كان المشروع `abwalzhraalsydy-62ccf` موجوداً بالفعل.

1. اضغط **"Add project"** في صفحة Firebase الرئيسية.
2. اسم المشروع: `abwalzhraalsydy-62ccf` (أو أي اسم تريده، لكن يجب أن يطابق `FIREBASE_PROJECT` في `config.py`).
3. اضغط **Continue**.
4. (اختياري) فعّل Google Analytics — **غير مطلوب** للمشروع.
5. اضغط **Create project**.
6. انتظر حتى يكتمل الإنشاء، ثم اضغط **Continue**.

---

### 3.3 إضافة تطبيقات إلى المشروع

في صفحة المشروع الرئيسية، اضغط أيقونة **⚙️ Project Settings** (أعلى يسار) ثم انتقل إلى تبويب **General**. في قسم **"Your apps"** يجب إضافة 3 تطبيقات:

#### 3.3.1 إضافة تطبيق Web (للوحة التحكم)

1. في صفحة Project Overview، اضغط أيقونة **`</>` (Web)**.
2. **App nickname:** `Abu-Zahra Web Dashboard`
3. **App ID:** سيُنشأ تلقائياً (مثال: `1:159319780620:web:ec599a59dfefd278f52d29`).
4. اضغط **Register app**.
5. سيُعرض لك كود `firebaseConfig` يحتوي على كل المفاتيح — احفظها في ملف آمن.
6. اضغط **Next** ثم **Continue to console**.

> 📸 **وصف لقطة الشاشة المتوقعة:** نافذة `Add app` بثلاث خيارات (iOS/Android/Web) مع تحديد أيقونة `</>` باللون البرتقالي.

#### 3.3.2 إضافة تطبيق Android — Admin-App (للإدارة)

1. اضغط أيقونة **Android** (🤖).
2. **Android package name:** `com.abuzahra.admin`
3. **App nickname:** `Admin-App`
4. **SHA-1 certificate fingerprint:** `0A:27:6F:32:73:15:92:AF:77:06:03:F8:4C:81:48:00:45:75:F4:D4`
5. (اختياري) **SHA-256:** يمكن إضافته لاحقاً.
6. اضغط **Register app**.
7. نزّل ملف `google-services.json` وضعه في:
   ```
   Admin-App/app/google-services.json
   ```
8. اضغط **Next** حتى النهاية.

> 📸 **وصف لقطة الشاشة المتوقعة:** نموذج يتطلب `Android package name` (مع تلميح أحمر إن كان الحزمة غير صالحة) وحقل `SHA-1` وزر `Register app` باللون الأزرق.

#### 3.3.3 إضافة تطبيق Android — Android-App (للعميل)

1. اضغط أيقونة **Android** مرة أخرى.
2. **Android package name:** `com.abuzahra.manager`
3. **App nickname:** `Android-App`
4. **SHA-1 certificate fingerprint:** يجب استخراجه من `release.keystore` الخاص بالـ Android-App (مختلف عن Admin-App).
5. اضغط **Register app**.
6. نزّل ملف `google-services.json` وضعه في:
   ```
   Android-App/app/google-services.json
   ```
7. اضغط **Next** حتى النهاية.

#### 3.3.4 استخراج SHA-1 من keystore

```bash
# للـ Admin-App
keytool -list -v -keystore Admin-App/release.keystore -alias release -storepass <password> | grep SHA1

# للـ Android-App
keytool -list -v -keystore Android-App/release.keystore -alias release -storepass <password> | grep SHA1
```

القيمة المعروفة حالياً للـ Admin-App: `0A:27:6F:32:73:15:92:AF:77:06:03:F8:4C:81:48:00:45:75:F4:D4`

> ⚠️ **مهم:** بدون SHA-1 صحيح، Google Sign-In سيفشل بالخطأ `DEVELOPER_ERROR (10)` أو `Sign-in failed (12500)`.

---

### 3.4 تفعيل Authentication

1. من القائمة الجانبية في Firebase Console، اضغط **Build → Authentication**.
2. اضغط **Get Started**.
3. في تبويب **Sign-in method**، فعّل ما يلي:

#### 3.4.1 تفعيل Email/Password

1. اضغط على **Email/Password**.
2. بدّل المفتاح إلى **Enable**.
3. اضغط **Save**.

#### 3.4.2 تفعيل Google Sign-In

1. اضغط على **Google**.
2. بدّل المفتاح إلى **Enable**.
3. **Project public-facing name:** `Abu-Zahra`
4. **Project support email:** اختر بريدك الإداري.
5. اضغط **Save**.
6. سيتم إنشاء **Web Client ID (OAuth 2.0)** تلقائياً — احفظه:
   ```
   159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com
   ```

> 📸 **وصف لقطة الشاشة المتوقعة:** قائمة Sign-in providers مع رمز ✓ أخضر بجانب Email/Password و Google.

---

### 3.5 إنشاء Realtime Database (RTDB)

1. من القائمة الجانبية، اضغط **Build → Realtime Database**.
2. اضغط **Create Database**.
3. **Database location:** اختر الأقرب لمستخدميك (مثلاً `us-central1`).
4. **Security rules:** اختر **Test mode** (للاختبار فقط — سنستبدلها بقواعد صارمة لاحقاً).
5. اضغط **Enable**.

> 📸 **وصف لقطة الشاشة المتوقعة:** نافذة اختيار الموقع الجغرافي مع تحذير أحمر عن Test mode.

#### التحقق من Database URL

في **Project Settings → General**، انسخ حقل **Realtime Database URL**:
```
https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com
```

> ⚠️ يجب أن يطابق النمط في `config.py:38`:
> ```python
> FIREBASE_RTDB_URL = f"https://{FIREBASE_PROJECT}-default-rtdb.firebaseio.com"
> ```

---

### 3.6 ضبط RTDB Security Rules

1. في صفحة Realtime Database، اضغط تبويب **Rules**.
2. استبدل المحتوى بالكامل بالقواعد التالية:

```json
{
  "rules": {
    "commands": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "results": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "sms": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "contacts": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "calls": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "notifications": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "location": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "device_info": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "logs": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "permanent_codes": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "code_to_email": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "link_codes": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

3. اضغط **Publish**.

> 📸 **وصف لقطة الشاشة المتوقعة:** محرر JSON مع زر **Publish** باللون الأزرق في الأعلى، وزر **Preview** بجانبه.

#### ملاحظة أمنية مهمة 🔐

القواعد أعلاه تسمح لأي مستخدم مصدَّق بالوصول إلى كل البيانات. **السبب:** السيرفر هو الوسيط المسؤول عن العزل بين المستخدمين — يستخدم `FIREBASE_DB_SECRET` الذي يتجاوز قواعد الأمان تماماً. العزل الفعلي بين المستخدمين يتم في كود السيرفر (`Server/modules/store.py`) وليس في قواعد RTDB.

**للإنتاج الصارم (production strict):** يجب ربط كل مسار `$device_id` بـ `auth.uid` الخاص بمالك الجهاز. لكن هذا يتطلب تخزين علاقة `device_id → owner_uid` في RTDB، وهي ميزة مستقبلية.

---

### 3.7 تنزيل Service Account JSON (Admin SDK)

> هذا الملف **مفقود حالياً** ويجب تنزيله لتفعيل `firebase-admin.ts` في لوحة التحكم Web.

1. اذهب إلى **Project Settings → Service Accounts**.
2. اضغط **Generate new private key**.
3. اضغط **Generate key** في نافذة التحذير.
4. سيُنزّل متصفحك ملفاً باسم مثل `abwalzhraalsydy-62ccf-firebase-adminsdk-xxxxx.json`.
5. أعد تسميته إلى:
   ```
   firebase-admin-sdk.json
   ```
6. ضعه في المسار التالي داخل مشروع لوحة التحكم Web:
   ```
   src/credentials/firebase-admin-sdk.json
   ```
   (المسار الذي يبحث فيه `firebase-admin.ts:23`.)

> ⚠️ **لا ترفع هذا الملف إلى Git.** أضفه إلى `.gitignore`:
> ```
> credentials/firebase-admin-sdk.json
> ```

> 📸 **وصف لقطة الشاشة المتوقعة:** تبويب Service Accounts مع زر **Generate new private key** باللون الأزرق ونافذة تحذير صفراء.

---

### 3.8 إنشاء Database Secret

> هذا المفتاح **مفقود حالياً** ويجب إنشاؤه للوصول الكامل إلى RTDB من الخادم.

#### الطريقة 1 — من Firebase Console (مفضّلة):

1. اذهب إلى **Project Settings → Service Accounts**.
2. اضغط **Database Secrets** في الأعلى (تبويب فرعي).
3. اضغط **Add Secret** (أو **Show** إذا كان موجوداً).
4. سيُعرض لك مفتاح طويل (40 حرفاً تقريباً).
5. انسخه فوراً — **لن يُعرض مرة أخرى**.
6. ضعه في `Server/.env`:
   ```
   FIREBASE_DB_SECRET=<الصق-هنا-المفتاح-الطويل>
   ```

#### الطريقة 2 — من Google Cloud Console (بديل):

1. اذهب إلى: https://console.cloud.google.com/apis/credentials
2. اختر مشروع Firebase.
3. ابحث عن **API Key** باسم "Database Secret" أو "Firebase API Key".
4. (الطريقة 1 أبسط وأكثر أماناً.)

> 📸 **وصف لقطة الشاشة المتوقعة:** قائمة بمفاتيح Database Secret مع زر **Add Secret** ورمز عين 👁 لعرض/إخفاء المفتاح.

---

### 3.9 تفعيل Storage (اختياري — غير مستخدم)

> المشروع **لا يستخدم Firebase Storage** للملفات. الملفات (لقطات الشاشة، تسجيلات الكاميرا) تُخزَّن مؤقتاً على السيرفر عبر `Server/data/uploads/` وتُحذف تلقائياً بعد ساعة (`FILE_TEMP_EXPIRE_SECONDS = 3600`).
>
> **Firebase يُستخدم فقط للبيانات النصية** (SMS، جهات الاتصال، المكالمات، الإشعارات، الموقع، معلومات الجهاز، الأكواد).

**إن أردت تفعيله مستقبلاً:**

1. اضغط **Build → Storage**.
2. اضغط **Get Started**.
3. اختر الموقع الجغرافي (يُفضّل نفس موقع RTDB).
4. اضغط **Done**.
5. (لا حاجة لتعديل Storage Rules — المشروع لا يكتب فيه).

---

## 4. قواعد أمان Firebase RTDB (Security Rules)

### 4.1 القواعد الموصى بها (موضوعة في 3.6)

```json
{
  "rules": {
    "commands":      { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "results":       { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "sms":           { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "contacts":      { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "calls":         { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "notifications": { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "location":      { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "device_info":   { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "logs":          { "$device_id": { ".read": "auth != null", ".write": "auth != null" } },
    "permanent_codes": { ".read": "auth != null", ".write": "auth != null" },
    "code_to_email":   { ".read": "auth != null", ".write": "auth != null" },
    "link_codes":    { "$code": { ".read": "auth != null", ".write": "auth != null" } }
  }
}
```

### 4.2 شرح منطق العزل بين المستخدمين

```
┌──────────────────────────────────────────────────────────────┐
│ المستخدم A يملك جهاز #1                                       │
│   │                                                           │
│   ▼                                                           │
│ Web Dashboard (Next.js)                                       │
│   │ - يسجّل الدخول عبر Firebase Auth → يحصل على ID Token      │
│   │ - يرسل ID Token إلى السيرفر في كل طلب                     │
│   ▼                                                           │
│ Abu-Zahra Server                                              │
│   │ - يتحقق من ID Token عبر identitytoolkit REST API         │
│   │ - يحوّل ID Token إلى user_id داخلي                        │
│   │ - يستعلم عن أجهزة المستخدم A فقط:                        │
│   │     store.devices = {device_id_1: {owner_id: A, ...}}    │
│   │ - يكتب إلى RTDB باستخدام FIREBASE_DB_SECRET:             │
│   │     sms/device_id_1/ ← فقط جهاز المستخدم A                │
│   ▼                                                           │
│ Firebase RTDB (يستقبل الكتابة بصلاحية الـ Secret)             │
└──────────────────────────────────────────────────────────────┘
```

### 4.3 لماذا `auth != null` يكفي؟

- **السيرفر** يستخدم `FIREBASE_DB_SECRET` في كل طلب REST API (`firebase_client.py:32`) — الـ Secret يتجاوز قواعد RTDB تماماً ويعامَل كـ "admin".
- **لوحة التحكم Web** لا تقرأ/تكتب RTDB مباشرةً — كل العمليات تمر عبر السيرفر.
- **تطبيقات Android** لا تتصل بـ RTDB مباشرةً — كل البيانات تمر عبر REST API للسيرفر.
- لذلك القاعدة `auth != null` تكفي **لمنع الوصول العام**، بينما يبقى السيرفر مسؤولاً عن العزل الحقيقي بين المستخدمين.

### 4.4 قواعد RTDB المتقدمة (اختياري للإنتاج)

عند تطبيق ميزة "device ownership" مستقبلاً، يمكن استخدام قواعد مثل:

```json
{
  "rules": {
    "sms": {
      "$device_id": {
        ".read":  "auth != null && root.child('device_owners/' + $device_id).val() === auth.uid",
        ".write": "auth != null && root.child('device_owners/' + $device_id).val() === auth.uid"
      }
    }
  }
}
```

(هذا يتطلب إضافة مسار `device_owners/$device_id = $uid` في RTDB عند تسجيل كل جهاز.)

---

## 5. ملفات .env المطلوبة للخادم ولوحة التحكم

### 5.1 ملف `Server/.env` (للخادم)

أنشئ ملف `Server/.env` (إن لم يكن موجوداً) بالمحتوى التالي:

```env
# ─── Server Config ─────────────────────────────────────────
SERVER_HOST=0.0.0.0
SERVER_PORT=8080

# ─── Admin Credentials ─────────────────────────────────────
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<strong-password>
ADMIN_EMAIL=admin@abuzahra.com

# ─── Telegram Bot ──────────────────────────────────────────
BOT_TOKEN=<telegram-bot-token>
ADMIN_CHAT_ID=<telegram-chat-id>

# ─── Firebase Config ───────────────────────────────────────
FIREBASE_PROJECT=abwalzhraalsydy-62ccf
FIREBASE_DB_SECRET=<from-firebase-console>
FIREBASE_WEB_API_KEY=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk
```

> 📌 `FIREBASE_RTDB_URL` يُشتق تلقائياً في `config.py:38` من `FIREBASE_PROJECT`، فلا حاجة لإضافته.

#### القيم التي يجب استبدالها:

| المتغير | كيف تحصل عليه |
|---------|---------------|
| `ADMIN_PASSWORD` | اختر كلمة مرور قوية (16+ حرفاً، أحرف كبيرة/صغيرة + أرقام + رموز). |
| `BOT_TOKEN` | من BotFather في Telegram — `@BotFather` → `/newbot`. |
| `ADMIN_CHAT_ID` | أرسل `/start` للبوت، ثم افتح `https://api.telegram.org/bot<BOT_TOKEN>/getUpdates`. |
| `FIREBASE_DB_SECRET` | من Firebase Console → Project Settings → Service Accounts → Database Secrets. |

---

### 5.2 ملف `.env.local` (للوحة التحكم Web — Next.js)

أنشئ ملف `.env.local` في جذر مشروع Next.js:

```env
# ─── Server URL ────────────────────────────────────────────
NEXT_PUBLIC_SERVER_URL=https://alsydyabwalzhra.online

# ─── Firebase Client SDK (Web) ─────────────────────────────
NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=abwalzhraalsydy-62ccf.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=abwalzhraalsydy-62ccf
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=abwalzhraalsydy-62ccf.firebasestorage.app
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=159319780620
NEXT_PUBLIC_FIREBASE_APP_ID=1:159319780620:web:ec599a59dfefd278f52d29

# ─── Web OAuth Client ID (Google Sign-In) ──────────────────
NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID=159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com
```

> 📌 جميع مفاتيح `NEXT_PUBLIC_*` تكون ظاهرة في المتصفح (client-side). هذا طبيعي لـ Firebase — الأمان يأتي من قواعد RTDB و Auth، ليس من إخفاء المفاتيح.

---

### 5.3 ملف Service Account JSON (للوحة التحكم Web — Admin SDK)

المسار المطلوب:
```
src/credentials/firebase-admin-sdk.json
```

محتوى الملف (تنسيق JSON من Firebase Console بعد الضغط على "Generate new private key"):

```json
{
  "type": "service_account",
  "project_id": "abwalzhraalsydy-62ccf",
  "private_key_id": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-xxxxx@abwalzhraalsydy-62ccf.iam.gserviceaccount.com",
  "client_id": "xxxxxxxxxxxxx",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-xxxxx%40abwalzhraalsydy-62ccf.iam.gserviceaccount.com"
}
```

> ⚠️ **مهم جداً:** لا ترفع هذا الملف إلى Git. أضف السطر التالي إلى `.gitignore`:
> ```
> credentials/firebase-admin-sdk.json
> *.env.local
> ```

---

### 5.4 ملف `google-services.json` (لتطبيقات Android)

كل تطبيق Android لديه ملف `google-services.json` خاص به، موجود بالفعل في المستودع:

| التطبيق | المسار | package_name |
|---------|--------|--------------|
| Admin-App | `Admin-App/app/google-services.json` | `com.abuzahra.admin` |
| Android-App | `Android-App/app/google-services.json` | `com.abuzahra.manager` |

> ✅ **لا حاجة لإعادة تنزيلها** — الملفات موجودة وصحيحة. تحتوي على:
> - `project_number`: `159319780620`
> - `project_id`: `abwalzhraalsydy-62ccf`
> - `storage_bucket`: `abwalzhraalsydy-62ccf.firebasestorage.app`
> - `current_key` (API Key): `AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk`
> - SHA-1 للـ Admin-App (لا يوجد للـ Android-App بعد — يجب إضافته).

---

## 6. خطوات التحقق (Verification)

### 6.1 التحقق من اتصال Firebase من الخادم

#### عبر السجل (Server Logs):

عند تشغيل الخادم، ابحث عن:
```
[INFO] firebase: Firebase connected
```

إذا ظهر:
```
[WARNING] firebase: Firebase check returned 401
```
→ يعني أن `FIREBASE_DB_SECRET` غير صحيح أو مفقود.

#### عبر اختبار cURL مباشر:

```bash
# اختبر الوصول إلى RTDB بالـ Secret
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/.json?auth=<FIREBASE_DB_SECRET>"

# النتيجة المتوقعة: {} أو JSON بالبيانات الحالية
# خطأ 401: secret خاطئ
# خطأ 403: قواعد الأمان تمنع الوصول
```

#### عبر سكربت Python اختباري:

```python
# Server/scripts/test_firebase.py
import asyncio
from modules.firebase_client import check_connectivity, set, get

async def main():
    ok = await check_connectivity()
    print(f"Firebase connected: {ok}")

    # اكتب قيمة اختبارية
    await set("test/hello", {"msg": "from-server", "ts": 1234567890})

    # اقرأها
    val = await get("test/hello")
    print(f"Read back: {val}")

    # احذفها
    await set("test/hello", None)

asyncio.run(main())
```

شغّله:
```bash
cd Server && python -m scripts.test_firebase
```

---

### 6.2 التحقق من عمل المصادقة (Authentication)

#### إنشاء مستخدم اختباري:

```bash
# عبر Firebase REST API (بدون كود)
curl -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@abuzahra.com",
    "password": "TestPass123!",
    "returnSecureToken": true
  }'
```

> النتيجة المتوقعة: JSON يحتوي على `idToken`, `refreshToken`, `localId`.

#### التحقق من ID Token على السيرفر:

```bash
curl -X POST "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk" \
  -H "Content-Type: application/json" \
  -d '{"idToken": "<idToken-من-الخطوة-السابقة>"}'
```

> النتيجة المتوقعة: JSON يحتوي على بيانات المستخدم (email, localId).

#### عبر واجهة Firebase Console:

1. اذهب إلى **Authentication → Users**.
2. يجب أن ترى المستخدم الاختباري في القائمة.

---

### 6.3 التحقق من تخزين البيانات في RTDB

#### الكتابة اليدوية عبر Console:

1. اذهب إلى **Realtime Database → Data**.
2. اضغط على عقدة `sms` → **+** لإضافة طفل.
3. المفتاح: `test_device_1`
4. القيمة: `[{"from":"123","body":"hello","ts":1700000000}]`
5. اضغط **Add**.

#### التحقق من القراءة:

```bash
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/sms/test_device_1.json?auth=<FIREBASE_DB_SECRET>"
```

> النتيجة المتوقعة: `[{"from":"123","body":"hello","ts":1700000000}]`

#### عبر السيرفر:

شغّل الجهاز الافتراضي وأرسل بيانات SMS حقيقية، ثم تحقق في Console من ظهور المسار:
```
sms/<device_id>/
  0: {from: "...", body: "...", ts: ...}
  1: {from: "...", body: "...", ts: ...}
```

---

### 6.4 التحقق من Google Sign-In

#### على الـ Web Dashboard:

1. افتح `https://alsydyabwalzhra.online` في المتصفح.
2. اضغط **"تسجيل الدخول عبر Google"**.
3. يجب أن تظهر نافذة Google لاختيار الحساب.
4. بعد الاختيار، يتم إعادة التوجيه إلى لوحة التحكم.

> ⚠️ إن فشل مع `auth/popup-closed-by-user` → المستخدم أغلق النافذة.
> ⚠️ إن فشل مع `auth/unauthorized-domain` → أضف نطاقك إلى **Authentication → Settings → Authorized domains**:
> - `alsydyabwalzhra.online`
> - `www.alsydyabwalzhra.online`
> - `localhost` (موجود افتراضياً)

#### على تطبيق Android (Admin-App):

1. افتح Admin-App على الجهاز.
2. اضغط **"تسجيل الدخول عبر Google"**.
3. يجب أن تظهر نافذة اختيار الحساب.

> ⚠️ **الخطأ الشائع `DEVELOPER_ERROR (10)`:**
> - السبب: SHA-1 fingerprint غير مسجّل في Firebase Console.
> - الحل: Project Settings → Admin-App → Add fingerprint → الصق `0A:27:6F:32:73:15:92:AF:77:06:03:F8:4C:81:48:00:45:75:F4:D4`.

#### على تطبيق Android (Android-App):

نفس الخطوات، لكن يجب استخراج SHA-1 الخاص بـ `Android-App/release.keystore` وإضافته إلى Firebase Console لتطبيق `com.abuzahra.manager`.

---

### 6.5 التحقق من Server ↔ Firebase ↔ Device Loop

```
1. من Web Dashboard: أرسل أمر "Get SMS"
   ↓
2. السيرفر يستقبل الطلب عبر REST API
   ↓
3. السيرفر يكتب الأمر إلى RTDB: commands/<device_id>/<cmd_id>
   ↓
4. الجهاز يقرأ الأمر من RTDB وينفّذه
   ↓
5. الجهاز يكتب النتيجة إلى RTDB: results/<device_id>/<cmd_id>
   ↓
6. السيرفر يقرأ النتيجة من RTDB (كل 3 ثوانٍ — result_listener)
   ↓
7. السيرفر يحدّث Web Dashboard عبر WebSocket/SSE
```

للتحقق من الخطوات 3-6:

```bash
# راقب أوامر الجهاز
watch -n 2 'curl -s "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/commands/<device_id>.json?auth=<SECRET>" | jq'

# راقب نتائج الجهاز
watch -n 2 'curl -s "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/results/<device_id>.json?auth=<SECRET>" | jq'
```

---

## 7. استكشاف الأخطاء وإصلاحها (Troubleshooting)

### 7.1 خطأ "Firebase: Unauthorized" أو 401

**الأعراض:**
- الخادم يطبع: `[WARNING] firebase: Firebase check returned 401`
- لا تظهر بيانات الأجهزة في لوحة التحكم.

**الأسباب المحتملة:**
1. `FIREBASE_DB_SECRET` غير موجود في `Server/.env`.
2. `FIREBASE_DB_SECRET` منسوخ بشكل ناقص (مع مسافات أو أسطر جديدة).
3. استخدام secret قديم تم حذفه من Firebase Console.

**الحل:**
```bash
# 1. تحقق من قيمة الـ Secret
grep FIREBASE_DB_SECRET Server/.env

# 2. اختبر الـ Secret مباشرة
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/.json?auth=<YOUR_SECRET>"

# 3. إذا فشل، أنشئ secret جديداً من Console:
#    Project Settings → Service Accounts → Database Secrets → Add Secret
```

---

### 7.2 خطأ "Google Sign-In failed: 10" أو "12500"

**الأعراض:**
- على Android: ضغط زر Google → فشل فوري بدون نافذة اختيار حساب.
- الكود: `DEVELOPER_ERROR` أو `status: 10`.

**الأسباب المحتملة:**
1. SHA-1 fingerprint غير مسجّل في Firebase Console للتطبيق.
2. `package_name` في `google-services.json` لا يطابق `applicationId` في `build.gradle`.
3. Web Client ID (OAuth 2.0) المستخدم في طلب `requestIdToken` خاطئ.

**الحل:**

#### أ) تحقق من SHA-1:

```bash
# استخرج SHA-1 من الـ keystore
keytool -list -v -keystore Admin-App/release.keystore -alias release -storepass <password> | grep SHA1
# النتيجة المتوقعة: 0A:27:6F:32:73:15:92:AF:77:06:03:F8:4C:81:48:00:45:75:F4:D4
```

#### ب) أضف SHA-1 إلى Firebase Console:

1. Project Settings → General → Your apps → Admin-App.
2. اضغط **Add fingerprint**.
3. الصق SHA-1 → **Save**.
4. نزّل `google-services.json` الجديد واستبدله في `Admin-App/app/`.
5. أعد بناء التطبيق (`./gradlew assembleRelease`).

#### ج) تحقق من Web Client ID:

في `LinkActivity.kt` (أو أي مكان يطلب Google ID Token):
```kotlin
val clientId = "159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com"
```
يجب أن يطابق **Web Client ID (type 3)** في `google-services.json`، وليس Android Client ID (type 1).

---

### 7.3 خطأ "Permission denied" أو 403 على RTDB

**الأعراض:**
- السيرفر يطبع: `Firebase SET sms/device_1 failed: 403`.
- البيانات لا تُحفظ في RTDB.

**الأسباب المحتملة:**
1. RTDB Security Rules تمنع الكتابة (مثلاً: `".write": false`).
2. الـ Secret المستخدم لا يملك صلاحية admin (نادراً).
3. تستخدم Auth Token عادي بدلاً من Secret.

**الحل:**

#### أ) تحقق من القواعد:

1. Realtime Database → Rules.
2. تأكد أن كل عقدة لها `".write": "auth != null"`.
3. اضغط **Publish**.

#### ب) اختبر مع Secret مباشرة:

```bash
# يجب أن يعمل بدون مشاكل
curl -X PUT "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/test.json?auth=<SECRET>" \
  -H "Content-Type: application/json" \
  -d '{"hello":"world"}'
```

#### ج) تفعّل Test Mode مؤقتاً:

```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

> ⚠️ **لا تترك Test Mode في الإنتاج** — استخدمه فقط للتشخيص.

---

### 7.4 البيانات لا تظهر في لوحة التحكم

**الأعراض:**
- الجهاز يرسل البيانات (SMS، جهات اتصال).
- لا تظهر في Web Dashboard.

**خطوات التشخيص:**

#### أ) تحقق من المسارات الصحيحة في RTDB:

المسارات المتوقعة (من `firebase_client.py`):

| النوع | المسار |
|------|--------|
| SMS | `sms/<device_id>` |
| Contacts | `contacts/<device_id>` |
| Calls | `calls/<device_id>` |
| Notifications | `notifications/<device_id>` |
| Location | `location/<device_id>` |
| Device Info | `device_info/<device_id>` |

```bash
# تحقق من وجود البيانات
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/sms.json?auth=<SECRET>" | jq

# يجب أن يرجع JSON مثل:
# {
#   "device_1": [{"from":"123","body":"hi",...}],
#   "device_2": [{"from":"456","body":"hello",...}]
# }
```

#### ب) تحقق من `device_id`:

```bash
# استعلم عن جهاز محدد
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/sms/<device_id>.json?auth=<SECRET>" | jq
```

إذا رجع `null`، فالجهاز لم يرسل بياناته بعد أو يستخدم `device_id` مختلفاً.

#### ج) تحقق من Server Logs:

```bash
tail -f Server/logs/server.log | grep -i firebase
# ابحث عن أخطاء مثل:
# "Firebase SET sms/device_1 failed: <error>"
```

#### د) تحقق من API endpoint في Web Dashboard:

في DevTools → Network:
- الطلب: `GET https://alsydyabwalzhra.online/api/sms/<device_id>`
- يجب أن يرجع: `200 OK` مع JSON.

---

### 7.5 خطأ "Firebase Admin SDK credentials not found"

**الأعراض:**
- Web Dashboard يطبع: `Firebase Admin SDK credentials not found at credentials/firebase-admin-sdk.json`.
- لا يمكن إنشاء مستخدمين جدد من لوحة التحكم.

**الحل:**

1. نزّل ملف Service Account JSON من Firebase Console:
   - Project Settings → Service Accounts → Generate new private key.
2. أعد تسميته إلى `firebase-admin-sdk.json`.
3. ضعه في: `src/credentials/firebase-admin-sdk.json`.
4. أضف إلى `.gitignore`:
   ```
   credentials/firebase-admin-sdk.json
   ```
5. أعد تشغيل خادم Next.js:
   ```bash
   npm run dev
   # أو
   npm run build && npm start
   ```

---

### 7.6 خطأ "auth/unauthorized-domain" على Web Dashboard

**الأعراض:**
- Google Sign-In على الويب يفشل مع: `auth/unauthorized-domain`.

**الحل:**

1. Firebase Console → Authentication → Settings → Authorized domains.
2. أضف:
   - `alsydyabwalzhra.online`
   - `www.alsydyabwalzhra.online`
   - `localhost` (موجود افتراضياً).
3. احفظ.

---

### 7.7 أخطاء CORS في Web Dashboard

**الأعراض:**
- في DevTools: `Access-Control-Allow-Origin` خطأ.

**الحل:**

في `Server/modules/config.py:95-102`، تأكد أن نطاقك موجود في `CORS_ORIGINS`:

```python
CORS_ORIGINS = [
    f"https://{SERVER_DOMAIN}",          # https://alsydyabwalzhra.online
    f"https://www.{SERVER_DOMAIN}",      # https://www.alsydyabwalzhra.online
    "http://localhost:8080",
    "http://localhost:3001",
    "http://localhost:3000",
    "http://localhost:8443",
]
```

أعد تشغيل الخادم بعد التعديل.

---

### 7.8 خطأ "Firebase App already initialized"

**الأعراض:**
- في Next.js console: `Firebase App named '[DEFAULT]' already exists`.

**السبب:**
- في React/Next.js مع Hot Reload، يُعاد استدعاء `initializeApp` مرات متعددة.

**الحل:** الكود الحالي في `src/lib/firebase.ts:15` يتعامل معه بالفعل:
```typescript
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp()
```

---

## 8. قائمة التحقق النهائية (Checklist)

استخدم هذه القائمة قبل إطلاق النظام في الإنتاج:

### 8.1 Firebase Console

- [ ] المشروع `abwalzhraalsydy-62ccf` موجود.
- [ ] تمت إضافة تطبيق Web وتم تسجيل `Web App ID`.
- [ ] تمت إضافة تطبيق Android `com.abuzahra.admin` مع SHA-1.
- [ ] تمت إضافة تطبيق Android `com.abuzahra.manager` مع SHA-1.
- [ ] تم تنزيل `google-services.json` لكلا التطبيقين ووضعهما في مكانيهما.

### 8.2 Authentication

- [ ] Email/Password مفعّل.
- [ ] Google Sign-In مفعّل.
- [ ] نطاقات الويب مُصرَّح بها في Authorized domains (`alsydyabwalzhra.online`, `www.alsydyabwalzhra.online`).

### 8.3 Realtime Database

- [ ] RTDB منشأ في الموقع الجغرافي الصحيح.
- [ ] Database URL يطابق `https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com`.
- [ ] Security Rules منشورة (القواعد من قسم 3.6).
- [ ] Test mode **معطّل** (في الإنتاج).

### 8.4 Service Accounts & Secrets

- [ ] تم تنزيل `firebase-admin-sdk.json` ووضعه في `src/credentials/`.
- [ ] تم إنشاء `FIREBASE_DB_SECRET` ونسخه.
- [ ] كلا الملفين مُضافَين إلى `.gitignore`.

### 8.5 Server `.env`

- [ ] `FIREBASE_PROJECT=abwalzhraalsydy-62ccf`
- [ ] `FIREBASE_DB_SECRET=<set>`
- [ ] `FIREBASE_WEB_API_KEY=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk`
- [ ] `BOT_TOKEN=<set>`
- [ ] `ADMIN_CHAT_ID=<set>`
- [ ] `ADMIN_PASSWORD=<strong>`

### 8.6 Web Dashboard `.env.local`

- [ ] جميع مفاتيح `NEXT_PUBLIC_FIREBASE_*` مضبوطة.
- [ ] `NEXT_PUBLIC_SERVER_URL=https://alsydyabwalzhra.online`
- [ ] `NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID=<set>`

### 8.7 اختبارات التشغيل

- [ ] السيرفر يطبع `Firebase connected` عند الإقلاع.
- [ ] cURL لـ `/.json?auth=<secret>` يرجع `200 OK`.
- [ ] إنشاء مستخدم اختباري عبر `signUp` API يعمل.
- [ ] Google Sign-In على الويب يعمل.
- [ ] Google Sign-In على Admin-App يعمل.
- [ ] Google Sign-In على Android-App يعمل.
- [ ] إرسال أمر من Web Dashboard → ظهور في `commands/<device_id>` في RTDB.
- [ ] الجهاز ينفّذ الأمر ويكتب النتيجة في `results/<device_id>`.
- [ ] السيرفر يلتقط النتيجة ويعرضها في لوحة التحكم.

---

## 📎 ملحق: مراجع سريعة

### روابط Firebase Console المباشرة

| الصفحة | الرابط |
|--------|-------|
| Project Overview | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/overview |
| Authentication | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/authentication |
| Realtime Database | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/database |
| Project Settings | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/settings |
| Service Accounts | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/settings/serviceaccounts/adminsdk |
| Authorized Domains | https://console.firebase.google.com/project/abwalzhraalsydy-62ccf/authentication/settings |

### ملفات المشروع المرتبطة

| الملف | الوصف |
|------|------|
| `Server/modules/config.py` | إعدادات الخادم ومتغيرات Firebase |
| `Server/modules/firebase_client.py` | عميل Firebase REST للخادم |
| `Server/.env` | متغيرات البيئة (غير موجود في Git) |
| `src/lib/firebase.ts` | تكوين Firebase Client SDK للويب |
| `src/lib/firebase-admin.ts` | تكوين Firebase Admin SDK للويب |
| `src/credentials/firebase-admin-sdk.json` | مفتاح Service Account (غير موجود في Git) |
| `Android-App/app/google-services.json` | تكوين Firebase لتطبيق العميل |
| `Admin-App/app/google-services.json` | تكوين Firebase لتطبيق الإدارة |

### أوامر سريعة مفيدة

```bash
# 1. اختبار اتصال Firebase من الخادم
cd Server && python -c "
import asyncio
from modules.firebase_client import check_connectivity
asyncio.run(check_connectivity())
"

# 2. اختبار RTDB عبر cURL
curl "https://abwalzhraalsydy-62ccf-default-rtdb.firebaseio.com/.json?auth=$FIREBASE_DB_SECRET"

# 3. استخراج SHA-1 من keystore
keytool -list -v -keystore Admin-App/release.keystore -alias release | grep SHA1

# 4. فحص Server Logs لأخطاء Firebase
tail -f Server/logs/server.log | grep -iE "firebase|firebase"

# 5. التحقق من متغيرات البيئة على الخادم
grep -E "FIREBASE|BOT_TOKEN|ADMIN" Server/.env
```

---

## 🎯 خلاصة سريعة: ما الذي تحتاج فعله الآن؟

### على Firebase Console:

1. **تنزيل Service Account JSON:**
   - Project Settings → Service Accounts → **Generate new private key**.
   - ضعه في `src/credentials/firebase-admin-sdk.json`.

2. **إنشاء Database Secret:**
   - Project Settings → Service Accounts → **Database Secrets** → Add Secret.
   - انسخ القيمة وضعها في `Server/.env` كـ `FIREBASE_DB_SECRET=...`.

3. **إضافة SHA-1 لتطبيق Android-App (إن لزم):**
   - Project Settings → Android-App (`com.abuzahra.manager`) → Add fingerprint.
   - الصق SHA-1 المستخرج من `Android-App/release.keystore`.

4. **تفعيل Sign-in methods:**
   - Authentication → Sign-in method → Email/Password = Enabled.
   - Authentication → Sign-in method → Google = Enabled.

5. **نشر RTDB Rules:**
   - Realtime Database → Rules → الصق القواعد من قسم 3.6 → Publish.

6. **إضافة النطاقات المُصرَّح بها:**
   - Authentication → Settings → Authorized domains → أضف `alsydyabwalzhra.online` و `www.alsydyabwalzhra.online`.

### على الخادم:

```bash
# أنشئ/حدّث Server/.env
cat > Server/.env << 'EOF'
SERVER_HOST=0.0.0.0
SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<your-strong-password>
ADMIN_EMAIL=admin@abuzahra.com
BOT_TOKEN=<your-telegram-bot-token>
ADMIN_CHAT_ID=<your-telegram-chat-id>
FIREBASE_PROJECT=abwalzhraalsydy-62ccf
FIREBASE_DB_SECRET=<paste-from-firebase-console>
FIREBASE_WEB_API_KEY=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk
EOF

# أعد تشغيل الخادم
cd Server && python main.py
# يجب أن ترى: [INFO] firebase: Firebase connected
```

### على لوحة التحكم Web:

```bash
# أنشئ src/credentials/firebase-admin-sdk.json
# (انسخ الملف الذي نزّلته من Firebase Console)

# أنشئ .env.local
cat > .env.local << 'EOF'
NEXT_PUBLIC_SERVER_URL=https://alsydyabwalzhra.online
NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSyBkFaZKn429L1Q6DcCiVL0wZf4EHQloaEk
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=abwalzhraalsydy-62ccf.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=abwalzhraalsydy-62ccf
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=abwalzhraalsydy-62ccf.firebasestorage.app
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=159319780620
NEXT_PUBLIC_FIREBASE_APP_ID=1:159319780620:web:ec599a59dfefd278f52d29
NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID=159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com
EOF

# أضف إلى .gitignore
echo "credentials/firebase-admin-sdk.json" >> .gitignore
echo ".env.local" >> .gitignore

# أعد البناء والإطلاق
npm run build && npm start
```

---

**انتهى الدليل.** ✅

بعد إكمال كل خطوة، استخدم [قائمة التحقق النهائية](#8-قائمة-التحقق-النهائية-checklist) للتأكد من أن كل شيء يعمل بشكل صحيح. لأي مشاكل، راجع قسم [استكشاف الأخطاء](#7-استكشاف-الأخطاء-وإصلاحها-troubleshooting).

— **Abu-Zahra Project Team** | الإصدار 4.0.0
