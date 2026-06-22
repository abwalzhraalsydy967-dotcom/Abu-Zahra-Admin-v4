"""
FCM (Firebase Cloud Messaging) client — silent push for instant command wake-up.

When the admin sends a command from the web dashboard, the server queues the
command, pushes it to Firebase RTDB (for the device's ChildEventListener), AND
fires an FCM data message to the device's registered token. FCM data-only
messages are delivered to the app's FirebaseMessagingService.onMessageReceived
immediately — even when the app is killed/backgrounded — providing instant
wake-up without waiting for the 5-second REST polling interval.

Architecture:
    Web admin → /api/web/send_command
                → store.queue_command()
                → firebase_client.push_command()       (RTDB)
                → fcm_client.send_fcm_command()         (this module)

Protocol: FCM legacy HTTP (https://fcm.googleapis.com/fcm/send) with the
server key. The legacy protocol is simpler than HTTP v1 (which requires a
service account JSON) and sufficient for our use case.

If FCM_SERVER_KEY is empty (not configured), all send_* calls return False
without raising — the server falls back to RTDB + REST polling.
"""

import json
import logging
import asyncio

import aiohttp

from .config import FCM_SERVER_KEY, FCM_API_URL

logger = logging.getLogger("fcm")

_fcm_session: aiohttp.ClientSession = None


async def _get_session() -> aiohttp.ClientSession:
    """Lazily initialize the shared aiohttp session for FCM calls."""
    global _fcm_session
    if _fcm_session is None or _fcm_session.closed:
        _fcm_session = aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=10),
            headers={
                "Authorization": f"key={FCM_SERVER_KEY}",
                "Content-Type": "application/json",
            },
        )
    return _fcm_session


def is_configured() -> bool:
    """Has an FCM server key been configured?"""
    return bool(FCM_SERVER_KEY)


async def send_fcm_command(token: str, command_name: str,
                            command_id: str = "",
                            params: dict = None,
                            device_id: str = "") -> bool:
    """Send an FCM data-only message to [token] carrying a command for the
    device to execute via AbuZahraFirebaseMessagingService.onMessageReceived.

    Data-only messages (no "notification" key) are delivered directly to
    onMessageReceived regardless of app state — that's what makes them "silent"
    wake-up pings.

    Returns True if FCM accepted the message, False otherwise.
    Always returns False (without raising) if FCM is not configured.
    """
    if not is_configured():
        logger.debug("FCM not configured (FCM_SERVER_KEY empty) — skipping send_fcm_command")
        return False
    if not token:
        logger.debug("send_fcm_command: empty token — skipping")
        return False

    payload = {
        "to": token,
        # high_priority + content_available=true maximize the chance of
        # immediate delivery even when the device is in Doze.
        "priority": "high",
        "content_available": True,
        # Do NOT include a "notification" key — that would route the message
        # through the system tray (and onMessageReceived would NOT fire when
        # the app is backgrounded).
        "data": {
            "command": command_name,
            "command_id": command_id,
            "params": json.dumps(params or {}),
            "device_id": device_id,
        },
    }

    try:
        session = await _get_session()
        async with session.post(FCM_API_URL, json=payload) as resp:
            text = await resp.text()
            if resp.status == 200:
                try:
                    body = json.loads(text)
                except Exception:
                    body = {}
                # FCM returns 200 even if the token is invalid. Inspect the
                # body to know if delivery actually happened.
                if body.get("failure", 0) > 0 or not body.get("success", 0):
                    err = (body.get("results") or [{}])[0].get("error", "unknown")
                    logger.warning(f"FCM send failed for token=…{token[-8:]}: {err}")
                    return False
                logger.info(f"FCM sent cmd='{command_name}' id={command_id} to token=…{token[-8:]}")
                return True
            else:
                logger.warning(f"FCM HTTP {resp.status}: {text[:200]}")
                return False
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning(f"FCM send exception: {e}")
        return False


async def send_fcm_wake(token: str) -> bool:
    """Send a minimal "wake" data message. The device's onMessageReceived will
    fire and the CommandService will poll REST + RTDB for pending work."""
    if not is_configured():
        return False
    if not token:
        return False

    payload = {
        "to": token,
        "priority": "high",
        "content_available": True,
        "data": {
            "type": "wake",
        },
    }

    try:
        session = await _get_session()
        async with session.post(FCM_API_URL, json=payload) as resp:
            text = await resp.text()
            if resp.status == 200:
                logger.info(f"FCM wake ping sent to token=…{token[-8:]}")
                return True
            logger.warning(f"FCM wake HTTP {resp.status}: {text[:200]}")
            return False
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning(f"FCM wake exception: {e}")
        return False


async def close():
    """Close the shared aiohttp session (called on server shutdown)."""
    global _fcm_session
    if _fcm_session and not _fcm_session.closed:
        await _fcm_session.close()
        _fcm_session = None
