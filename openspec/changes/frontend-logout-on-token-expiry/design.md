## Context

Front usa dois modos de sessão atrás de `useActiveAuth`: OIDC (`react-oidc-context`, Hosted UI) e direct auth (`amazon-cognito-identity-js`, tokens em `sessionStorage`, dev local). Nenhum dos dois reage a token expirado:

- `apiFetch` (`api/client.ts`) joga `ApiError` em qualquer `!response.ok`; páginas só exibem `err.message` ou nem têm `.catch`.
- `ProtectedRoute` lê `isAuthenticated`, que é snapshot (OIDC: calculado no load; direct: `session !== null`, sem checar `exp`).
- SSE do chat (`WorkspacePage.tsx`, `fetchEventSource`) não passa por `apiFetch`; `onopen` só lança `Error("stream failed: N")`.

Backend (`SecurityConfig`: `oauth2ResourceServer(jwt)` default) responde 401 com **corpo vazio** e `WWW-Authenticate: Bearer error="invalid_token"`. Não devolve `ApiErrorBody`, ao contrário do que `API.md` diz (`UNAUTHORIZED`). Logo, `apiFetch` cairia no fallback `UNKNOWN_ERROR`; só o status é confiável.

## Goals / Non-Goals

**Goals:**
- 401 em qualquer chamada autenticada (REST ou SSE) desloga e leva para `/`.
- Reload com sessão direta expirada não restaura sessão morta.
- Falha de silent renew do OIDC desloga.

**Non-Goals:**
- Refresh de token no direct auth (`refreshSession`).
- Timer proativo no `exp`.
- Retry automático da requisição após renew.
- Mudar backend ou corrigir divergência com `API.md`.

## Decisions

1. **Handler global por módulo, não evento DOM nem contexto.** `client.ts` exporta `setUnauthorizedHandler(fn)` e `handleUnauthorized()`. `apiFetch` e o `onopen` do SSE chamam `handleUnauthorized()` em `status === 401`. Um componente/efeito sob o `BrowserRouter` registra o handler (`signOut()` + `navigate('/', { replace: true })`). Alternativas: `window.dispatchEvent` (indireção sem tipagem), React Context + wrapper de fetch (muda todos os callers). Módulo é o menor diff e cobre todos os callers num ponto só.
2. **Decisão só pelo status 401**, ignorando corpo/header, por causa do corpo vazio do Spring. 403 não desloga (é autorização, não sessão).
3. **`apiFetch` ainda lança `ApiError`** depois de chamar o handler, para os callers existentes não precisarem mudar nem receberem `undefined`; o redirect desmonta a página.
4. **Direct auth: checar `exp` em `getStoredSession`.** Decodificar o payload do access token (base64url, `atob`, sem lib) e, se `exp * 1000 <= Date.now()`, chamar `clearStoredSession()` e devolver `null`. Alternativa de timer foi descartada (YAGNI: o próximo 401 já cobre sessão aberta).
5. **OIDC: `useEffect` com `oidc.events.addSilentRenewError(handleUnauthorized)`** e cleanup via a função retornada (`removeSilentRenewError`).

## Risks / Trade-offs

- [Cada `useActiveAuth()` tem seu próprio `useDirectAuth` com `useState`; o `signOut` do handler não atualiza outras instâncias] → o `navigate('/')` remonta as rotas, que releem `sessionStorage` já limpo. Não precisa estado compartilhado.
- [Mensagem de SSE em andamento fica `failed` antes do redirect] → aceitável, a página é desmontada logo em seguida.
- [Payload do JWT malformado em `getStoredSession`] → tratar como expirado (limpar e `null`), nunca lançar.
- [Sessão expira sem nenhuma requisição] → usuário só vê tela parada até a próxima ação; aceito, ver Non-Goals.
