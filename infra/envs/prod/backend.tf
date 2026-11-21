terraform {
  backend "s3" {
    bucket         = "boomdoom-tfstate"
    key            = "prod/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "boomdoom-tf-lock"
    encrypt        = true
  }
}
