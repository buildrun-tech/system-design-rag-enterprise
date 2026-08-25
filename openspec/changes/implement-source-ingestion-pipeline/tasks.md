## 1. Dependências e config

- [x] 1.1 Confirmar (spike manual `curl`) que `openai/text-embedding-3-large` responde via base-url OpenRouter atual com a API key configurada — pulado (sem `.env` local); validar em runtime com credenciais reais
- [x] 1.2 Adicionar `spring-cloud-aws-starter-sqs` e `spring-cloud-aws-starter-s3` ao `pom.xml`
- [x] 1.3 Adicionar dependência do Tika reader do Spring AI (confirmar artifact ID via `mvn dependency:tree` contra `spring-ai-bom:2.0.0`)
- [x] 1.4 Adicionar `spring.ai.openai.embedding.options.model=openai/text-embedding-3-large` no `application.yml`
- [x] 1.5 Adicionar config `spring.cloud.aws.sqs.*` e `spring.cloud.aws.s3.*` (region, endpoint override pra LocalStack) no `application.yml`, reaproveitando `aws.region`/`aws.endpoint-url`/`aws.s3.bucket-name`/`aws.sqs.ingestion-queue-url` já existentes

## 2. Schema (Flyway)

- [x] 2.1 Migration: `DROP TABLE tb_source_chunks` — feito editando `V1__init_schema.sql` direto (migration nunca rodou fora do dev local, sem dado, evita V2 de churn)
- [x] 2.2 Migration: criar tabela do `PgVectorStore` (schema esperado pela lib) com index HNSW — mesma edição em V1
- [x] 2.3 Configurar `PgVectorStore` bean com `initialize-schema: false` — via property `spring.ai.vectorstore.pgvector.initialize-schema=false` (autoconfigure já expõe o bean, sem `@Bean` manual)

## 3. Remoção do código obsoleto

- [x] 3.1 Remover `SourceChunk`, `SourceChunkRepository`, `VectorConverter` e testes associados (`SourceChunkTest`, `SourceChunkRepositoryTest`, cenário de `CascadeDeleteTest` referente a chunks)

## 4. Upload (fluxo síncrono)

- [x] 4.1 `SourceController`: `POST /api/v1/notebooks/{notebookId}/sources` (multipart), valida ownership do notebook e extensão do arquivo (PDF/DOCX/MD → `415` se não suportado)
- [x] 4.2 `SourceService.create()`: persiste `Source` (`PENDING`), sobe arquivo pro S3 via `S3Template` (chave `{userId}/{notebookId}/{sourceId}/{filename}`), publica mensagem SQS com `sourceId`, retorna `202`
- [x] 4.3 `GET /api/v1/notebooks/{notebookId}/sources` — lista sources do notebook
- [x] 4.4 `GET /api/v1/notebooks/{notebookId}/sources/{sourceId}` — status de uma source
- [x] 4.5 `DELETE /api/v1/notebooks/{notebookId}/sources/{sourceId}` — apaga `Source`, objeto S3, e entradas no `VectorStore` filtradas por `source_id` (cascade agora é explícito, não FK)

## 5. Consumer SQS (fluxo assíncrono)

- [x] 5.1 `SourceIngestionConsumer`: `@SqsListener` na fila de ingestão, atualiza `source.status = PROCESSING` ao receber mensagem
- [x] 5.2 Download do arquivo do S3 via `S3Template`
- [x] 5.3 Extração de texto via `TikaDocumentReader`
- [x] 5.4 Chunking via `TokenTextSplitter` — sem overlap (lib não suporta; `ponytail:` comment no código com upgrade path)
- [x] 5.5 Anexar metadata (`source_id`, `chunk_index`, `embedding_model`) em cada `Document` antes de `VectorStore.add()` — `chunk_index` já vem do `TextSplitter`, só completa `source_id`/`embedding_model`
- [x] 5.6 `VectorStore.add(documents)` — gera embedding (via `EmbeddingModel` auto-configurado) e persiste
- [x] 5.7 Atualiza `source.status = READY` ao final; em falha, `markFailed()` roda em transação `REQUIRES_NEW` (senão o rollback do processamento apagaria o FAILED) antes de relançar pra DLQ

## 6. RAG retrieval (ajuste do consumer de chat)

- [x] 6.1 Retrieval nunca tinha sido implementado (dia 7 só trouxe chat puro, sem grounding) — plugado `QuestionAnswerAdvisor` (Spring AI, dependência `spring-ai-vector-store-advisor` já instalada e sem uso) no `ChatClient`, com filtro de metadata `source_id in [...]` montado a partir de `Conversation.activeSources` ou, se vazio, das sources `READY` do notebook

## 7. Qualidade

- [x] 7.1 Testes: `SourceServiceTest` (upload feliz, extensão inválida `415`, notebook alheio, delete remove S3+vectorstore), `SourceIngestionConsumerTest` (processa e seta `READY`, source inexistente propaga), `SourceApiIntegrationTest` (fluxo HTTP completo dos 4 endpoints), retrieval coberto indiretamente via `ConversationMessageServiceTest` (advisor plugado, filtro por sources ativas)
- [x] 7.2 Rodar o skill `java-quality-gate`: coverage 95.73% (PASS), mutation score 92.31% (PASS) — sem exclusões no pom.xml, nenhum mutante marcado como equivalente
