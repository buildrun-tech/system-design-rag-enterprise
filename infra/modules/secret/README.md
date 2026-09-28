# secret

Cria um único `aws_secretsmanager_secret` + `aws_secretsmanager_secret_version`
com o JSON recebido em `secret_json`.

`aws_secretsmanager_secret_version.this` tem `lifecycle { ignore_changes =
[secret_string] }`: o valor de `secret_json` só é gravado na **criação** do
secret. Isso existe pra permitir que `openrouter_api_key` (e qualquer outro
campo) seja setado manualmente via `aws secretsmanager put-secret-value` fora
do Terraform, sem que o próximo `apply` reverta pro valor do módulo. Ver
`infra/README.md` pro passo manual.

Consequência: mudar `secret_json` na raiz (ex: rotacionar a senha do RDS) não
atualiza o secret sozinho depois da criação — precisa do mesmo
`put-secret-value` manual, ou remover o `ignore_changes` e aceitar que a chave
volte a passar pelo Terraform/CI.
