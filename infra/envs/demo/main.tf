terraform {
  required_version = ">= 1.9"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.80" }
  }
}

provider "aws" {
  region = var.region
  default_tags { tags = { project = "boomdoom", env = "demo" } }
}

# Wire modules here, e.g.:
# module "network" { source = "../../modules/network"  env = "demo" }
