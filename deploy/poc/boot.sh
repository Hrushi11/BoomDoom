#!/usr/bin/env bash
# (Re)start the POC stack: fetch .env from SSM, log in to ECR, pull, up. Idempotent.
# Run as root on the instance (user_data on first boot; `deploy.sh` afterwards via SSM).
set -euo pipefail
cd /opt/boomdoom
source /opt/boomdoom/host.env   # AWS_REGION, ENV_PARAM, BOOMDOOM_IMAGE, API_HOST, ECR_REGISTRY

aws ssm get-parameter --region "$AWS_REGION" --name "$ENV_PARAM" --with-decryption \
  --query Parameter.Value --output text > .env
chmod 600 .env
# compose interpolation needs these in the shell environment too
export $(grep -E '^(POSTGRES_PASSWORD)=' .env | xargs)
export AWS_REGION BOOMDOOM_IMAGE API_HOST

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker compose pull --quiet
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
