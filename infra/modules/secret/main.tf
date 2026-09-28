resource "aws_secretsmanager_secret" "this" {
  name                    = var.name
  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "this" {
  secret_id     = aws_secretsmanager_secret.this.id
  secret_string = jsonencode(var.secret_json)

  # O valor real (openrouter_api_key) é setado fora do Terraform via
  # `aws secretsmanager put-secret-value` (ver infra/README.md). Sem isso, o
  # próximo apply reverteria pro placeholder de var.secret_json.
  lifecycle {
    ignore_changes = [secret_string]
  }
}
