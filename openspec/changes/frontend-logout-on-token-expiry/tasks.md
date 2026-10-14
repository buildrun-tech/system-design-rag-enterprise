## 1. Handler global de 401

- [x] 1.1 Em `app/frontend/src/api/client.ts`, exportar `setUnauthorizedHandler(fn)` e `handleUnauthorized()`; em `apiFetch`, chamar `handleUnauthorized()` quando `response.status === 401` antes de lançar `ApiError`
- [x] 1.2 Em `WorkspacePage.tsx`, no `onopen` do `fetchEventSource`, chamar `handleUnauthorized()` quando `response.status === 401` antes de lançar o erro
- [x] 1.3 Registrar o handler sob o `BrowserRouter` (efeito em `App.tsx`): `signOut()` de `useActiveAuth` + `navigate('/', { replace: true })`; limpar com `setUnauthorizedHandler(null)` no cleanup

## 2. Sessão expirada

- [x] 2.1 Em `auth/directAuth.ts`, `getStoredSession` decodifica `exp` do access token; se expirado ou payload ilegível, `clearStoredSession()` e retorna `null`
- [x] 2.2 Em `useActiveAuth.ts` (ou no efeito de 1.3), registrar `oidc.events.addSilentRenewError(handleUnauthorized)` com cleanup `removeSilentRenewError`

## 3. Validação

- [x] 3.1 `rtk npm run build` e `rtk npm run lint` em `app/frontend` sem erros
- [ ] 3.2 Teste manual direct auth: logar, alterar `exp` no `sessionStorage` (ou invalidar o token) e recarregar → cai em `/`
- [ ] 3.3 Teste manual 401: com sessão ativa, forçar 401 (token adulterado) em listagem de notebooks e no envio de mensagem do chat → logout e redirect para `/`
