## Why

Backend API está funcional (notebooks, sources, conversas, chat SSE) mas não existe frontend algum. Sem UI, ninguém consegue usar o produto. Precisamos de um SPA mínimo viável que cubra o fluxo core: login → listar/criar notebooks → conversar com os documentos de um notebook.

## What Changes

- Cria app frontend novo em `app/frontend/` (Vite + React + TypeScript), separado do `app/backend-api/`.
- Login via AWS Cognito Hosted UI (Google/GitHub), fluxo OAuth2 Authorization Code + PKCE, usando `react-oidc-context`/`oidc-client-ts`. Sem endpoint de login próprio — o backend só valida JWT.
- Tela de notebooks: listar (`GET /api/v1/notebooks`) e criar (`POST /api/v1/notebooks`).
- Tela de workspace por notebook: lista de sources (`GET /api/v1/notebooks/{id}/sources`) e chat com histórico (`GET /api/v1/conversations/{id}/messages`) + envio de mensagem via SSE (`POST /api/v1/conversations/{id}/messages`), usando `@microsoft/fetch-event-source` para permitir header `Authorization` no stream.
- Client HTTP central com injeção automática do Bearer token e tratamento do formato de erro padrão da API (`{error, message}`).
- Sem upload de source, sem edição/delete de notebook, sem paginação, sem design system — deliberadamente fora do escopo do MVP.

## Capabilities

### New Capabilities
- `frontend-auth`: login via Cognito Hosted UI, gerência de sessão/token no browser, rotas protegidas.
- `frontend-notebooks`: listagem e criação de notebooks (Tela 2).
- `frontend-workspace`: visualização de sources e chat com streaming SSE dentro de um notebook (Tela 3).

### Modified Capabilities
(nenhuma — capabilities de backend `auth`, `chat`, `notebook-management` não mudam de requisito, só ganham um consumidor novo)

## Impact

- Novo diretório `app/frontend/` (build/tooling independente do Maven do backend).
- Nenhuma mudança em `app/backend-api/` — consome API existente como está documentada em `API.md`.
- Requer configuração de CORS no backend para aceitar origem do frontend em dev (`localhost:5173`) — se ainda não existir, deve ser levantado em design.md.
- Requer variáveis de ambiente de frontend: Cognito domain, client id, redirect URIs, API base URL.
