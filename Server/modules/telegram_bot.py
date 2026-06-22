"""
Telegram Bot Module - Multi-user system with per-user isolation.
Each Telegram user gets their own session with isolated devices, files, commands.

AUTH FLOW (rebuilt 16-BOT):
    /start → ask for email/username → ask for 8-char link code →
    verify code against user.permanent_link_code + Firebase code_to_email/$code →
    link chat_id to user, persist session, show dashboard.

Each email = one lifelong 8-char permanent_link_code (used once per device).
Sessions persist in tg_sessions.json so users don't re-auth on every /start.
Device notifications are forwarded instantly to the linked chat.
"""

import asyncio
import json
import time
import logging
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

# Bot username (fetched dynamically via getMe on startup; kept for backward
# compatibility — the new flow no longer uses deep-link tokens, but other
# modules may still want to know the bot's @handle).
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
    """Construct a Telegram deep-link URL. Kept for backward compat — the new
    auth flow no longer uses deep-link tokens, but some web routes may still
    reference this helper.
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
            store.messages_sent += 1
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
    return await api_call("sendMessage", payload)


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


# ─── User Session Management (Multi-User, State Machine) ─────

# auth_state values:
#   "waiting_email"    → bot is waiting for email/username input
#   "waiting_code"     → bot is waiting for 8-char link code
#   "authenticated"    → fully logged in, show dashboard
DEFAULT_AUTH_STATE = "waiting_email"


def get_or_create_tg_session(chat_id: str) -> dict:
    """Get or create an isolated Telegram user session.

    Adds the new auth_state / pending_email fields used by the email+code
    flow. Existing persisted sessions (pre-16-BOT) are migrated on the fly:
    authenticated sessions keep their authenticated state; everything else
    defaults to waiting_email.

    The caller is responsible for calling `await store.save_tg_sessions()`
    when an auth-state mutation happens (login, logout, link account,
    selected_device change). We do NOT persist here on every get/create to
    avoid excessive disk I/O for plain menu navigation.
    """
    # Normalise chat_id to string for consistent dict keys + JSON keys.
    chat_id = str(chat_id)
    if chat_id not in store.tg_sessions:
        store.tg_sessions[chat_id] = {
            "chat_id": chat_id,
            "authenticated": False,
            "user_id": None,
            "auth_state": DEFAULT_AUTH_STATE,
            "pending_email": None,
            "selected_device": None,
            "current_menu": "main",
            "created_at": datetime.utcnow().isoformat(),
            "last_activity": time.time(),
        }
    sess = store.tg_sessions[chat_id]
    # Migration: ensure new fields exist on old persisted sessions.
    if "auth_state" not in sess:
        sess["auth_state"] = "authenticated" if sess.get("authenticated") else DEFAULT_AUTH_STATE
    if "pending_email" not in sess:
        sess["pending_email"] = None
    sess["last_activity"] = time.time()
    return sess


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
    session = store.tg_sessions.get(str(chat_id))
    if session and session.get("user_id"):
        return store.users.get(session["user_id"])
    return None


def get_devices_for_tg(chat_id: str) -> list:
    """Get devices accessible by this Telegram user."""
    session = store.tg_sessions.get(str(chat_id))
    if not session:
        return []
    user_id = session.get("user_id")
    if not user_id:
        return []
    user = store.users.get(user_id)
    if not user:
        return []
    if user.get("role") == "admin":
        return list(store.devices.values())
    return [d for d in store.devices.values() if d.get("owner_id") == user_id]


# ─── Authentication (email + code flow) ──────────────────────

def find_user_by_email_or_username(identifier: str) -> Optional[dict]:
    """Look up a user in store.users by email OR username (case-insensitive).

    Returns the user dict or None.
    """
    if not identifier:
        return None
    ident = identifier.strip().lower()
    for u in store.users.values():
        if u.get('email', '').lower() == ident:
            return u
        if u.get('username', '').lower() == ident:
            return u
    return None


async def verify_link_code(user: dict, code: str) -> bool:
    """Verify a Telegram link code.

    Two checks (both must pass):
      1. code matches user.permanent_link_code (server-side source of truth)
      2. code exists in Firebase code_to_email/$code AND maps to the same
         email as the user (so the Android app + server stay in sync)

    Falls back gracefully to server-only verification if Firebase is offline.
    """
    if not user or not code:
        return False
    code = code.strip().upper()
    user_code = (user.get('permanent_link_code') or '').strip().upper()
    if not user_code or code != user_code:
        return False

    # Firebase cross-check (best-effort: if Firebase is offline, server-side
    # match alone is sufficient — the server is the source of truth).
    if _fb.firebase_connected:
        try:
            from .firebase_client import verify_permanent_code_firebase
            mapping = await verify_permanent_code_firebase(code)
            if mapping is None:
                logger.warning(f"TG auth: code {code} not found in Firebase code_to_email")
                return False
            mapped_email = (mapping.get('email') or '').strip().lower()
            user_email = (user.get('email') or '').strip().lower()
            if mapped_email and mapped_email != user_email:
                logger.warning(f"TG auth: Firebase email mismatch ({mapped_email} != {user_email})")
                return False
        except Exception as e:
            logger.warning(f"TG auth: Firebase verify failed ({e}); relying on server-side code match")
    return True


async def link_tg_chat_to_user(chat_id: str, user_id: str) -> bool:
    """Link a Telegram chat to a web user account (post code verification).

    Persists the session so the link survives restarts. Returns True on
    success.
    """
    user = store.users.get(user_id)
    if not user:
        return False
    session = get_or_create_tg_session(chat_id)
    session["authenticated"] = True
    session["user_id"] = user_id
    session["auth_state"] = "authenticated"
    session["pending_email"] = None
    session["linked_at"] = datetime.utcnow().isoformat()
    await _persist_tg_session(chat_id)
    return True


async def authenticate_tg_user(chat_id: str, username: str, password: str) -> bool:
    """Legacy username/password auth. Kept for compatibility / admin fallback.

    On successful auth the session is persisted to disk so the user stays
    logged in across server restarts.
    """
    user = await store.authenticate_user(username, password)
    if user:
        session = get_or_create_tg_session(chat_id)
        session["authenticated"] = True
        session["user_id"] = user["id"]
        session["auth_state"] = "authenticated"
        session["pending_email"] = None
        await _persist_tg_session(chat_id)
        return True
    return False


# ─── Notification Forwarding ─────────────────────────────────

async def forward_notification(user_id: str, notif: dict) -> None:
    """Forward a device notification to the Telegram chat linked to a user.

    Called from api_handlers.api_device_data when a device pushes typed data
    with type='notifications'. Looks up the user's linked chat_id in
    tg_sessions and sends an Arabic-formatted message. No-op if the user
    has no linked chat or isn't authenticated.

    `notif` is a single notification dict (app, title, text, etc.).
    """
    if not user_id or not notif:
        return
    try:
        chat_id = None
        for cid, sess in store.tg_sessions.items():
            if sess.get("user_id") == user_id and sess.get("authenticated"):
                chat_id = cid
                break
        if not chat_id:
            return
        app = notif.get('app') or notif.get('package') or ''
        title = notif.get('title') or ''
        text = notif.get('text') or notif.get('body') or ''
        msg = (
            "🔔 <b>إشعار جديد</b>\n\n"
            f"📱 التطبيق: <code>{app}</code>\n"
            f"📌 العنوان: {title}\n"
            f"📝 النص: {text}"
        )
        await send_message(chat_id, msg)
    except Exception as e:
        logger.warning(f"forward_notification failed for user {user_id}: {e}")


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
    """Dashboard keyboard matching the web sidebar:
    overview · devices · commands · streaming · files · events · settings
    """
    session = get_or_create_tg_session(chat_id)
    is_auth = session.get("authenticated", False)

    if not is_auth:
        return inline_keyboard([
            [("🔐 تسجيل الدخول", "do_login")],
            [("📡 حالة الخادم", "srv_status")],
        ])

    devices = get_devices_for_tg(chat_id)
    online_count = sum(1 for d in devices if store._device_last_online.get(d['id'], False))

    buttons = [
        [(f"📊 نظرة عامة", "srv_overview")],
        [(f"📱 الأجهزة ({len(devices)} — {online_count} متصل)", "menu_devices")],
        [("🎮 الأوامر", "menu_commands"), ("📡 البث", "menu_streaming")],
        [("📁 الملفات", "menu_files"), ("🔔 الإشعارات", "menu_notifications")],
        [("📅 الأحداث", "menu_events"), ("⚙️ الإعدادات", "menu_settings")],
        [("🔗 كود الربط الخاص بي", "do_link"), ("🚪 تسجيل الخروج", "do_logout")],
    ]
    return inline_keyboard(buttons)


def device_list_keyboard(chat_id: str) -> dict:
    devices = get_devices_for_tg(chat_id)
    if not devices:
        return inline_keyboard([[("لا توجد أجهزة — استخدم /link للحصول على كود الربط", "back_main")]])

    buttons = []
    for dev in devices[:10]:  # Max 10 devices
        online = store._device_last_online.get(dev['id'], False)
        status = "🟢" if online else "🔴"
        name = dev.get('name', dev.get('model', 'Unknown'))[:20]
        buttons.append([(f"{status} {name}", f"dev_{dev['id']}")])
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
    """Handle incoming Telegram text messages via the email+code state machine."""
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

    # ─── /start → entry point of the email+code flow ─────────
    if text.startswith("/start"):
        # If a token argument is supplied (legacy deep-link), ignore it and
        # always route to the new email+code flow. Old tokens are no longer
        # generated by the web app.
        await start_email_code_flow(chat_id, session)
        return

    # ─── Authenticated: handle slash commands ────────────────
    if session.get("authenticated"):
        if text.startswith("/"):
            cmd = text.split()[0].lower()
            await handle_command(chat_id, cmd, text, session)
        else:
            await send_message(chat_id,
                "استخدم الأزرار أدناه أو الأوامر المتاحة.\n"
                "أرسل /help لعرض قائمة الأوامر.",
                reply_markup=main_menu_keyboard(chat_id))
        return

    # ─── Unauthenticated: drive the state machine ────────────
    state = session.get("auth_state", DEFAULT_AUTH_STATE)

    if state == "waiting_email":
        await handle_waiting_email(chat_id, text, session)
    elif state == "waiting_code":
        await handle_waiting_code(chat_id, text, session)
    else:
        # Stale state — restart the flow.
        await start_email_code_flow(chat_id, session)


async def start_email_code_flow(chat_id: str, session: dict) -> None:
    """Begin the email+code authentication flow.

    If the chat is already linked to a user account, skip auth and show the
    dashboard directly (sessions persist across /start).
    """
    # Already authenticated (persisted session) → straight to dashboard.
    if session.get("authenticated") and session.get("user_id"):
        user = store.users.get(session["user_id"])
        if user:
            await show_dashboard(chat_id, user)
            return
        # Stale user_id (user was deleted) → reset.
        session["authenticated"] = False
        session["user_id"] = None

    # Auto-authenticate admin chat (backdoor for ops — admin chat id is set
    # in config). Skips the email+code dance so the admin can always get in.
    if str(chat_id) == str(ADMIN_CHAT_ID):
        admin_user = None
        for u in store.users.values():
            if u.get('role') == 'admin':
                admin_user = u
                break
        if admin_user:
            session["authenticated"] = True
            session["user_id"] = admin_user["id"]
            session["auth_state"] = "authenticated"
            session["pending_email"] = None
            await _persist_tg_session(chat_id)
            await send_message(chat_id,
                f"✅ مرحباً {admin_user['username']}!\n"
                f"تم تسجيل الدخول تلقائياً كمسؤول.\n\n"
                f"معرف المستخدم: <code>{admin_user['id']}</code>",
                reply_markup=main_menu_keyboard(chat_id))
            return

    # Fresh start: ask for email/username.
    session["auth_state"] = "waiting_email"
    session["pending_email"] = None
    session["authenticated"] = False
    session["user_id"] = None
    await _persist_tg_session(chat_id)
    await send_message(chat_id,
        "👋 <b>مرحباً! بوت أبو زهرا للإدارة</b>\n\n"
        "أرسل <b>اسم المستخدم</b> أو <b>البريد الإلكتروني</b> الذي سجلت به في تطبيق الإدارة.")


async def handle_waiting_email(chat_id: str, text: str, session: dict) -> None:
    """User sent an email/username — look them up and ask for the link code."""
    identifier = text.strip()
    if not identifier:
        await send_message(chat_id, "⚠️ أرسل اسم المستخدم أو البريد الإلكتروني.")
        return

    user = find_user_by_email_or_username(identifier)
    if not user:
        await send_message(chat_id,
            "❌ لم يتم العثور على حساب بهذا الاسم أو البريد.\n"
            "تأكد من أنك مسجّل في تطبيق الإدارة، ثم أعد الإرسال.\n\n"
            "💡 أرسل /start للبدء من جديد.")
        return

    if not user.get('is_active', True):
        await send_message(chat_id, "❌ هذا الحساب معطّل. تواصل مع المسؤول.")
        return

    # Remember which user is authenticating, advance the state machine.
    session["pending_email"] = user.get('email') or identifier
    session["auth_state"] = "waiting_code"
    await _persist_tg_session(chat_id)

    username = user.get('username', '')
    await send_message(chat_id,
        f"أهلاً <b>{username}</b>! 👋\n\n"
        f"أرسل <b>كود الربط الخاص بك (8 أحرف)</b>.\n\n"
        f"💡 تجد الكود في تطبيق الإدارة: الإعدادات ← كود الربط مع بوت Telegram.")


async def handle_waiting_code(chat_id: str, text: str, session: dict) -> None:
    """User sent the 8-char link code — verify it and link the chat."""
    code = text.strip()
    # Be lenient about formatting: strip spaces, uppercase.
    code_clean = code.replace(" ", "").upper()
    if len(code_clean) < 6 or len(code_clean) > 12:
        await send_message(chat_id,
            "⚠️ الكود يجب أن يكون 8 أحرف. أعد إرسال الكود الصحيح.\n\n"
            "أو أرسل /start للبدء من جديد.")
        return

    pending_email = session.get("pending_email")
    user = find_user_by_email_or_username(pending_email) if pending_email else None
    if not user:
        # Lost pending email (server restart?) — restart the flow.
        session["auth_state"] = "waiting_email"
        session["pending_email"] = None
        await _persist_tg_session(chat_id)
        await send_message(chat_id,
            "⚠️ انتهت الجلسة. أرسل /start ثم بريدك الإلكتروني من جديد.")
        return

    ok = await verify_link_code(user, code_clean)
    if not ok:
        await send_message(chat_id, "❌ الكود غير صحيح. حاول مرة أخرى.")
        return

    # Success — link chat to user account.
    ok = await link_tg_chat_to_user(chat_id, user["id"])
    if not ok:
        await send_message(chat_id, "❌ تعذّر ربط الحساب. حاول مرة أخرى.")
        return

    username = user.get('username', '')
    await send_message(chat_id,
        f"✅ <b>تم تسجيل الدخول بنجاح!</b> تم حفظ الجلسة.\n\n"
        f"مرحباً <b>{username}</b>، إليك لوحة التحكم:",
        reply_markup=main_menu_keyboard(chat_id))

    # Show linked device info right after login (spec: "تطلع لوحة التحكم
    # والجهاز المرتبط").
    devices = get_devices_for_tg(chat_id)
    if devices:
        for dev in devices[:3]:
            online = store._device_last_online.get(dev['id'], False)
            status = "🟢 متصل" if online else "🔴 غير متصل"
            await send_message(chat_id,
                f"📱 <b>الجهاز المرتبط</b>\n\n"
                f"🏷️ <code>{dev['id']}</code>\n"
                f"📋 {dev.get('model', 'Unknown')}\n"
                f"🔋 بطارية: {dev.get('battery', 'N/A')}%\n"
                f"{status}")


async def show_dashboard(chat_id: str, user: dict) -> None:
    """Show the main dashboard message + menu."""
    await send_message(chat_id,
        f"👋 مرحباً <b>{user.get('username', '')}</b>!\n\n"
        f"🤖 بوت أبو زهرا للإدارة — الإصدار {VERSION}\n"
        f"🌐 {SERVER_DOMAIN}\n\n"
        f"استخدم الأزرار أدناه للتحكم في أجهزتك.",
        reply_markup=main_menu_keyboard(chat_id))


async def handle_command(chat_id: str, cmd: str, full_text: str, session: dict):
    """Handle slash commands from authenticated users."""
    user = get_user_for_tg(chat_id)
    if not user:
        # Lost auth somehow — restart flow.
        await start_email_code_flow(chat_id, session)
        return

    args = full_text.split()[1:] if len(full_text.split()) > 1 else []
    devices = get_devices_for_tg(chat_id)

    if cmd == "/start":
        await show_dashboard(chat_id, user)

    elif cmd == "/help":
        help_text = (
            "📖 <b>قائمة الأوامر</b>\n\n"
            "/start - القائمة الرئيسية / تسجيل الدخول\n"
            "/menu - عرض لوحة التحكم\n"
            "/devices - قائمة الأجهزة\n"
            "/link - كود الربط الخاص بك (دائم)\n"
            "/unlink <معرف_الجهاز> - فك الربط\n"
            "/status - حالة الخادم\n"
            "/stats - الإحصائيات\n"
            "/logs - آخر السجلات\n"
            "/settings - الإعدادات\n"
            "/logout - تسجيل الخروج\n"
            "/search <بحث> - البحث في الأجهزة\n\n"
            "يمكنك أيضاً التحكم مباشرة عبر الأزرار."
        )
        await send_message(chat_id, help_text, reply_markup=main_menu_keyboard(chat_id))

    elif cmd == "/menu":
        await show_dashboard(chat_id, user)

    elif cmd == "/devices":
        if not devices:
            await send_message(chat_id, "📱 لا توجد أجهزة مسجلة.\nاستخدم /link للحصول على كود الربط.")
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
        # verification intermediary).
        code = await store.get_or_create_permanent_code(user['id'])
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

    elif cmd == "/logout":
        session["authenticated"] = False
        session["user_id"] = None
        session["auth_state"] = "waiting_email"
        session["pending_email"] = None
        session["selected_device"] = None
        await _persist_tg_session(chat_id)
        await send_message(chat_id,
            "👋 تم تسجيل الخروج. أرسل /start لتسجيل الدخول مرة أخرى.")

    elif cmd == "/unlink" and args:
        device_id = args[0]
        success = await store.unlink_device(device_id, user['id'])
        if success:
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
        await send_settings_view(chat_id)

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
        if not session.get("selected_device"):
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً من قائمة الأجهزة.",
                             reply_markup=device_list_keyboard(chat_id))
            return
        device_id = session["selected_device"]
        await execute_device_command(chat_id, cmd.lstrip("/"), device_id, user)

    else:
        await send_message(chat_id, "❓ أمر غير معروف. أرسل /help لعرض الأوامر.",
                         reply_markup=main_menu_keyboard(chat_id))


# ─── View builders for dashboard menu items ──────────────────

async def send_overview_view(chat_id: str, user: dict) -> None:
    """📊 نظرة عامة — quick summary of the user's account + devices."""
    devices = get_devices_for_tg(chat_id)
    online = sum(1 for d in devices if store._device_last_online.get(d['id'], False))
    stats = store.get_stats()
    pending = sum(1 for c in store.commands.values()
                  if c['status'] == 'pending' and any(c.get('device_id') == d['id'] for d in devices))
    text = (
        f"📊 <b>نظرة عامة</b>\n\n"
        f"👤 المستخدم: <b>{user.get('username', '')}</b>\n"
        f"📧 البريد: {user.get('email', '')}\n"
        f"🆔 معرف المستخدم: <code>{user.get('id', '')}</code>\n\n"
        f"📱 أجهزتك: {len(devices)} (🟢 {online} متصل)\n"
        f"⏳ أوامر معلقة على أجهزتك: {pending}\n\n"
        f"— الخادم —\n"
        f"⏱️ التشغيل: {stats['uptime'] // 3600}س {stats['uptime'] % 3600 // 60}د\n"
        f"🔥 Firebase: {'متصل' if _fb.firebase_connected else 'غير متصل'}\n"
        f"📊 الإصدار: {VERSION}"
    )
    await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))


async def send_settings_view(chat_id: str) -> None:
    text = "⚙️ <b>الإعدادات</b>\n\n"
    for key, value in store.settings.items():
        text += f"• <b>{key}</b>: {value}\n"
    await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))


async def send_events_view(chat_id: str, user: dict) -> None:
    """📅 الأحداث — recent events for this user's devices."""
    devices = get_devices_for_tg(chat_id)
    device_ids = {d['id'] for d in devices}
    events = await store.get_events(limit=30)
    # Filter to events tied to this user's devices (admin sees all).
    if user.get('role') != 'admin':
        events = [e for e in events
                  if not e.get('device_id') or e.get('device_id') in device_ids]
    if not events:
        await send_message(chat_id, "📅 لا توجد أحداث بعد.", reply_markup=main_menu_keyboard(chat_id))
        return
    text = "📅 <b>آخر الأحداث</b>\n\n"
    for evt in events[:15]:
        icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
        t = evt.get('time', '')[:19].replace('T', ' ')
        text += f"{icon} {t}\n   {evt.get('event', '')[:80]}\n"
    await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))


async def send_files_view(chat_id: str, user: dict) -> None:
    """📁 الملفات — list recent files from the user's devices."""
    devices = get_devices_for_tg(chat_id)
    device_ids = {d['id'] for d in devices}
    files = [f for f in store.files.values()
             if f.get('device_id') in device_ids]
    files.sort(key=lambda x: x.get('uploaded_at', ''), reverse=True)
    if not files:
        await send_message(chat_id, "📁 لا توجد ملفات من أجهزتك بعد.", reply_markup=main_menu_keyboard(chat_id))
        return
    text = f"📁 <b>الملفات ({len(files)})</b>\n\n"
    for f in files[:15]:
        text += (
            f"📄 {f.get('filename', '?')}\n"
            f"   <code>{f.get('id', '')[:8]}</code> · "
            f"{(f.get('size', 0) or 0) // 1024} KB · "
            f"{f.get('uploaded_at', '')[:10]}\n"
        )
    await send_message(chat_id, text, reply_markup=main_menu_keyboard(chat_id))


async def send_notifications_view(chat_id: str, user: dict) -> None:
    """🔔 الإشعارات — pull recent notifications from Firebase for the user's
    primary device and show them inline.
    """
    devices = get_devices_for_tg(chat_id)
    if not devices:
        await send_message(chat_id, "🔔 لا توجد أجهزة لعرض إشعاراتها.", reply_markup=main_menu_keyboard(chat_id))
        return
    text_parts = ["🔔 <b>آخر الإشعارات</b>\n"]
    shown = 0
    if _fb.firebase_connected:
        from .firebase_client import get as fb_get
        for dev in devices[:3]:
            try:
                notifs = await fb_get(f"notifications/{dev['id']}")
                if isinstance(notifs, list):
                    recent = notifs[-5:][::-1]
                elif isinstance(notifs, dict):
                    recent = list(notifs.values())[-5:][::-1]
                else:
                    recent = []
                if not recent:
                    continue
                text_parts.append(f"\n📱 <b>{dev.get('model', dev['id'])}</b>")
                for n in recent[:5]:
                    if not isinstance(n, dict):
                        continue
                    app = n.get('app') or n.get('package') or '?'
                    title = n.get('title', '')[:40]
                    text_parts.append(f"  • <b>{app}</b>: {title}")
                    shown += 1
                if shown >= 15:
                    break
            except Exception as e:
                logger.warning(f"send_notifications_view failed for {dev['id']}: {e}")
    if shown == 0:
        text_parts.append("\nلا توجد إشعارات محفوظة.")
    await send_message(chat_id, "\n".join(text_parts), reply_markup=main_menu_keyboard(chat_id))


async def send_streaming_view(chat_id: str, user: dict) -> None:
    """📡 البث — show streaming options for the user's devices."""
    devices = get_devices_for_tg(chat_id)
    if not devices:
        await send_message(chat_id, "📡 لا توجد أجهزة للبث.", reply_markup=main_menu_keyboard(chat_id))
        return
    buttons = []
    for dev in devices[:8]:
        online = store._device_last_online.get(dev['id'], False)
        status = "🟢" if online else "🔴"
        name = dev.get('name', dev.get('model', 'Unknown'))[:20]
        buttons.append([(f"{status} 📡 بث: {name}", f"stream_{dev['id']}")])
    buttons.append([("🔙 رجوع", "back_main")])
    await send_message(chat_id,
        "📡 <b>البث المباشر</b>\n\nاختر جهازاً لبدء البث:",
        reply_markup=inline_keyboard(buttons))


# ─── Callback Query Handlers ──────────────────────────────────

async def handle_callback(chat_id: str, callback_data: str, message_id: int):
    """Handle inline button presses."""
    session = get_or_create_tg_session(chat_id)
    user = get_user_for_tg(chat_id)

    # Unauthenticated actions
    if callback_data == "do_login":
        await answer_callback_query(callback_query_id=message_id,
                                    text="أرسل /start ثم بريدك الإلكتروني وكود الربط",
                                    show_alert=True)
        # Kick the user into the email flow.
        await start_email_code_flow(chat_id, session)
        return

    if callback_data == "do_logout":
        if user:
            session["authenticated"] = False
            session["user_id"] = None
            session["auth_state"] = "waiting_email"
            session["pending_email"] = None
            session["selected_device"] = None
            await _persist_tg_session(chat_id)
        await answer_callback_query(callback_query_id=message_id, text="تم تسجيل الخروج")
        await send_message(chat_id, "👋 تم تسجيل الخروج. أرسل /start للدخول مرة أخرى.")
        return

    if not session.get("authenticated") or not user:
        await answer_callback_query(callback_query_id=message_id, text="سجل دخولك أولاً", show_alert=True)
        return

    await answer_callback_query(callback_query_id=message_id)
    devices = get_devices_for_tg(chat_id)

    # ─── Navigation: top-level dashboard menu ────────────────
    if callback_data == "back_main":
        await show_dashboard(chat_id, user)

    elif callback_data == "srv_overview":
        await send_overview_view(chat_id, user)

    elif callback_data == "menu_devices":
        if not devices:
            await send_message(chat_id, "📱 لا توجد أجهزة. استخدم /link للحصول على كود الربط.")
        else:
            text = f"📱 <b>اختر جهازاً ({len(devices)})</b>"
            await send_message(chat_id, text, reply_markup=device_list_keyboard(chat_id))

    elif callback_data == "menu_commands":
        if not devices:
            await send_message(chat_id, "🎮 لا توجد أجهزة. اربط جهازاً أولاً.")
            return
        if not session.get("selected_device"):
            await send_message(chat_id, "⚠️ اختر جهازاً أولاً.", reply_markup=device_list_keyboard(chat_id))
        else:
            await send_message(chat_id, "🎮 اختر فئة الأوامر:",
                             reply_markup=command_category_picker(session["selected_device"]))

    elif callback_data == "menu_streaming":
        await send_streaming_view(chat_id, user)

    elif callback_data == "menu_files":
        await send_files_view(chat_id, user)

    elif callback_data == "menu_notifications":
        await send_notifications_view(chat_id, user)

    elif callback_data == "menu_events":
        await send_events_view(chat_id, user)

    elif callback_data == "menu_settings":
        await send_settings_view(chat_id)

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

    # ─── Server Management (legacy aliases) ──────────────────
    elif callback_data == "srv_status":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📡 <b>حالة الخادم</b>\n✅ يعمل | ⏱️ {stats['uptime'] // 3600}س | "
            f"📱 {stats['devices_online']}/{stats['devices_total']} | "
            f"🔥 {'متصل' if _fb.firebase_connected else 'غير متصل'} | v{VERSION}",
            reply_markup=main_menu_keyboard(chat_id))

    elif callback_data == "srv_stats":
        stats = store.get_stats()
        await send_message(chat_id,
            f"📊 الأجهزة: {stats['devices_total']} | متصل: {stats['devices_online']} | "
            f"أوامر: {stats['commands_completed']}/{stats['commands_total']} | "
            f"أحداث: {stats['events_count']} | ملفات: {stats['files_active']}",
            reply_markup=main_menu_keyboard(chat_id))

    elif callback_data == "srv_logs":
        events = await store.get_events(limit=10)
        text = "📋 آخر السجلات:\n"
        for evt in events[:5]:
            icon = {"info": "ℹ️", "success": "✅", "warning": "⚠️", "error": "❌"}.get(evt.get('level', ''), "ℹ️")
            text += f"{icon} {evt.get('event', '')[:60]}\n"
        await send_message(chat_id, text if len(text) > 25 else "📋 لا توجد سجلات.",
                         reply_markup=main_menu_keyboard(chat_id))

    elif callback_data == "srv_settings":
        await send_settings_view(chat_id)

    # ─── Device Selection ─────────────────────────────────────
    elif callback_data.startswith("dev_"):
        device_id = callback_data[4:]
        device = store.devices.get(device_id)
        if not device:
            await send_message(chat_id, "❌ الجهاز غير موجود.")
            return
        if device.get('owner_id') != user['id'] and user.get('role') != 'admin':
            await send_message(chat_id, "⛔ ليس لديك صلاحية للوصول إلى هذا الجهاز.")
            return
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

    # ─── Streaming shortcut ──────────────────────────────────
    elif callback_data.startswith("stream_"):
        device_id = callback_data[len("stream_"):]
        device = store.devices.get(device_id)
        if not device or (device.get('owner_id') != user['id'] and user.get('role') != 'admin'):
            await send_message(chat_id, "⛔ ليس لديك صلاحية للوصول إلى هذا الجهاز.")
            return
        session["selected_device"] = device_id
        await _persist_tg_session(chat_id)
        # Trigger a single screenshot as the first frame.
        await execute_device_command(chat_id, "screenshot", device_id, user)

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

    # Fetch the bot username dynamically via getMe (kept for backward
    # compatibility — used to be needed for deep-link URLs; still useful
    # for diagnostics).
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
            {"command": "start", "description": "تسجيل الدخول / لوحة التحكم"},
            {"command": "help", "description": "قائمة الأوامر"},
            {"command": "menu", "description": "عرض لوحة التحكم"},
            {"command": "devices", "description": "قائمة الأجهزة"},
            {"command": "link", "description": "كود الربط الخاص بك (دائم)"},
            {"command": "logout", "description": "تسجيل الخروج"},
            {"command": "status", "description": "حالة الخادم"},
            {"command": "stats", "description": "الإحصائيات"},
            {"command": "logs", "description": "آخر السجلات"},
            {"command": "settings", "description": "الإعدادات"},
        ]
    })
    asyncio.create_task(poll_loop())
