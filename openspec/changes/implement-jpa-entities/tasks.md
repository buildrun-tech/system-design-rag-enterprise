## 1. Infraestrutura de migration

- [x] 1.1 Adicionar dependências `flyway-core` e `flyway-database-postgresql` no `pom.xml`
- [x] 1.2 Configurar `spring.jpa.hibernate.ddl-auto=validate` e `spring.flyway.enabled=true` em `application.properties`/`application.yml`
- [x] 1.3 Confirmar que `local/docker-compose` sobe Postgres com extensão `pgvector` habilitada (imagem `pgvector/pgvector:pgXX` se necessário)
- [x] 1.4 Criar `src/main/resources/db/migration/V1__init_schema.sql` com o DDL completo do DOMAIN.md (extensão vector, 6 tabelas, índices, constraints, HNSW)

## 2. Converter de vetor

- [x] 2.1 Criar `VectorConverter implements AttributeConverter<float[], String>` (serializa/deserializa formato pgvector `[0.1,0.2,...]`)
- [x] 2.2 Escrever teste de round-trip (persistir `float[]` conhecido, ler de volta, comparar) usando Testcontainers Postgres+pgvector

## 3. Entidades JPA

- [x] 3.1 Criar entidade `User` (`id`, `cognitoSub` único, `email`, `name`, `createdAt` via `@CreationTimestamp`)
- [x] 3.2 Criar entidade `Notebook` (`id`, `owner` `@ManyToOne` para `User`, `name`, `description`, `createdAt`, `updatedAt` via `@CreationTimestamp`/`@UpdateTimestamp`)
- [x] 3.3 Criar entidade `Source` (`id`, `notebook` `@ManyToOne`, `name`, `type` enum `FILE`/`URL`, `s3Key`, `url`, `status` enum `PENDING`/`PROCESSING`/`READY`/`FAILED`, `errorMessage`, `createdAt`)
- [x] 3.4 Criar entidade `SourceChunk` (`id`, `source` `@ManyToOne`, `content`, `embedding` via `VectorConverter`, `chunkIndex`, `embeddingModel`, `createdAt`)
- [x] 3.5 Criar entidade `Conversation` (`id`, `notebook` `@ManyToOne`, `createdAt`, `activeSources` `@ManyToMany` via `@JoinTable conversation_active_sources`)
- [x] 3.6 Criar entidade `ConversationMessage` (`id`, `conversation` `@ManyToOne`, `role` enum `USER`/`ASSISTANT`, `content`, `createdAt`)
- [x] 3.7 Garantir que nenhuma entidade usa `CascadeType.REMOVE`/`orphanRemoval` (cascade fica só no DDL, conforme design.md decisão 3)

## 4. Repositórios Spring Data JPA

- [x] 4.1 `UserRepository`: `findByCognitoSub(String)`
- [x] 4.2 `NotebookRepository`: `findByOwnerId(UUID)`
- [x] 4.3 `SourceRepository`: `findByNotebookId(UUID)`, `findByNotebookIdAndStatus(UUID, SourceStatus)`
- [x] 4.4 `SourceChunkRepository`: `findBySourceId(UUID)`
- [x] 4.5 `ConversationRepository`: `findByNotebookId(UUID)`
- [x] 4.6 `ConversationMessageRepository`: `findByConversationIdOrderByCreatedAtAsc(UUID)`

## 5. Validação de schema

- [x] 5.1 Teste de integração (Testcontainers) subindo o contexto Spring completo com Flyway + Hibernate `validate` — falha se schema divergir das entidades
- [x] 5.2 Teste de cascade delete real: deletar `User` de teste e confirmar remoção em cascata até `SourceChunk` e `ConversationMessage`

## 6. Quality gate

- [x] 6.1 Rodar skill `java-quality-gate` sobre o código produzido (entidades, converter, repositórios) e corrigir apontamentos antes de considerar a implementação concluída
