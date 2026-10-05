#!/usr/bin/env bash
# Provisions everything Q-Bits needs on a free-tier AWS account: an IAM role scoped to
# Anthropic workload identity federation, a security group, a key pair, and a single EC2
# instance bootstrapped by cloud-init.yaml.
#
# Run this yourself, from a machine with the AWS CLI (v2) installed and `aws configure` already
# done for the account you want to deploy into:
#
#   bash deploy/aws/provision.sh
#
# It's safe to re-run: every step checks whether its resource already exists first.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

REGION="${AWS_REGION:-us-east-1}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.micro}"   # free-tier eligible; t2.micro also qualifies
KEY_NAME="qbits-key"
SG_NAME="qbits-sg"
ROLE_NAME="qbits-ec2-role"
PROFILE_NAME="qbits-ec2-profile"
TAG_NAME="qbits-server"

echo "== Region: $REGION, instance type: $INSTANCE_TYPE =="

echo "== IAM role for the EC2 instance (lets it call sts:GetWebIdentityToken) =="
if ! aws iam get-role --role-name "$ROLE_NAME" --region "$REGION" >/dev/null 2>&1; then
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document '{
      "Version": "2012-10-17",
      "Statement": [{
        "Effect": "Allow",
        "Principal": { "Service": "ec2.amazonaws.com" },
        "Action": "sts:AssumeRole"
      }]
    }' >/dev/null
  echo "created role $ROLE_NAME"
else
  echo "role $ROLE_NAME already exists"
fi

aws iam put-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-name "AnthropicWebIdentityToken" \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["sts:GetWebIdentityToken"],
      "Resource": "*"
    }]
  }'

if ! aws iam get-instance-profile --instance-profile-name "$PROFILE_NAME" >/dev/null 2>&1; then
  aws iam create-instance-profile --instance-profile-name "$PROFILE_NAME" >/dev/null
  aws iam add-role-to-instance-profile --instance-profile-name "$PROFILE_NAME" --role-name "$ROLE_NAME"
  echo "created instance profile $PROFILE_NAME"
  echo "waiting for the instance profile to propagate..."
  sleep 15
else
  echo "instance profile $PROFILE_NAME already exists"
fi

echo "== Enabling outbound web identity federation on the account (one-time, account-level) =="
if aws iam enable-outbound-web-identity-federation --region "$REGION" 2>/tmp/wif-enable.err; then
  echo "enabled"
else
  if grep -qi "already" /tmp/wif-enable.err; then
    echo "already enabled"
  else
    echo "Could not enable it automatically (see /tmp/wif-enable.err)."
    echo "If 'enable-outbound-web-identity-federation' is not a recognized command, run:"
    echo "  pip install --upgrade awscli   (or reinstall the AWS CLI v2)"
    echo "and re-run this script, or enable it manually: IAM console -> Account settings ->"
    echo "'Outbound web identity federation'."
  fi
fi

echo "== STS issuer URL for this AWS account (register this in Claude Console) =="
aws iam get-outbound-web-identity-federation-info --region "$REGION" 2>/dev/null \
  || echo "(couldn't read it back yet -- check IAM console -> Account settings -> Get Token Issuer URL)"

echo "== Key pair =="
if [ ! -f "$KEY_NAME.pem" ]; then
  aws ec2 create-key-pair --key-name "$KEY_NAME" --region "$REGION" \
    --query 'KeyMaterial' --output text > "$KEY_NAME.pem"
  chmod 400 "$KEY_NAME.pem"
  echo "saved $KEY_NAME.pem (keep this; it's your only way to SSH in)"
else
  echo "$KEY_NAME.pem already exists locally, reusing it"
fi

echo "== Security group =="
VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="$SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)

if [ -z "$SG_ID" ] || [ "$SG_ID" = "None" ]; then
  SG_ID=$(aws ec2 create-security-group --region "$REGION" \
    --group-name "$SG_NAME" --description "Q-Bits web+ssh" --vpc-id "$VPC_ID" \
    --query 'GroupId' --output text)
  MY_IP="$(curl -s https://checkip.amazonaws.com)/32"
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 22 --cidr "$MY_IP" >/dev/null
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null
  echo "created security group $SG_ID (SSH restricted to $MY_IP, HTTP open to everyone)"
else
  echo "security group $SG_ID already exists, reusing it"
fi

echo "== Latest Ubuntu 22.04 LTS AMI =="
AMI_ID=$(aws ssm get-parameters --region "$REGION" \
  --names /aws/service/canonical/ubuntu/server/22.04/stable/current/amd64/hvm/ebs-gp2/ami-id \
  --query 'Parameters[0].Value' --output text)
echo "AMI: $AMI_ID"

echo "== Launching the instance =="
EXISTING=$(aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Name,Values=$TAG_NAME" "Name=instance-state-name,Values=pending,running,stopped" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)

if [ -n "$EXISTING" ] && [ "$EXISTING" != "None" ]; then
  echo "an instance tagged $TAG_NAME already exists: $EXISTING -- not launching another"
  INSTANCE_ID="$EXISTING"
else
  INSTANCE_ID=$(aws ec2 run-instances --region "$REGION" \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --security-group-ids "$SG_ID" \
    --iam-instance-profile Name="$PROFILE_NAME" \
    --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}' \
    --user-data file://cloud-init.yaml \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG_NAME}]" \
    --query 'Instances[0].InstanceId' --output text)
  echo "launched $INSTANCE_ID, waiting for it to enter 'running' state..."
  aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
fi

PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

cat <<EOF

== Done ==
Instance:   $INSTANCE_ID
Public IP:  $PUBLIC_IP
SSH:        ssh -i $KEY_NAME.pem ubuntu@$PUBLIC_IP

Cloud-init is still installing Docker/Java/Maven/Node/nginx in the background (~2-5 min).
Check progress with: ssh -i $KEY_NAME.pem ubuntu@$PUBLIC_IP 'cloud-init status --wait'

Next steps:
1. In the Claude Console (platform.claude.com) -> Settings -> Workload identity -> Connect
   workload -> AWS, register the STS issuer URL printed above and create a federation rule for
   role arn:aws:iam::<your-account-id>:role/$ROLE_NAME. Note the federation_rule_id,
   organization_id and service_account_id it gives you.
2. Copy deploy/aws/env.example to deploy/aws/.env and fill in those IDs plus QBITS_STORY_MODEL.
3. Run deploy/aws/deploy-app.sh to ship the code and start the app.
EOF
