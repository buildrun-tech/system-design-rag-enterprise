## Context

Sistema de design vive todo em CSS custom properties centralizadas (`index.css` define os tokens, `ui.css` consome via `var(--x)`). Componentes `ui/` (Button, Input, Card) e páginas (`LoginPage`, `NotebooksPage`, `WorkspacePage`) já leem esses tokens — nenhuma cor hardcoded nas páginas exceto os inline styles do `LoginPage`. Isso significa: trocar valor de token propaga pra tudo sem tocar em cada componente. `App.css` é resíduo do template Vite (`.hero`, `.framework`, `.vite`, `#next-steps`), não importado por `App.tsx` — dead code confirmado.

## Goals / Non-Goals

**Goals:**
- Paleta accent monocromática (preto/branco), radius 8px, botão primary com hover/active + sombra sutil
- Dark mode coerente com a mesma lógica de cor (sem introduzir roxo)
- Zero inline style em `LoginPage.tsx`, zero dead CSS
- `DESIGN.md` sincronizado com os tokens reais do código

**Non-Goals:**
- Não migra pra Tailwind/CSS-in-JS/design tokens em JSON — mantém CSS custom properties simples, já funciona
- Não adiciona nova variante de Button (danger, ghost, etc.) — YAGNI, só ajusta as 2 existentes
- Não altera `NotebooksPage`/`WorkspacePage` diretamente — herdam via tokens
- Não mexe em fluxo de auth, rotas, ou testes e2e além do necessário pra CSS class matching

## Decisions

**Accent preto (`#0d0d0d`) em vez de manter `#8e8ea0` como base e só escurecer**
`#8e8ea0` tem contraste ~3.5:1 em branco — já documentado no próprio `DESIGN.md` como abaixo de WCAG AA, por isso o código já força `--text-h: #000` pro texto. Faz mais sentido parar de tentar salvar o tom roxo-acinzentado como accent e ir direto pro preto, que é o que produtos OpenAI atuais usam pra CTA (alto contraste, zero ambiguidade de acessibilidade, e "parece moderno" é literalmente isso: monocromia confiante, sem gradiente/cor de marca gritante).

**Dark mode invertido, não "roxo mais escuro"**
Trocar `--accent` no dark pra branco/cinza-claro sobre fundo escuro, mantendo a mesma relação preto-no-claro / branco-no-escuro que outros produtos OpenAI usam. Evita introduzir uma terceira cor (o roxo `#c084fc` de hoje não aparece em nenhum lugar do restante da paleta — inconsistente por design).

**Radius 8px: token único, não por componente**
Mantém `--radius-sm` como nome (evita renomear todo `var()` espalhado) só muda o valor de `5px` pra `8px`. Considerado criar `--radius-md`/`--radius-lg` pra variação por componente — descartado, YAGNI: só 3 componentes (Button/Input/Card), todos com o mesmo radius hoje, não há caso de uso pra diferenciar ainda.

**Hover/active via `background`/`box-shadow`, não nova classe de estado**
CSS `:hover`/`:active` direto em `.ui-button--primary`/`--secondary`, sem JS, sem prop `isHovered`. Sombra usa um único valor fixo (não token novo `--shadow-sm` seria overkill pra 1 uso) — reconsiderar só se sombra aparecer em mais de 1 lugar.

**`App.css` deletado, não esvaziado**
Zero referência (`grep` confirmou `App.tsx` não importa). Deletar > comentar/esvaziar — arquivo morto não serve de "documentação", só confunde.

**Inline styles do `LoginPage` → classes utilitárias mínimas + props existentes dos componentes `ui/`**
`Button`/`Card` já aceitam `style` prop (aceita override), mas o objetivo é não precisar dele: onde for layout puro (`flex`, `gap`), adiciona classes utilitárias pequenas no `ui.css` (ex: `.stack`, `.full-width`) reaproveitáveis por outras páginas também com inline style futuro. Evita inventar sistema de design tokens de layout (grid system completo) — só o suficiente pra sumir os 10 usos atuais.

## Risks / Trade-offs

[Troca de `--accent` de roxo pra preto muda contraste em elementos que hoje contam com `--accent-bg`/`--accent-border` (rgba derivado do roxo)] → Mitigação: recalcular esses derivados a partir do preto/branco novo, não só trocar o hex base e deixar rgba desalinhado.

[`DESIGN.md` é doc "medido" — mudar os valores sem re-medir o site real pode ficar arbitrário] → Mitigação: registrar no próprio `DESIGN.md` que a versão foi ajustada manualmente (não remedida), mantendo rationale de por que preto/branco reflete melhor o produto atual.

[Deletar `App.css` pode quebrar se algo importar dinamicamente] → Mitigação: `grep -rl "App.css"` já confirmou único import em `App.tsx`... na verdade zero import — só presença do arquivo. Build (`tsc`/`vite build`) confirma se algo quebrar.

## Migration Plan

Mudança puramente visual/CSS + remoção de dead code, sem dado ou schema envolvido. Deploy normal. Rollback = reverter o commit, sem efeito colateral em backend/dados.

## Open Questions

Nenhuma — decisões fechadas nesta rodada; ajustes finos de tom (contraste exato, valor de sombra) resolvidos durante implementação por inspeção visual.
