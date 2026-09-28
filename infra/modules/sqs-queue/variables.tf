variable "name" {
  description = "Nome da fila principal (a DLQ recebe o sufixo -dlq)"
  type        = string
}

variable "visibility_timeout_seconds" {
  description = "Tempo de visibilidade da mensagem, deve cobrir o tempo máximo de processamento"
  type        = number
  default     = 300
}

variable "max_receive_count" {
  description = "Tentativas antes de mover a mensagem para a DLQ"
  type        = number
  default     = 5
}

variable "dlq_message_retention_seconds" {
  description = "Retenção de mensagens na DLQ (deve ser maior que a da fila principal)"
  type        = number
  default     = 1209600 # 14 dias
}

variable "message_retention_seconds" {
  description = "Retenção de mensagens na fila principal"
  type        = number
  default     = 345600 # 4 dias
}
