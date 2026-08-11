## Context

Backend (`app/backend-api`) é resource server puro: Spring Security valida JWT do Cognito, não expõe login/token endpoint. `SecurityConfig` exige `authorizeHttpRequests(anyRequest().authenticated())` + `oauth2ResourceServer(jwt)`. Arquitetura (`ARCHITECTURE.md`) já define o frontend como "React SPA + Cognito SDK" na frente do API Gateway. Não existe nenhum código de frontend hoje — build do zero.

Fluxos de dado (`API.md`) já definidos: REST comum para notebooks/sources/conversations, SSE token-a-token para chat (`POST /conversations/{id}/messages`), erro padrão `{error, message}`.

## Goals / Non-Goals

**Goals:**
- 3 telas funcionais: login, lista de notebooks, workspace (sources + chat).
- Login OAuth2/PKCE contra Cognito Hosted UI, sem backend de auth próprio.
- Chat com streaming SSE real (token a token), autenticado.
- Rodar local contra backend local (`docker-compose`) com CORS configurado.

**Non-Goals:**
- Design visual/polish — CSS mínimo, sem design system.
- Upload de source, delete/edit de notebook, delete de source.
- Paginação de notebooks (API já suporta, MVP ignora — lista completa).
- Retry/reconexão sofisticada de SSE, offline support.
- Testes E2E (fica pra depois; MVP funcional primeiro).

## Decisions

**1. Vite + React + TypeScript, sem framework SSR.**
Arquitetura já declara "React SPA". Next.js traria SSR/roteamento de servidor que não tem uso aqui (tudo atrás de auth, nada indexável). Vite dá dev server rápido e zero config de servidor.

**2. `react-oidc-context` (wrapper de `oidc-client-ts`) para o fluxo Cognito.**
Alternativas consideradas: AWS Amplify (mais pesado, traz categorias inteiras — Storage, API — que não usamos, MVP não precisa) e PKCE manual (reimplementar validação de state/nonce/code_verifier é risco de segurança desnecessário quando lib madura resolve). `react-oidc-context` só faz OIDC, pluga em qualquer Cognito User Pool com endpoint OIDC padrão, cuida de silent renew.

**3. `@microsoft/fetch-event-source` para o chat SSE.**
`EventSource` nativo não permite header `Authorization` customizado (só cookies/query string). Passar o JWT via query string vazaria token em logs de proxy/access log — inaceitável. A lib faz fetch com header normal e expõe parsing de evento SSE incremental.

**4. Client HTTP fino em `src/api/client.ts`, sem axios.**
`fetch` nativo cobre tudo que precisamos (JSON body, multipart não usado no MVP). Uma função wrapper injeta `Authorization: Bearer <token>` e normaliza erro `{error, message}` lançando exceção tipada. Evita dependência extra pra reimplementar o que fetch já faz.

**5. Sem state manager global (Redux/Zustand).**
3 telas, estado local por página (`useState`/`useEffect` + o hook de auth do oidc-context). Introduzir uma lib de estado global pra isso é over-engineering — YAGNI até aparecer necessidade real de estado compartilhado entre telas distantes.

**6. Token guardado em memória via `react-oidc-context` (não localStorage manual).**
A lib já gerencia isso com sessionStorage por padrão (mais seguro que localStorage pra XSS) e cuida de refresh silencioso. Não reinventar.

## Risks / Trade-offs

- **CORS no backend não configurado ainda** → sem isso dev local não funciona (browser bloqueia). Mitigação: adicionar `CorsConfigurationSource` no `SecurityConfig` liberando `localhost:5173` em profile dev, como task deste change (mudança mínima, não é nova capability de backend).
- **SSE sem reconexão automática em caso de queda de rede** → mensagem incompleta fica truncada na tela. Mitigação MVP: mostrar erro simples e permitir reenviar; reconexão automática fica para depois.
- **Sem paginação na tela de notebooks** → se usuário tiver centenas de notebooks, lista fica pesada. Aceitável pro MVP (poucos notebooks por usuário na prática).
- **Cognito Hosted UI redirect** exige domain/client-id configurados por ambiente (dev/prod) → variáveis de ambiente via `.env` do Vite (`VITE_COGNITO_*`), não commitadas.

## Migration Plan

Não há sistema anterior — é criação nova, sem dado a migrar. Deploy: build estático (`vite build`) publicado como S3 + CloudFront (fora de escopo deste change, mencionar em Open Questions).

## Open Questions

- Hospedagem do build final (S3+CloudFront? Amplify Hosting?) — não bloqueia MVP local, decidir depois.
- Callback URL de produção do Cognito ainda não existe (depende de domínio) — usar apenas `localhost:5173/callback` por ora.
