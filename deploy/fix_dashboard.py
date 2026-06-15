import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)
sftp = ssh.open_sftp()
sftp.put('/home/z/my-project/Server/main.py', '/opt/abu-zahra/Server/main.py')
sftp.close()

i,o,e = ssh.exec_command('systemctl restart abu-zahra', timeout=15)
o.read(); e.read(); o.channel.recv_exit_status()
time.sleep(3)

# Test dashboard
i,o,e = ssh.exec_command('curl -s http://localhost:8080/ 2>&1 | head -5', timeout=10)
dash = o.read().decode().strip()[:300]
print('DASHBOARD:', dash)

# Test HTTPS dashboard
i,o,e = ssh.exec_command('curl -sk https://alsydyabwalzhra.online/ 2>&1 | head -5', timeout=10)
https_dash = o.read().decode().strip()[:300]
print('HTTPS DASHBOARD:', https_dash)

# Health check
i,o,e = ssh.exec_command('curl -s http://localhost:8080/api/health', timeout=10)
print('HEALTH:', o.read().decode().strip())

# Check for errors
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager --since \"10 sec ago\"', timeout=10)
print('LOGS:', o.read().decode().strip())

ssh.close()