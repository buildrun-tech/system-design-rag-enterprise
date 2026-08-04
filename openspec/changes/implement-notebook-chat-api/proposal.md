## Why

Entities e repositories (dia 5) já existem, mas nenhuma rota REST real está implementada — só `HelloController` de exemplo. As specs `auth`, `notebook-management` e `chat` já descrevem o comportamento esperado, mas não há controller/service/security layer que os satisfaça. Sem isso o frontend não tem API pra consumir e o sistema fica vulnerável a IDOR (qualquer rota que existir hoje não filtra por owner).

## What Changes

- Implementa `GET/POST /api/v1/notebooks`, `GET/PATCH/DELETE /api/v1/notebooks/{notebookId}`
- Implementa `GET/POST /api/v1/notebooks/{notebookId}/conversations`
- Implementa `GET /api/v1/conversations/{conversationId}/messages`
- Adiciona `UserUpsertFilter` (`OncePerRequestFilter`) registrado após o filtro de autenticação Bearer: resolve `User` a partir do `cognito_sub` do JWT, cria automaticamente se não existir, trata concorrência de insert duplicado
- Adiciona `@CurrentUser` (`HandlerMethodArgumentResolver`) que injeta o `User` já resolvido pelo filter direto no controller
- Blinda todas as rotas contra IDOR via queries com filtro de `owner_id` no repository (nunca em memória/aplicação): `Notebook` direto, `Conversation` via `notebook.owner_id`, `ConversationMessage` via `conversation.notebook.owner_id` (2 hops)
- **BREAKING**: `GET /api/v1/notebooks` passa a retornar página (Spring `Page<Notebook>`) em vez de array simples — muda o contrato documentado em `API.md`

## Capabilities

### New Capabilities
(nenhuma — todo o escopo já está coberto pelas specs existentes)

### Modified Capabilities
- `auth`: formaliza requirement "Criação automática de perfil de usuário no primeiro acesso" com o mecanismo concreto (filter global pós-autenticação, resolução por `cognito_sub`, tratamento de concorrência)
- `notebook-management`: requirement "Listar notebooks do usuário" muda de array simples para resposta paginada (`page`, `size`, `sort`)
- `chat`: nenhuma mudança de requirement — implementação pura do que já está especificado

## Impact

- Código novo: `controller/NotebookController`, `controller/ConversationController`, `service/NotebookService`, `service/ConversationService`, `security/UserUpsertFilter`, `security/CurrentUserArgumentResolver`, `security/CurrentUser` (anotação)
- Repositories existentes ganham métodos com `JOIN` até `owner_id` (`NotebookRepository`, `ConversationRepository`, `ConversationMessageRepository`)
- `SecurityConfig` ganha registro do novo filter na chain
- `API.md` precisa atualização do contrato de `GET /notebooks` (paginação)
- Sem mudança de schema — DDL do dia 5 já cobre todas as tabelas necessárias
