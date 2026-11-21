# Deploy the POC on AWS

One EC2 instance runs everything with Docker Compose. Caddy serves HTTPS with a Let's Encrypt certificate.
Lesson videos are stored in a private S3 bucket and served with presigned URLs.

```
Android app --HTTPS--> Caddy --> api ----------\
                                 script-worker --+--> Postgres (on the instance, EBS-backed)
                                 render-worker --/
                                 scheduler
                 lessons <---- S3 (private) <---- render-worker
```

| Resource | Notes |
|---|---|
| EC2 `c7i.xlarge` (4 vCPU / 8 GB), Amazon Linux 2023 | IMDSv2; no SSH (use SSM Session Manager) |
| Elastic IP | API at `https://<ip-with-dashes>.sslip.io` unless `domain_name` is set |
| S3 bucket | Private, encrypted; lessons under `lessons/{id}/v{n}/` |
| ECR repository | Backend image (keeps the last 5) |
| SSM SecureString `/boomdoom-poc/env` | Runtime `.env`: OpenAI key, generated JWT keys, generated DB password |
| CloudWatch Logs `/boomdoom/poc` | Container logs (JSON) and metrics (Embedded Metric Format) |

Rough cost: ~$0.18/h for the instance in ap-south-1, plus small S3/ECR charges and OpenAI at about $0.01 per lesson.

## Prerequisites

Install the AWS CLI (logged in to the target account), Terraform ≥ 1.6, and Docker.

## First deploy

```bash
cd infra/poc
cp terraform.tfvars.example terraform.tfvars      # set openai_api_key
terraform init && terraform apply                 # ~3 min
cd ../.. && deploy/poc/deploy.sh                  # build + push image, start the stack
curl https://$(terraform -chdir=infra/poc output -raw api_host)/readyz
```

The first HTTPS request can take a few seconds while Caddy gets the certificate. Point the app at the
`api_url` output (Android: `API_BASE_URL` in `core/network/build.gradle.kts`).

## Every later deploy

```bash
deploy/poc/deploy.sh
```

This builds the image, pushes it, copies the compose file to the instance, then pulls and restarts the stack
through SSM. Migrations run automatically (the `migrate` service runs before the app starts).

## Operate

```bash
aws ssm start-session --target $(terraform -chdir=infra/poc output -raw instance_id)
sudo docker compose -f /opt/boomdoom/docker-compose.yml ps
sudo docker compose -f /opt/boomdoom/docker-compose.yml logs -f render-worker
```

To change a secret, edit the SSM parameter (or the Terraform variable plus `terraform apply`), then run `deploy.sh`.

## Tear down

```bash
terraform -chdir=infra/poc destroy     # deletes the instance, the database volume, the bucket and the images
```

## Security notes

- Terraform state contains the generated secrets (JWT key, DB password) and the OpenAI key. Keep it local or in
  an encrypted, access-controlled bucket. `*.tfstate` and `terraform.tfvars` are git-ignored.
- Only ports 80/443 are open; there is no SSH.
- The S3 bucket blocks all public access; clients get time-limited presigned URLs.
