data "aws_caller_identity" "me" {}

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_vpc" "default" {
  default = true
}

locals {
  name     = "boomdoom-poc"
  api_host = var.domain_name != "" ? var.domain_name : "${replace(aws_eip.api.public_ip, ".", "-")}.sslip.io"
  image    = lower("ghcr.io/${var.github_owner}/boomdoom-backend:latest")
}

# ---------------------------------------------------------------- storage (lesson videos)

resource "aws_s3_bucket" "lessons" {
  bucket_prefix = "${local.name}-lessons-"
  force_destroy = true # POC: `terraform destroy` removes lessons too
}

resource "aws_s3_bucket_public_access_block" "lessons" {
  bucket                  = aws_s3_bucket.lessons.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lessons" {
  bucket = aws_s3_bucket.lessons.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

# ---------------------------------------------------------------- secrets -> one SecureString .env

resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "random_password" "postgres" {
  length  = 32
  special = false
}

# Classic GitHub token with read:packages, so the host can pull the private image from ghcr.io.
resource "aws_ssm_parameter" "ghcr_token" {
  name  = "/${local.name}/ghcr-token"
  type  = "SecureString"
  tier  = "Standard"
  value = var.ghcr_token
}

# Operator token for /v1/admin (queue controls). Read it with: terraform output -raw admin_token
resource "random_password" "admin" {
  length  = 40
  special = false
}

resource "aws_ssm_parameter" "env" {
  name = "/${local.name}/env"
  type = "SecureString"
  tier = "Standard"
  value = join("\n", [
    "BOOMDOOM_ENV=poc",
    "AWS_REGION=${var.region}",
    "LESSONS_BUCKET=${aws_s3_bucket.lessons.bucket}",
    "RENDERER=${var.renderer}",
    "ADMIN_TOKEN=${random_password.admin.result}",
    "ADMIN_PASSWORD=${var.admin_password}",
    "POSTGRES_PASSWORD=${random_password.postgres.result}",
    "OPENAI_API_KEY=${var.openai_api_key}",
    "OPENAI_DAILY_COST_CAP_USD=${var.openai_daily_cost_cap_usd}",
    "JWT_PRIVATE_KEY_PEM=\"${replace(trimspace(tls_private_key.jwt.private_key_pem_pkcs8), "\n", "\\n")}\"",
    "JWT_PUBLIC_KEY_PEM=\"${replace(trimspace(tls_private_key.jwt.public_key_pem), "\n", "\\n")}\"",
    "",
  ])
}

# ---------------------------------------------------------------- instance role

resource "aws_iam_role" "host" {
  name = "${local.name}-host"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore" # Session Manager + Run Command, no SSH
}

resource "aws_iam_role_policy" "host" {
  name = "${local.name}-host"
  role = aws_iam_role.host.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:DeleteObjectVersion"],
      Resource = "${aws_s3_bucket.lessons.arn}/*" },
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:ListBucketVersions"], Resource = aws_s3_bucket.lessons.arn },
      { Effect = "Allow", Action = ["ssm:GetParameter"], Resource = [aws_ssm_parameter.env.arn, aws_ssm_parameter.ghcr_token.arn] },
      { Effect = "Allow", Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
      Resource = "arn:aws:logs:${var.region}:${data.aws_caller_identity.me.account_id}:log-group:/boomdoom/*" },
    ]
  })
}

resource "aws_iam_instance_profile" "host" {
  name = "${local.name}-host"
  role = aws_iam_role.host.name
}

# ---------------------------------------------------------------- network + host

resource "aws_security_group" "host" {
  name        = "${local.name}-host"
  description = "HTTPS for the API; no SSH (use SSM Session Manager)"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP (ACME challenge + redirect)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_eip" "api" {
  domain = "vpc"
}

resource "aws_instance" "host" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  iam_instance_profile   = aws_iam_instance_profile.host.name
  vpc_security_group_ids = [aws_security_group.host.id]

  metadata_options {
    http_tokens                 = "required" # IMDSv2
    http_put_response_hop_limit = 2          # containers can reach instance credentials
  }

  root_block_device {
    volume_size = var.root_volume_gb
    volume_type = "gp3"
    encrypted   = true
  }

  user_data_replace_on_change = false
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    region      = var.region
    env_param   = aws_ssm_parameter.env.name
    image       = local.image
    github_user = var.github_owner
    ghcr_param  = aws_ssm_parameter.ghcr_token.name
    api_host    = local.api_host
    compose_b64 = filebase64("${path.module}/../../deploy/poc/docker-compose.yml")
    boot_b64    = filebase64("${path.module}/../../deploy/poc/boot.sh")
  })

  tags = { Name = local.name }

  lifecycle {
    ignore_changes = [ami, user_data] # redeploys go through deploy.sh, not instance replacement
  }
}

resource "aws_eip_association" "api" {
  instance_id   = aws_instance.host.id
  allocation_id = aws_eip.api.id
}
