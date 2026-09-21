# Architecture — system-design-rag-enterprise

Visão de alto nível da infraestrutura AWS e relacionamento entre serviços.
Documentos relacionados: [DOMAIN.md](DOMAIN.md) · [API.md](API.md)

---

## 1. Infraestrutura AWS

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              USUÁRIO (Browser)                               │
│                           React SPA + Cognito SDK                            │
└───────────────────────────────────┬──────────────────────────────────────────┘
                                    │ HTTPS
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     AWS API GATEWAY — HTTP API                               │
│          (suporta SSE / chunked transfer; sem timeout de 29s)                │
└───────────────────────────────────┬──────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│               APPLICATION LOAD BALANCER (ALB)                                │
└──────────────┬────────────────────────────────────┬─────────────────────────┘
               │                                    │
               ▼                                    ▼
   ┌───────────────────────┐          ┌───────────────────────┐
   │   ECS Task — App A    │          │   ECS Task — App B    │
   │   Spring Boot 3       │          │   Spring Boot 3       │
   │   Spring AI           │  ...     │   Spring AI           │
   │   (STATELESS)         │          │   (STATELESS)         │
   └───────────┬───────────┘          └───────────┬───────────┘
               │                                  │
               └──────────────┬───────────────────┘
                              │
           ┌──────────────────┼──────────────────────────┐
           │                  │                          │
           ▼                  ▼                          ▼
  ┌─────────────────┐ ┌──────────────┐        ┌──────────────────────┐
  │  AWS Cognito    │ │   AWS S3     │        │  AWS SQS             │
  │  User Pool      │ │  (sources    │        │  ingestion-queue     │
  │  (Google/GitHub)│ │   bucket)    │        │  + DLQ               │
  └─────────────────┘ └──────────────┘        └──────────────────────┘
                                                         │
                              ┌──────────────────────────┘
                              │  (mesmo app consome a fila)
                              ▼
                   ┌────────────────────────┐
                   │  RDS PostgreSQL 16      │
                   │  + pgvector extension  │
                   │  (embeddings, metadata)│
                   └────────────────────────┘
                              │
                              ▼
                   ┌────────────────────────┐
                   │  LLM Provider          │
                   │  (OpenAI-compatible)   │
                   │  AWS Bedrock           │
                   │    OR OpenRouter       │
                   └────────────────────────┘
```

---

## 2. Responsabilidades dos Serviços

| Serviço | Papel |
|---------|-------|
| **API Gateway HTTP API** | Ponto de entrada único; roteia tráfego para o ALB; escolhido por suportar SSE sem o timeout de 29s do REST API |
| **ALB** | Distribui requisições entre as tasks ECS; health check nas instâncias |
| **ECS (Spring Boot)** | Lógica de negócio stateless; serve HTTP + SSE; consome fila SQS no mesmo processo |
| **AWS Cognito** | Autenticação OAuth2 (Google/GitHub); emite JWT validado stateless pelo app |
| **AWS S3** | Armazenamento de arquivos brutos das sources; chave `{userId}/{notebookId}/{sourceId}/{filename}` |
| **AWS SQS** | Desacopla upload de arquivo do processamento; DLQ para mensagens com falha após retentativas |
| **RDS PostgreSQL + pgvector** | Metadados do domínio + chunks com vetores de embedding; índice HNSW para similarity search |
| **LLM Provider** | Geração de embeddings e respostas de chat; interface OpenAI-compatible (Bedrock ou OpenRouter) |

---

## 3. Fluxos de Dados

### 3.1 Upload e Ingestão de Source (Async Request-Reply)

```
[Browser]
    │
    │  POST /api/v1/notebooks/{id}/sources
    │  (multipart/form-data ou JSON com URL)
    │
    ▼
[Spring Boot — request síncrono]
    │── 1. Valida JWT e ownership do notebook
    │── 2. Cria registro em `sources` com status = PENDING
    │── 3. Upload do arquivo para S3
    │        chave: {userId}/{notebookId}/{sourceId}/{filename}
    │── 4. Publica mensagem SQS: { "sourceId": "uuid" }
    │── 5. Retorna 202 Accepted + { "sourceId": "...", "status": "PENDING" }
    │
    ▼ [assíncrono — SQS Consumer no mesmo app]
    │
    │── 6.  Atualiza source.status = PROCESSING
    │── 7.  Baixa arquivo do S3 (ou faz HTTP GET da URL)
    │── 8.  Extrai texto via Apache Tika (PDF/DOCX/MD) ou fetch (URL)
    │── 9.  Chunking: blocos de ~512 tokens com overlap de ~50 tokens
    │── 10. Gera embeddings via Spring AI EmbeddingModel (batch)
    │── 11. Persiste chunks + vetores em `source_chunks` (pgvector)
    │── 12. Atualiza source.status = READY (ou FAILED em caso de erro)
    │
    ▼
[Browser faz polling: GET /api/v1/notebooks/{id}/sources/{sourceId}]
```

### 3.2 Chat com RAG e SSE

```
[Browser]
    │
    │  POST /api/v1/conversations/{id}/messages
    │  { "content": "Qual o prazo de entrega?" }
    │  Accept: text/event-stream
    │
    ▼
[Spring Boot]
    │── 1. Valida JWT e ownership da conversa
    │── 2. Persiste mensagem em `conversation_messages` (role: user)
    │── 3. Recupera activeSourceIds da conversa
    │── 4. pgvector similarity search:
    │        SELECT content FROM source_chunks
    │        WHERE source_id = ANY(activeSourceIds)
    │        ORDER BY embedding <=> query_embedding
    │        LIMIT 5
    │── 5. Monta prompt:
    │        [System] Você é um assistente...
    │        [Context] chunk1 | chunk2 | chunk3 ...
    │        [History] últimas 10 mensagens da conversa
    │        [User] "Qual o prazo de entrega?"
    │── 6. Chama LLM provider com streaming habilitado
    │
    ▼ [SSE stream — token a token]
    │
    │  data: {"token": "O "}
    │  data: {"token": "prazo "}
    │  ...
    │  data: {"done": true, "messageId": "..."}
    │
    ▼ [após stream completo]
    │── 7. Persiste resposta completa em `conversation_messages` (role: assistant)
```

---

## 4. CI/CD (GitHub Actions)

Um workflow por trilha em `.github/workflows/`, cada um só dispara se arquivos da própria trilha mudaram (`paths:`). Deploy da app **nunca** roda `terraform` — infra e app são jobs distintos.

```
PR → develop|main         push → develop  (ambiente dev)          push → main  (ambiente prod)
─────────────────         ─────────────────────────────────        ───────────────────────────────────
backend.yml   ci          push-dev  build + push :<sha>-dev        promote-backend-prod  retag :<sha>-prod
  ./mvnw package          deploy-backend-dev  ECS                   deploy-backend-prod   ECS
frontend.yml  ci          push-dev  build + artifact dist/         promote-frontend-prod dist/ do run de dev
  lint + build            deploy-frontend-dev  S3 + CloudFront      deploy-frontend-prod  S3 + CloudFront
infra.yml     ci
  fmt -check + validate   (sem terraform apply nesta etapa)         (sem rebuild: mesmo artefato do dev)
```

- **`develop` = dev, `main` = prod.** Prod nunca rebuilda: backend reusa a imagem `develop:<sha>-dev` (retag pra `production:<sha>-prod`), frontend reusa o artifact `frontend-dist-<sha>` do run de `develop` (retenção 7 dias). Se não achar, o job falha — sem fallback de rebuild. Por isso `main` deve receber o mesmo sha testado em `develop` (fast-forward).
- **Deploy backend** (`.github/actions/deploy-ecs`): copia a task definition atual do service, troca a imagem, `register-task-definition`, `update-service --force-new-deployment`, `wait services-stable` e confere via `describe-services` que o service roda a nova revisão.
- **Deploy frontend**: `aws s3 sync dist --delete` + `create-invalidation /*`.
- **Auth AWS**: OIDC, role `arn:aws:iam::069765036136:role/ghactions-rag-enterprise` (dev e prod), sem access key.
- **State Terraform**: backend S3 parcial em `infra/backend.tf` (`use_lockfile = true`, sem DynamoDB). Bucket, region e key entram no `init` via `-backend-config`; no CI vêm do env do `infra.yml` (`TF_STATE_BUCKET`, `TF_STATE_REGION`) e a key é `rag-enterprise/<env>/terraform.tfstate`. A role OIDC precisa de `s3:ListBucket`, `GetObject`, `PutObject` e `DeleteObject` no bucket (o delete é do lockfile). O job `ci` usa `init -backend=false`, sem credenciais.
- **Destroy**: `infra.yml` via `workflow_dispatch` (escolhe `dev`/`prod`); aborta se `infra/destroy_config.json` tiver `false` pro ambiente. Nunca roda em push.

### Pré-requisitos pro deploy funcionar de ponta a ponta

Jobs `deploy-*` falham até a infra existir (fora do escopo do CI/CD) e as variables abaixo serem preenchidas. Criar GitHub **Environments** `dev` e `prod` (Settings → Environments), cada um com as mesmas variables, valores do respectivo ambiente:

| Variable | Uso |
|---|---|
| `ECS_CLUSTER_NAME` | cluster ECS do ambiente |
| `ECS_SERVICE_NAME` | service ECS do backend |
| `FRONTEND_BUCKET_NAME` | bucket S3 do frontend |
| `CLOUDFRONT_DISTRIBUTION_ID` | distribuição CloudFront do frontend |

Também: role OIDC com trust pro repo (já criada), repositórios ECR `buildrun-ragenterprise/develop` e `/production` (já criados). Recomendado: required reviewers no Environment `prod` e branch protection exigindo os checks `ci`.

### Rollback manual

Não há rollback automático. Re-apontar pra versão anterior:

- **Backend**: `aws ecs update-service --cluster <cluster> --service <service> --task-definition <ARN da revisão anterior>` (revisões antigas ficam registradas), ou re-rodar o job de deploy do commit anterior (imagem `<sha>-prod` continua no ECR).
- **Frontend**: re-rodar o workflow `frontend` do commit anterior (artifact vale por 7 dias), ou `aws s3 sync` de um `dist/` baixado do run antigo + `create-invalidation`.
