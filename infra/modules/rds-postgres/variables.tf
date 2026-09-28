variable "name_prefix" {
  description = "Prefixo de nomenclatura (ex: rag-dev)"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "db_subnet_ids" {
  description = "Subnets isoladas (sem rota pra internet) para o subnet group do RDS"
  type        = list(string)
}

variable "engine_version" {
  description = "Versão da engine PostgreSQL (major >= 16, requisito de pgvector >= 0.5.0 / HNSW)"
  type        = string

  validation {
    condition     = tonumber(split(".", var.engine_version)[0]) >= 16
    error_message = "engine_version deve ser PostgreSQL major >= 16 (pgvector >= 0.5.0 exige HNSW, disponível a partir do PG 16)."
  }
}

variable "instance_class" {
  type = string
}

variable "allocated_storage" {
  type = number
}

variable "db_name" {
  type    = string
  default = "notebooklm"
}

variable "master_username" {
  type = string
}

variable "master_password" {
  type      = string
  sensitive = true
}

variable "multi_az" {
  type    = bool
  default = false
}

variable "deletion_protection" {
  type    = bool
  default = false
}

variable "skip_final_snapshot" {
  type    = bool
  default = true
}
