import paramiko, time, json

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)
sftp = ssh.open_sftp()

# Upload fixed files
for f in ['main.py', 'modules/api_handlers.py', 'modules/telegram_bot.py']:
    local = f'/home/z/my-project/Server/{f}'
    remote = f'/opt/abu-zahra/Server/{f}'
    sftp.put(local, remote)
    print(f"  Uploaded {f}")
sftp.close()

# Clear old data and restart
i,o,e = ssh.exec_command('rm -f /opt/abu-zahra/data/*.json && systemctl restart abu-zahra', timeout=15)
o.read(); e.read(); o.channel.recv_exit_status()
time.sleep(5)

# Check startup
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager -n 20', timeout=10)
for line in o.read().decode().strip().split('\n'):
    if any(k in line for k in ['INFO', 'ERROR', 'WARNING']):
        print(f"  {line.strip()[-130:]}")

# Test login + Firebase sync
i,o,e = ssh.exec_command('''curl -s http://127.0.0.1:8080/api/login -H "Content-Type: application/json" -d '{"username":"admin","password":"changeme"}' ''', timeout=10)
login = json.loads(o.read().decode().strip())
print(f"\nLogin: code={login.get('permanent_code')}")

# Check Firebase for synced code
time.sleep(2)
safe_email = "admin_at_abuzahra_com"
i,o,e = ssh.exec_command(f'curl -s "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/permanent_codes/{safe_email}.json"', timeout=10)
fb_code = o.read().decode().strip()
print(f"Firebase permanent_codes: {fb_code[:200]}")

# Check health
i,o,e = ssh.exec_command('curl -s http://127.0.0.1:8080/api/health', timeout=10)
health = json.loads(o.read().decode().strip())
print(f"\nHealth: firebase={health['firebase']}")

# Check Telegram
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "10 sec ago" 2>&1 | grep -iE "telegram|bot"', timeout=10)
tg = o.read().decode().strip()
print(f"Telegram: {tg[-200:] if tg else 'checking...'}")
time.sleep(5)
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "15 sec ago" 2>&1 | grep -iE "telegram|bot"', timeout=10)
tg = o.read().decode().strip()
print(f"Telegram (delayed): {tg[-300:] if tg else 'No telegram activity in logs (bot may be polling silently)'}")

# Check for errors
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "30 sec ago" 2>&1 | grep -i error', timeout=10)
errors = o.read().decode().strip()
print(f"\nErrors: {'NONE' if not errors else errors}")

ssh.close()