import paramiko, json, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Wait for Telegram bot
time.sleep(3)

# Check Telegram status
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "30 sec ago" 2>&1 | grep -iE "telegram|error"', timeout=10)
print("RECENT LOGS:")
for line in o.read().decode().strip().split('\n'):
    if line.strip():
        print(f"  {line.strip()[-120:]}")

# Login test
i,o,e = ssh.exec_command('''curl -s http://127.0.0.1:8080/api/login -H "Content-Type: application/json" -d '{"username":"admin","password":"changeme"}' ''', timeout=10)
login = json.loads(o.read().decode().strip())
print(f"\nLOGIN: email={login.get('email')}, code={login.get('permanent_code')}")

# Regenerate code
token = login['token']
i,o,e = ssh.exec_command(f'''curl -s -X POST http://127.0.0.1:8080/api/web/regenerate_code -H "Authorization: Bearer {token}"''', timeout=10)
regen = json.loads(o.read().decode().strip())
print(f"REGENERATE: {regen}")

# Create user
i,o,e = ssh.exec_command(f'''curl -s -X POST http://127.0.0.1:8080/api/web/users -H "Authorization: Bearer {token}" -H "Content-Type: application/json" -d '{{"email":"test@test.com","username":"testuser","password":"test123"}}' ''', timeout=10)
new_user = json.loads(o.read().decode().strip())
print(f"NEW USER: ok={new_user.get('ok')}, permanent_code={new_user.get('user',{}).get('permanent_link_code','N/A')}")

# Login as test user
i,o,e = ssh.exec_command('''curl -s http://127.0.0.1:8080/api/login -H "Content-Type: application/json" -d '{"username":"testuser","password":"test123"}' ''', timeout=10)
test_login = json.loads(o.read().decode().strip())
print(f"TEST LOGIN: code={test_login.get('permanent_code')}, email={test_login.get('email')}")

# Dashboard
i,o,e = ssh.exec_command('curl -sk -o /dev/null -w "%{http_code}" https://alsydyabwalzhra.online/', timeout=10)
print(f"\nDASHBOARD: HTTP {o.read().decode().strip()}")

# Health
i,o,e = ssh.exec_command('curl -s http://127.0.0.1:8080/api/health', timeout=10)
health = json.loads(o.read().decode().strip())
print(f"HEALTH: firebase={health['firebase']}, uptime={health['uptime']}s")

# Verify admin permanent code in Firebase
i,o,e = ssh.exec_command('curl -s "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/permanent_codes/admin_at_abuzahra_com.json"', timeout=10)
fb_data = o.read().decode().strip()
print(f"\nFIREBASE permanent_codes: {fb_data[:200]}")

# Check code_to_email reverse mapping
admin_code = test_login.get('permanent_code') or login.get('permanent_code')
i,o,e = ssh.exec_command(f'curl -s "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/code_to_email/{login.get("permanent_code","")}.json"', timeout=10)
fb_reverse = o.read().decode().strip()
print(f"FIREBASE code_to_email: {fb_reverse[:200]}")

# Test Telegram bot connectivity
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "1 min ago" 2>&1 | grep -iE "telegram|bot" | tail -5', timeout=10)
tg_logs = o.read().decode().strip()
print(f"\nTELEGRAM: {tg_logs[-300:] if tg_logs else 'No telegram activity'}")

ssh.close()