variable "name_prefix" {
  type = string
}

variable "callback_urls" {
  type = list(string)
}

variable "logout_urls" {
  type = list(string)
}

variable "allow_admin_create_user_only" {
  description = "true = só admin cria usuário; false = signup direto habilitado"
  type        = bool
  default     = false
}

variable "google_client_id" {
  type    = string
  default = ""
}

variable "google_client_secret" {
  type      = string
  default   = ""
  sensitive = true
}
