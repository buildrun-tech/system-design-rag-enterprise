## Context

Entities e repositories já existem (dia 5): `User`, `Notebook`, `Source`, `Conversation`, `ConversationMessage`. `SecurityConfig` já valida JWT do Cognito via `oauth2ResourceServer`. Nenhuma rota real existe além de `HelloController`. Repositories hoje são `JpaRepository` puro, sem query customizada.

Regra de domínio inegociável (DOMAIN.md): `userId` NUNCA vem de parâmetro de request — sempre do JWT. Toda query que retorna dado de outro usuário deve ser `404`, não `403` (não revelar existência).

## Goals / Non-Goals

**Goals:**
- Toda rota resolve o usuário autenticado a partir do JWT, sem repetir lookup em cada controller
- Toda query de leitura/escrita já nasce filtrada por `owner_id` — sem filtro pós-query em memória
- Upsert de `User` correto sob concorrência (dois requests simultâneos do mesmo `cognito_sub` novo)
- `GET /notebooks` paginado com `Page<Notebook>` nativo do Spring Data

**Non-Goals:**
- Não implementa `sources` (upload, ingestão S3/SQS) — fora do escopo desta change
- Não implementa `POST /conversations/{id}/messages` com SSE/RAG — só leitura de histórico (`GET messages`) entra aqui; envio de mensagem com streaming fica pra change futura
- Não muda schema do banco — DDL do dia 5 já cobre tudo

## Decisions

### 1. Upsert de User via filter global (não via service manual, não via resolver puro)
Um `UserUpsertFilter extends OncePerRequestFilter`, registrado com `.addFilterAfter(userUpsertFilter, BearerTokenAuthenticationFilter.class)` no `SecurityConfig`. Roda depois da validação JWT (assim `SecurityContextHolder` já tem o `Authentication` populado). Lê `Jwt.getSubject()` (=`cognito_sub`), resolve ou cria o `User`, guarda em `request.setAttribute("currentUser", user)`.

**Alternativas consideradas:**
- Service manual chamado em cada controller: rejeitado, repete boilerplate e é fácil esquecer numa rota nova
- `HandlerMethodArgumentResolver` fazendo o upsert ele mesmo: rejeitado porque roda tarde demais na chain (depois do dispatch), dificulta reuso em outros pontos (ex: SQS consumer futuro)

O filter resolve; um `CurrentUserArgumentResolver` (`HandlerMethodArgumentResolver` + anotação `@CurrentUser`) só **lê** o atributo já setado e injeta `User` no método do controller. Zero lookup duplicado.

### 2. Concorrência no upsert
`findByCognitoSub(sub)` primeiro; se vazio, tenta `save(new User(...))`; se lançar `DataIntegrityViolationException` (unique constraint em `cognito_sub`), refaz `findByCognitoSub(sub)` — outro request venceu a corrida. Sem lock explícito, sem `SELECT ... FOR UPDATE` — a unique constraint do banco já é a fonte de verdade.

### 3. IDOR via query com JOIN até owner_id, nunca filtro em memória
Cada repository ganha método específico:
```java
// NotebookRepository
Optional<Notebook> findByIdAndOwnerId(UUID id, UUID ownerId);
Page<Notebook> findByOwnerId(UUID ownerId, Pageable pageable);

// ConversationRepository
Optional<Conversation> findByIdAndNotebook_Owner_Id(UUID id, UUID ownerId);
List<Conversation> findByNotebook_IdAndNotebook_Owner_IdOrderByCreatedAtDesc(UUID notebookId, UUID ownerId);

// ConversationMessageRepository
@Query("select m from ConversationMessage m where m.conversation.id = :conversationId and m.conversation.notebook.owner.id = :ownerId order by m.createdAt asc")
List<ConversationMessage> findAllByConversationIdAndOwnerId(UUID conversationId, UUID ownerId);
```
Service layer nunca carrega a entidade sem o filtro de owner e depois checa em Java — isso vazaria timing/dado em código futuro por engano. `Optional.empty()` do repository vira `404 NOTEBOOK_NOT_FOUND` / `404 CONVERSATION_NOT_FOUND` direto no service.

### 4. Paginação: Spring `Page<T>` nativo
Controller aceita `Pageable` via `@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)`. Retorna `Page<NotebookResponse>` direto — Jackson serializa nativamente (`content`, `totalElements`, `totalPages`, `number`, `size`). Sem DTO de envelope custom — menos código, contrato já é bem conhecido de quem consome API Spring.

**BREAKING**: muda contrato de `GET /notebooks` documentado em `API.md` (era array). `API.md` e a spec `notebook-management` precisam refletir isso.

## Risks / Trade-offs

- **Filter acoplado a JPA dentro da security chain** → Mitigação: filter só faz 1 SELECT + upsert condicional, sem lógica de negócio; se crescer, extrair pra service chamado pelo filter (já é o desenho: filter delega pra `UserUpsertService`)
- **`Page<T>` nativo vaza estrutura interna do Spring Data** → aceito conscientemente (decisão do usuário); se trocar de ORM no futuro, contrato muda — não é ponytail-friendly mas é o padrão mais barato agora
- **Claims `email`/`name` do Cognito local (localstack) podem não vir no shape esperado** → Mitigação: validar contra o token real gerado pelo `docker-compose` local antes de codar o parsing; se faltar claim, usar fallback (`email` do claim `email`, `name` do claim `name` ou `username` como já faz `HelloController`)

## Migration Plan

Sem dado existente em produção (schema é novo). Deploy é direto: sobe o código, roda migrations Flyway (nenhuma nova necessária), filter novo já entra ativo na primeira request. Rollback = reverter deploy, nenhuma migração de dado envolvida.

## Open Questions

- Shape exato dos claims do JWT emitido pelo Cognito local (localstack) — confirmar durante implementação rodando o docker-compose
R:
```
{
  "sub": "97344607-aac4-459e-92d2-1b418baeb839",
  "event_id": "22e2f014-27ee-41ef-a922-8f2128965349",
  "token_use": "access",
  "auth_time": 1785798520,
  "iss": "http://localhost:4566/us-east-1_97169b922",
  "exp": 1785802120,
  "iat": 1785798520,
  "username": "admin",
  "email": "admin",
  "cognito:username": "admin",
  "jti": "77318b1d-cf37-49cb-a8a5-a74ba3620af1",
  "client_id": "747e811baa4d4989ad034d4312"
}
```

- `POST /conversations/{id}/messages` (SSE) fica pra change separada — confirmar que está realmente fora de escopo antes de arquivar esta change
R: essa rota fica para outra change no futuro, não implementamos aqui.
