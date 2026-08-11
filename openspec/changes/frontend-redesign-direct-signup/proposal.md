## Why

Frontend hoje usa estilo inline cru sem seguir DESIGN.md (tokens órfãos de scaffold Vite ainda em `index.css`), e o único fluxo de login (Hosted UI + Google/GitHub) não funciona contra o emulador Cognito local (floci), forçando desenvolvedores a gerar token via script CLI (`login.sh`) em vez de logar pela UI.

## What Changes

- Cria sistema de componentes base (Button, Input, Card) com tokens visuais extraídos de DESIGN.md (paleta `#8e8ea0`, radius 5px, grid 8px, system-ui)
- Substitui tokens órfãos de `index.css` pelos tokens do DESIGN.md
- Aplica os componentes base nas 3 telas existentes (Login, Notebooks, Workspace)
- Adiciona formulário de cadastro/login direto por email+senha na tela de login, usando Cognito Identity SDK (`InitiateAuth`/`SignUp`), habilitado condicionalmente (flag de ambiente) sem substituir os botões de login social existentes
- Habilita `USER_PASSWORD_AUTH` no client Cognito local (`start_local.sh`) e remove exigência de confirmação de email para o user pool local, para que cadastro direto funcione sem passo manual
- **BREAKING**: nenhuma — social login e client existente continuam funcionando

## Capabilities

### New Capabilities
- `frontend-design-system`: componentes visuais base (Button, Input, Card) e tokens de design derivados de DESIGN.md, reutilizados pelas telas existentes

### Modified Capabilities
- `frontend-auth`: adiciona requisito de cadastro/login direto por email+senha via Cognito Identity SDK, como alternativa ao Hosted UI, habilitada por flag de ambiente
- `local-dev-environment`: client Cognito local passa a habilitar `USER_PASSWORD_AUTH` e user pool local não exige confirmação de email no cadastro

## Impact

- `app/frontend/src/index.css`: tokens substituídos
- `app/frontend/src/components/`: novos componentes (Button, Input, Card)
- `app/frontend/src/pages/LoginPage.tsx`, `NotebooksPage.tsx`, `WorkspacePage.tsx`: restyle com componentes base
- `app/frontend/src/auth/`: novo módulo de auth direto (Cognito Identity SDK), config de flag de ambiente
- `app/frontend/package.json`: nova dependência `amazon-cognito-identity-js`
- `app/backend-api/local/start_local.sh`: client com `USER_PASSWORD_AUTH`, user pool sem verificação de email obrigatória
- `.env.example` (frontend): nova variável de flag de ambiente
