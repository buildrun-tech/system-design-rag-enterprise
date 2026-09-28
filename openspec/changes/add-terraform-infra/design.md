## Context

Monorepo com 3 trilhas (`app/backend-api` Spring Boot 4 / Java 25, `app/frontend` React/Vite, `infra/`). `infra/` hoje: `main.tf`, `variables.tf`, `outputs.tf` vazios, `envs/{dev,prod}/terraform.tfvars` só com comentário, `destroy_config.json` com `{"dev": true, "prod": true}`.

A change `add-cicd-github-actions` (0/23 tasks) entrega workflows e Dockerfile, e adia a infra pra esta change. Decisões herdadas dela: Terraform gerencia cluster/rede/ALB/RDS/S3/CloudFront/IAM; CI registra task definition e faz rollout; role OIDC `ghactions-rag-enterprise` e ECRs (`buildrun-ragenterprise/{develop,production}`) são manuais; conta `069765036136`, região `us-east-2`.

Alvo é a arquitetura de `docs/solution.drawio` / `ARCHITECTURE.md`:

```
Internet ─▶ API GW (HTTP API) ─▶ VPC Link ─▶ ALB (internal) ─▶ ECS Fargate (N tasks) ─┬▶ RDS PG + pgvector
                                                                                     ├▶ S3 sources
                                                                                     ├▶ SQS ingest (+DLQ) ─▶ (mesmo app consome)
                                                                                     ├▶ Secrets Manager (1 secret)
                                                                                     └▶ OpenRouter / JWKS Cognito (internet)
Internet ─▶ CloudFront ─(OAC)─▶ S3 frontend
Internet ─▶ Cognito (user pool, domínio padrão)
```

Restrições decididas com o usuário: sem NAT gateway (subnets públicas), proteção de acesso por security groups, 2 buckets S3, 1 secret, sem ACM/Route53 (domínios padrão), RDS com pgvector.

Estado do backend relevante à infra (verificado no código):
- `application.yml` tem defaults `credentials.access-key: test`, `secret-key: test`, `endpoint: http://localhost:4566` — em ECS sobrescrevem a task role.
- Sem actuator no `pom.xml`; Spring Security exige JWT em tudo — health check do ALB não tem endpoint.
- `V1__init_schema.sql` faz `CREATE EXTENSION IF NOT EXISTS vector` e cria índice HNSW; `V2` faz `CREATE EXTENSION IF NOT EXISTS hstore` e cria `vector_store` com HNSW (1536 dims).
- Local usa `pgvector/pgvector:pg18`; `ARCHITECTURE.md` diz PG16.

## Goals / Non-Goals

**Goals:**
- 11 módulos pequenos, auto-contidos, reutilizáveis (`s3-bucket` 2x; `ecs-cluster`/`ecs-service` prontos pra 2º serviço de ingestão futuro).
- Isolamento de rede provado por SG: só API Gateway → VPC Link alcança a app; só a task alcança o banco.
- IAM mínimo com trust explícito e nomes determinísticos.
- Custo baixo em dev: sem NAT, sem endpoints, sem Route53/ACM.
- Outputs como contrato estável pro CI, eliminando `vars.*` manuais.
- RDS pronto pra pgvector sem o Terraform precisar de conexão ao banco.

**Non-Goals:**
- Não criar bucket de state, role OIDC nem ECR (manuais).
- Não gerenciar revisões de task definition após o bootstrap (CI).
- Não configurar domínio customizado, ACM, Route53, WAF, GitHub IdP.
- Não adicionar OTEL sidecar, autoscaling, alarmes, dashboards (follow-up).
- Não alterar código do backend/frontend (pré-requisitos listados abaixo).
- Não separar a ingestão em app própria (TODO do diagrama).

## Decisions

**1. Raiz única `infra/` + `envs/<env>/{tfvars,backend.hcl}`, em vez de uma raiz por ambiente.**
Casa com o scaffolding existente (`envs/*/terraform.tfvars`, `destroy_config.json` único). State separado por `-backend-config`. Alternativa (`envs/<env>/main.tf` chamando os módulos) isola mais o blast radius mas duplica wiring 2x. Aceito: risco de aplicar no ambiente errado mitigado por `backend.hcl` obrigatório no `init` e por o CI passar `ENV` explícito.

```
infra/
├── main.tf providers.tf backend.tf versions.tf variables.tf outputs.tf README.md
├── destroy_config.json
├── envs/{dev,prod}/{terraform.tfvars, backend.hcl}
└── modules/
    ├── network/            ├── alb/
    ├── s3-bucket/          ├── ecs-cluster/
    ├── sqs-queue/          ├── ecs-service/
    ├── secret/             ├── api-gateway-http/
    ├── rds-postgres/       └── cloudfront-spa/
    └── cognito-user-pool/
```

**2. Módulos escritos à mão (recursos `aws_*` diretos), não wrappers de `terraform-aws-modules`.**
Cada módulo tem 1–10 recursos; wrapper adiciona uma camada de variáveis e versão de terceiro pra ganhar pouco, e a regra "SG vazio + regra do consumidor" é mais difícil de impor sobre módulo externo. Alternativa: `terraform-aws-modules/vpc|rds|alb` — descartada por controle e por ser exercício de design do projeto.

**3. Sem NAT: tasks em subnet pública com public IP; SG é a única barreira.**
Tasks precisam sair (OpenRouter, JWKS, ECR, S3, SQS, Secrets Manager, Logs) e VPC endpoints interface custam por hora/AZ. Public IP + `task-sg` sem ingress público resolve. Alternativas: NAT gateway (~US$32/mês + dados, por AZ) e fck-nat (instância a manter) — descartadas por custo/operação. Trade-off aceito: egress 443 aberto (`0.0.0.0/0`); exfiltração por HTTPS não é bloqueada.

**4. Subnets `db` isoladas separadas das públicas.**
Custo zero (sem NAT/IGW route). RDS nunca fica em subnet com rota pra internet mesmo por engano de `publicly_accessible`. Alternativa: RDS nas públicas com `publicly_accessible=false` — funciona, mas tira uma camada de defesa sem ganho. 4 subnets em 2 AZs; CIDRs derivados por `cidrsubnet` de `vpc_cidr`.

**5. SGs nascem vazios; regras criadas por quem consome.**
Resolve ciclos clássicos (`rds` ↔ `ecs-service`, `alb` ↔ `api-gateway-http`) sem recorrer a CIDR. Cada módulo exporta só `sg_id`; o consumidor recebe o `sg_id` do alvo e cria as duas pontas da regra.

```
                 dono do SG             regras criadas por
vpclink-sg      api-gateway-http        api-gateway-http (egress 80→alb-sg)
alb-sg          alb                     api-gateway-http (ingress 80←vpclink) + ecs-service (egress 8080→task-sg)
task-sg         ecs-service             ecs-service (ingress 8080←alb; egress 5432→db; egress 443→0.0.0.0/0)
db-sg           rds-postgres            ecs-service (ingress 5432←task-sg); sem egress
```

Provider AWS ≥ 5: recursos `aws_vpc_security_group_{ingress,egress}_rule` (não blocos inline, que conflitam com regras externas). `aws_security_group` remove o egress padrão, então `db-sg` fica sem saída.

**6. ALB `internal` em subnets públicas, listener HTTP :80.**
Único caminho até o ALB é o VPC Link (ENIs na mesma VPC). TLS termina no API Gateway; ALB↔task e VPC Link↔ALB ficam em HTTP dentro da VPC — sem ACM, escolha do usuário. Alternativa HTTPS interno exigiria certificado; descartada. Listener default `404` fixo; regra de path/prioridade por service.

**7. API Gateway HTTP API sem authorizer, app valida JWT.**
Mantém o desenho do diagrama (ECS busca JWKS). Authorizer JWT do Cognito no gateway é opcional/sem custo e barraria requisições sem token antes do ECS — fica como melhoria; não incluído (YAGNI, app já valida). Rota `ANY /{proxy+}`; CORS por variável.

**8. Um secret JSON (`rag/<env>/app`), senha por `random_password`, sem `manage_master_user_password`.**
Pedido: um único secret. `manage_master_user_password` criaria um 2º. Módulo `secret` é genérico (nome, JSON sensível, recovery window). Consequência: senha e `openrouter_api_key` no state → state em S3 com SSE, acesso restrito, versioning. Injeção na task: `valueFrom = "<arn>:<chave>::"` (extração de chave JSON, Fargate ≥ 1.4).
Chave OpenRouter — alternativas: (a) `TF_VAR_openrouter_api_key` de GitHub secret, TF mantém o JSON inteiro [reproduzível, sem passo manual, mas a chave passa pelo pipeline]; (b) placeholder + `ignore_changes` + `put-secret-value` manual [**escolhida**: chave nunca passa pelo CI/TF_VAR/state em claro após o primeiro apply — `aws_secretsmanager_secret_version` do módulo `secret` tem `lifecycle.ignore_changes = [secret_string]`, valor real setado via `aws secretsmanager put-secret-value` fora do Terraform, ver `infra/README.md`]. Trade-off aceito: rotação de senha do RDS feita pelo Terraform também não propaga sozinha pro secret depois do primeiro apply — precisa do mesmo passo manual atualizando `password` junto.
`random_password` com `special = false` (ou `override_special` sem `@ / " :`) pra não quebrar URL/JDBC.

**9. pgvector: engine PG ≥ 16, extensão criada pelo Flyway, não pelo Terraform.**
RDS PostgreSQL suporta pgvector como extensão gerenciada (sem `shared_preload_libraries`); basta `CREATE EXTENSION vector` por usuário `rds_superuser`. O RDS fica em subnet isolada — o runner do GitHub não alcança, então provider `postgresql` do Terraform é inviável sem túnel/bastion. As migrations já fazem `CREATE EXTENSION IF NOT EXISTS vector` (`V1`) e `hstore` (`V2`) e a app usa o usuário master (`rds_superuser`) → nenhuma migration nova é necessária. O módulo `rds-postgres`:
- valida `engine_version` major ≥ 16 (pgvector ≥ 0.5.0, HNSW);
- cria parameter group da família (`postgres16`) — sem parâmetros obrigatórios pra pgvector, mas pronto pra `maintenance_work_mem` (build de HNSW) se necessário;
- `publicly_accessible = false`, `storage_encrypted = true`, `db_name = notebooklm`.
Alternativas descartadas: (i) provider `postgresql` (rede); (ii) ECS one-off task rodando `CREATE EXTENSION` (mais uma peça, redundante com Flyway); (iii) migration `V0` (redundante, `V1` já faz).
Verificação pós-primeiro-boot: `SELECT extname FROM pg_extension` → `vector`, `hstore`.
Nota de versão: local usa pg18, RDS terá PG16 (alinhado com `ARCHITECTURE.md`); pgvector local mais novo pode divergir em features — sem impacto nas migrations atuais (HNSW + `vector_cosine_ops`). Se a dev quiser paridade, subir imagem local pra `pg16`.
Consequência: app não pode usar usuário não-superuser até haver role dedicada com extensão pré-criada.

**10. ECS service criado pelo Terraform; task definition bootstrap; CI clona a última revisão.**
`aws_ecs_service` com `lifecycle.ignore_changes = [task_definition, desired_count]`; primeiro apply com `desired_count = 0` (sem imagem no ECR ainda). CI faz `describe-task-definition` da família, troca só `image`, registra e chama `update-service` com `--desired-count`. Vantagem sobre template no repo: env e `secrets` têm fonte única (Terraform), CI só mexe em imagem. Mudança de env feita no Terraform gera nova revisão que o CI herda no próximo deploy (a família tem revisões incrementais; CI usa a mais recente). Alternativa: `task-def.json` versionado no app — duplica ARNs/env entre TF e app; descartada. Isso ajusta a design da change `add-cicd-github-actions`.
Deployment circuit breaker com rollback habilitado.

**11. IAM: exec role e task role separadas, nomes fixos, trust com condição.**
Trust `ecs-tasks.amazonaws.com` com `aws:SourceAccount` e `aws:SourceArn` (confused deputy). Exec role: `AmazonECSTaskExecutionRolePolicy` + `GetSecretValue` só no secret único (segredo usa chave KMS padrão `aws/secretsmanager`, sem `kms:Decrypt` explícito). Task role: policy montada na raiz (ARNs de bucket `sources` e fila) e passada como JSON pro `ecs-service`; módulo não conhece S3/SQS. Sem Bedrock (LLM é OpenRouter). Nomes `rag-<env>-exec`/`-task` fixos porque a role OIDC manual precisa de `iam:PassRole` restrito por ARN.

| Ator | Mecanismo | Permissão |
|---|---|---|
| ECS agent | exec role | ECR pull, logs, `GetSecretValue` (1 secret) |
| App | task role | S3 `sources/*`, SQS ingest-queue |
| CloudFront | OAC + bucket policy (`AWS:SourceArn`) | `s3:GetObject` no bucket frontend |
| API GW → ALB | VPC Link, sem IAM | — |
| GitHub Actions | role OIDC (manual) | ECR, ECS register/update, `PassRole` restrito, S3 frontend, CF invalidation, state, apply |

**12. CloudFront com domínio padrão, OAC, fallback SPA.**
OAC (não OAI, legado). `custom_error_response` 403/404 → `/index.html` 200. `cloudfront-spa` cria a bucket policy porque só ele conhece o ARN da distribuição; `s3-bucket` não anexa policy de acesso. Origem única (S3): API não passa pelo CloudFront, evita ciclo e mantém o desenho do diagrama.

**13. Cognito só com domínio padrão; callbacks derivadas do CloudFront.**
Cadeia linear: `cloudfront-spa` → (`cognito-user-pool`, `api-gateway-http` CORS, `ecs-service` CORS). Google IdP opcional. GitHub IdP fora: Cognito não suporta nativamente (exige OIDC intermediário). App client público (SPA), sem client secret.

**14. Buckets: módulo genérico `s3-bucket` com `Deny` non-TLS.**
Block public total, SSE AES256, `BucketOwnerEnforced`. `sources` sem CORS (upload passa pelo backend). Nomes com account id pra unicidade.

**15. SQS: fila + DLQ no mesmo módulo.**
`maxReceiveCount` padrão 5, `visibility_timeout` ≥ tempo máximo de ingestão (configurável; default alto pra chunk+embedding de 35 páginas), retenção DLQ 14 dias. Mesma app publica e consome; task role só na fila principal.

**16. Camadas de apply e ordem de dependências.**
1. bootstrap manual do bucket de state;
2. `network`, `s3-bucket` (x2), `sqs-queue`, `cloudfront-spa`, `cognito-user-pool`;
3. `rds-postgres`, `secret`, `alb`, `ecs-cluster`;
4. `ecs-service` (cria regras de SG cruzadas);
5. `api-gateway-http` (cria regras `vpclink-sg`↔`alb-sg`).
Terraform resolve por grafo; a lista guia o `tasks.md` e permite `-target` em depuração.

## Risks / Trade-offs

- **[HTTP API e SSE]** `ARCHITECTURE.md` diz "sem timeout de 29s", mas HTTP API tem limite de integração de 30 s e o comportamento de streaming é incerto → spike (uma rota SSE de teste atrás do gateway) antes de tratar chat como pronto; `integration_timeout_ms` como variável; plano B: CloudFront na frente do ALB ou API GW REST com response streaming.
- **[Egress 443 aberto]** exfiltração via HTTPS não bloqueada → aceito; fechar exige NAT/firewall ou endpoints.
- **[Segredos no state]** senha RDS sempre em texto plano no state (bucket com SSE, versioning, acesso mínimo). Chave OpenRouter **não** — decisão 8 usa a alternativa (b): só o placeholder passa pelo Terraform/state; o valor real entra via `put-secret-value` manual, fora do CI.
- **[Role OIDC única pra dev e prod]** PR de dev com permissão de apply toca prod → mitigar por `sub` do OIDC limitado a branches (`develop`, `main`) e environments do GitHub com aprovação em prod; role separada é follow-up.
- **[App não pronta pra ECS]** defaults `test`/`localhost:4566` ignoram a task role; sem endpoint de health, o target group marca tasks unhealthy → provisionamento funciona (`desired_count = 0`), deploy não. Pré-requisitos (change separada): `application-prod.yml` sem defaults de credencial/endpoint; `/actuator/health` (ou equivalente) `permitAll`; health check do target group configurável (`health_check_path`).
- **[Task definition em dois donos]** Terraform (bootstrap) e CI (revisões) → CI clona a última revisão, `ignore_changes` no service; mudança de env só chega ao service no próximo deploy do CI.
- **[Recriação do secret em dev]** nome preso por `recovery_window` → `recovery_window_in_days = 0` em dev.
- **[pgvector e usuário]** extensão criada pelo master; app com usuário restrito quebra a migration → app usa master por ora; role dedicada exige `CREATE EXTENSION` prévio.
- **[Custo]** ALB (~US$16/mês), RDS, API GW VPC Link (sem custo fixo relevante), CloudFront/S3 baixo. Sem NAT. RDS single-AZ em dev.
- **[Sem TLS interno]** VPC Link→ALB→task em HTTP dentro da VPC → aceito, sem ACM.
- **[Drift de versão pg local x RDS]** ver decisão 9.

## Migration Plan

1. Bootstrap manual: bucket de state por ambiente (SSE, versioning, bloqueio público); preencher `envs/*/backend.hcl`; validar permissões da role OIDC (README).
2. `terraform init -backend-config=envs/dev/backend.hcl && terraform plan -var-file=envs/dev/terraform.tfvars` com `TF_VAR_openrouter_api_key` exportada.
3. Apply dev por camadas (decisão 16). Conferir outputs do contrato.
4. Verificar isolamento: cadeia de SGs (regras esperadas, nenhum ingress `0.0.0.0/0`), ALB sem IP público, RDS não público.
5. Preencher no CI a leitura de outputs (substitui `vars.*`), ajustar jobs de deploy pra clonar task definition.
6. Ajustes no backend (change separada) → publicar imagem → deploy sobe `desired_count`.
7. Validar: `SELECT extname FROM pg_extension` (`vector`, `hstore`), upload de source → S3 → SQS → ingestão → pgvector; SSE via gateway (spike).
8. Repetir pra prod (`envs/prod`) com aprovação manual.
- **Rollback:** infra é declarativa — `git revert` + `apply`; dev pode `destroy` se `destroy_config.json` permitir; prod protegido por `deletion_protection`. Rollback de app: CI reaponta pra revisão/tag anterior.

## Open Questions

- **Chave OpenRouter:** confirmar opção (a) `TF_VAR` via GitHub secret; (b) é o plano B.
- **Fargate vs EC2:** assumido Fargate (diagrama ambíguo); confirmar.
- **CPU/memória e contagem de tasks** por ambiente (proposta: dev 0,5 vCPU/1 GB × 1; prod 1 vCPU/2 GB × 2) e classe do RDS (dev `db.t4g.micro`, prod `db.t4g.small` ou maior).
- **Versão exata do PG:** 16.x mínimo com pgvector ≥ 0.5; confirmar a menor minor que traz a versão desejada na região antes do primeiro apply.
- **JWT authorizer no gateway:** incluir agora ou deixar pro follow-up?
- **Role OIDC separada por ambiente:** vale criar na change de hardening?
