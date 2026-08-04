## 1. Backend — CORS para dev local

- [x] 1.1 Adicionar `CorsConfigurationSource` em `SecurityConfig` liberando origem `http://localhost:5173` (profile dev), métodos GET/POST/PATCH/DELETE, header `Authorization`
- [x] 1.2 Rodar `/java-quality-gate` sobre a mudança em `SecurityConfig`

## 2. Setup do projeto frontend

- [x] 2.1 Criar `app/frontend/` com Vite + React + TypeScript (`npm create vite@latest`)
- [x] 2.2 Instalar dependências: `react-router-dom`, `react-oidc-context`, `oidc-client-ts`, `@microsoft/fetch-event-source`
- [x] 2.3 Configurar `.env.example` com `VITE_API_BASE_URL`, `VITE_COGNITO_AUTHORITY`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_REDIRECT_URI`
- [x] 2.4 Configurar `.gitignore` do frontend (`node_modules`, `dist`, `.env`)

## 3. Autenticação (frontend-auth)

- [x] 3.1 Configurar `AuthProvider` do `react-oidc-context` em `main.tsx` com endpoint OIDC do Cognito
- [x] 3.2 Criar `LoginPage.tsx` (Tela 1) com botões "Login Google" e "Login Github", cada um chamando `signinRedirect` com o `identity_provider` correspondente
- [x] 3.3 Criar componente `ProtectedRoute` que redireciona para `/` quando não autenticado
- [x] 3.4 Criar `api/client.ts`: wrapper de `fetch` que injeta `Authorization: Bearer <token>` e lança erro tipado a partir de `{error, message}`
- [x] 3.5 Implementar logout (ícone "p") limpando sessão e redirecionando para login

## 4. Notebooks (frontend-notebooks)

- [x] 4.1 Criar `NotebooksPage.tsx` (Tela 2): buscar `GET /api/v1/notebooks` e listar com botão "abrir" por item
- [x] 4.2 Implementar botão "criar": modal/inline form com campo nome, chama `POST /api/v1/notebooks`, atualiza lista
- [x] 4.3 Tratar erro de validação (`400 VALIDATION_ERROR`) exibindo mensagem inline
- [x] 4.4 Ligar ação "abrir" à navegação `react-router` para `/notebooks/:id`

## 5. Workspace — sources e chat (frontend-workspace)

- [x] 5.1 Criar `WorkspacePage.tsx` (Tela 3) com layout de dois painéis: sources | chat
- [x] 5.2 Buscar e listar sources via `GET /api/v1/notebooks/{id}/sources`, exibindo status
- [x] 5.3 Ao entrar no workspace: buscar `GET /api/v1/notebooks/{id}/conversations`; se vazio, criar via `POST` com `activeSourceIds` omitido
- [x] 5.4 Buscar histórico via `GET /api/v1/conversations/{id}/messages` e renderizar mensagens (`user`/`assistant`)
- [x] 5.5 Implementar input de envio: `POST /api/v1/conversations/{id}/messages` usando `@microsoft/fetch-event-source` com header `Authorization`
- [x] 5.6 Parsear eventos SSE (`token`, `done`, `error`) atualizando a mensagem do assistente em streaming
- [x] 5.7 Tratar erro de stream exibindo estado de falha na mensagem, sem travar a UI

## 6. Verificação manual

- [x] 6.1 Rodar backend local (`docker-compose`) + frontend (`npm run dev`), validar CORS — build/dev server verificados; CORS depende de backend rodando com Cognito real (não executável neste ambiente)
- [ ] 6.2 Testar fluxo completo: login → criar notebook → abrir → enviar mensagem → ver resposta em streaming — requer credenciais reais Cognito/Google/Github, manual
- [ ] 6.3 Testar logout e proteção de rota (acesso direto a `/notebooks` sem sessão) — requer sessão real, manual
