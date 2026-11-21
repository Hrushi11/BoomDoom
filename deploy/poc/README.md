# Deploy the POC on AWS (via GitHub)

```
git push main ──► GitHub Actions: tests ─► docker build ─► smoke render (Stickman) ─► ghcr.io/<owner>/boomdoom-backend:latest
                                                                                              │ pulled every 2 min
Android app ──HTTPS──► EC2 (Docker Compose): Caddy ─► api · script-worker · render-worker · scheduler · Postgres
                                                                         │
                                                          S3 (private lesson videos, presigned URLs)
```

GitHub never needs AWS credentials. The host polls ghcr.io and runs `docker compose up -d`, which restarts only
what changed. That also makes this work with short-lived AWS Workshop credentials.

| Resource | Notes |
|---|---|
| EC2 `c7i.xlarge` (4 vCPU / 8 GB), Amazon Linux 2023 | IMDSv2; no SSH (use SSM Session Manager) |
| Elastic IP | API at `https://<ip-with-dashes>.sslip.io` (Let's Encrypt via Caddy) unless `domain_name` is set |
| S3 bucket | Private, encrypted; lessons under `lessons/{id}/v{n}/` |
| SSM SecureString `/boomdoom-poc/env` | Runtime `.env`: OpenAI key, generated JWT keys, DB password, `RENDERER=stickman` |
| SSM SecureString `/boomdoom-poc/ghcr-token` | GitHub token the host uses to pull the private image |
| CloudWatch Logs `/boomdoom/poc` | Container logs (JSON) and metrics (Embedded Metric Format) |

Rough cost: ~$0.18/h for the instance, plus small S3 charges and OpenAI at about $0.008 per lesson.

## One-time setup

1. **GitHub token for the host.** Create a *classic* token with only the `read:packages` scope (GitHub → Settings →
   Developer settings → Personal access tokens → Tokens (classic)).
2. **Infrastructure** (needs Terraform ≥ 1.6 and AWS credentials in the environment):

   ```bash
   cd infra/poc
   cp terraform.tfvars.example terraform.tfvars   # openai_api_key, ghcr_token, region
   terraform init && terraform apply
   ```

3. **First image:** push to `main` (or run the *deploy* workflow by hand). The workflow runs the tests, builds the
   image, renders a real lesson inside it as a smoke test, then pushes. Within ~2 minutes the host pulls the image,
   runs the migrations and starts serving:

   ```bash
   curl https://$(terraform -chdir=infra/poc output -raw api_host)/readyz
   ```

4. **App:** set `boomdoom.apiBaseUrl=https://<api_host>/` in `android/local.properties` and rebuild.

## Every later deploy

`git push` to `main`. To see what the host is doing:

```bash
aws ssm start-session --target $(terraform -chdir=infra/poc output -raw instance_id)
sudo journalctl -u boomdoom.service -n 30          # pull/up log (runs every 2 min)
sudo docker compose -f /opt/boomdoom/docker-compose.yml ps
sudo docker compose -f /opt/boomdoom/docker-compose.yml logs -f render-worker
```

Changing a secret: update `terraform.tfvars` and run `terraform apply`. The host reads the new value on its next
2-minute run (restart the stack to apply it to running containers).

## Tear down

```bash
terraform -chdir=infra/poc destroy   # instance, database volume, bucket, parameters
```

## Notes

- Terraform state (local, git-ignored) contains the generated secrets and the tokens. Keep it private.
- Only ports 80/443 are open. There is no SSH.
- The image is private on ghcr.io. The token on the host can only read packages.
