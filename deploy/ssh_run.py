#!/usr/bin/env python3
"""Connect to VPS and run a single command."""
import paramiko
import sys

HOST = "216.128.156.226"
USER = "root"
PASS = "E%t7SBQUAL2SE[kc"

def run(cmd, timeout=120):
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    try:
        stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode('utf-8', errors='replace')
        err = stderr.read().decode('utf-8', errors='replace')
        code = stdout.channel.recv_exit_status()
        print(out)
        if err.strip() and code != 0:
            print(f"[STDERR] {err}")
        return code
    finally:
        ssh.close()

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "echo 'no command'"
    sys.exit(run(cmd))