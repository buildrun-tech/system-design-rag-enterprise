terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

# DUMMY: só pra exercitar a pipeline (validate/destroy). Remover quando a infra real chegar.
resource "aws_s3_bucket" "dummy" {
  bucket        = "rag-enterprise-dummy-${var.environment}-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # destroy funciona mesmo com objetos dentro

  tags = {
    Project     = "rag-enterprise"
    Environment = var.environment
  }
}
