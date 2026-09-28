variable "bucket_name" {
  description = "Nome do bucket (deve ser globalmente único)"
  type        = string
}

variable "force_destroy" {
  description = "Permite destruir o bucket mesmo com objetos dentro"
  type        = bool
  default     = false
}

variable "versioning" {
  description = "Habilita versionamento do bucket"
  type        = bool
  default     = false
}

variable "cors_rules" {
  description = "Regras de CORS opcionais"
  type = list(object({
    allowed_methods = list(string)
    allowed_origins = list(string)
    allowed_headers = optional(list(string), ["*"])
    exposed_headers = optional(list(string), [])
    max_age_seconds = optional(number, 3000)
  }))
  default = []
}
