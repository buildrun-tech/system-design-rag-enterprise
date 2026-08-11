## 1. Spike: validar floci

- [x] 1.1 Testar `SignUp` e `InitiateAuth` (`USER_PASSWORD_AUTH`) via AWS CLI direto contra floci (`http://localhost:4566`), confirmando suporte antes de tocar o frontend — confirmado: floci suporta `USER_PASSWORD_AUTH` e `ConfirmSignUp` não valida o código (qualquer valor confirma)
- [x] 1.2 Se floci não suportar, documentar em design.md a alternativa — não necessário, floci suporta o fluxo completo; decisão registrada em design.md (`ConfirmSignUp` com código fixo, sem endpoint backend nem mudança de pool)

## 2. Local dev: Cognito client

- [x] 2.1 Atualizar `start_local.sh` para habilitar `ALLOW_USER_PASSWORD_AUTH`/`ALLOW_REFRESH_TOKEN_AUTH` no client (criação e atualização via `update-user-pool-client`, client público sem secret)
- [x] 2.2 Rodar `start_local.sh` do zero e validar que o client sobe com os auth flows corretos (`describe-user-pool-client`)

## 3. Tokens visuais e componentes base

- [x] 3.1 Substituir tokens de `index.css` pelos valores de DESIGN.md (cor primary, radius, spacing, tipografia)
- [x] 3.2 Criar `components/ui/Button.tsx`
- [x] 3.3 Criar `components/ui/Input.tsx`
- [x] 3.4 Criar `components/ui/Card.tsx`

## 4. Aplicar componentes nas telas existentes

- [x] 4.1 Restyle `LoginPage.tsx` com `Button`/`Card`
- [x] 4.2 Restyle `NotebooksPage.tsx` com `Button`/`Input`/`Card`
- [x] 4.3 Restyle `WorkspacePage.tsx` com `Button`/`Input`/`Card`
- [x] 4.4 Restyle `UserMenu.tsx` com `Button`

## 5. Auth direto por email/senha

- [x] 5.1 Adicionar dependência `amazon-cognito-identity-js` em `app/frontend/package.json`
- [x] 5.2 Adicionar `VITE_ENABLE_DIRECT_SIGNUP` em `.env.example` do frontend
- [x] 5.3 Criar módulo `src/auth/directAuth.ts` (CognitoUserPool, funções signUp/signIn)
- [x] 5.4 Criar hook `useDirectAuth` expondo estado de sessão do fluxo direto
- [x] 5.5 Criar hook único `useActiveAuth()` que consulta sessão OIDC primeiro, depois sessão direta (ajuste: hook React em vez de função pura, token OIDC só existe via contexto `useAuth()`)
- [x] 5.6 Atualizar `ProtectedRoute.tsx`, `UserMenu.tsx` e páginas para usar `useActiveAuth()`
- [x] 5.7 Adicionar formulário de cadastro/login direto em `LoginPage.tsx`, condicionado a `VITE_ENABLE_DIRECT_SIGNUP`

## 6. Validação manual

- [x] 6.1 Testar cadastro direto local: criar conta, login automático — validado via script Node usando `amazon-cognito-identity-js` (mesmo código de `directAuth.ts`) contra floci real; sem tool de browser neste ambiente, não cliquei na UI renderizada
- [x] 6.2 Testar login direto local com credenciais existentes — validado no mesmo script (passo 3)
- [x] 6.3 Testar login direto com senha incorreta — validado no mesmo script (passo 4), falha como esperado sem sessão
- [x] 6.4 Fluxo social intacto quando flag desligada — verificado por leitura de código: `LoginPage.tsx` sempre renderiza os botões sociais (`auth.signinRedirect`), independente de `DIRECT_SIGNUP_ENABLED`; não testado em browser
- [x] 6.5 Form direto não aparece com flag desligada — verificado por leitura de código: bloco do formulário é `{DIRECT_SIGNUP_ENABLED && (...)}`; não testado em browser
