#!/usr/bin/env bash
# Build and push the backend image, then restart the stack on the instance (via SSM, no SSH).
#   deploy/poc/deploy.sh            # uses `terraform output` from infra/poc
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TF="terraform -chdir=$ROOT/infra/poc"
REGION=$($TF output -raw region)
REPO=$($TF output -raw ecr_repository_url)
INSTANCE=$($TF output -raw instance_id)

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPO%%/*}"
docker build --platform linux/amd64 -t "$REPO:latest" "$ROOT/backend"
docker push "$REPO:latest"

# copy the latest compose file + boot script, then restart
COMPOSE_B64=$(base64 < "$ROOT/deploy/poc/docker-compose.yml" | tr -d '\n')
BOOT_B64=$(base64 < "$ROOT/deploy/poc/boot.sh" | tr -d '\n')
CMD_ID=$(aws ssm send-command --region "$REGION" --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --comment "boomdoom deploy" \
  --parameters "commands=[\"echo $COMPOSE_B64 | base64 -d > /opt/boomdoom/docker-compose.yml\",\"echo $BOOT_B64 | base64 -d > /opt/boomdoom/boot.sh\",\"bash /opt/boomdoom/boot.sh\"]" \
  --query Command.CommandId --output text)
aws ssm wait command-executed --region "$REGION" --command-id "$CMD_ID" --instance-id "$INSTANCE" || true
aws ssm get-command-invocation --region "$REGION" --command-id "$CMD_ID" --instance-id "$INSTANCE" \
  --query '{status:Status,out:StandardOutputContent,err:StandardErrorContent}' --output yaml
echo "API: https://$($TF output -raw api_host)"
