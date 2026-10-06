# Deploying Q-Bits to a free-tier AWS EC2 instance

One `t3.micro` instance runs everything: Postgres (Docker), the Spring Boot backend (systemd),
and the web app (built JS/CSS served by nginx, which sends `/api` and every page to the backend). No
Anthropic API key is stored on the instance -- it authenticates via AWS workload identity
federation instead.

A `t3.micro` only has 1GB RAM, which is enough to *run* Postgres + the JVM + nginx but not enough
to also compile Java and build the web app at the same time without risking the OOM killer. So
the backend jar and the web app's static build are produced **locally** (where you already have
JDK 21 / Maven / Node installed) and only the build output is shipped to the instance --
`deploy-app.sh` does this for you.

## Current deployment

- **Domain**: `https://qbitsnews.com` (registered via Cloudflare)
- **Instance**: `i-0edf8718127ae876b`, Elastic IP `54.157.171.57` (stable -- won't change on
  stop/start)
- **HTTPS**: handled entirely by Cloudflare's proxy (orange-cloud DNS records + SSL/TLS mode
  "Flexible") -- the origin nginx still only serves plain HTTP on port 80; no certificate is
  installed on the instance itself.

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
- **To use a real domain**: buy one anywhere, then create a DNS **A record** pointing it at the
  instance's (Elastic) IP -- that's done at your registrar, not in this repo. Once DNS resolves,
  update `QBITS_WEB_ORIGINS` in `deploy/aws/.env` to `https://yourdomain.com` and redeploy
  (`deploy-app.sh`). nginx's config here already answers any hostname (`server_name _;`), so no
  nginx change is needed.
- **For HTTPS**, the easiest path (what this deployment actually uses) is registering through
  **Cloudflare** and turning on its proxy (orange-cloud) with SSL/TLS mode "Flexible" -- Cloudflare
  terminates HTTPS at the edge for free and talks to the plain-HTTP origin behind the scenes, no
  certificate to install or renew. If your registrar isn't Cloudflare, you can still point its
  nameservers at Cloudflare to get the same effect, or install a cert directly on the instance with
  [Certbot](https://certbot.eff.org/) instead.

## SSH access from a changing home IP

The security group allows SSH (port 22) from one address only. A home connection's public IP
changes on reconnects, which makes SSH time out (deploy-app.sh then wrongly suggests cloud-init
did not finish). `deploy-app.sh` now points the rule at your current IP before it connects: it adds
the new address, then removes the old one. Set `QBITS_KEEP_SSH_RULE=1` to skip this, for example
when deploying from a second place you want to keep allowed.

## Pages and search engines (spec 007)

nginx serves only the built files (`/assets/*`, `favicon.svg`) itself. Every page (`/`,
`/story/...`, `/section/...`, `/about`, unknown paths) plus `robots.txt`, `sitemap.xml` and
`sitemap-news.xml` goes to the backend. The backend fills in the built `index.html` with that
page's title, description, canonical link, structured data, readable content and initial data,
then the React app takes over as usual. If the backend is down, nginx serves the plain
`index.html` with a 503 so the app still loads. `/actuator` and `/v3/` stay private.

- The nginx config is `nginx-qbits.conf` (plus `qbits-proxy.conf`). `deploy-app.sh` installs it
  on **every** deploy and tests it with `nginx -t` first, restoring the previous config if the
  test fails. `cloud-init.yaml`'s nginx block is only the first-boot config.
- The backend reads `QBITS_SHELL_FILE` (the deployed `web/dist/index.html`) once at startup. The
  deploy copies the web build before restarting the backend, so new asset names are picked up.
  `deploy-app.sh` adds the variable to the instance's `.env` if your local `.env` lacks it.
- `QBITS_SITE_URL` (default `https://qbitsnews.com`) is used for canonical links and sitemaps.
- After restarting, `deploy-app.sh` checks that the home page comes from the backend and loads
  the bundle, that `robots.txt` is served, and that unknown pages answer 404.

One-time steps outside the code:

1. Cloudflare: SSL/TLS → Edge Certificates → turn on **Always Use HTTPS** (http → https), and keep
   the `www` DNS record proxied so nginx can redirect `www.qbitsnews.com` to `qbitsnews.com`.
2. [Google Search Console](https://search.google.com/search-console): add `qbitsnews.com` as a
   domain property (verify with the DNS TXT record in Cloudflare), then submit
   `https://qbitsnews.com/sitemap.xml` and `https://qbitsnews.com/sitemap-news.xml`.
3. Use Search Console's URL Inspection on a story page to confirm Google sees the headline.
4. Optional: add the site to [Bing Webmaster Tools](https://www.bing.com/webmasters) (it can import
   from Search Console), and apply in Google News Publisher Center.

## One story per event (spec 008)

Before each daily edition goes live, the backend asks the summary service which stories report the
same event, compared with the stories already live in the last 72 hours. For each event it
publishes one: a story that is already live first, then one from an `official: true` source in
`config/sources.yml`, then the earliest. The others are stored as `DUPLICATE` and never shown. It
takes one call from the daily cap. If the call fails it retries on the next poll, and near the
publish deadline it publishes without de-duplicating. `qbits.dedupe.enabled: false` turns it off.

To hold back repeats that are **already live** (for example after first deploying this), run the
clean-up once on the instance. It starts a second, small JVM next to the running backend, with no
web server, does one call, prints each group and exits:

```bash
ssh -i ~/.ssh/qbits-key.pem ubuntu@<ip> 'cd /opt/qbits/app && set -a && . /opt/qbits/.env && set +a \
  && java -Xmx256m -jar backend.jar --spring.profiles.active=admin \
       --spring.main.web-application-type=none --dedupe-now'
```

A held-back story can be put back with
`update items set story_status = 'PUBLISHED', duplicate_of = null where id = '<id>';`.

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

SSH from WSL using the key `deploy-app.sh` stages at `~/.ssh/qbits-key.pem` (not the copy in this
repo directory -- that one lives on the Windows-mounted drive, which can't carry the restrictive
permissions SSH requires; see `deploy-app.sh`'s comments).

- `ssh -i ~/.ssh/qbits-key.pem ubuntu@<ip> 'cloud-init status --wait'` -- confirms the one-time
  instance bootstrap finished.
- `ssh -i ~/.ssh/qbits-key.pem ubuntu@<ip> 'sudo journalctl -u qbits-backend -f'` -- backend logs.
- `ssh -i ~/.ssh/qbits-key.pem ubuntu@<ip> 'sudo journalctl -u qbits-refresh-token -f'` -- confirms
  the identity token is refreshing; if this errors, double check step 2 and that
  `aws iam enable-outbound-web-identity-federation` succeeded during provisioning.
- A `stories_section_check` (or similar) constraint violation in the backend logs means a new
  `Section` enum value was added in Java without a matching Flyway migration updating the
  database's check constraint -- this silently aborts the whole digest job transaction on every
  tick. Add a migration like `V8__new_releases_section.sql` to fix it.
