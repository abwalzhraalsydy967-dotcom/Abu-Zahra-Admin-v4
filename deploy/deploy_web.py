#!/usr/bin/env python3
"""Deploy web dashboard to VPS"""
import paramiko, time, os

HOST = "216.128.156.226"
USER = "root"
PASS = "E%t7SBQUAL2SE[kc"

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    sftp = ssh.open_sftp()

    def run(cmd, timeout=120):
        i, o, e = ssh.exec_command(cmd, timeout=timeout)
        out = o.read().decode('utf-8', errors='replace').strip()
        err = e.read().decode('utf-8', errors='replace').strip()
        code = o.channel.recv_exit_status()
        for line in out.split('\n')[-10:]:
            print(f"  {line}")
        if err and code != 0:
            for line in err.split('\n')[-5:]:
                print(f"  [ERR] {line}")
        return out, err, code

    def ensure_dir(remote_path):
        remote_dir = '/'.join(remote_path.split('/')[:-1])
        try:
            sftp.stat(remote_dir)
        except FileNotFoundError:
            parts = remote_dir.split('/')
            for i in range(3, len(parts) + 1):
                d = '/'.join(parts[:i])
                try:
                    sftp.stat(d)
                except FileNotFoundError:
                    try:
                        sftp.mkdir(d)
                    except Exception:
                        pass

    # 1. Fix Caddy config
    print("=== 1. Fixing Caddy config ===")
    caddy_config = """alsydyabwalzhra.online {
    handle /api/* {
        reverse_proxy localhost:8080
    }
    handle /_next/* {
        reverse_proxy localhost:3001
    }
    handle /mobile-auth {
        reverse_proxy localhost:3001
    }
    handle {
        reverse_proxy localhost:3001
    }
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
    with sftp.open('/etc/caddy/Caddyfile', 'w') as f:
        f.write(caddy_config)
    print("  Done")

    # 2. Update .env files
    print("\n=== 2. Updating env files ===")
    env_local = """NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSyDylCf1mP48Py3n9vHw-MFtAovbiC6iLQk
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=abwalzhraalsydy-62ccf.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=abwalzhraalsydy-62ccf
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=abwalzhraalsydy-62ccf.firebasestorage.app
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=159319780620
NEXT_PUBLIC_FIREBASE_APP_ID=1:159319780620:web:ec599a59dfefd278f52d29
NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID=159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com
NEXT_PUBLIC_SERVER_URL=https://alsydyabwalzhra.online
"""
    with sftp.open('/opt/abu-zahra/web-dashboard/.env.local', 'w') as f:
        f.write(env_local)

    server_env = """SERVER_HOST=0.0.0.0
SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
ADMIN_EMAIL=admin@abuzahra.com
BOT_TOKEN=8743374928:AAEKpv0qFlpKYMJw6y4jukD0CBTXQ6EAsAw
ADMIN_CHAT_ID=7344776596
FIREBASE_PROJECT=abwalzhraalsydy-62ccf
FIREBASE_DB_SECRET=
"""
    with sftp.open('/opt/abu-zahra/Server/.env', 'w') as f:
        f.write(server_env)
    print("  Done")

    # 3. Upload Next.js source files
    print("\n=== 3. Uploading files ===")
    src_dir = '/home/z/my-project/src'
    for root, dirs, files in os.walk(src_dir):
        dirs[:] = [d for d in dirs if d not in ('node_modules', '.next', '__pycache__')]
        for f in files:
            if f.endswith(('.tsx', '.ts', '.css')):
                local = os.path.join(root, f)
                rel = os.path.relpath(local, '/home/z/my-project')
                remote = f'/opt/abu-zahra/web-dashboard/{rel}'
                ensure_dir(remote)
                sftp.put(local, remote)
                print(f"  {rel}")

    for f in ['package.json', 'next.config.ts', 'tsconfig.json', 'postcss.config.mjs', 'components.json']:
        local = f'/home/z/my-project/{f}'
        if os.path.exists(local):
            sftp.put(local, f'/opt/abu-zahra/web-dashboard/{f}')
            print(f"  {f}")

    # 4. Install deps and build
    print("\n=== 4. Installing deps ===")
    run('cd /opt/abu-zahra/web-dashboard && export PATH=$HOME/.bun/bin:$PATH && bun install 2>&1 | tail -3', timeout=120)

    print("\n=== 5. Building Next.js ===")
    run('cd /opt/abu-zahra/web-dashboard && export PATH=$HOME/.bun/bin:$PATH && NODE_OPTIONS="--max-old-space-size=512" bun run build 2>&1 | tail -20', timeout=300)

    # 6. Restart
    print("\n=== 6. Restarting services ===")
    run('systemctl restart abu-zahra', timeout=15)
    time.sleep(2)
    run('systemctl restart abu-zahra-web', timeout=15)
    time.sleep(3)
    run('systemctl restart caddy', timeout=15)
    time.sleep(2)

    # 7. Test
    print("\n=== 7. Testing ===")
    out, _, _ = run('curl -sk -o /dev/null -w "%{http_code}" https://alsydyabwalzhra.online/', timeout=15)
    print(f"  Dashboard HTTP: {out}")
    out2, _, _ = run('curl -sk https://alsydyabwalzhra.online/api/health', timeout=15)
    print(f"  API: {out2[:150]}")

    sftp.close()
    ssh.close()
    print("\n=== DEPLOYMENT COMPLETE ===")

if __name__ == "__main__":
    main()