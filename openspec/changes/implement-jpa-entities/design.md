## Context

Skeleton Spring Boot 4.1 (Java 25) já tem dependências `spring-boot-starter-data-jpa`, `postgresql` driver, `spring-ai-starter-vector-store-pgvector`. DOMAIN.md define 6 tabelas com DDL completo (ver `DOMAIN.md` seção 3). Nenhuma migration tool configurada ainda, nenhuma entidade criada.

## Goals / Non-Goals

**Goals:**
- Mapear as 6 entidades (`User`, `Notebook`, `Source`, `SourceChunk`, `Conversation`, `ConversationMessage`) fielmente ao DDL do DOMAIN.md
- Repositórios Spring Data JPA com queries derivadas necessárias pros specs existentes (auth, notebook-management, source-ingestion, rag-retrieval, chat)
- Suporte a `vector(1536)` via converter customizado
- Cascade delete via banco (DDL), não via Hibernate

**Non-Goals:**
- Endpoints REST / controllers (changes futuras)
- Lógica de negócio (services) além do necessário pra repositórios funcionarem
- Migration de dados existentes (projeto greenfield)
- Testes de carga/performance do pgvector HNSW

## Decisions

### 1. Camada simples — entidade JPA direta, sem separação domain/entity
Uma classe `@Entity` por tabela, sem DTO/domain model intermediário nesta fase.
**Por quê**: projeto greenfield, sem lógica de domínio complexa ainda que justifique separação. YAGNI — separar agora é abstração prematura. Revisitar se services crescerem lógica de domínio pesada.

### 2. `embedding` via `AttributeConverter<float[], String>` customizado
```java
@Convert(converter = VectorConverter.class)
@Column(columnDefinition = "vector(1536)")
private float[] embedding;
```
`VectorConverter` serializa `float[]` pro formato textual do pgvector (`[0.1,0.2,...]`) e de volta.
**Por quê**: Spring Data JPA não tem tipo nativo pra `vector`. Alternativa (deixar Spring AI's `VectorStore` gerenciar a tabela) foi descartada — perderíamos controle do schema unificado com as outras entidades e o cascade delete via FK.
**Alternativa descartada**: Hibernate `UserType` customizado — mais boilerplate que `AttributeConverter` pra este caso.

### 3. Cascade delete via banco (DDL `ON DELETE CASCADE`), não Hibernate
Migrations (Flyway) replicam o DDL do DOMAIN.md com `REFERENCES ... ON DELETE CASCADE`. Entidades JPA mapeiam `@ManyToOne` sem `CascadeType.REMOVE`/`orphanRemoval`.
**Por quê**: cascade via Hibernate carrega entidades filhas em memória antes de deletar — caro para `source_chunks` (potencialmente milhares por source). Cascade no banco é O(1) do lado da aplicação.
**Trade-off**: deleção fica menos "visível" no código Java — quem ler só a entidade não vê a cascata. Mitigado por comentário na entidade apontando pro DDL.

### 4. Timestamps via `@CreationTimestamp` / `@UpdateTimestamp` (Hibernate)
```java
@CreationTimestamp
@Column(updatable = false)
private Instant createdAt;

@UpdateTimestamp
private Instant updatedAt;
```
**Por quê**: mais simples que `@EnableJpaAuditing` (que exige `AuditorAware` mesmo sem precisar de "quem" criou, só "quando"). Sem boilerplate de `@PrePersist`/`@PreUpdate` manual.
**Nota**: `sources`, `source_chunks`, `conversations`, `conversation_messages` só têm `created_at` (sem `updated_at` no DDL) — usar `@CreationTimestamp` sozinho nessas.

### 5. M:N `Conversation` × `Source` via `@ManyToMany` + `@JoinTable`
```java
@ManyToMany
@JoinTable(
    name = "conversation_active_sources",
    joinColumns = @JoinColumn(name = "conversation_id"),
    inverseJoinColumns = @JoinColumn(name = "source_id")
)
private Set<Source> activeSources;
```
**Por quê**: tabela de junção pura (só 2 FKs, sem colunas extras) — `@ManyToMany` é o mapeamento direto, sem necessidade de entidade explícita. Se a tabela ganhar colunas extras no futuro (ex: `added_at`), migrar pra entidade explícita nesse momento.

### 6. Extração de `cognito_sub` — adiado
Como extrair `cognito_sub` do JWT e fazer upsert de `User` é decisão de Spring Security (filter/interceptor), fora do escopo deste change (só persistência). Repositório `UserRepository.findByCognitoSub(String)` é criado aqui; quem chama é resolvido no change de `auth`.

### Migration tool: Flyway
**Por quê**: integração simples com `spring-boot-starter-data-jpa`, sem dependência extra de infra (ao contrário de Liquibase que usa XML/YAML mais verboso). DDL do DOMAIN.md vira `V1__init_schema.sql` quase 1:1.

## Risks / Trade-offs

- **[Risco]** `AttributeConverter` de vetor manual pode ter bug de parsing (formato pgvector é sensível) → Mitigação: testar round-trip (insert/select) com vetor conhecido antes de aceitar a entidade `SourceChunk` como pronta.
- **[Risco]** Cascade delete só no banco significa que testes de integração precisam rodar contra Postgres real (não H2) pra validar a cascata → Mitigação: usar Testcontainers com Postgres+pgvector (já hà `local/docker-compose` no projeto, confirmar se serve de base).
- **[Trade-off]** Sem domain/entity separation, qualquer regra de negócio futura mistura-se com anotações JPA → aceito conscientemente, revisitar se necessário.

## Migration Plan

1. Adicionar dependência `flyway-core` + `flyway-database-postgresql` no `pom.xml`
2. Criar `src/main/resources/db/migration/V1__init_schema.sql` com o DDL completo do DOMAIN.md
3. Criar entidades JPA na ordem: `User` → `Notebook` → `Source` → `SourceChunk` → `Conversation` → `ConversationMessage` (ordem de dependência FK)
4. Criar `VectorConverter` e aplicar em `SourceChunk.embedding`
5. Criar repositórios com queries derivadas
6. Validar com teste de integração (Testcontainers) que schema criado bate com entidades (Hibernate `validate` mode, não `update`)

Rollback: `flyway undo` não disponível na edição community — rollback é nova migration corretiva, não reversão automática.

## Open Questions

- Confirmar se `local/docker-compose` já roda Postgres com extensão `vector` habilitada, ou se precisa de imagem customizada (`pgvector/pgvector:pg16`)
- `hibernate.ddl-auto` deve ser `validate` em todos os ambientes (Flyway é fonte da verdade) — confirmar configuração em `application.yml`/`application.properties`
