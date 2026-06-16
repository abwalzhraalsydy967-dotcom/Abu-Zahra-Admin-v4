#!/usr/bin/env python3
"""Upload server files to VPS via SFTP."""
import paramiko
import os
import sys

HOST = "216.128.156.226"
USER = "root"
PASS = "E%t7SBQUAL2SE[kc"

LOCAL_SERVER_DIR = "/home/z/my-project/Server"
REMOTE_DIR = "/opt/abu-zahra/Server"

def upload_dir(sftp, local_dir, remote_dir):
    for root, dirs, files in os.walk(local_dir):
        dirs[:] = [d for d in dirs if d not in ('__pycache__', '.git', 'venv', 'node_modules', '.next')]
        rel = os.path.relpath(root, local_dir)
        remote_path = f"{remote_dir}/{rel}" if rel != '.' else remote_dir
        try:
            sftp.stat(remote_path)
        except FileNotFoundError:
            sftp.mkdir(remote_path)
        for f in files:
            if f.endswith(('.pyc', '.pyo')):
                continue
            local_file = os.path.join(root, f)
            remote_file = f"{remote_path}/{f}"
            print(f"  {os.path.relpath(local_file, local_dir)}")
            sftp.put(local_file, remote_file)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    sftp = ssh.open_sftp()
    
    try:
        # Create directories (parent first)
        for d in ["/opt/abu-zahra", REMOTE_DIR, f"{REMOTE_DIR}/modules", "/opt/abu-zahra/data", "/opt/abu-zahra/data/uploads", "/opt/abu-zahra/data/temp"]:
            try:
                sftp.stat(d)
            except FileNotFoundError:
                try:
                    sftp.mkdir(d)
                    print(f"Created: {d}")
                except IOError:
                    pass
        
        # Upload server files
        print("Uploading Server files:")
        upload_dir(sftp, LOCAL_SERVER_DIR, REMOTE_DIR)
        
        # Create .env
        env = """# Abu-Zahra Server Environment
SERVER_HOST=0.0.0.0
SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
ADMIN_EMAIL=admin@abuzahra.com
BOT_TOKEN=YOUR_BOT_TOKEN_HERE
ADMIN_CHAT_ID=YOUR_CHAT_ID_HERE
FIREBASE_PROJECT=YOUR_FIREBASE_PROJECT_ID
FIREBASE_DB_SECRET=
"""
        with sftp.open(f"{REMOTE_DIR}/.env", 'w') as f:
            f.write(env)
        print("  .env created")
        
        # Create systemd service
        service = f"""[Unit]
Description=Abu-Zahra Server v4.0
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory={REMOTE_DIR}
ExecStart={REMOTE_DIR}/venv/bin/python -u main.py
Restart=always
RestartSec=5
EnvironmentFile={REMOTE_DIR}/.env
StandardOutput=journal
StandardError=journal
SyslogIdentifier=abu-zahra
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
"""
        with sftp.open("/etc/systemd/system/abu-zahra.service", 'w') as f:
            f.write(service)
        print("  systemd service created")
        
        # Create Caddyfile
        caddy = """alsydyabwalzhra.online {
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
            f.write(caddy)
        print("  Caddyfile created")
        
        print("\nAll files uploaded!")
    finally:
        sftp.close()
        ssh.close()

if __name__ == "__main__":
    main()