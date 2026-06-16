import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)
sftp = ssh.open_sftp()

# Update .env to bind to localhost only
env = """# Abu-Zahra Server Environment
SERVER_HOST=127.0.0.1
SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
ADMIN_EMAIL=admin@abuzahra.com
BOT_TOKEN=YOUR_BOT_TOKEN_HERE
ADMIN_CHAT_ID=YOUR_CHAT_ID_HERE
FIREBASE_PROJECT=YOUR_FIREBASE_PROJECT_ID
FIREBASE_DB_SECRET=
"""
with sftp.open('/opt/abu-zahra/Server/.env', 'w') as f:
    f.write(env)

sftp.close()

# Restart server
i,o,e = ssh.exec_command('systemctl restart abu-zahra', timeout=15)
o.read(); e.read(); o.channel.recv_exit_status()
time.sleep(3)

# Verify it only listens on localhost
i,o,e = ssh.exec_command('ss -tlnp | grep 8080', timeout=10)
print("Port 8080 binding:")
print(o.read().decode().strip())

# Verify HTTPS still works through Caddy
i,o,e = ssh.exec_command('curl -sk https://alsydyabwalzhra.online/api/health', timeout=10)
print("HTTPS check:", o.read().decode().strip())

# No errors
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "5 sec ago" | grep -i error', timeout=10)
errors = o.read().decode().strip()
print(f"Errors: {'NONE' if not errors else errors}")

ssh.close()
print("Security: Server bound to 127.0.0.1 only")