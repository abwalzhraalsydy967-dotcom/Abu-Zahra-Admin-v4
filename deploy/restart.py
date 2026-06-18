import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

def run(cmd, t=15):
    i,o,e = ssh.exec_command(cmd, timeout=t)
    out = o.read().decode().strip()
    err = e.read().decode().strip()
    code = o.channel.recv_exit_status()
    if out: print(f'  {out[:300]}')
    if err and code != 0: print(f'  [ERR] {err[:300]}')
    return code

print('=== Restarting web ===')
run('systemctl restart abu-zahra-web')
time.sleep(3)
print('=== Restarting caddy ===')
run('systemctl restart caddy')
time.sleep(2)
print('=== Tests ===')
run('curl -sk -o /dev/null -w "Dashboard: %{http_code}\n" https://alsydyabwalzhra.online/', 10)
run('curl -sk https://alsydyabwalzhra.online/api/health', 10)
run('curl -sk -o /dev/null -w "MobileAuth: %{http_code}\n" https://alsydyabwalzhra.online/mobile-auth', 10)

ssh.close()
print('Done')