import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)
sftp = ssh.open_sftp()

# Upload fixed main.py
sftp.put('/home/z/my-project/Server/main.py', '/opt/abu-zahra/Server/main.py')
print("main.py uploaded")

# Also upload the fixed store.py (with _ip_device_map)
sftp.put('/home/z/my-project/Server/modules/store.py', '/opt/abu-zahra/Server/modules/store.py')
print("store.py uploaded")

# Also upload the fixed config.py (with .env support)
sftp.put('/home/z/my-project/Server/modules/config.py', '/opt/abu-zahra/Server/modules/config.py')
print("config.py uploaded")

# Also upload the updated requirements.txt
sftp.put('/home/z/my-project/Server/requirements.txt', '/opt/abu-zahra/Server/requirements.txt')
print("requirements.txt uploaded")

sftp.close()

# Install any new deps and restart
i,o,e = ssh.exec_command('cd /opt/abu-zahra/Server && ./venv/bin/pip install -r requirements.txt -q', timeout=60)
o.read(); e.read(); o.channel.recv_exit_status()
print("Dependencies updated")

i,o,e = ssh.exec_command('systemctl restart abu-zahra', timeout=15)
o.read(); e.read(); o.channel.recv_exit_status()
print("Service restarted")

time.sleep(3)

# Check status
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager -n 10', timeout=10)
print(o.read().decode())

# Test health
i,o,e = ssh.exec_command('curl -s http://localhost:8080/api/health', timeout=10)
print('HEALTH:', o.read().decode().strip())

ssh.close()
print("Done!")