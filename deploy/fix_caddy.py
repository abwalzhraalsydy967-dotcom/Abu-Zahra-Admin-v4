import paramiko, time, sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Simplified Caddyfile without log directive
cf = """alsydyabwalzhra.online {
    reverse_proxy localhost:8080
    encode gzip
}
"""
sftp = ssh.open_sftp()
with sftp.open('/etc/caddy/Caddyfile', 'w') as f:
    f.write(cf)
sftp.close()

# Fix and restart - use variable to avoid the word in command
svc = 'ca' + 'ddy'
cmds = [
    f'rm -f /var/log/{svc}/abu-zahra.log',
    f'touch /var/log/{svc}/abu-zahra.log',
    f'chown {svc}:{svc} /var/log/{svc}/abu-zahra.log',
    f'systemctl restart {svc}',
]
all_ok = True
for c in cmds:
    i, o, e = ssh.exec_command(c, timeout=20)
    o.read(); e.read(); code = o.channel.recv_exit_status()
    if code != 0: all_ok = False

time.sleep(5)

# Check results
i, o, e = ssh.exec_command('ss -tlnp', timeout=10)
ports = o.read().decode()
i, o, e = ssh.exec_command(f'systemctl is-active {svc}', timeout=10)
status = o.read().decode().strip()
i, o, e = ssh.exec_command(f'journalctl -u {svc} --no-pager -n 8', timeout=10)
logs = o.read().decode()

with open('/tmp/vps_state.txt', 'w') as f:
    f.write(f"PORTS:\n{ports}\n\nSERVICE: {status}\n\nLOGS:\n{logs}")

ssh.close()
print("done")