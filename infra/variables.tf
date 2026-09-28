variable "environment" {
  description = "Ambiente (dev|prod)"
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment deve ser \"dev\" ou \"prod\"."
  }
}

variable "project" {
  description = "Nome do projeto, usado no prefixo de nomenclatura"
  type        = string
  default     = "rag"
}

variable "aws_region" {
  description = "Região AWS"
  type        = string
  default     = "us-east-2"
}

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "openrouter_api_key" {
  description = "Placeholder pro secret único; o valor real é setado fora do Terraform via `aws secretsmanager put-secret-value` (ver infra/README.md) — nunca passa pelo state nem pelo CI"
  type        = string
  sensitive   = true
  default     = "REPLACE_VIA_PUT_SECRET_VALUE"
}

# --- Dimensionamento ---

variable "rds_instance_class" {
  description = "Classe da instância RDS"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "Storage alocado do RDS em GB"
  type        = number
  default     = 20
}

variable "rds_engine_version" {
  description = "Versão da engine PostgreSQL (major >= 16 para pgvector >= 0.5.0 / HNSW)"
  type        = string
  default     = "16.4"
}

variable "rds_multi_az" {
  description = "Multi-AZ do RDS"
  type        = bool
  default     = false
}

variable "rds_master_username" {
  description = "Usuário master do RDS (rds_superuser, usado pela app até existir role dedicada)"
  type        = string
  default     = "raguser"
}

variable "task_cpu" {
  description = "CPU da task Fargate (unidades)"
  type        = string
  default     = "512"
}

variable "task_memory" {
  description = "Memória da task Fargate (MiB)"
  type        = string
  default     = "1024"
}

variable "desired_count" {
  description = "Quantidade desejada de tasks no ECS service (0 no primeiro apply, sem imagem publicada)"
  type        = number
  default     = 0
}

variable "container_port" {
  description = "Porta exposta pelo container da app"
  type        = number
  default     = 8080
}

# --- Proteção / gate de destroy ---

variable "deletion_protection" {
  description = "Proteção contra deleção do RDS"
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "Pula snapshot final ao destruir o RDS"
  type        = bool
  default     = true
}

variable "force_destroy" {
  description = "Permite destruir buckets S3 com objetos dentro"
  type        = bool
  default     = true
}

variable "secret_recovery_window_days" {
  description = "Janela de recuperação do secret no Secrets Manager (0 = deleção imediata)"
  type        = number
  default     = 0
}

# --- Cognito ---

variable "cognito_allow_signup" {
  description = "Permite signup direto (false = allow_admin_create_user_only)"
  type        = bool
  default     = true
}

variable "google_client_id" {
  description = "Client ID do IdP Google no Cognito (opcional)"
  type        = string
  default     = ""
}

variable "google_client_secret" {
  description = "Client secret do IdP Google no Cognito (opcional)"
  type        = string
  default     = ""
  sensitive   = true
}

# --- Container insights ---

variable "container_insights_enabled" {
  description = "Habilita Container Insights no cluster ECS"
  type        = bool
  default     = false
}

variable "integration_timeout_ms" {
  description = "Timeout da integração HTTP_PROXY do API Gateway (max 30000)"
  type        = number
  default     = 30000
}

variable "app_image" {
  description = "Imagem placeholder da task definition bootstrap (CI troca por revisão própria depois)"
  type        = string
  default     = "public.ecr.aws/docker/library/nginx:stable"
}
