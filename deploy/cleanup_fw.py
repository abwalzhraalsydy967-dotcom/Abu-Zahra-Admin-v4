import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Remove unnecessary firewall rules
for port in ['81', '8443']:
    i,o,e = ssh.exec_command(f'ufw delete allow {port}/tcp', timeout=10)
    o.read(); e.read(); o.channel.recv_exit_status()
    
# Delete v6 duplicates
for port in ['81', '8443']:
    i,o,e = ssh.exec_command(f'ufw delete allow {port}/tcp', timeout=10)
    o.read(); e.read(); o.channel.recv_exit_status()

print("=== Firewall (cleaned) ===")
i,o,e = ssh.exec_command('ufw status', timeout=10)
print(o.read().decode())

# Check what the "errors" are
print("=== Recent Errors ===")
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "5 min ago" 2>&1 | grep -i "error\\|warning\\|traceback" | head -10', timeout=10)
print(o.read().decode())

# Check that device monitor is running without errors
time.sleep(2)
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since "10 sec ago"', timeout=10)
print("=== Latest logs ===")
print(o.read().decode().strip())

ssh.close()