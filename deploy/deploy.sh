#!/bin/bash
# Abu-Zahra Server Deployment Script
# Run as root on the VPS

set -e

echo "=== Abu-Zahra Server Deployment ==="

# 1. Install system dependencies
echo "[1/6] Installing system dependencies..."
apt-get update -y
apt-get install -y python3 python3-pip python3-venv caddy curl ufw

# 2. Create directories
echo "[2/6] Creating directories..."
mkdir -p /opt/abu-zahra/Server
mkdir -p /opt/abu-zahra/Server/modules
mkdir -p /opt/abu-zahra/data/uploads
mkdir -p /opt/abu-zahra/data/temp

# 3. Copy server files
echo "[3/6] Copying server files..."
cp -r /tmp/deploy/Server/* /opt/abu-zahra/Server/

# 4. Create virtual environment and install dependencies
echo "[4/6] Setting up Python environment..."
cd /opt/abu-zahra/Server
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install -r requirements.txt

# 5. Configure systemd service
echo "[5/6] Configuring systemd service..."
cp /tmp/deploy/abu-zahra.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable abu-zahra
systemctl restart abu-zahra

# 6. Configure Caddy
echo "[6/6] Configuring Caddy reverse proxy..."
cp /tmp/deploy/Caddyfile /etc/caddy/Caddyfile
systemctl restart caddy

# Firewall
echo "Configuring firewall..."
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo ""
echo "=== Deployment Complete ==="
echo "Dashboard: https://alsydyabwalzhra.online"
echo "API Health: https://alsydyabwalzhra.online/api/health"
echo ""
echo "Useful commands:"
echo "  systemctl status abu-zahra   # Check server status"
echo "  journalctl -u abu-zahra -f   # View live logs"
echo "  systemctl restart abu-zahra  # Restart server"
echo ""