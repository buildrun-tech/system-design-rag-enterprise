variable "name_prefix" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "alb_arn_suffix" {
  type = string
}

variable "target_group_arn_suffix" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "desired_count" {
  type = number
}

variable "rds_identifier" {
  type = string
}

variable "queue_name" {
  type = string
}

variable "dlq_name" {
  type = string
}

variable "bucket_name" {
  description = "Bucket de sources (precisa de request metrics habilitadas, filtro EntireBucket)"
  type        = string
}

variable "api_id" {
  type = string
}

variable "log_group_name" {
  type = string
}

variable "alert_email" {
  description = "E-mail dos alarmes; vazio = tópico SNS sem subscription. A subscription exige confirmação manual."
  type        = string
  default     = ""
}
