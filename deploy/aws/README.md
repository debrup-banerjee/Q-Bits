# Deploying Q-Bits to a free-tier AWS EC2 instance

One `t3.micro` instance runs everything: Postgres (Docker), the Spring Boot backend (systemd),
and the web app (static files served by nginx, which also proxies `/api` to the backend). No
Anthropic API key is stored on the instance -- it authenticates via AWS workload identity
federation instead.

A `t3.micro` only has 1GB RAM, which is enough to *run* Postgres + the JVM + nginx but not enough
to also compile Java and build the web app at the same time without risking the OOM killer. So
the backend jar and the web app's static build are produced **locally** (where you already have
JDK 21 / Maven / Node installed) and only the build output is shipped to the instance --
`deploy-app.sh` does this for you.

## Prerequisites

- AWS CLI v2, configured (`aws configure`) for the account you want to deploy into.
- `rsync` and `ssh` (already present in WSL/Linux; run these scripts from WSL -- `deploy-app.sh`
  shells out to `powershell.exe` for the actual Java/Node build steps, so it needs both).

## Steps

1. **Provision the infrastructure** (IAM role, security group, key pair, EC2 instance):

   ```bash
   bash deploy/aws/provision.sh
   ```

   This prints your AWS account's STS issuer URL and the instance's public IP.

2. **Register workload identity federation in the Claude Console**: Settings -> Workload
   identity -> Connect workload -> AWS. Use the issuer URL from step 1, and when it asks for the
   IAM role, use `arn:aws:iam::<your-account-id>:role/qbits-ec2-role`. It gives you back a
   `federation_rule_id`, `organization_id`, and `service_account_id`.

3. **Fill in the environment file**:

   ```bash
   cp deploy/aws/env.example deploy/aws/.env
   # edit deploy/aws/.env: paste the three IDs from step 2, plus QBITS_STORY_MODEL.
   # Also set QBITS_AUTH_SECRET (any random string, e.g. `openssl rand -hex 32`) if you want
   # login/registration to work, and QBITS_GOOGLE_CLIENT_ID + VITE_GOOGLE_CLIENT_ID (same
   # value) if you want Google sign-in -- both optional, see the main README for Google setup.
   ```

4. **Deploy the app**:

   ```bash
   bash deploy/aws/deploy-app.sh
   ```

   This builds the backend jar and the web app locally, ships the build output (plus
   `config/sources.yml` and `docker-compose.yml`) to the instance, and (re)starts both services.
   Re-run it any time you push changes -- it's idempotent.

5. Open `http://<public-ip>` (printed at the end of step 4).

## Hostname

There's no hostname here by default -- just the EC2 instance's **public IP address**, which is
what you open in step 5 and what `QBITS_WEB_ORIGINS` should be set to (`http://<public-ip>`).

Two things worth knowing:

- **That IP changes** if you ever stop and start the instance (rebooting alone is fine, it keeps
  the same IP). If you want a stable address before pointing a real domain at it, allocate an
  [Elastic IP](https://console.aws.amazon.com/ec2/home#Addresses) and associate it with the
  instance -- a few clicks in the EC2 console, free as long as it's attached to a running
  instance.
- **To use a real domain** (e.g. `news.yourdomain.com`): buy one anywhere (Route 53, Namecheap,
  whatever), then create a DNS **A record** pointing it at the instance's (Elastic) IP -- that's
  done at your registrar or in Route 53, not in this repo. Once DNS resolves, update
  `QBITS_WEB_ORIGINS` in `deploy/aws/.env` to `http://news.yourdomain.com` and redeploy
  (`deploy-app.sh`). nginx's config here already answers any hostname (`server_name _;`), so no
  nginx change is needed for HTTP. For HTTPS, you'd add a free cert via
  [Certbot](https://certbot.eff.org/) once the domain resolves -- not set up yet in this config.

## Costs and limits

- `t3.micro` + 30 GB gp3 EBS fits in AWS's free tier (first 12 months on the classic free tier;
  check your account's current offer). After that it's a few dollars a month.
- There's no load balancer, auto-scaling, or HTTPS here -- fine for a personal project, not for
  production traffic. Adding a domain + Let's Encrypt cert to nginx is a reasonable next step if
  you want HTTPS later.

## How the identity federation actually works here

`qbits-refresh-token.timer` runs every 5 minutes and calls `aws sts get-web-identity-token`
(using the instance's attached IAM role, no credentials stored anywhere) to refresh
`/opt/qbits/identity-token`. The backend's `AnthropicConfig` bean picks up
`ANTHROPIC_FEDERATION_RULE_ID` from `.env` and has the Anthropic SDK read that file, exchanging
it for a short-lived Anthropic access token on each use. Nothing long-lived is ever written to
disk on either side.

## Troubleshooting

- `ssh -i deploy/aws/qbits-key.pem ubuntu@<ip> 'cloud-init status --wait'` -- confirms the
  one-time instance bootstrap finished.
- `ssh -i deploy/aws/qbits-key.pem ubuntu@<ip> 'sudo journalctl -u qbits-backend -f'` -- backend
  logs.
- `ssh -i deploy/aws/qbits-key.pem ubuntu@<ip> 'sudo journalctl -u qbits-refresh-token -f'` --
  confirms the identity token is refreshing; if this errors, double check step 2 and that
  `aws iam enable-outbound-web-identity-federation` succeeded during provisioning.
