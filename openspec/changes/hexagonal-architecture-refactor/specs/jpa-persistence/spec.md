## ADDED Requirements

### Requirement: Mapeamento JPA confinado ao adapter de persistência
Entidades JPA SHALL ser classes `*JpaEntity` em `adapter.out.persistence.entity`, acessadas apenas por repositórios Spring Data em `adapter.out.persistence.repository` e expostas ao core somente por `*RepositoryAdapter` que implementam ports de `core.port.out`, convertendo via mappers.

#### Scenario: Core não enxerga JPA
- **WHEN** o teste ArchUnit analisa `core..`
- **THEN** nenhuma classe depende de `jakarta.persistence` ou `*JpaEntity`

#### Scenario: Adapter converte domínio e entidade
- **WHEN** `NotebookRepositoryAdapter.save(notebook)` é chamado com um `Notebook` de domínio
- **THEN** persiste um `NotebookJpaEntity` e retorna um `Notebook` de domínio equivalente

## MODIFIED Requirements

### Requirement: Entidade User mapeada via JPA
O sistema SHALL persistir usuários na tabela `users` com `id` (UUID gerado), `cognito_sub` (único), `email`, `name`, `created_at` (gerado automaticamente), via `UserJpaEntity`.

#### Scenario: Busca de usuário por cognito_sub
- **WHEN** `UserRepositoryPort.findByCognitoSub("abc-123")` é chamado
- **THEN** o sistema retorna o `User` de domínio correspondente se existir, ou vazio caso contrário

#### Scenario: cognito_sub duplicado é rejeitado
- **WHEN** dois registros de `User` tentam persistir o mesmo `cognito_sub`
- **THEN** o banco rejeita a segunda inserção por violação de constraint `UNIQUE`

### Requirement: Entidade Notebook mapeada via JPA com FK para User
O sistema SHALL persistir notebooks na tabela `notebooks` com `id`, `owner_id` (FK para `users`), `name`, `description` opcional, `created_at`, `updated_at` (ambos gerados automaticamente), via `NotebookJpaEntity`.

#### Scenario: Busca de notebooks por dono
- **WHEN** `NotebookRepositoryPort.findByOwnerId(ownerId, pageQuery)` é chamado
- **THEN** o sistema retorna um `PageResult` com os notebooks cujo `owner_id` corresponde

#### Scenario: Deleção de User remove Notebooks em cascata
- **WHEN** um `User` com notebooks associados é deletado
- **THEN** o banco remove automaticamente (via `ON DELETE CASCADE`) todos os notebooks associados

### Requirement: Entidade Source mapeada via JPA com FK para Notebook
O sistema SHALL persistir sources na tabela `sources` com `id`, `notebook_id` (FK), `name`, `type` (`FILE`|`URL`), `s3_key` opcional, `url` opcional, `status` (`PENDING`|`PROCESSING`|`READY`|`FAILED`), `error_message` opcional, `created_at`, via `SourceJpaEntity`.

#### Scenario: Busca de sources por notebook e status
- **WHEN** `SourceRepositoryPort.findByNotebookIdAndStatus(notebookId, READY)` é chamado
- **THEN** o sistema retorna apenas as sources daquele notebook com `status = READY`

#### Scenario: Deleção de Notebook remove Sources em cascata
- **WHEN** um `Notebook` com sources associadas é deletado
- **THEN** o banco remove automaticamente todas as sources associadas

### Requirement: Entidade Conversation mapeada via JPA com relação M:N para Source
O sistema SHALL persistir conversas na tabela `conversations` com `id`, `notebook_id` (FK), `created_at`, e relação M:N com sources via tabela `conversation_active_sources`, via `ConversationJpaEntity`. O domínio SHALL representar as sources ativas como conjunto de ids.

#### Scenario: Associação de sources ativas a uma conversa
- **WHEN** uma `Conversation` de domínio é salva com um `Set<UUID>` em `activeSourceIds`
- **THEN** o adapter persiste as associações na tabela `conversation_active_sources`

#### Scenario: Deleção de Notebook remove Conversations em cascata
- **WHEN** um `Notebook` com conversas associadas é deletado
- **THEN** o banco remove automaticamente todas as conversas associadas

### Requirement: Entidade ConversationMessage mapeada via JPA com FK para Conversation
O sistema SHALL persistir mensagens na tabela `conversation_messages` com `id`, `conversation_id` (FK), `role` (`user`|`assistant`), `content`, `created_at`, via `ConversationMessageJpaEntity`.

#### Scenario: Busca de mensagens ordenadas por criação
- **WHEN** `ConversationMessageRepositoryPort.findByConversationIdOrderByCreatedAtAsc(conversationId)` é chamado
- **THEN** o sistema retorna as mensagens da conversa em ordem cronológica ascendente

#### Scenario: Deleção de Conversation remove Messages em cascata
- **WHEN** uma `Conversation` com mensagens associadas é deletada
- **THEN** o banco remove automaticamente todas as mensagens associadas
