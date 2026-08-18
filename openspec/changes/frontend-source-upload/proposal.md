## Why

`WorkspacePage` só lista sources — não há como o usuário adicionar arquivos. O backend de ingestão (`implement-source-ingestion-pipeline`) expõe `POST/GET/DELETE /api/v1/notebooks/{notebookId}/sources` já documentado em `API.md`; falta o frontend consumir.

## What Changes

- Botão de upload no painel "sources" do workspace, abrindo file picker nativo e disparando `POST /api/v1/notebooks/{notebookId}/sources` (multipart) na seleção do arquivo
- Source recém-criada (`PENDING`) entra na lista local imediatamente (otimista)
- Polling de `GET /api/v1/notebooks/{notebookId}/sources` a cada 3s enquanto existir source `PENDING`/`PROCESSING`, parando quando todas resolverem
- Botão de deletar por source, chamando `DELETE /api/v1/notebooks/{notebookId}/sources/{sourceId}`
- Mensagens de erro inline pra falha de upload; badge de erro já existente exibe `errorMessage` de sources `FAILED`

## Capabilities

### New Capabilities
(nenhuma)

### Modified Capabilities
- `frontend-workspace`: adiciona requirement de upload/delete de source no painel de sources (spec atual só cobre listagem read-only)

## Impact

- `app/frontend/src/pages/WorkspacePage.tsx` — upload, polling, delete
- `app/frontend/src/components/icons.tsx` — ícones de upload/trash/spinner
- `app/frontend/src/components/ui/ui.css` — estado de loading/spinner se necessário
- Sem mudança de backend (endpoints já existem/estão sendo implementados em change separado)
