## Purpose

Mapear as entidades de domínio (User, Notebook, Source, SourceChunk, Conversation, ConversationMessage) via JPA/Hibernate sobre o schema versionado por Flyway, para que a camada de persistência reflita fielmente o modelo relacional — incluindo relações, cascatas e o tipo vetorial do pgvector — e falhe de forma rápida e explícita caso entidade e schema divirjam.

## Requirements

### Requirement: Entidade User mapeada via JPA
O sistema SHALL persistir usuários na tabela `users` com `id` (UUID gerado), `cognito_sub` (único), `email`, `name`, `created_at` (gerado automaticamente).

#### Scenario: Busca de usuário por cognito_sub
- **WHEN** `UserRepository.findByCognitoSub("abc-123")` é chamado
- **THEN** o sistema retorna o `User` correspondente se existir, ou vazio caso contrário

#### Scenario: cognito_sub duplicado é rejeitado
- **WHEN** dois registros de `User` tentam persistir o mesmo `cognito_sub`
- **THEN** o banco rejeita a segunda inserção por violação de constraint `UNIQUE`

### Requirement: Entidade Notebook mapeada via JPA com FK para User
O sistema SHALL persistir notebooks na tabela `notebooks` com `id`, `owner_id` (FK para `users`), `name`, `description` opcional, `created_at`, `updated_at` (ambos gerados automaticamente).

#### Scenario: Busca de notebooks por dono
- **WHEN** `NotebookRepository.findByOwnerId(ownerId)` é chamado
- **THEN** o sistema retorna todos os notebooks cujo `owner_id` corresponde

#### Scenario: Deleção de User remove Notebooks em cascata
- **WHEN** um `User` com notebooks associados é deletado
- **THEN** o banco remove automaticamente (via `ON DELETE CASCADE`) todos os notebooks associados

### Requirement: Entidade Source mapeada via JPA com FK para Notebook
O sistema SHALL persistir sources na tabela `sources` com `id`, `notebook_id` (FK), `name`, `type` (`FILE`|`URL`), `s3_key` opcional, `url` opcional, `status` (`PENDING`|`PROCESSING`|`READY`|`FAILED`), `error_message` opcional, `created_at`.

#### Scenario: Busca de sources por notebook e status
- **WHEN** `SourceRepository.findByNotebookIdAndStatus(notebookId, READY)` é chamado
- **THEN** o sistema retorna apenas as sources daquele notebook com `status = READY`

#### Scenario: Deleção de Notebook remove Sources em cascata
- **WHEN** um `Notebook` com sources associadas é deletado
- **THEN** o banco remove automaticamente todas as sources associadas

### Requirement: Entidade SourceChunk mapeada via JPA com vetor de embedding
O sistema SHALL persistir chunks na tabela `source_chunks` com `id`, `source_id` (FK), `content`, `embedding` (`vector(1536)` convertido de/para `float[]`), `chunk_index`, `embedding_model`, `created_at`.

#### Scenario: Persistência de embedding como vetor pgvector
- **WHEN** um `SourceChunk` é salvo com `embedding` como `float[1536]`
- **THEN** o valor é persistido na coluna `vector(1536)` no formato nativo do pgvector
- **AND** ao ser recuperado, o `float[]` retornado é equivalente ao original (round-trip)

#### Scenario: Deleção de Source remove SourceChunks em cascata
- **WHEN** uma `Source` com chunks associados é deletada
- **THEN** o banco remove automaticamente todos os chunks associados

### Requirement: Entidade Conversation mapeada via JPA com relação M:N para Source
O sistema SHALL persistir conversas na tabela `conversations` com `id`, `notebook_id` (FK), `created_at`, e relação M:N com `Source` via tabela `conversation_active_sources`.

#### Scenario: Associação de sources ativas a uma conversa
- **WHEN** uma `Conversation` é salva com um `Set<Source>` em `activeSources`
- **THEN** o sistema persiste as associações na tabela `conversation_active_sources`

#### Scenario: Deleção de Notebook remove Conversations em cascata
- **WHEN** um `Notebook` com conversas associadas é deletado
- **THEN** o banco remove automaticamente todas as conversas associadas

### Requirement: Entidade ConversationMessage mapeada via JPA com FK para Conversation
O sistema SHALL persistir mensagens na tabela `conversation_messages` com `id`, `conversation_id` (FK), `role` (`user`|`assistant`), `content`, `created_at`.

#### Scenario: Busca de mensagens ordenadas por criação
- **WHEN** `ConversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)` é chamado
- **THEN** o sistema retorna as mensagens da conversa em ordem cronológica ascendente

#### Scenario: Deleção de Conversation remove Messages em cascata
- **WHEN** uma `Conversation` com mensagens associadas é deletada
- **THEN** o banco remove automaticamente todas as mensagens associadas

### Requirement: Schema gerenciado via Flyway
O sistema SHALL usar Flyway para versionar e aplicar o schema do banco de dados, com `hibernate.ddl-auto=validate` garantindo que as entidades JPA correspondam exatamente ao schema criado pelas migrations.

#### Scenario: Aplicação inicia com schema validado
- **WHEN** a aplicação Spring Boot sobe com uma migration Flyway pendente
- **THEN** o Flyway aplica a migration antes do Hibernate validar o mapeamento das entidades

#### Scenario: Divergência entre entidade e schema falha no startup
- **WHEN** uma entidade JPA mapeia uma coluna que não existe no schema aplicado
- **THEN** a aplicação falha ao iniciar com erro de validação do Hibernate (fail-fast)
