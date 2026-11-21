output "region" { value = var.region }
output "api_host" { value = local.api_host }
output "api_url" { value = "https://${local.api_host}" }
output "instance_id" { value = aws_instance.host.id }
output "image" { value = local.image }
output "lessons_bucket" { value = aws_s3_bucket.lessons.bucket }
