provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "rag-enterprise"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
