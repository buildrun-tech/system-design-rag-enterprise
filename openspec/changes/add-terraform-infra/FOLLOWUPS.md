# Follow-ups da `add-terraform-infra`

Itens fora do escopo desta change, bloqueiam o deploy real (não o provisionamento).
Cada um vira uma change própria via `/opsx:propose <nome>` quando for priorizado.

## 1. `application-prod.yml` sem defaults inseguros

`app/backend-api/src/main/resources/application.yml` tem defaults
`credentials.access-key: test`, `secret-key: test`, `endpoint: http://localhost:4566`.
Em ECS esses defaults ignoram a task role IAM. Precisa de um profile `prod`
(ou remoção dos defaults) que force uso das credenciais da task role e do
endpoint real do serviço AWS.

## 2. Health endpoint sem JWT

Spring Security hoje exige JWT em todas as rotas. O target group do ALB
(`health_check_path`, módulo `ecs-service`) não consegue autenticar, então
tasks nunca ficam `healthy`. Precisa de `/actuator/health` (ou equivalente)
com `permitAll` no filtro de segurança.

## 3. Alinhar imagem local do Postgres com a versão do RDS

`app/local/docker-compose.yml` usa `pgvector/pgvector:pg18`; o RDS provisionado
é PG16.x (`infra/variables.tf`, `rds_engine_version`). Não bloqueia as migrations
atuais (HNSW + `vector_cosine_ops` funcionam nas duas), mas gera divergência
dev/prod. Trocar a imagem local pra `pgvector/pgvector:pg16`.

## 4. Remover box `dns` de `docs/solution.drawio`

O diagrama ainda mostra um componente de DNS/domínio customizado. Esta change
usa domínios padrão (`*.cloudfront.net`, `execute-api` padrão, `amazoncognito.com`)
— sem ACM/Route53. Remover a box pra refletir a arquitetura real.
