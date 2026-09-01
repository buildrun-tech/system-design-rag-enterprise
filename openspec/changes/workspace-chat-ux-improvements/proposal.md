## Why

Frontend do chat renderiza mensagens como texto puro (sem markdown, sem quebra de linha) e faltam ações básicas de navegação/gestão: voltar do workspace, apagar notebook, iniciar novo chat e feedback visual (spinner) enquanto a primeira resposta não chega. Backend já expõe todos os endpoints necessários (`DELETE /api/v1/notebooks/{id}`, `POST`/`GET /api/v1/notebooks/{id}/conversations`); falta só o frontend consumir.

## What Changes

- Renderizar `content` das mensagens do chat como markdown (negrito, listas, código, quebras de linha) usando `react-markdown` + `remark-gfm` (sem `rehype-raw`, sem HTML cru).
- Adicionar botão "voltar" no `Navbar` do workspace, navegando para `/notebooks`.
- Adicionar lixeira em cada card de notebook em `NotebooksPage`, com confirmação, chamando `DELETE /api/v1/notebooks/{id}` e removendo o card da lista.
- Adicionar botão "novo chat" no workspace que cria uma conversa via `POST /api/v1/notebooks/{id}/conversations` e a torna ativa.
- Adicionar seletor de conversas no workspace, listando conversas via `GET /api/v1/notebooks/{id}/conversations` e permitindo trocar a conversa ativa (recarrega histórico via `GET /api/v1/conversations/{id}/messages`).
- Exibir spinner na bolha da mensagem do assistente enquanto `streaming === true` e `content === ''` (antes do primeiro token chegar).

## Capabilities

### New Capabilities
(nenhuma — todo comportamento se encaixa nas capabilities de frontend já existentes)

### Modified Capabilities
- `frontend-workspace`: novo requirement de renderização markdown nas mensagens, botão voltar, seletor/criação de conversas, spinner de espera pela primeira resposta.
- `frontend-notebooks`: novo requirement de exclusão de notebook via lixeira com confirmação.

## Impact

- Código: `app/frontend/src/pages/WorkspacePage.tsx`, `app/frontend/src/pages/NotebooksPage.tsx`, `app/frontend/src/components/Navbar.tsx`, `app/frontend/src/components/icons.tsx` (novo `IconArrowLeft`).
- Dependências novas: `react-markdown`, `remark-gfm` (primeira lib de markdown do projeto).
- Backend: nenhuma mudança — endpoints já existem.
- Sem breaking changes.
