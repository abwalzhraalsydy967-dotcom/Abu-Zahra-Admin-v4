#!/usr/bin/env python3
import paramiko
import sys

HOST = "216.128.156.226"
USER = "root"
PASS = "E%t7SBQUAL2SE[kc"

cmd = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else "echo ok"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASS, timeout=15)
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=120)
# Read all in binary mode to avoid encoding issues  
out = stdout.read().decode()
err = stderr.read().decode()
code = stdout.channel.recv_exit_status()
# Print only clean output
lines = []
for line in (out + '\n' + err).split('\n'):
    if 'can not execute caddy' not in line:
        lines.append(line)
print('\n'.join(l for l in lines if l.strip()))
ssh.close()