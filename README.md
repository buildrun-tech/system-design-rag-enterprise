# system-design-rag-enterprise

> NotebookLM empresarial simplificado — plataforma RAG onde usuários organizam documentos em notebooks e conversam com eles via IA.

Projeto construído ao vivo, dia após dia, como estudo de caso de **system design** aplicado: da arquitetura AWS ao código Java rodando em produção.

---

## 🧠 O que é

Um clone simplificado do NotebookLM: usuários criam **notebooks**, adicionam **sources** (PDF, DOCX, Markdown ou URL), e conversam com esses documentos via chat com **RAG** (Retrieval-Augmented Generation) e streaming de respostas em tempo real.

## ⚙️ Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.x + Spring AI |
| Cloud | AWS (Cognito, S3, RDS, SQS, API Gateway HTTP API, ALB, ECS) |
| Banco | PostgreSQL 16 + pgvector |
| Auth | AWS Cognito — Google e GitHub via OAuth2; JWT stateless |
| LLM | Provider OpenAI-compatible: AWS Bedrock ou OpenRouter |
| Frontend | React SPA |
| Streaming | SSE (Server-Sent Events) |
| Processamento async | AWS SQS (async request-reply) |
| Build | Maven Wrapper (`./mvnw`) |

## 🏗️ Arquitetura

```
Browser (React SPA)
      │ HTTPS
      ▼
API Gateway HTTP API  ──▶  ALB  ──▶  ECS (Spring Boot, stateless)
                                          │
                    ┌─────────────────────┼──────────────────────┐
                    ▼                     ▼                      ▼
              AWS Cognito            AWS S3                 AWS SQS
             (Google/GitHub)      (sources bucket)       (ingestion queue)
                                          │                      │
                                          └──────────┬───────────┘
                                                     ▼
                                        RDS PostgreSQL + pgvector
                                                     │
                                                     ▼
                                     LLM Provider (Bedrock / OpenRouter)
```

Upload de source dispara um fluxo **async request-reply** via SQS: extração de texto (Apache Tika), chunking, geração de embeddings e indexação em `pgvector`. O chat usa **similarity search** para montar o contexto do prompt e transmite a resposta via **SSE**, token a token.

📄 Detalhes completos: [ARCHITECTURE.md](ARCHITECTURE.md) · [DOMAIN.md](DOMAIN.md) · [API.md](API.md)

## 📁 Estrutura

```
/
├── app/backend-api/    ← Spring Boot app (Java 21 + Spring AI)
├── docs/               ← diagramas (draw.io)
├── infra/              ← infraestrutura como código
├── openspec/           ← design docs (proposals, specs, tasks)
├── ARCHITECTURE.md      ← infraestrutura AWS, serviços, fluxos de dados
├── DOMAIN.md            ← entidades, regras de negócio, ERD, DDL
└── API.md               ← contratos REST (endpoints, request/response, SSE)
```

## 🚀 Rodando localmente

```bash
# sobe Postgres + pgvector localmente
cd app/backend-api/local
docker compose up -d

# roda a aplicação
cd ../
./mvnw spring-boot:run
```

Variáveis de ambiente necessárias em [.env.example](.env.example) — copie para `.env` e preencha AWS/Cognito/LLM.

---

## 📺 Acompanhe ao vivo

Esse projeto é construído em **live, toda segunda-feira às 20h**, no canal **BuildRun**.

👉 **[youtube.com/@buildrun-tech](https://www.youtube.com/@buildrun-tech)**

Se curte system design, arquitetura AWS e Java na prática, bora junto — segunda tem live nova.
