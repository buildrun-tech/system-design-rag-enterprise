variable "environment" {
  description = "Ambiente (dev|prod)"
  type        = string
}

variable "aws_region" {
  description = "Região AWS"
  type        = string
  default     = "us-east-2"
}
