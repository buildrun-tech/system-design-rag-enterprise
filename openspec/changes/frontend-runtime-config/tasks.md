## 1. Frontend runtime config

- [x] 1.1 Criar `app/frontend/src/config.ts` exportando `config` (`apiBaseUrl`, `cognitoAuthority`, `cognitoClientId`, `cognitoUserPoolId`, `cognitoRedirectUri`) com `window.__APP_CONFIG__?.x ?? import.meta.env.VITE_X` e declaração de tipo de `window.__APP_CONFIG__`
- [x] 1.2 Adicionar `<script src="/config.js"></script>` no `app/frontend/index.html` antes do script module
- [x] 1.3 Trocar `import.meta.env` por `config` em `src/auth/oidcConfig.ts`, `src/api/client.ts`, `src/pages/WorkspacePage.tsx`
- [x] 1.4 Tornar `CognitoUserPool` lazy em `src/auth/directAuth.ts` (getter memoizado usando `config`; `VITE_COGNITO_ENDPOINT` segue build-time)
- [ ] 1.5 `npm run lint` e `npm run build` passam; `npm run dev` com `.env` local faz login como antes

## 2. Deploy

- [x] 2.1 Criar composite `.github/actions/frontend-runtime-config/action.yml` (input `environment`): chama `terraform-outputs`, valida chaves obrigatórias (vazio/`null` falha nomeando a chave), gera `dist/config.js` via `jq -n`, expõe `frontend_bucket_name` e `cloudfront_distribution_id`
- [x] 2.2 Atualizar `deploy-frontend-dev` e `deploy-frontend-prod` em `.github/workflows/frontend.yml`: usar a composite, `s3 sync dist --delete --exclude config.js`, `s3 cp dist/config.js` com `--cache-control no-cache --content-type application/javascript`, invalidation `/*`
- [x] 2.3 Incluir `.github/actions/frontend-runtime-config/**` no filtro `frontend` de `.github/workflows/pipeline.yml`

## 3. Verificação

- [ ] 3.1 Após deploy dev: `curl https://<cloudfront_domain>/config.js` retorna JS com valores de dev e header `cache-control: no-cache`
- [ ] 3.2 App em CloudFront dev carrega sem erro no console e login via Hosted UI funciona
- [ ] 3.3 Após promote para main: `config.js` de prod tem pool/client de prod com mesmo bundle hash de dev
