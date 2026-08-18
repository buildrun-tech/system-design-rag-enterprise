## Why

Entidades (`Source`, `SourceChunk`) e specs de `source-ingestion` já existem, mas nenhum código do pipeline de upload/ingestão foi implementado. Sem ele, sources ficam presas em `PENDING` para sempre — não há caminho de arquivo bruto até chunk vetorizado, logo o RAG não tem o que buscar.

## What Changes

- Adiciona endpoint `POST /notebooks/{notebookId}/sources` (multipart) que persiste `Source` (`PENDING`), sobe arquivo para S3 e publica mensagem SQS
- Adiciona consumer SQS (`spring-cloud-aws-starter-sqs`, `@SqsListener`) que baixa do S3, extrai texto (`TikaDocumentReader`), faz chunking (`TokenTextSplitter`, Spring AI), gera embeddings via OpenRouter (`openai/text-embedding-3-large`) e persiste via `VectorStore` (`PgVectorStore`, Spring AI) — sem reimplementar geração/persistência de vetor na mão
- Adiciona endpoints `GET /notebooks/{notebookId}/sources` e `GET .../sources/{sourceId}` para status
- Adiciona `DELETE /notebooks/{notebookId}/sources/{sourceId}` (remove arquivo S3 + entradas no vector store)
- **BREAKING (schema, pré-produção)**: substitui a tabela custom `tb_source_chunks` + entidade `SourceChunk` + `VectorConverter` pelo schema gerenciado pelo `PgVectorStore` do Spring AI (tabela própria, `metadata` JSONB carregando `source_id`/`chunk_index`/`embedding_model`). Sem dado em produção — apenas específica de dia anterior nunca implementada.

## Capabilities

### New Capabilities
(nenhuma — `source-ingestion` já existe como capability; este change implementa e ajusta a spec já escrita)

### Modified Capabilities
- `jpa-persistence`: remove o requirement "Entidade SourceChunk mapeada via JPA com vetor de embedding" — chunk + embedding deixam de ser uma entidade JPA custom e passam a viver na tabela do `PgVectorStore`
- `rag-retrieval`: similarity search deixa de ser SQL manual contra `source_chunks` e passa a usar `VectorStore.similaritySearch()` com filtro de metadata (`source_id IN (...)`)

(`source-ingestion` NÃO entra aqui — implementa o comportamento já especificado, sem mudar requirement/scenario observável; escolhas de lib ficam só em design.md)

## Impact

- `pom.xml`: + `spring-cloud-aws-starter-sqs`, + `spring-ai-tika-document-reader` (ou artifact reader equivalente do BOM); `SourceChunkRepository`, `SourceChunk`, `VectorConverter` removidos
- Flyway: nova migration dropando `tb_source_chunks` (nunca teve dado), Spring AI cria/gerencia sua própria tabela via `PgVectorStore` init
- `application.yml`: novo bloco `spring.ai.openai.embedding.options.model=openai/text-embedding-3-large` (mesmo base-url/api-key do OpenRouter já configurado para chat), config `spring.cloud.aws.sqs` e `spring.cloud.aws.s3` (ou client beans manuais se o starter não cobrir S3)
- Novo: `SourceController`, `SourceService`, `SourceIngestionConsumer` (ou nome equivalente), `S3Service`/`S3Client` bean, `EmbeddingModel` bean via config existente
- `openspec/specs/jpa-persistence/spec.md` e `openspec/specs/rag-retrieval/spec.md` recebem delta specs neste change
