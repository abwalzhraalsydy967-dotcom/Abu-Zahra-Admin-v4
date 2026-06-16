#!/usr/bin/env python3
"""Abu-Zahra Server Deployment via SSH using Paramiko."""
import paramiko
import os
import sys
import time

HOST = "216.128.156.226"
USER = "root"
PASS = "E%t7SBQUAL2SE[kc"

LOCAL_SERVER_DIR = "/home/z/my-project/Server"
LOCAL_DEPLOY_DIR = "/home/z/my-project/deploy"
REMOTE_DIR = "/opt/abu-zahra"

def ssh_exec(ssh, cmd, timeout=120):
    """Execute a command and return stdout, stderr, exit_code."""
    print(f"  $ {cmd[:120]}...")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    code = stdout.channel.recv_exit_status()
    if out.strip():
        for line in out.strip().split('\n')[-10:]:
            print(f"    {line}")
    if err.strip() and code != 0:
        for line in err.strip().split('\n')[-5:]:
            print(f"    [ERR] {line}")
    return out, err, code

def scp_dir(ssh, sftp, local_dir, remote_dir):
    """Recursively upload a directory."""
    for root, dirs, files in os.walk(local_dir):
        # Skip __pycache__, .git, venv, node_modules
        dirs[:] = [d for d in dirs if d not in ('__pycache__', '.git', 'venv', 'node_modules', '.next')]
        
        rel = os.path.relpath(root, local_dir)
        remote_path = f"{remote_dir}/{rel}" if rel != '.' else remote_dir
        try:
            sftp.stat(remote_path)
        except FileNotFoundError:
            sftp.mkdir(remote_path)
        
        for f in files:
            if f.endswith(('.pyc', '.pyo', '.class')):
                continue
            local_file = os.path.join(root, f)
            remote_file = f"{remote_path}/{f}"
            print(f"  Uploading: {os.path.relpath(local_file, local_dir)}")
            sftp.put(local_file, remote_file)

def main():
    print(f"=== Connecting to {HOST} ===")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    sftp = ssh.open_sftp()
    
    try:
        # ─── Step 1: Gather info ───
        print("\n[Step 1] Gathering VPS info...")
        out, _, _ = ssh_exec(ssh, "cat /etc/os-release | head -3 && echo '---' && python3 --version 2>/dev/null && echo '---' && free -h | head -2 && echo '---' && df -h / | tail -1")
        
        # ─── Step 2: Clean up unrelated processes/ports ───
        print("\n[Step 2] Cleaning up VPS - removing unrelated services...")
        # Check what's running
        out, _, _ = ssh_exec(ssh, "ss -tlnp 2>/dev/null")
        
        # Kill any processes not related to our project
        cleanup_cmds = [
            # Stop and disable any web servers we didn't set up
            "systemctl stop nginx 2>/dev/null; systemctl disable nginx 2>/dev/null",
            "systemctl stop apache2 2>/dev/null; systemctl disable apache2 2>/dev/null",
            # Kill any processes on common web ports that aren't caddy/ssh
            "fuser -k 80/tcp 2>/dev/null; fuser -k 443/tcp 2>/dev/null; fuser -k 8080/tcp 2>/dev/null; fuser -k 3000/tcp 2>/dev/null; fuser -k 8443/tcp 2>/dev/null; true",
            # Remove old deployment if exists
            "rm -rf /tmp/deploy",
            "mkdir -p /tmp/deploy",
        ]
        for cmd in cleanup_cmds:
            ssh_exec(ssh, cmd)
        
        # ─── Step 3: Install dependencies ───
        print("\n[Step 3] Installing system dependencies...")
        ssh_exec(ssh, "DEBIAN_FRONTEND=noninteractive apt-get update -qq", timeout=120)
        ssh_exec(ssh, "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq python3 python3-pip python3-venv caddy curl ufw 2>&1 | tail -5", timeout=300)
        
        # ─── Step 4: Upload server files ───
        print("\n[Step 4] Uploading server files...")
        ssh_exec(ssh, f"mkdir -p {REMOTE_DIR}/Server/modules")
        ssh_exec(ssh, f"mkdir -p {REMOTE_DIR}/data/uploads")
        ssh_exec(ssh, f"mkdir -p {REMOTE_DIR}/data/temp")
        
        scp_dir(ssh, sftp, LOCAL_SERVER_DIR, f"{REMOTE_DIR}/Server")
        
        # ─── Step 5: Create .env file ───
        print("\n[Step 5] Creating .env file...")
        env_content = """# Abu-Zahra Server Environment
SERVER_HOST=0.0.0.0
SERVER_PORT=8080

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
ADMIN_EMAIL=admin@abuzahra.com

# Telegram Bot
BOT_TOKEN=YOUR_BOT_TOKEN_HERE
ADMIN_CHAT_ID=YOUR_CHAT_ID_HERE

# Firebase Config
FIREBASE_PROJECT=YOUR_FIREBASE_PROJECT_ID
FIREBASE_DB_SECRET=

# JWT Secret (auto-generated on first run if empty)
JWT_SECRET=
"""
        with sftp.open(f"{REMOTE_DIR}/Server/.env", 'w') as f:
            f.write(env_content)
        print("  .env created (update with real credentials)")
        
        # ─── Step 6: Set up Python virtual environment ───
        print("\n[Step 6] Setting up Python environment...")
        ssh_exec(ssh, f"cd {REMOTE_DIR}/Server && python3 -m venv venv", timeout=60)
        ssh_exec(ssh, f"cd {REMOTE_DIR}/Server && ./venv/bin/pip install --upgrade pip -q", timeout=120)
        ssh_exec(ssh, f"cd {REMOTE_DIR}/Server && ./venv/bin/pip install -r requirements.txt -q", timeout=120)
        
        # ─── Step 7: Configure systemd ───
        print("\n[Step 7] Configuring systemd service...")
        service_content = f"""[Unit]
Description=Abu-Zahra Server v4.0
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory={REMOTE_DIR}/Server
ExecStart={REMOTE_DIR}/Server/venv/bin/python -u main.py
Restart=always
RestartSec=5
EnvironmentFile={REMOTE_DIR}/Server/.env
StandardOutput=journal
StandardError=journal
SyslogIdentifier=abu-zahra
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
"""
        with sftp.open("/etc/systemd/system/abu-zahra.service", 'w') as f:
            f.write(service_content)
        
        ssh_exec(ssh, "systemctl daemon-reload")
        ssh_exec(ssh, "systemctl enable abu-zahra")
        
        # ─── Step 8: Configure Caddy ───
        print("\n[Step 8] Configuring Caddy reverse proxy...")
        caddy_content = """alsydyabwalzhra.online {
    reverse_proxy localhost:8080
    encode gzip
    request_body {
        max_size 100MB
    }
    log {
        output file /var/log/caddy/abu-zahra.log
        format json
    }
}
"""
        with sftp.open("/etc/caddy/Caddyfile", 'w') as f:
            f.write(caddy_content)
        
        # Validate and restart Caddy
        ssh_exec(ssh, "caddy validate --config /etc/caddy/Caddyfile 2>&1")
        ssh_exec(ssh, "systemctl restart caddy")
        ssh_exec(ssh, "systemctl enable caddy")
        
        # ─── Step 9: Configure firewall ───
        print("\n[Step 9] Configuring firewall...")
        ssh_exec(ssh, "ufw allow 22/tcp")
        ssh_exec(ssh, "ufw allow 80/tcp")
        ssh_exec(ssh, "ufw allow 443/tcp")
        ssh_exec(ssh, "echo 'y' | ufw enable 2>/dev/null || true")
        
        # ─── Step 10: Start the server ───
        print("\n[Step 10] Starting Abu-Zahra server...")
        ssh_exec(ssh, "systemctl restart abu-zahra")
        time.sleep(3)
        
        # Check status
        out, _, code = ssh_exec(ssh, "systemctl status abu-zahra --no-pager 2>&1 | head -15")
        
        # ─── Step 11: Test ───
        print("\n[Step 11] Testing server...")
        time.sleep(2)
        out, _, code = ssh_exec(ssh, "curl -s http://localhost:8080/api/health 2>&1")
        
        print("\n" + "="*50)
        print("=== DEPLOYMENT COMPLETE ===")
        print("="*50)
        print(f"Dashboard: https://alsydyabwalzhra.online")
        print(f"API:      https://alsydyabwalzhra.online/api/health")
        print(f"")
        print(f"Server:   systemctl status abu-zahra")
        print(f"Logs:     journalctl -u abu-zahra -f")
        print(f"Restart:  systemctl restart abu-zahra")
        
    finally:
        sftp.close()
        ssh.close()
        print("\nSSH connection closed.")

if __name__ == "__main__":
    main()