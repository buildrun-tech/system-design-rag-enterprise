## Why

Quando o JWT expira, o front não desloga: `apiFetch` joga `ApiError(401)` que cada página trata como erro genérico (ou nem trata), `ProtectedRoute` continua vendo a sessão como autenticada e o SSE do chat só marca a mensagem como `failed`. Resultado: app "travada" com telas vazias até o usuário recarregar e fazer logout manualmente.

## What Changes

- `apiFetch` e o `onopen` do `fetchEventSource` (chat) passam a tratar `status === 401` como sessão expirada: executam logout (`signOut`) e redirecionam para `/`.
- Handler olha **só o status**: o backend (Spring `oauth2ResourceServer` default) responde 401 com corpo vazio e header `WWW-Authenticate: Bearer error="invalid_token"`, não com `ApiErrorBody` JSON (apesar de `API.md` documentar `UNAUTHORIZED`).
- Direct auth: `getStoredSession` decodifica o `exp` do access token e descarta a sessão (limpa `sessionStorage`) se expirada, cobrindo reload com token morto.
- OIDC: erro de silent renew (`events.addSilentRenewError`) dispara logout.
- Sem refresh de token no direct auth e sem timer proativo no `exp`: o próximo request que levar 401 já desloga.
- Sem mudanças no backend.

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `frontend-auth`: requisito "Sessão autenticada em requisições à API" ganha cenário de token expirado/rejeitado (401) com logout automático; requisito "Rotas protegidas" cobre sessão direta expirada no reload.

## Impact

- Código: `app/frontend/src/api/client.ts`, `app/frontend/src/auth/useActiveAuth.ts`, `app/frontend/src/auth/directAuth.ts`, `app/frontend/src/pages/WorkspacePage.tsx` (SSE `onopen`), ponto de montagem do handler (ex.: `App.tsx`/`main.tsx`).
- APIs/backend/infra: nenhum impacto. Sem novas dependências.
- Doc: `API.md` diz que 401 retorna `UNAUTHORIZED`; divergência com o comportamento real do backend fica registrada no design, fora de escopo aqui.
