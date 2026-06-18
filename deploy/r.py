#!/usr/bin/env python3
"""Run SSH commands on VPS, suppressing Caddy snap warnings."""
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
out = stdout.read().decode('utf-8', errors='replace')
err = stderr.read().decode('utf-8', errors='replace')
code = stdout.channel.recv_exit_status()

# Filter out caddy snap noise
for line in (out + err).split('\n'):
    line = line.strip()
    if line and 'can not execute caddy' not in line.lower():
        print(line)
ssh.close()
sys.exit(code)