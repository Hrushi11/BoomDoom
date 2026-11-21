#!/usr/bin/env bash
# Pull the newest image from ghcr.io and (re)start the POC stack. Idempotent; run every 2 min by
# boomdoom.timer. `docker compose up -d` only recreates containers whose image or config changed.
set -euo pipefail
cd /opt/boomdoom
source /opt/boomdoom/host.env   # AWS_REGION, ENV_PARAM, GHCR_PARAM, GHCR_USER, BOOMDOOM_IMAGE, API_HOST

aws ssm get-parameter --region "$AWS_REGION" --name "$ENV_PARAM" --with-decryption \
  --query Parameter.Value --output text > .env.new
chmod 600 .env.new && mv .env.new .env

TOKEN=$(aws ssm get-parameter --region "$AWS_REGION" --name "$GHCR_PARAM" --with-decryption \
  --query Parameter.Value --output text)
if [ "$TOKEN" = "unset" ]; then
  echo "ghcr token not set yet: set ghcr_token in infra/poc/terraform.tfvars and re-apply"; exit 0
fi
echo "$TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin >/dev/null

export AWS_REGION BOOMDOOM_IMAGE API_HOST
export $(grep -E '^POSTGRES_PASSWORD=' .env | xargs)

if ! docker compose pull --quiet; then
  echo "image not available yet (first GitHub Actions build still running?)"; exit 0
fi
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
