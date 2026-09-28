variable "name_prefix" {
  description = "Prefixo de nomenclatura (ex: rag-dev)"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
}
