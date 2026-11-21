terraform {
  required_version = ">= 1.6"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.80" }
    tls    = { source = "hashicorp/tls", version = "~> 4.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  # POC: local state. For a shared setup, add an S3 backend here.
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { project = "boomdoom", env = "poc" }
  }
}
