## Context

`VITE_*` são substituídos no build. CI (`frontend.yml` job `push-dev`) builda sem `.env` e sem credenciais AWS; outputs do Terraform só são lidos no deploy, depois do build. `directAuth.ts` cria `CognitoUserPool` no top-level do módulo, então qualquer import com valores `undefined` derruba o app. Promote para prod reusa `dist/` de dev (build once).

Outputs relevantes já expostos por `.github/actions/terraform-outputs`: `api_url`, `cognito_authority`, `cognito_client_id`, `cognito_user_pool_id`, `cloudfront_domain`. Callback URLs do Cognito já usam domínio CloudFront (`infra/main.tf:54`).

## Goals / Non-Goals

**Goals:**
- App sobe em dev e prod AWS com config correta por ambiente.
- Manter build once + promote sem rebuild.
- Dev local e e2e sem fricção nova.

**Non-Goals:**
- Mudar backend.
- Levar direct signup (floci) para AWS. `VITE_ENABLE_DIRECT_SIGNUP` e `VITE_COGNITO_ENDPOINT` seguem build-time/local.
- Passar outputs do job `apply` para o frontend (quebra quando infra é skipped; state S3 é a fonte).

## Decisions

**1. `config.js` síncrono em vez de `config.json` via fetch.**
`<script src="/config.js"></script>` antes do `<script type="module">` no `index.html` define `window.__APP_CONFIG__`. Módulos continuam lendo constantes (ex.: `oidcConfig`), sem bootstrap assíncrono antes do `createRoot`. Alternativa fetch exigiria refatorar `main.tsx` e todos os módulos que leem config no top-level.

**2. Módulo único `src/config.ts` com fallback.**
Exporta objeto `config` com cada campo `window.__APP_CONFIG__?.x ?? import.meta.env.VITE_X`. Consumidores (`oidcConfig.ts`, `directAuth.ts`, `api/client.ts`, `WorkspacePage.tsx`) trocam `import.meta.env` por `config`. Declaração de tipo de `window.__APP_CONFIG__` no mesmo arquivo. Alternativa `public/config.js` obrigatório local foi descartada: mais um arquivo para copiar, e2e mudaria.

**3. Composite action `.github/actions/frontend-runtime-config`.**
Inputs: `environment`. Chama `terraform-outputs`, valida chaves obrigatórias (vazio ou literal `null` falha com `::error::` nomeando a chave), escreve `dist/config.js`. Jobs de deploy dev/prod passam a: download artifact, credenciais, esta action (substitui chamada direta a `terraform-outputs` e expõe `frontend_bucket_name`/`cloudfront_distribution_id`), `s3 sync dist --delete --exclude config.js`, `s3 cp dist/config.js --cache-control no-cache --content-type application/javascript`, invalidation `/*`. Gerar via `jq -n` garante escape JSON correto dos valores.

**4. Ordem de upload: `config.js` antes do sync dos assets?**
Sync com `--exclude config.js` primeiro, depois `cp` do `config.js`. Como `--delete` com `--exclude` não remove `config.js` existente, não há janela sem config. Valores novos de config só mudam quando infra muda; janela entre assets novos e config nova é curta e coberta pela invalidação.

**5. `CognitoUserPool` lazy.**
Getter interno memoizado em `directAuth.ts` (`getUserPool()`), chamado dentro de `signIn`/`signUp`/`getStoredSession`/`signOut` quando precisam do pool. Import do módulo deixa de ter efeito colateral.

**6. `redirectUri`.**
Gerado no deploy como `https://<cloudfront_domain>/`, casando com `callback_urls` do Cognito.

**7. Policy única no bucket do frontend.**
Bucket S3 aceita uma policy só. `s3-bucket` (`DenyInsecureTransport`) e `cloudfront-spa` (`AllowCloudFrontOAC`) tinham cada um seu `aws_s3_bucket_policy` e se sobrescreviam. `s3-bucket` ganha `policy_documents` (mesclado via `source_policy_documents`); `cloudfront-spa` expõe `bucket_policy_json` em vez de aplicar. Sem ciclo: bucket, distribution, policy doc, bucket policy.

## Risks / Trade-offs

- [`config.js` ausente no bucket: CloudFront responde 200 + `index.html` (custom_error_response 403/404), browser falha com `SyntaxError`] → validação no deploy impede publicar sem config; `cp` explícito com erro se arquivo não existir.
- [Browser cacheia `config.js` antigo] → `Cache-Control: no-cache` + invalidation `/*` já existente.
- [Dev local: Vite responde 404 para `/config.js`, erro no console] → inofensivo; fallback cobre. Aceito.
- [Config pública exposta em `config.js`] → valores já seriam públicos no bundle (client id, pool id, URLs). Nenhum segredo entra no arquivo.
- [Destroy de `aws_s3_bucket_policy.frontend_oac` chama DeleteBucketPolicy e apagaria a policy mesclada] → bloco `removed { lifecycle { destroy = false } }` só tira do state; update de `aws_s3_bucket_policy.this` grava a policy com as duas statements.
- [`getStoredSession` usado no init de `useDirectAuth` com direct auth desligado] → só lê `sessionStorage`; não toca no pool.

## Migration Plan

Merge em develop: deploy dev gera `config.js` e app sobe. Promote para main reusa mesmo `dist/`, deploy prod gera `config.js` de prod. Rollback: reverter commit; sem estado persistente envolvido.
