## 1. Tokens

- [x] 1.1 Atualizar `index.css`: `--accent` pra `#0d0d0d`, recalcular `--accent-bg`/`--accent-border` (rgba derivado do preto), `--radius-sm` pra `8px`
- [x] 1.2 Atualizar bloco `@media (prefers-color-scheme: dark)` em `index.css`: `--accent` pra branco/cinza-claro (sem roxo `#c084fc`), recalcular `--accent-bg`/`--accent-border` correspondentes

## 2. Componentes ui/

- [x] 2.1 Adicionar hover/active state em `.ui-button--primary` e `.ui-button--secondary` em `ui.css` (mudança de `background`/`border-color`, não só `opacity`)
- [x] 2.2 Adicionar `box-shadow` sutil em `.ui-button--primary`
- [x] 2.3 Adicionar classes utilitárias mínimas em `ui.css` pra layout (ex: `.stack`, `.full-width`) cobrindo os casos de `LoginPage`

## 3. Limpeza de dead code

- [x] 3.1 Deletar `app/frontend/src/App.css`
- [x] 3.2 Confirmar via build (`vite build`/`tsc`) que nada quebra com a remoção

## 4. LoginPage

- [x] 4.1 Remover os `style={{}}` inline de `LoginPage.tsx`, migrando pras classes utilitárias novas e props existentes dos componentes `ui/`

## 5. Docs

- [x] 5.1 Atualizar `DESIGN.md` (raiz) com a paleta nova (accent preto, radius 8px) e nota de que foi ajuste manual, não remedição do site

## 6. Verificação

- [x] 6.1 Rodar app localmente e checar visualmente LoginPage em light e dark mode (screenshots enviados). NotebooksPage/WorkspacePage exigem backend rodando (fora de escopo) — herdam a paleta via CSS custom properties, sem edição direta, então não checadas visualmente.
- [x] 6.2 Rodar testes e2e existentes (`e2e/`) — 2/3 passaram; a falha é `ERR_CONNECTION_REFUSED` (backend não rodando), sem relação com CSS/classes.
