import paramiko, time, json

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)
sftp = ssh.open_sftp()

# 1. Upload all updated server files
print("=== Uploading updated files ===")
files_to_upload = [
    ('/home/z/my-project/Server/main.py', '/opt/abu-zahra/Server/main.py'),
    ('/home/z/my-project/Server/modules/store.py', '/opt/abu-zahra/Server/modules/store.py'),
    ('/home/z/my-project/Server/modules/firebase_client.py', '/opt/abu-zahra/Server/modules/firebase_client.py'),
    ('/home/z/my-project/Server/modules/api_handlers.py', '/opt/abu-zahra/Server/modules/api_handlers.py'),
    ('/home/z/my-project/Server/modules/config.py', '/opt/abu-zahra/Server/modules/config.py'),
    ('/home/z/my-project/Server/modules/commands.py', '/opt/abu-zahra/Server/modules/commands.py'),
    ('/home/z/my-project/Server/modules/file_storage.py', '/opt/abu-zahra/Server/modules/file_storage.py'),
    ('/home/z/my-project/Server/modules/telegram_bot.py', '/opt/abu-zahra/Server/modules/telegram_bot.py'),
    ('/home/z/my-project/Server/modules/dashboard_html.py', '/opt/abu-zahra/Server/modules/dashboard_html.py'),
    ('/home/z/my-project/Server/modules/__init__.py', '/opt/abu-zahra/Server/modules/__init__.py'),
    ('/home/z/my-project/Server/requirements.txt', '/opt/abu-zahra/Server/requirements.txt'),
]

for local, remote in files_to_upload:
    sftp.put(local, remote)
    print(f"  {local.split('/')[-1]}")

# 2. Write .env with real credentials
print("\n=== Writing .env with real credentials ===")
env_content = """# Abu-Zahra Server Environment - Production
SERVER_HOST=127.0.0.1
SERVER_PORT=8080

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
ADMIN_EMAIL=admin@abuzahra.com

# Telegram Bot
BOT_TOKEN=8743374928:AAEKpv0qFlpKYMJw6y4jukD0CBTXQ6EAsAw
ADMIN_CHAT_ID=7344776596

# Firebase Config
FIREBASE_PROJECT=studio-7073076148-6afe0
FIREBASE_DB_SECRET=

# Firebase RTDB URL (auto-constructed from project ID)
# FIREBASE_RTDB_URL=https://studio-7073076148-6afe0-default-rtdb.firebaseio.com
"""

with sftp.open('/opt/abu-zahra/Server/.env', 'w') as f:
    f.write(env_content)
print("  .env written with Telegram + Firebase credentials")

sftp.close()

# 3. Install dependencies
print("\n=== Installing dependencies ===")
i,o,e = ssh.exec_command('cd /opt/abu-zahra/Server && ./venv/bin/pip install -r requirements.txt -q', timeout=60)
o.read(); e.read(); o.channel.recv_exit_status()
print("  Dependencies OK")

# 4. Clear old data (fresh start with permanent codes)
print("\n=== Resetting data for clean start ===")
i,o,e = ssh.exec_command('rm -f /opt/abu-zahra/data/*.json', timeout=10)
o.read(); e.read()

# 5. Restart server
print("\n=== Restarting server ===")
i,o,e = ssh.exec_command('systemctl restart abu-zahra', timeout=15)
o.read(); e.read(); o.channel.recv_exit_status()
time.sleep(4)

# 6. Check for errors
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager -n 15', timeout=10)
logs = o.read().decode()
print("STARTUP LOGS:")
for line in logs.strip().split('\n'):
    if 'ERROR' in line or 'INFO' in line.split(']')[-1] if ']' in line else False:
        print(f"  {line.strip()}")

# 7. Test login with permanent code
print("\n=== Testing login + permanent code ===")
import json as j
i,o,e = ssh.exec_command('curl -s http://127.0.0.1:8080/api/login -H "Content-Type: application/json" -d \'{"username":"admin","password":"changeme"}\'', timeout=10)
login_result = o.read().decode().strip()
print(f"  Login result: {login_result[:300]}")

# Check if permanent_code is in response
if 'permanent_code' in login_result:
    data = j.loads(login_result)
    print(f"  Permanent Code: {data['permanent_code']} ✅")
else:
    print("  WARNING: permanent_code not in response!")

# 8. Test Firebase connection
print("\n=== Firebase Status ===")
i,o,e = ssh.exec_command('curl -s http://127.0.0.1:8080/api/health', timeout=10)
health = o.read().decode().strip()
print(f"  {health}")

# 9. Test HTTPS
print("\n=== HTTPS Test ===")
i,o,e = ssh.exec_command('curl -sk https://alsydyabwalzhra.online/api/health', timeout=10)
print(f"  {o.read().decode().strip()}")

# 10. Check for any errors
time.sleep(2)
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "10 sec ago" 2>&1 | grep -i error', timeout=10)
errors = o.read().decode().strip()
print(f"\n=== Errors: {'NONE ✅' if not errors else errors} ===")

ssh.close()
print("\n=== Deployment Complete ===")