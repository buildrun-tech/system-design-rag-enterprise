variable "name" {
  description = "Nome do secret (ex: rag/dev/app)"
  type        = string
}

variable "secret_json" {
  description = "Conteúdo do secret como map, será serializado em JSON"
  type        = map(string)
  sensitive   = true
}

variable "recovery_window_in_days" {
  description = "Janela de recuperação após deleção (0 = deleção imediata)"
  type        = number
  default     = 0
}
