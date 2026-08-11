## 1. Security — Upsert de User via filter global

- [x] 1.1 Criar `UserUpsertService` com método `resolve(String cognitoSub, String email, String name)`: `findByCognitoSub`, se vazio tenta `save`, captura `DataIntegrityViolationException` e refaz `findByCognitoSub` (corrida de concorrência)
- [x] 1.2 Adicionar `findByCognitoSub(String cognitoSub)` em `UserRepository`
- [x] 1.3 Criar `UserUpsertFilter extends OncePerRequestFilter`: lê `Authentication`/`Jwt` do `SecurityContextHolder`, extrai `sub`/`email`/`name` dos claims, chama `UserUpsertService.resolve`, guarda `User` em `request.setAttribute`
- [x] 1.4 Registrar `UserUpsertFilter` no `SecurityConfig` com `.addFilterAfter(userUpsertFilter, BearerTokenAuthenticationFilter.class)`
- [x] 1.5 Criar anotação `@CurrentUser` e `CurrentUserArgumentResolver implements HandlerMethodArgumentResolver`: lê o atributo setado pelo filter e injeta `User` no método do controller
- [x] 1.6 Registrar `CurrentUserArgumentResolver` via `WebMvcConfigurer.addArgumentResolvers`
- [x] 1.7 Validar contra o Cognito local (localstack) o shape real dos claims (`email`, `name`) e ajustar fallback se necessário

## 2. Repositories — Queries IDOR-safe

- [x] 2.1 `NotebookRepository`: adicionar `findByIdAndOwnerId(UUID id, UUID ownerId)` e `Page<Notebook> findByOwnerId(UUID ownerId, Pageable pageable)`
- [x] 2.2 `ConversationRepository`: adicionar `findByIdAndNotebook_Owner_Id(UUID id, UUID ownerId)` e `findByNotebook_IdAndNotebook_Owner_IdOrderByCreatedAtDesc(UUID notebookId, UUID ownerId)`
- [x] 2.3 `ConversationMessageRepository`: adicionar `@Query` `findAllByConversationIdAndOwnerId(UUID conversationId, UUID ownerId)` com join até `conversation.notebook.owner.id`

## 3. Notebooks — Controller e Service

- [x] 3.1 Criar DTOs: `NotebookRequest` (`name`, `description`), `NotebookResponse` (`id`, `name`, `description`, `createdAt`, `updatedAt`), `NotebookDetailResponse` (inclui `sources`)
- [x] 3.2 Criar `NotebookService` com `create`, `listByOwner(Pageable)`, `getByIdOrThrow(id, ownerId)`, `update`, `delete` — todos recebendo `ownerId` explícito, nunca de request
- [x] 3.3 Criar `NotebookController`: `GET /api/v1/notebooks` (paginado, `@PageableDefault(size=20, sort="createdAt", direction=DESC)`), `POST /api/v1/notebooks`, `GET /api/v1/notebooks/{notebookId}`, `PATCH /api/v1/notebooks/{notebookId}`, `DELETE /api/v1/notebooks/{notebookId}`
- [x] 3.4 Validação de `name` (1-256 chars) e `description` (max 2048) via Bean Validation
- [x] 3.5 Mapear `NotebookNotFoundException` (ou similar) para `404 NOTEBOOK_NOT_FOUND` no formato de erro padrão do `API.md`

## 4. Conversations — Controller e Service

- [x] 4.1 Criar DTOs: `ConversationCreateRequest` (`activeSourceIds` opcional), `ConversationResponse` (`id`, `notebookId`, `createdAt`, `preview`), `ConversationDetailResponse` (inclui `activeSourceIds`)
- [x] 4.2 Criar `ConversationService.create(notebookId, ownerId, activeSourceIds)`: valida notebook pertence ao owner, se `activeSourceIds` vazio/nulo usa todas as sources `READY` do notebook, valida cada `sourceId` pertence ao notebook (`400 INVALID_SOURCE_IDS` caso contrário)
- [x] 4.3 Criar `ConversationService.listByNotebook(notebookId, ownerId)`: retorna ordenado por `createdAt DESC`, `preview` = primeira mensagem truncada
- [x] 4.4 Criar `ConversationController`: `GET /api/v1/notebooks/{notebookId}/conversations`, `POST /api/v1/notebooks/{notebookId}/conversations`
- [x] 4.5 Mapear `ConversationNotFoundException` para `404 CONVERSATION_NOT_FOUND`

## 5. Conversation Messages — Controller e Service (somente leitura)

- [x] 5.1 Criar DTO `ConversationMessageResponse` (`id`, `role`, `content`, `createdAt`)
- [x] 5.2 Criar `ConversationMessageService.listByConversation(conversationId, ownerId)`: usa query com 2 hops (`conversation.notebook.owner.id`), retorna `404` se não pertencer ao usuário, ordena por `createdAt ASC`
- [x] 5.3 Criar `ConversationMessageController`: `GET /api/v1/conversations/{conversationId}/messages`

## 6. Testes

- [x] 6.1 Teste de integração: upsert de User cria registro no primeiro JWT válido e reaproveita nos seguintes
- [x] 6.2 Teste de integração: request concorrente de upsert não gera duplicata (simula race condition)
- [x] 6.3 Teste de integração por rota: usuário A não consegue ler/editar/deletar notebook de usuário B (`404`, não `403`)
- [x] 6.4 Teste de integração: `GET /conversations/{id}/messages` de conversa de outro usuário retorna `404` mesmo com `conversationId` válido (2-hop IDOR)
- [x] 6.5 Teste de paginação: `GET /notebooks` respeita `page`/`size`/default de 20 itens ordenado por `createdAt DESC`
- [x] 6.6 Teste unitário: `ConversationService` valida `sourceId` fora do notebook retorna `400 INVALID_SOURCE_IDS`

## 7. Documentação e Quality Gate

- [x] 7.1 Atualizar `API.md`: contrato de `GET /notebooks` para resposta paginada (`content`, `totalElements`, `totalPages`, `number`, `size`)
- [x] 7.2 Rodar o skill `java-quality-gate` sobre todo o código Java novo/alterado desta change
