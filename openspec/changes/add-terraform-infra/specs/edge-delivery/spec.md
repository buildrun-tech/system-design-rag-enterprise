## ADDED Requirements

### Requirement: API Gateway HTTP API com VPC Link privado
O módulo `api-gateway-http` SHALL criar uma HTTP API, um VPC Link nas subnets públicas com SG `vpclink-sg`, uma integração `HTTP_PROXY` privada para o `listener_arn` do ALB e a rota `ANY /{proxy+}` com stage `$default` em auto-deploy. SHALL NOT usar domínio customizado nem certificado; a URL exposta é o endpoint padrão `execute-api`. A integração SHALL NOT exigir role IAM. CORS SHALL permitir apenas as origens recebidas por variável (domínio CloudFront), com métodos e headers explícitos (incluindo `Authorization`, `Content-Type`).

#### Scenario: Requisição chega ao ALB
- **WHEN** um cliente faz `GET <api_url>/api/v1/notebooks` com `Authorization: Bearer <jwt>`
- **THEN** o API Gateway encaminha via VPC Link para o ALB, que roteia para o target group da app

#### Scenario: Preflight CORS
- **WHEN** o browser envia `OPTIONS` com `Origin` igual ao domínio CloudFront
- **THEN** a resposta inclui `Access-Control-Allow-Origin` com essa origem

#### Scenario: Origem não autorizada
- **WHEN** o `Origin` não está na lista
- **THEN** a resposta não inclui `Access-Control-Allow-Origin`

### Requirement: Validação de streaming SSE registrada
O design SHALL registrar que o limite de timeout de integração do HTTP API é 30 s e que o comportamento de streaming SSE atrás do API Gateway precisa ser validado por spike antes do uso em produção. O módulo SHALL expor `integration_timeout_ms` como variável (padrão 30000).

#### Scenario: Timeout configurável
- **WHEN** `integration_timeout_ms` é alterado dentro do limite do serviço
- **THEN** a integração aplica o novo valor no próximo apply

### Requirement: CloudFront para o frontend estático com OAC
O módulo `cloudfront-spa` SHALL criar uma distribuição com origem no bucket `frontend` via Origin Access Control (SigV4), `default_root_object = index.html`, redirect HTTP→HTTPS, e `custom_error_response` mapeando 403 e 404 para `/index.html` com status 200. SHALL usar o domínio padrão `*.cloudfront.net` (sem alias, sem ACM). O módulo SHALL criar a bucket policy do bucket `frontend` permitindo `s3:GetObject` ao principal `cloudfront.amazonaws.com` com condição `AWS:SourceArn = <arn da distribuição>`. SHALL exportar `distribution_id`, `distribution_arn` e `domain_name`.

#### Scenario: Rota da SPA
- **WHEN** o browser requisita `/notebooks/123` (rota client-side inexistente no S3)
- **THEN** recebe `index.html` com status 200

#### Scenario: Só a distribuição lê o bucket
- **WHEN** outra distribuição CloudFront tenta ler o bucket `frontend`
- **THEN** a requisição é negada por `AWS:SourceArn`

#### Scenario: HTTPS obrigatório
- **WHEN** o browser acessa a distribuição por HTTP
- **THEN** é redirecionado para HTTPS

### Requirement: Cognito user pool com domínio padrão
O módulo `cognito-user-pool` SHALL criar um user pool, um app client público (sem secret) com fluxos `ALLOW_USER_PASSWORD_AUTH`/`ALLOW_REFRESH_TOKEN_AUTH`, e um domain prefix em `amazoncognito.com`. `callback_urls` e `logout_urls` SHALL ser recebidas por variável (derivadas do domínio CloudFront). Signup direto SHALL ser controlado por variável (`allow_admin_create_user_only`). IdP Google SHALL ser opcional (criado só se client id/secret forem informados). O módulo SHALL NOT configurar GitHub como IdP. SHALL exportar `user_pool_id`, `client_id`, `issuer_url` (`https://cognito-idp.<region>.amazonaws.com/<pool_id>`) e `domain`.

#### Scenario: Issuer compatível com a app
- **WHEN** a app usa `issuer_url` como `spring.security.oauth2.resourceserver.jwt.issuer-uri`
- **THEN** o JWKS `/.well-known/jwks.json` do pool é resolvido e os JWT são validados

#### Scenario: Sem Google
- **WHEN** as credenciais do Google não são informadas
- **THEN** nenhum `aws_cognito_identity_provider` é criado e o pool funciona só com login direto

### Requirement: Cadeia de origens sem ciclo
As dependências de domínio SHALL ser lineares: `cloudfront-spa` depende só do bucket `frontend`; `cognito-user-pool`, `api-gateway-http` (CORS) e `ecs-service` (`APP_CORS_ALLOWED_ORIGINS`) dependem do `domain_name` do CloudFront. O CloudFront SHALL NOT referenciar o API Gateway.

#### Scenario: Grafo acíclico
- **WHEN** `terraform graph` é gerado
- **THEN** não existe ciclo envolvendo `cloudfront-spa`, `cognito-user-pool`, `api-gateway-http` e `ecs-service`

### Requirement: Contrato de build do frontend
O frontend SHALL ser configurado em build com valores derivados dos outputs: `VITE_API_BASE_URL = api_url`, `VITE_COGNITO_AUTHORITY = cognito_authority`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_USER_POOL_ID` e `VITE_COGNITO_REDIRECT_URI` = `https://<cloudfront_domain>/`.

#### Scenario: Outputs suficientes
- **WHEN** o CI consulta `terraform output -json`
- **THEN** consegue montar todas as variáveis `VITE_*` sem input manual
