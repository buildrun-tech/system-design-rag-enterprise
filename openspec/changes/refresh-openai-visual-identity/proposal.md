## Why

`DESIGN.md` mede a identidade da OpenAI com um accent roxo-acinzentado apagado (`#8e8ea0`) que não bate mais com o produto atual (ChatGPT/OpenAI hoje é essencialmente monocromático: preto/branco, sem cor de acento saturada). O dark mode atual piora isso trocando pra um roxo vibrante (`#c084fc`) que não existe na identidade real. Botões têm radius pequeno (5px), sem hover state visível (só opacity) e sem profundidade. Além disso há debito: `App.css` é scaffold do Vite morto (zero uso) e `LoginPage.tsx` tem ~10 `style={{}}` inline fora do sistema de tokens.

## What Changes

- Paleta: accent primário passa de `#8e8ea0` pra preto quase puro (`#0d0d0d`), cinza neutro mantido só pra borda/texto secundário.
- Dark mode: inverte pra monocromático (preto↔branco), remove o roxo vibrante `#c084fc` — sem introduzir cor de acento nova.
- Radius de componentes (`--radius-sm`) sobe de 5px pra 8px.
- Botão primary ganha hover/active state visível (não só `opacity`) e leve `box-shadow`.
- **BREAKING** (visual, não de contrato): tokens de cor em `index.css`/`ui.css` mudam de valor — qualquer CSS que hardcode `#8e8ea0` fora dos tokens fica dessincronizado.
- Remove `app/frontend/src/App.css` (scaffold Vite morto, não importado por `App.tsx`).
- Remove os `style={{}}` inline de `LoginPage.tsx`, migrando pro sistema `components/ui/` (Button, Input, Card) e classes utilitárias.
- Atualiza `DESIGN.md` na raiz pra refletir a paleta nova (era doc "medido" desatualizado).

## Capabilities

### New Capabilities
(nenhuma — refactor visual sobre capability existente)

### Modified Capabilities
- `frontend-design-system`: tokens de cor (accent, dark mode), radius, e comportamento visual (hover/active) do Button mudam. Spec original em `openspec/changes/frontend-redesign-direct-signup/specs/frontend-design-system/spec.md` (ainda não arquivada em `openspec/specs/` — trato como base existente).

## Impact

- `app/frontend/src/index.css` — tokens de cor/radius
- `app/frontend/src/components/ui/ui.css` — estilo de Button/Input/Card
- `app/frontend/src/App.css` — removido
- `app/frontend/src/App.tsx` — sem mudança de lógica (App.css não é importado aqui, confirma remoção segura)
- `app/frontend/src/pages/LoginPage.tsx` — remove inline styles
- `DESIGN.md` (raiz) — tokens atualizados
- Sem mudança de rota, auth, ou contrato de API. `NotebooksPage`/`WorkspacePage` herdam a paleta nova automaticamente via CSS custom properties, sem edição direta.
