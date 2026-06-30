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
