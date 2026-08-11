## Why

Specs (`auth`, `notebook-management`, `source-ingestion`, `rag-retrieval`, `chat`) já descrevem comportamento de negócio, mas o projeto não tem nenhuma camada de persistência real — só o skeleton Spring Boot. Sem entidades JPA e repositórios, nenhum desses specs pode ser implementado.

## What Changes

- Cria entidades JPA para as 6 tabelas definidas em `DOMAIN.md`: `User`, `Notebook`, `Source`, `SourceChunk`, `Conversation`, `ConversationMessage`
- Cria repositórios Spring Data JPA (`JpaRepository`) para cada entidade, com métodos de consulta necessários (ex: `findByCognitoSub`, `findByOwnerId`, `findByNotebookIdAndStatus`)
- Mapeia `conversation_active_sources` como relação `@ManyToMany` com `@JoinTable` entre `Conversation` e `Source`
- Mapeia `embedding vector(1536)` via `AttributeConverter` customizado (`float[]` ↔ `vector`)
- Cascade delete (`User → Notebook → Source → SourceChunk`, `Notebook → Conversation → ConversationMessage`) delegado ao banco via DDL `ON DELETE CASCADE` (Flyway/Liquibase migration), não ao Hibernate
- Timestamps (`created_at`, `updated_at`) via `@CreationTimestamp` / `@UpdateTimestamp` do Hibernate
- Camada simples: entidades JPA diretamente anotadas (sem separação domain/entity)

## Capabilities

### New Capabilities
- `jpa-persistence`: Modelo de persistência JPA para as entidades do domínio (User, Notebook, Source, SourceChunk, Conversation, ConversationMessage), incluindo mapeamento de relacionamentos, cascade delete, timestamps automáticos, e suporte a vetor de embedding via pgvector.

### Modified Capabilities
(nenhuma — specs existentes descrevem comportamento de API/negócio que ainda será implementado em changes futuras; este change cobre apenas a camada de persistência que os sustenta)

## Impact

- Módulo `app/backend-api`: novo pacote `entity/` (ou equivalente) e `repository/`
- Novo mecanismo de migration de schema (Flyway ou Liquibase) para criar tabelas com `CREATE EXTENSION vector`, índice HNSW, e constraints `CHECK`/`CASCADE`
- Dependência nova: driver/migration tool (Flyway recomendado, já compatível com `spring-boot-starter-data-jpa`)
- Nenhum endpoint REST é criado neste change — isso fica para changes futuras que consomem os repositórios
