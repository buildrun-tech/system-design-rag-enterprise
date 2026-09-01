## Context

`WorkspacePage.tsx` renderiza `message.content` como texto puro dentro de `<div>`; sempre reusa `conversations[0]` ou cria uma única conversa por notebook; sem navegação de volta; `NotebooksPage.tsx` sem exclusão. Backend já expõe todos endpoints necessários (notebook delete, conversation list/create). `package.json` do frontend não tem nenhuma lib de markdown hoje.

## Goals / Non-Goals

**Goals:**
- Markdown completo (negrito, listas, código, links, quebras de linha) nas mensagens do chat.
- Navegação de volta, exclusão de notebook, criação/troca de conversa, spinner de espera — tudo via endpoints já existentes.

**Non-Goals:**
- Excluir/renomear conversas (endpoint não existe no backend, fora de escopo).
- Editar/regerar mensagens.
- Qualquer mudança de backend.

## Decisions

- **Markdown**: `react-markdown` + `remark-gfm`, sem `rehype-raw` — evita XSS por injeção de HTML cru nas mensagens (conteúdo pode vir de fontes externas via RAG). Alternativa descartada: parser custom (reinventa roda, não cobre GFM).
- **Seletor de conversas**: dropdown simples no header do `chat-panel` listando `GET .../conversations` (ordenado como a API retorna), sem paginação — volume esperado é baixo (poucas conversas por notebook). Trocar seleção recarrega `GET /conversations/{id}/messages` e cancela stream em andamento (aborta `abortRef`).
- **Novo chat**: reusa `POST .../conversations` (já cria sempre nova) — vira a conversa ativa e some no topo do seletor.
- **Lixeira do notebook**: confirmação via `window.confirm` (Modal existente é overkill para uma confirmação simples de sim/não — ponytail: nativo antes de componente).
- **Spinner**: renderiza `IconSpinner` na bolha do assistente quando `streaming && content === ''`; assim que primeiro token chega, `content` deixa de ser vazio e o spinner some naturalmente (sem estado extra).
- **Botão voltar**: `IconArrowLeft` novo em `icons.tsx` (segue padrão dos demais ícones do arquivo), passado como `children` do `Navbar` — já suporta children.

## Risks / Trade-offs

- [Markdown malicioso vindo de source ingerida ou resposta do LLM] → mitigado por não habilitar `rehype-raw` (react-markdown escapa HTML por padrão).
- [Trocar de conversa durante streaming ativo perde a resposta em construção] → aborta o `EventSource` da conversa anterior antes de trocar.
- [Seletor de conversas sem paginação pode ficar longo com uso pesado] → aceitável no volume atual; escalar depois se necessário.

## Migration Plan

Só frontend, sem dado persistido migrando. Deploy padrão do build do Vite; rollback é reverter o deploy do frontend.
