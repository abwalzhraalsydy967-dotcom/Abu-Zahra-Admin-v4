"""
Configuration module for Abu-Zahra Server.
All configuration constants and environment variables.
"""

import os
import secrets
from pathlib import Path

# Load .env file if present
_env_path = Path(__file__).parent.parent / ".env"
if _env_path.exists():
    try:
        from dotenv import load_dotenv
        load_dotenv(_env_path)
    except ImportError:
        pass

# ─── Server Config ────────────────────────────────────────────
SERVER_HOST = os.environ.get("SERVER_HOST", "0.0.0.0")
SERVER_PORT = int(os.environ.get("SERVER_PORT", "8443"))
SERVER_DOMAIN = "alsydyabwalzhra.online"
SERVER_URL = f"https://{SERVER_DOMAIN}"

# ─── Admin Credentials ────────────────────────────────────────
ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "changeme")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@abuzahra.com")
JWT_SECRET = os.environ.get("JWT_SECRET", secrets.token_hex(32))
JWT_EXPIRE_HOURS = 24

# ─── Telegram Bot ─────────────────────────────────────────────
BOT_TOKEN = os.environ.get("BOT_TOKEN", "YOUR_BOT_TOKEN_HERE")
ADMIN_CHAT_ID = os.environ.get("ADMIN_CHAT_ID", "YOUR_CHAT_ID_HERE")

# ─── Firebase Config ──────────────────────────────────────────
FIREBASE_PROJECT = os.environ.get("FIREBASE_PROJECT", "YOUR_FIREBASE_PROJECT_ID")
FIREBASE_RTDB_URL = f"https://{FIREBASE_PROJECT}-default-rtdb.firebaseio.com"
FIREBASE_DB_SECRET = os.environ.get("FIREBASE_DB_SECRET", "")

# ─── Data Directory ───────────────────────────────────────────
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")
UPLOADS_DIR = os.path.join(DATA_DIR, "uploads")
TEMP_DIR = os.path.join(DATA_DIR, "temp")
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(UPLOADS_DIR, exist_ok=True)
os.makedirs(TEMP_DIR, exist_ok=True)

# ─── Pairing Codes ────────────────────────────────────────────
PAIRING_CODE_LENGTH = 8
PAIRING_CODE_EXPIRE_SECONDS = 600  # 10 minutes
PAIRING_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # No O, 0, I, 1

# ─── File Storage ─────────────────────────────────────────────
MAX_UPLOAD_SIZE = 50 * 1024 * 1024  # 50MB
FILE_TEMP_EXPIRE_SECONDS = 3600  # 1 hour
FILE_CLEANUP_INTERVAL = 300  # 5 minutes

# ─── Rate Limiting ────────────────────────────────────────────
MAX_COMMANDS_PER_MINUTE = 20
TG_RATE_LIMIT = 10  # messages per 30 seconds
TG_DEDUP_SECONDS = 10
LINK_CODE_COOLDOWN = 30

# ─── Device Settings ──────────────────────────────────────────
DEVICE_OFFLINE_TIMEOUT = 120  # seconds
HEARTBEAT_INTERVAL = 60  # seconds
LOCATION_INTERVAL = 300  # 5 minutes
SYNC_INTERVAL = 15  # minutes (via WorkManager on device)

# ─── Streaming ────────────────────────────────────────────────
STREAM_FRAME_CACHE_SIZE = 5
JPEG_STREAM_INTERVAL = 2.0  # seconds
STREAM_QUALITY_PRESETS = {
    "480p": {"width": 854, "height": 480, "fps": 15, "bitrate": 800000},
    "720p": {"width": 1280, "height": 720, "fps": 30, "bitrate": 2500000},
    "1080p": {"width": 1920, "height": 1080, "fps": 30, "bitrate": 5000000},
    "1440p": {"width": 2560, "height": 1440, "fps": 60, "bitrate": 10000000},
}

# ─── Monitoring ───────────────────────────────────────────────
BATTERY_ALERT_COOLDOWN = 600  # 10 minutes
MAX_EVENTS = 2000
MAX_LINK_CODES = 500
MAX_PENDING_COMMANDS_AGE = 900  # 15 minutes
MAX_BATCH_AGE = 600  # 10 minutes

# ─── CORS ─────────────────────────────────────────────────────
CORS_ORIGINS = [
    f"https://{SERVER_DOMAIN}",
    "http://localhost:8443",
]

# ─── Version ──────────────────────────────────────────────────
VERSION = "4.0.0"
BUILD_DATE = "2025-01-15"