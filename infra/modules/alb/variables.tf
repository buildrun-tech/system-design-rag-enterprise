variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets públicas (ALB é internal, mas o único caminho de entrada é o VPC Link, também nelas)"
  type        = list(string)
}
