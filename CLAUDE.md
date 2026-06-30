# system-design-rag-enterprise

NotebookLM empresarial simplificado — plataforma RAG onde usuários organizam documentos em notebooks e conversam com eles via IA.

## Documentação do Projeto

```
/
├── CLAUDE.md          ← este arquivo: visão geral, stack, estrutura
├── ARCHITECTURE.md    ← infraestrutura AWS, serviços e fluxos de dados
├── DOMAIN.md          ← entidades, regras de negócio, ERD, DDL
├── API.md             ← contratos REST (endpoints, request/response, SSE)
│
├── openspec/          ← design docs (proposals, specs, tasks por change)
│   ├── config.yaml
│   ├── specs/         ← specs por capability
│   └── changes/       ← histórico de changes
```

## Stack Tecnológica

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

## Variáveis de Ambiente

| Variável | Descrição |
|----------|-----------|
| `AWS_REGION` | Região AWS (ex: `us-east-1`) |
| `COGNITO_USER_POOL_ID` | ID do User Pool do Cognito |
| `COGNITO_CLIENT_ID` | Client ID do Cognito App |
| `S3_BUCKET_NAME` | Nome do bucket S3 para sources |
| `SQS_INGESTION_QUEUE_URL` | URL da fila SQS de ingestão |
| `SPRING_AI_OPENAI_BASE_URL` | Base URL do provider LLM |
| `SPRING_AI_OPENAI_API_KEY` | API key do provider LLM |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | Modelo de chat |
| `SPRING_DATASOURCE_URL` | JDBC URL do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco |
