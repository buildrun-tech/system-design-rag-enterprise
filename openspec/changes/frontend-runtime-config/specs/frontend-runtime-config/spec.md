## ADDED Requirements

### Requirement: Configuração de ambiente em runtime
O frontend SHALL ler API base URL, Cognito authority, Cognito client id, Cognito user pool id e redirect URI de `window.__APP_CONFIG__`, definido por `/config.js` carregado de forma síncrona antes do bundle da aplicação.

#### Scenario: config.js presente
- **WHEN** página carrega e `/config.js` define `window.__APP_CONFIG__` com todos os campos
- **THEN** OIDC, cliente de API e workspace usam os valores de `window.__APP_CONFIG__`

#### Scenario: Mesmo build em ambientes distintos
- **WHEN** mesmo `dist/` é publicado em dev e em prod com `config.js` diferentes
- **THEN** cada ambiente usa Cognito e API do seu próprio `config.js`, sem rebuild

### Requirement: Fallback para variáveis de build em dev local
O frontend SHALL usar `import.meta.env.VITE_*` para cada campo ausente em `window.__APP_CONFIG__`.

#### Scenario: Dev local sem config.js
- **WHEN** app roda via `npm run dev` sem `public/config.js` e com `.env` preenchido
- **THEN** app usa valores do `.env` e login local funciona como antes

### Requirement: Geração validada de config.js no deploy
O deploy do frontend (dev e prod) SHALL gerar `config.js` a partir dos outputs do Terraform do ambiente alvo, MUST falhar se qualquer valor obrigatório estiver vazio ou `null`, e MUST publicar `config.js` com `Cache-Control: no-cache`.

#### Scenario: Outputs completos
- **WHEN** `terraform-outputs` retorna todos os valores do ambiente
- **THEN** `config.js` é publicado no bucket do ambiente com `Cache-Control: no-cache` e redirect URI `https://<cloudfront_domain>/`

#### Scenario: Output ausente
- **WHEN** algum output obrigatório vem vazio ou `null`
- **THEN** job de deploy falha antes do `s3 sync` com erro nomeando a chave ausente

### Requirement: Direct auth não inicializa fora do modo habilitado
O cliente direct auth (floci) SHALL instanciar `CognitoUserPool` somente no primeiro uso, e o carregamento da aplicação MUST NOT falhar quando direct signup está desabilitado e config de user pool ausente.

#### Scenario: Direct signup desabilitado
- **WHEN** `VITE_ENABLE_DIRECT_SIGNUP` não é `true` e user pool id não está configurado
- **THEN** app carrega sem erro e login via Hosted UI funciona
