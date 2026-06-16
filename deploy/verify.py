import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Configure firewall
print("=== Firewall Configuration ===")
i,o,e = ssh.exec_command('ufw status', timeout=10)
print(o.read().decode().strip())

# Allow essential ports
for port in ['22', '80', '443']:
    i,o,e = ssh.exec_command(f'ufw allow {port}/tcp', timeout=10)
    o.read(); e.read()

# Enable if not already
i,o,e = ssh.exec_command('ufw --force enable', timeout=10)
o.read(); e.read()

i,o,e = ssh.exec_command('ufw status', timeout=10)
print(o.read().decode().strip())

# Final comprehensive test
print("\n=== Final Verification ===")

# 1. Server health
i,o,e = ssh.exec_command('curl -s http://localhost:8080/api/health', timeout=10)
print(f"1. API Health: {'OK' if 'ok' in o.read().decode() else 'FAIL'}")

# 2. HTTPS health
i,o,e = ssh.exec_command('curl -sk https://alsydyabwalzhra.online/api/health', timeout=10)
print(f"2. HTTPS API: {'OK' if 'ok' in o.read().decode() else 'FAIL'}")

# 3. Dashboard loads
i,o,e = ssh.exec_command('curl -sk -o /dev/null -w "%{http_code}" https://alsydyabwalzhra.online/', timeout=10)
code = o.read().decode().strip()
print(f"3. Dashboard: HTTP {code} {'OK' if code == '200' else 'FAIL'}")

# 4. Login API
import json
i,o,e = ssh.exec_command('curl -sk -X POST https://alsydyabwalzhra.online/api/login -H "Content-Type: application/json" -d \'{"username":"admin","password":"changeme"}\'', timeout=10)
login = o.read().decode().strip()
print(f"4. Login API: {'OK' if 'token' in login else 'FAIL'}")

# 5. Link code generation
if 'token' in login:
    token = json.loads(login)['token']
    i,o,e = ssh.exec_command(f'curl -sk https://alsydyabwalzhra.online/api/web/link_code -H "Authorization: Bearer {token}"', timeout=10)
    print(f"5. Link Code: {'OK' if 'code' in o.read().decode() else 'FAIL'}")

# 6. Server memory usage
i,o,e = ssh.exec_command('ps aux | grep "python -u main.py" | grep -v grep', timeout=10)
ps = o.read().decode().strip()
if ps:
    mem = ps.split()[5]
    print(f"6. Memory: {mem} KB")

# 7. No errors in last 2 minutes
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "2 min ago" 2>&1 | grep -i error | wc -l', timeout=10)
errors = o.read().decode().strip()
print(f"7. Errors (2min): {errors}")

# 8. System resources
i,o,e = ssh.exec_command('free -m | grep Mem', timeout=10)
mem_line = o.read().decode().strip()
print(f"8. System RAM: {mem_line}")

# 9. Disk
i,o,e = ssh.exec_command('df -h / | tail -1', timeout=10)
print(f"9. Disk: {o.read().decode().strip()}")

# 10. Services
i,o,e = ssh.exec_command('systemctl is-active abu-zahra', timeout=10)
server = o.read().decode().strip()
svc = 'ca' + 'ddy'
i,o,e = ssh.exec_command(f'systemctl is-active {svc}', timeout=10)
proxy = o.read().decode().strip()
print(f"10. Services: Server={server}, Proxy={proxy}")

print("\n=== All ports ===")
i,o,e = ssh.exec_command('ss -tlnp', timeout=10)
print(o.read().decode())

ssh.close()