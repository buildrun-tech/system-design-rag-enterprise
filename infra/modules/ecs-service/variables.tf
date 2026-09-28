variable "name_prefix" {
  description = "rag-<env>; as roles nascem exatamente <name_prefix>-exec e <name_prefix>-task"
  type        = string
}

variable "account_id" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets públicas onde as tasks rodam (assign_public_ip = true, sem NAT)"
  type        = list(string)
}

variable "cluster_arn" {
  type = string
}

variable "listener_arn" {
  type = string
}

variable "alb_sg_id" {
  type = string
}

variable "db_sg_id" {
  type = string
}

variable "listener_rule_priority" {
  type    = number
  default = 100
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "health_check_path" {
  type    = string
  default = "/actuator/health"
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "task_cpu" {
  type = string
}

variable "task_memory" {
  type = string
}

variable "desired_count" {
  type    = number
  default = 0
}

variable "app_image" {
  description = "Imagem placeholder da task definition bootstrap; CI substitui em deploys seguintes"
  type        = string
}

variable "secret_arn" {
  description = "ARN do secret único da aplicação (rag/<env>/app)"
  type        = string
}

variable "task_policy_json" {
  description = "Policy JSON da task role, montada pela raiz (S3 sources + SQS ingest-queue)"
  type        = string
}

variable "environment_variables" {
  description = "Variáveis de ambiente não secretas da task (SPRING_DATASOURCE_URL, AWS_REGION, S3_BUCKET_NAME, SQS_INGESTION_QUEUE_URL, COGNITO_*, APP_CORS_ALLOWED_ORIGINS, SPRING_AI_OPENAI_* não secretas...)"
  type        = map(string)
}
