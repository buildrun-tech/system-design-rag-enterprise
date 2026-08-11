## Context

Frontend React+Vite, roteamento `react-router-dom`, auth hoje 100% via `react-oidc-context` (fluxo OIDC Authorization Code + PKCE contra Cognito Hosted UI). Ambiente local usa floci (LocalStack) emulando só a API Cognito, sem servir a página Hosted UI — por isso `login.sh` contorna o browser com `admin-initiate-auth`. `index.css` tem tokens de scaffold Vite não relacionados a DESIGN.md. Telas usam inline style, sem componentes reutilizáveis.

## Goals / Non-Goals

**Goals:**
- Tokens visuais e componentes base (Button, Input, Card) seguindo DESIGN.md, aplicados nas 3 telas
- Fluxo de cadastro/login direto por email+senha funcionando contra floci, sem passar pelo Hosted UI
- Não quebrar fluxo social existente (continua íntegro para produção)

**Non-Goals:**
- Não remove nem substitui Hosted UI/login social
- Não implementa recuperação de senha nem MFA
- Não migra para Amplify (SDK completo) — usa `amazon-cognito-identity-js`, biblioteca menor, direto pros API calls (`SignUp`, `InitiateAuth`)
- Não cobre confirmação de email real (útil só em produção, fora de escopo aqui)

## Decisions

**Biblioteca de auth direta: `amazon-cognito-identity-js` em vez de AWS Amplify**
Amplify traz Auth+Storage+API acoplados, config extra, bundle maior. `amazon-cognito-identity-js` faz só `CognitoUserPool`/`CognitoUser`, chama `SignUp`/`InitiateAuth`/`authenticateUser` direto — equivalente ao client usado por `login.sh`, mais fácil mapear 1:1.

**Coexistência dos dois fluxos: flag de ambiente, não detecção automática**
`VITE_ENABLE_DIRECT_SIGNUP=true` no `.env` local liga o form extra na LoginPage. Produção não seta a flag → só botões sociais aparecem. Evita lógica implícita tipo "se for localhost" que quebra em preview/staging.

**Sessão do fluxo direto integra com o mesmo `AuthProvider`**
`react-oidc-context` gerencia sessão via `User` do `oidc-client-ts`. Fluxo direto não passa pelo OIDC discovery, mas token retornado por `InitiateAuth` é JWT Cognito válido — mesmo formato que o backend já valida via `issuer-uri`. Solução: módulo próprio de sessão (`directAuth.ts`) armazenando token em memória/sessionStorage e um hook `useDirectAuth` que os componentes (`ProtectedRoute`, `apiFetch`) consultam como fallback quando `auth.isAuthenticated` (OIDC) é falso. Evita reescrever `react-oidc-context` ou forjar um `User` OIDC fake.

**Confirmação de signup: `ConfirmSignUp` com código fixo dummy (validado via spike)**
Spike confirmou: floci aceita `ALLOW_USER_PASSWORD_AUTH` via `update-user-pool-client`, e `ConfirmSignUp` não valida o conteúdo do código — qualquer valor (`"000000"`) confirma o usuário (`UserStatus: CONFIRMED`). Não precisa mexer em `AutoVerifiedAttributes` do pool nem criar endpoint backend dev-only: o módulo `directAuth.ts` chama `SignUp` e, na sequência, `ConfirmSignUp` com um código fixo, ambos client-side. Client Cognito local ganha `ALLOW_USER_PASSWORD_AUTH`/`ALLOW_REFRESH_TOKEN_AUTH` além do `ADMIN_NO_SRP_AUTH` existente, e já é client público (sem secret) — confirmado via `update-user-pool-client`.

**Componentes base: pasta `components/ui/`**
`Button.tsx`, `Input.tsx`, `Card.tsx` — props mínimas (variant quando fizer sentido), sem lib de estilo nova (styled-components, tailwind). CSS via classes + as custom properties já centralizadas em `index.css` (tokens DESIGN.md). Sem storybook, sem testes de snapshot — YAGNI pro tamanho do projeto.

## Risks / Trade-offs

[Dois sistemas de sessão em paralelo (OIDC + direto) aumentam superfície de bug em `ProtectedRoute`/`apiFetch`] → Mitigação: único ponto de leitura de token (`getActiveToken()` que checa OIDC primeiro, depois direto), coberto por teste manual dos 2 fluxos antes de mergear.

[Recriar/alterar user pool local pode invalidar `COGNITO_USER_POOL_ID`/`COGNITO_CLIENT_ID` já salvos em `.env`] → Mitigação: `start_local.sh` já é idempotente (checa existência antes de criar); ajuste usa `update-user-pool-client`/`update-user-pool` quando já existe, não recria do zero.

[floci pode não implementar 100% da API `SignUp`/`InitiateAuth` com `USER_PASSWORD_AUTH`] → Mitigação: validar manualmente contra floci antes de finalizar; se não suportar, cair para `ADMIN_NO_SRP_AUTH` via endpoint backend dev-only como alternativa (registrar em Open Questions).

## Migration Plan

Sem dado de produção envolvido — mudança aditiva em componente novo + config local. Deploy: normal, sem passo de rollback especial. Se `USER_PASSWORD_AUTH` no client de produção for indesejado, flag `VITE_ENABLE_DIRECT_SIGNUP` continua desligada lá e o client de produção nem precisa do flow habilitado.

## Open Questions

- floci suporta `USER_PASSWORD_AUTH`/`SignUp` completo? Validar antes de codar o form (spike rápido com AWS CLI antes de tocar frontend).
- Se floci não suportar, alternativa é endpoint dev-only no backend chamando `admin-initiate-auth` (mesmo mecanismo do `login.sh`) — decidir só se o SDK direto falhar.
