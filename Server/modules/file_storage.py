"""
File Storage Module - Temporary server-side file storage.
Upload → temporary hold → download/view → auto-delete.
Files are NOT stored in Firebase; only on the server.
"""

import os
import time
import uuid
import asyncio
import logging
import base64
from datetime import datetime, timedelta

from .config import UPLOADS_DIR, TEMP_DIR, MAX_UPLOAD_SIZE, FILE_TEMP_EXPIRE_SECONDS, FILE_CLEANUP_INTERVAL
from .store import store

logger = logging.getLogger("file_storage")


async def save_upload(device_id: str, file_data: bytes, filename: str,
                      file_type: str = "file", command_id: str = None,
                      caption: str = "") -> dict:
    """Save an uploaded file to temporary storage. Returns file metadata."""
    # Validate size
    if len(file_data) > MAX_UPLOAD_SIZE:
        raise ValueError(f"File too large: {len(file_data)} bytes (max {MAX_UPLOAD_SIZE})")
    
    # Sanitize filename
    safe_name = os.path.basename(filename)
    if not safe_name:
        safe_name = f"file_{uuid.uuid4().hex[:8]}"
    
    # Create device-specific subdirectory
    device_dir = os.path.join(UPLOADS_DIR, device_id.replace('/', '_').replace('\\', '_'))
    os.makedirs(device_dir, exist_ok=True)
    
    # Generate unique filename to avoid collisions
    file_id = str(uuid.uuid4())
    stored_name = f"{file_id}_{safe_name}"
    file_path = os.path.join(device_dir, stored_name)
    
    # Write file
    with open(file_path, 'wb') as f:
        f.write(file_data)
    
    # Track in store
    file_meta = await store.add_file(
        device_id=device_id,
        filename=stored_name,
        file_type=file_type,
        size=len(file_data),
        command_id=command_id,
        caption=caption,
    )
    file_meta['path'] = file_path
    
    return file_meta


async def save_base64_upload(device_id: str, base64_data: str, filename: str,
                              file_type: str = "file", command_id: str = None,
                              caption: str = "") -> dict:
    """Save a base64-encoded file to temporary storage."""
    try:
        file_bytes = base64.b64decode(base64_data)
    except Exception:
        raise ValueError("Invalid base64 data")
    return await save_upload(device_id, file_bytes, filename, file_type, command_id, caption)


async def get_file(file_id: str) -> tuple:
    """Get file bytes and metadata. Returns (bytes, metadata) or (None, None)."""
    file_meta = await store.get_file(file_id)
    if not file_meta:
        return None, None
    
    path = file_meta.get('path', '')
    if not path or not os.path.exists(path):
        return None, None
    
    try:
        with open(path, 'rb') as f:
            data = f.read()
        # Mark as retrieved (will be auto-deleted on next cleanup)
        await store.mark_file_retrieved(file_id)
        return data, file_meta
    except Exception as e:
        logger.error(f"Error reading file {file_id}: {e}")
        return None, None


async def get_file_by_device(device_id: str, filename: str) -> tuple:
    """Get a file by device_id and filename."""
    for fid, fmeta in store.files.items():
        if fmeta.get('device_id') == device_id and fmeta.get('filename') == filename:
            return await get_file(fid)
    return None, None


def get_file_path(device_id: str, filename: str) -> str:
    """Get the file path on disk for a device's file."""
    device_dir = os.path.join(UPLOADS_DIR, device_id.replace('/', '_').replace('\\', '_'))
    return os.path.join(device_dir, filename)


async def delete_file(file_id: str) -> bool:
    """Delete a file from storage."""
    file_meta = await store.get_file(file_id)
    if not file_meta:
        return False
    
    path = file_meta.get('path', '')
    if path and os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass
    
    if file_id in store.files:
        del store.files[file_id]
    return True


async def cleanup_loop():
    """Background task: periodically clean up expired and retrieved files."""
    while True:
        try:
            await asyncio.sleep(FILE_CLEANUP_INTERVAL)
            await store.cleanup_expired_files()
            logger.debug("File cleanup completed")
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"File cleanup error: {e}")
            await asyncio.sleep(30)


def get_storage_stats() -> dict:
    """Get storage statistics."""
    total_size = 0
    file_count = 0
    for root, dirs, files in os.walk(UPLOADS_DIR):
        for f in files:
            path = os.path.join(root, f)
            try:
                total_size += os.path.getsize(path)
                file_count += 1
            except OSError:
                pass
    
    return {
        "total_files": file_count,
        "total_size": total_size,
        "total_size_mb": round(total_size / (1024 * 1024), 2),
        "active_files": len(store.files) if hasattr(store, 'files') else 0,
        "uploads_dir": UPLOADS_DIR,
    }