#!/usr/bin/env bash
# Builds the backend jar and the web app LOCALLY (the EC2 instance only has 1GB RAM -- too
# little to safely compile Java/TypeScript and run Postgres+backend at the same time), then
# ships just the build output, config, and docker-compose.yml to the instance and (re)starts it.
#
# Run from WSL:
#   bash deploy/aws/deploy-app.sh
#
# Requires the JDK 21 / Maven / Node already installed on the Windows side (see the chat history
# for how those were installed) -- this script drives them through powershell.exe.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
REPO_ROOT="$(cd ../.. && pwd)"
WIN_REPO_ROOT="$(wslpath -w "$REPO_ROOT")"

REGION="${AWS_REGION:-us-east-1}"
REPO_KEY_FILE="qbits-key.pem"
TAG_NAME="qbits-server"

if [ ! -f "$REPO_KEY_FILE" ]; then
  echo "missing $REPO_KEY_FILE -- run provision.sh first" >&2
  exit 1
fi

# SSH refuses a key with loose permissions, but a file on a Windows-mounted drive (anything
# under /mnt/* in WSL) can't carry real Unix permission bits -- chmod on it is a no-op. So stage
# a copy on WSL's own filesystem, where chmod actually sticks, and use that copy for every
# ssh/scp/rsync call below.
KEY_FILE="$HOME/.ssh/qbits-key.pem"
mkdir -p "$HOME/.ssh"
rm -f "$KEY_FILE"
cp "$REPO_KEY_FILE" "$KEY_FILE"
chmod 400 "$KEY_FILE"
if [ ! -f ".env" ]; then
  echo "missing deploy/aws/.env -- copy env.example to .env and fill it in first" >&2
  exit 1
fi
if ! command -v rsync >/dev/null 2>&1; then
  echo "rsync not found -- install it (e.g. 'sudo apt install rsync' in WSL) and retry" >&2
  exit 1
fi

echo "== Building the backend locally (Windows JDK 21 / Maven) =="
powershell.exe -NoProfile -Command "
  \$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
  \$mavenHome = [Environment]::GetEnvironmentVariable('MAVEN_HOME','User')
  Set-Location '$WIN_REPO_ROOT'
  & \"\$mavenHome\bin\mvn.cmd\" -f backend/pom.xml package -DskipTests -q
  if (\$LASTEXITCODE -ne 0) { exit 1 }
"

# Vite bakes VITE_* vars in at build time, so this has to come from .env now, not from the
# instance's .env later -- the Google Sign-In button would otherwise never appear in production.
VITE_GOOGLE_CLIENT_ID=$(grep -m1 '^VITE_GOOGLE_CLIENT_ID=' .env | cut -d= -f2-)

echo "== Building the web app locally (Windows Node) =="
powershell.exe -NoProfile -Command "
  \$userPath = [Environment]::GetEnvironmentVariable('PATH','User')
  \$nodePath = (\$userPath -split ';') | Where-Object { \$_ -like '*nodejs*' } | Select-Object -First 1
  \$env:PATH = \"\$nodePath;\$env:PATH\"
  \$env:VITE_GOOGLE_CLIENT_ID = '$VITE_GOOGLE_CLIENT_ID'
  Set-Location '$WIN_REPO_ROOT'
  npm install --no-audit --no-fund
  if (\$LASTEXITCODE -ne 0) { exit 1 }
  npm run build --workspace web
  if (\$LASTEXITCODE -ne 0) { exit 1 }
"

JAR_PATH=$(ls "$REPO_ROOT"/backend/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)
if [ -z "$JAR_PATH" ]; then
  echo "no runnable jar found under backend/target -- did the Maven build actually succeed?" >&2
  exit 1
fi
if [ ! -d "$REPO_ROOT/web/dist" ]; then
  echo "web/dist not found -- did the npm build actually succeed?" >&2
  exit 1
fi

PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Name,Values=$TAG_NAME" "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
if [ -z "$PUBLIC_IP" ] || [ "$PUBLIC_IP" = "None" ]; then
  echo "no running instance tagged $TAG_NAME found -- run provision.sh first" >&2
  exit 1
fi

# The security group allows SSH from one address only, and a home connection's public IP changes
# (on reconnect, or overnight). Point the rule at this machine's current IP before connecting:
# add it first, then remove any other port-22 address, so SSH is never left open to more than one.
# Set QBITS_KEEP_SSH_RULE=1 to leave the rule alone (e.g. when deploying from a second location).
if [ "${QBITS_KEEP_SSH_RULE:-}" != "1" ]; then
  MY_IP=$(curl -s --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')
  if [[ ! "$MY_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "could not find this machine's public IP (got '$MY_IP') -- check the connection" >&2
    exit 1
  fi
  SG_ID=$(aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Name,Values=$TAG_NAME" "Name=instance-state-name,Values=running" \
    --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' --output text)
  SSH_CIDRS=$(aws ec2 describe-security-groups --region "$REGION" --group-ids "$SG_ID" \
    --query 'SecurityGroups[0].IpPermissions[?FromPort==`22`].IpRanges[].CidrIp' --output text)
  if [ "$SSH_CIDRS" != "$MY_IP/32" ]; then
    echo "== Allowing SSH from this machine ($MY_IP); was: ${SSH_CIDRS:-none} =="
    if ! grep -qw "$MY_IP/32" <<<"$SSH_CIDRS"; then
      aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
        --protocol tcp --port 22 --cidr "$MY_IP/32" >/dev/null
    fi
    for cidr in $SSH_CIDRS; do
      if [ "$cidr" != "$MY_IP/32" ]; then
        aws ec2 revoke-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
          --protocol tcp --port 22 --cidr "$cidr" >/dev/null
      fi
    done
  fi
fi

SSH_OPTS="-i $KEY_FILE -o StrictHostKeyChecking=accept-new"
SSH="ssh $SSH_OPTS ubuntu@$PUBLIC_IP"
echo "== Deploying to $PUBLIC_IP =="

echo "== Waiting for cloud-init to finish (first deploy only takes a few minutes) =="
# `|| true`: cloud-init's exit status is a permanent record of its first boot and never changes
# afterward, even if something was fixed later -- so a stale error here shouldn't block deploys
# forever. The real readiness check is the systemd units existing, checked next.
$SSH 'cloud-init status --wait' || true
if ! $SSH 'test -f /etc/systemd/system/qbits-backend.service'; then
  echo "qbits-backend.service is missing on the instance -- cloud-init did not finish setting" >&2
  echo "up the instance. SSH in and check 'cloud-init status --long' and" >&2
  echo "/var/log/cloud-init.log before retrying." >&2
  exit 1
fi

echo "== Copying the backend jar =="
$SSH 'mkdir -p /opt/qbits/app/web /opt/qbits/app/config'
scp $SSH_OPTS "$JAR_PATH" "ubuntu@$PUBLIC_IP:/opt/qbits/app/backend.jar"

echo "== Copying the web app build =="
rsync -az --delete -e "ssh $SSH_OPTS" "$REPO_ROOT/web/dist/" "ubuntu@$PUBLIC_IP:/opt/qbits/app/web/dist/"

echo "== Copying config and docker-compose.yml =="
scp $SSH_OPTS "$REPO_ROOT/config/sources.yml" "ubuntu@$PUBLIC_IP:/opt/qbits/app/config/sources.yml"
scp $SSH_OPTS "$REPO_ROOT/docker-compose.yml" "ubuntu@$PUBLIC_IP:/opt/qbits/app/docker-compose.yml"

echo "== Copying .env =="
scp $SSH_OPTS .env "ubuntu@$PUBLIC_IP:/opt/qbits/.env"
# The backend runs in /opt/qbits/app, so the local default (../web/dist/index.html) doesn't
# resolve there. Added to the instance's copy only if the local .env doesn't set it (spec 007).
$SSH "grep -q '^QBITS_SHELL_FILE=' /opt/qbits/.env \
  || echo 'QBITS_SHELL_FILE=/opt/qbits/app/web/dist/index.html' >> /opt/qbits/.env"

echo "== Installing the nginx config =="
# Tested before it replaces the running config; on a failed test the previous config is put
# back and the deploy stops, so nginx keeps serving.
scp $SSH_OPTS nginx-qbits.conf qbits-proxy.conf "ubuntu@$PUBLIC_IP:/tmp/"
$SSH 'set -e
  sudo cp /etc/nginx/sites-available/qbits /tmp/qbits.nginx.previous
  sudo install -m 644 /tmp/qbits-proxy.conf /etc/nginx/snippets/qbits-proxy.conf
  sudo install -m 644 /tmp/nginx-qbits.conf /etc/nginx/sites-available/qbits
  if ! sudo nginx -t; then
    sudo cp /tmp/qbits.nginx.previous /etc/nginx/sites-available/qbits
    echo "nginx config test failed -- the previous config was restored" >&2
    exit 1
  fi'

echo "== Starting Postgres =="
$SSH 'cd /opt/qbits/app && docker compose up -d'

echo "== Restarting services =="
$SSH 'sudo systemctl restart qbits-backend && sudo systemctl reload nginx'

echo "== Checking the pages =="
# Any HTTP answer from the backend means it is up (health may report DOWN for a degraded source).
$SSH 'for i in $(seq 1 90); do
    curl -s -o /dev/null http://127.0.0.1:8080/actuator/health && break
    sleep 2
  done
  home=$(curl -s http://127.0.0.1/)
  echo "$home" | grep -q "rel=\"canonical\"" \
    || { echo "the home page is not coming from the backend -- check the backend logs" >&2; exit 1; }
  echo "$home" | grep -q "/assets/" \
    || { echo "the home page does not load the web app bundle -- check QBITS_SHELL_FILE" >&2; exit 1; }
  curl -s http://127.0.0.1/robots.txt | grep -q "^Sitemap:" \
    || { echo "robots.txt is not coming from the backend -- check the nginx config" >&2; exit 1; }
  code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/no-such-page)
  [ "$code" = 404 ] || { echo "unknown pages answer $code, expected 404" >&2; exit 1; }
  echo "pages OK"'

cat <<EOF

== Deployed ==
  http://$PUBLIC_IP

Check the backend logs with:
  ssh -i $KEY_FILE ubuntu@$PUBLIC_IP 'sudo journalctl -u qbits-backend -f'
EOF
