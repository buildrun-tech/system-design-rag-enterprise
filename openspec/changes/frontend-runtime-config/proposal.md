## Why

Frontend em CloudFront quebra no load com `Uncaught Error: Both UserPoolId and ClientId are required.`. `VITE_*` são inlined no build, e o build do CI roda sem `.env` (gitignored) nem outputs do Terraform — tudo vira `undefined`. Pior: promote para prod reusa o `dist/` de dev sem rebuild, então config de build-time levaria Cognito/API de dev para prod.

## What Changes

- Config de ambiente (API base URL, Cognito authority/client/user pool, redirect URI) passa a ser lida em **runtime** de `/config.js` (`window.__APP_CONFIG__`), carregado por `<script>` síncrono no `index.html` antes do bundle.
- Fallback para `import.meta.env.VITE_*` quando `window.__APP_CONFIG__` ausente — dev local e e2e seguem usando `.env`.
- Deploy (dev e prod) gera `config.js` a partir de `terraform-outputs` do ambiente alvo, falha se algum valor vier vazio/`null`, sobe com `Cache-Control: no-cache`. Composite action única para dev e prod.
- `CognitoUserPool` em `directAuth.ts` deixa de ser instanciado no import; criado lazy, só quando direct signup (floci) está ligado.
- Build once preservado: mesmo `dist/` serve dev e prod; promote sem rebuild continua válido.

## Capabilities

### New Capabilities
- `frontend-runtime-config`: frontend obtém configuração de ambiente em runtime via `config.js` gerado no deploy, com fallback build-time para dev local.

### Modified Capabilities
<!-- nenhuma: comportamento de login/rotas/API inalterado; muda só a origem dos valores -->

## Impact

- `app/frontend/index.html`, novo `app/frontend/src/config.ts`, `src/auth/oidcConfig.ts`, `src/auth/directAuth.ts`, `src/api/client.ts`, `src/pages/WorkspacePage.tsx`.
- `.github/workflows/frontend.yml` (jobs `deploy-frontend-dev`, `deploy-frontend-prod`), nova composite em `.github/actions/frontend-runtime-config/`.
- Sem mudança em Terraform, backend ou `pipeline.yml`.
