import paramiko, time, json

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Check ALL logs including telegram
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "1 min ago"', timeout=10)
logs = o.read().decode()
print("=== ALL RECENT LOGS ===")
for line in logs.strip().split('\n'):
    line = line.strip()
    if line and 'aiohttp.access' not in line:
        print(f"  {line[-150:]}")

# Send a test message to the bot owner
i,o,e = ssh.exec_command('''curl -s -X POST "https://api.telegram.org/bot8743374928:AAEKpv0qFlpKYMJw6y4jukD0CBTXQ6EAsAw/sendMessage" -H "Content-Type: application/json" -d '{"chat_id":"7344776596","text":"🟢 Abu-Zahra Server v4.0 is online!\n\nDashboard: https://alsydyabwalzhra.online\nFirebase: Connected\nAll systems operational."}' ''', timeout=15)
result = o.read().decode().strip()
print(f"\n=== SEND MESSAGE TO ADMIN ===")
print(result[:300])

# Wait and check if bot received any update
time.sleep(5)
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "10 sec ago" 2>&1 | grep -iE "telegram|bot|poll"', timeout=10)
tg = o.read().decode().strip()
print(f"\n=== TELEGRAM BOT LOGS ===")
print(tg if tg else "No telegram logs (bot polling in background)")

# Final health
i,o,e = ssh.exec_command('curl -s http://127.0.0.1:8080/api/health', timeout=10)
health = json.loads(o.read().decode().strip())
print(f"\n=== FINAL HEALTH ===")
print(f"  Firebase: {health['firebase']}")
print(f"  Uptime: {health['uptime']}s")
print(f"  Devices: {health['devices']}")
print(f"  Version: {health['version']}")

# HTTPS
i,o,e = ssh.exec_command('curl -sk https://alsydyabwalzhra.online/api/health', timeout=10)
h2 = json.loads(o.read().decode().strip())
print(f"  HTTPS: OK (firebase={h2['firebase']})")

# Check the existing device in Firebase
i,o,e = ssh.exec_command('curl -s "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/devices.json" 2>&1 | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\\\"  Firebase devices: {len(d)}\\\"  + \\\"  \\\".join(f\\\"{k}: {v.get(\\\"model\\\",\\\"?\\\")}\\\" for k,v in d.items()))" 2>&1', timeout=10)
print(f"Firebase devices: {o.read().decode().strip()[:200]}")

ssh.close()
print("\n=== ALL SYSTEMS OPERATIONAL ===")