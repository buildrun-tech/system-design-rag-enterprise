variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets públicas para o VPC Link"
  type        = list(string)
}

variable "alb_sg_id" {
  type = string
}

variable "listener_arn" {
  type = string
}

variable "cors_allowed_origins" {
  type = list(string)
}

variable "integration_timeout_ms" {
  type    = number
  default = 30000
}
