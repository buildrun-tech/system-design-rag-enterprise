## Context

`Source`/`SourceChunk` entities e `source-ingestion` spec já existiam (dia 5/6), mas sem implementação. Dia 7 trouxe `ChatClientConfig` (Spring AI + OpenRouter, só chat) e as dependências `spring-ai-starter-vector-store-pgvector` + `spring-ai-vector-store-advisor` no pom — instaladas, nunca usadas. Este design escolhe usar essas dependências como estão (rung 5 da lazy ladder: já instalado, não reinventar) em vez de manter a entidade `SourceChunk` custom.

## Goals / Non-Goals

**Goals:**
- Upload de arquivo (PDF/DOCX/MD) → S3 → SQS → extração → chunk → embedding → busca vetorial, ponta a ponta
- Zero código de infra vetorial na mão: `PgVectorStore` do Spring AI faz schema, insert, e similarity search
- Embedding via OpenRouter, mesmo `base-url`/`api-key` já configurados para chat

**Non-Goals:**
- Suporte a `type = URL` fica fora deste change (spec já cobre, mas escopo aqui é só `FILE`) — abre issue separada se quiser fetch de URL
- Reprocessamento/retry manual de source `FAILED` (fica pra DLQ + observação, sem endpoint de retry)
- Multi-tenancy de embedding model (troca de modelo mid-flight) — fora de escopo, `embedding_model` fica fixo em `openai/text-embedding-3-large` via metadata

## Decisions

**1. `PgVectorStore` (Spring AI) em vez de `SourceChunk` entity/`source_chunks` table**
Pom já tem a dependência. `PgVectorStore` cria e gerencia sua própria tabela (`content`, `metadata` JSONB, `embedding vector`), com index HNSW pronto. Guardamos `source_id`, `chunk_index`, `embedding_model` como metadata no `Document` do Spring AI. Elimina `VectorConverter`, `SourceChunkRepository`, a entidade inteira — menos código pra manter, index e query já otimizados pela lib.
*Alternativa descartada*: manter `SourceChunk` JPA + `VectorConverter` custom, fazer `PgVectorStore` só pra retrieval. Rejeitado — duas fontes de verdade pro mesmo dado, sem ganho.

**2. `TikaDocumentReader` + `TokenTextSplitter` (Spring AI) para extração/chunking**
Cobre PDF/DOCX/MD nativamente (Tika por baixo). `TokenTextSplitter` já implementa chunk-by-token-count com overlap configurável — bate com o requirement de ~512 tokens / ~50 overlap do spec sem reimplementar splitter.
*Alternativa descartada*: Tika manual + splitter próprio por caractere. Mais controle, zero ganho — spec já pede exatamente o que o splitter pronto faz.

**3. Embedding via OpenRouter, modelo `openai/text-embedding-3-large`**
Reusa `spring.ai.openai.base-url`/`api-key` já configurados (dia 7). Adiciona só `spring.ai.openai.embedding.options.model`. `EmbeddingModel` bean é auto-configurado pelo starter — não precisa `@Bean` manual, só a property.
*Risco conhecido*: nem todo endpoint OpenRouter expõe `/embeddings` pro modelo escolhido — validar contra API real antes de codar (spike de 5 min: `curl` no endpoint com a key).

**4. `spring-cloud-aws-starter-sqs` (`@SqsListener`) pro consumer**
Anotação declarativa, sem loop de polling manual, sem lidar com delete de mensagem na mão (starter cuida do ack). Roda no mesmo processo ECS, como já desenhado em ARCHITECTURE.md.
*Alternativa descartada*: AWS SDK v2 (`SqsClient`) raw com loop próprio. Mais boilerplate pro mesmo resultado.

**5. Upload S3: `spring-cloud-aws-starter-s3` (`S3Template`) — mesma família de starter do SQS**
Evita subir duas libs AWS diferentes (SDK raw + Cloud AWS) pra duas partes do mesmo fluxo. `S3Template.upload()`/`.download()` cobre o necessário sem SDK client boilerplate.

**6. Deleção de source: cascade vira explícita, não mais FK do banco**
Sem `SourceChunk`/FK, `DELETE /sources/{id}` precisa chamar `VectorStore.delete()` filtrando por `source_id` no metadata antes/depois de apagar a `Source` e o objeto S3 — não é mais `ON DELETE CASCADE` automático do Postgres. Comportamento observável (spec `source-ingestion`, requirement "Deletar source") não muda, só o mecanismo.

## Risks / Trade-offs

- [OpenRouter pode não suportar embedding para o modelo escolhido] → Mitigação: validar endpoint antes de implementar; fallback seria apontar `base-url` de embedding pra OpenAI direto, mantendo chat no OpenRouter (dois `base-url` distintos exigiria segundo `ChatModel`/`EmbeddingModel` bean manual)
- [`PgVectorStore` cria tabela própria fora do padrão de migration Flyway do projeto] → Mitigação: `initialize-schema: false` no application.yml + migration Flyway explícita criando a tabela no formato esperado pelo `PgVectorStore`, mantendo Flyway como única fonte de schema (consistente com `spring.jpa.hibernate.ddl-auto: validate` já em uso)
- [Consumer SQS falha silenciosa se exception não propagar] → Mitigação: garantir que qualquer falha de processamento seta `source.status = FAILED` antes de relançar/deixar a mensagem ir pra DLQ (requirement já existe na spec)
- [Arquivo grande trava request síncrono de upload] → Aceito como trade-off conhecido pro MVP; sem streaming multipart direto pro S3 neste change

## Migration Plan

1. Flyway: migration nova cria tabela do vector store (schema compatível com `PgVectorStore`) + `DROP TABLE tb_source_chunks` (sem dado — dia 5/6 nunca populou)
2. Deploy único (sem dados a migrar); rollback = reverter migration + código, já que não há tráfego de produção usando o pipeline ainda

## Open Questions

- Confirmar em runtime se o modelo `openai/text-embedding-3-large` responde via base-url OpenRouter atual antes de codar o consumer
- ~~Nome exato do artifact Maven do Tika reader~~ — resolvido: `org.springframework.ai:spring-ai-tika-document-reader`
- ~~Dimensão do vetor~~ — resolvido durante implementação: `text-embedding-3-large` nativo sai em 3072, mas pgvector HNSW não indexa acima de 2000 dimensões. Truncado pra 1536 via `spring.ai.openai.embedding.options.dimensions=1536` (suportado nativamente pela família text-embedding-3 da OpenAI). Falta confirmar se o OpenRouter repassa o parâmetro `dimensions` no request — se não repassar, a chamada falha ou ignora o truncamento e a criação da tabela com `vector(1536)` rejeita o vetor de 3072 na escrita (erro explícito, não silencioso)
