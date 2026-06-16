import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('216.128.156.226', username='root', password='E%t7SBQUAL2SE[kc', timeout=15)

# Full startup logs
i,o,e = ssh.exec_command('journalctl -u abu-zahra --no-pager -n 50', timeout=10)
print("=== FULL STARTUP ===")
print(o.read().decode())

# Test Firebase directly from server
print("=== FIREBASE DIRECT TEST ===")
i,o,e = ssh.exec_command('''curl -s -w "\\nHTTP_CODE:%{http_code}" "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/.json"''', timeout=15)
print(o.read().decode()[:500])

# Test with auth parameter
print("\n=== FIREBASE WITH EMPTY AUTH ===")
i,o,e = ssh.exec_command('''curl -s -w "\\nHTTP_CODE:%{http_code}" "https://studio-7073076148-6afe0-default-rtdb.firebaseio.com/.json?auth="''', timeout=15)
print(o.read().decode()[:500])

# Check Telegram bot API directly
print("\n=== TELEGRAM BOT TEST ===")
i,o,e = ssh.exec_command('''curl -s "https://api.telegram.org/bot8743374928:AAEKpv0qFlpKYMJw6y4jukD0CBTXQ6EAsAw/getMe"''', timeout=15)
print(o.read().decode()[:300])

ssh.close()