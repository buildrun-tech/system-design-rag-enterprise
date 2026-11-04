## Why

O chat já executa tools (`DateTimeTools`), mas o frontend não sabe: durante a execução o stream fica mudo e o usuário não vê que o LLM está consultando algo. Expor início e fim de cada tool call dá feedback imediato e prepara o terreno para tools mais lentas.

## What Changes

- Novo evento SSE `{"toolCall": {"id", "name", "status": "start" | "end" | "error", "durationMs"?}}` emitido no mesmo stream de `POST /conversations/{id}/messages`, entre os eventos `token`.
- Backend: decorator de `ToolCallback` que mede cada execução e notifica um listener recebido via `ToolContext` da request; `ChatClientConfig` passa a registrar as tools embrulhadas; `MessageStream` ganha `onToolEvent`.
- Frontend: `WorkspacePage` trata `toolCall` e mostra uma timeline vertical (offset, duração, status) na mensagem do assistente.
- Frontend: dev mode (botão DEV, lembrado em `localStorage`) com painel de log bruto do SSE (abertura, eventos, erro, fechamento, com tempo), copiar e limpar.
- `API.md` documenta o novo evento.
- Fora de escopo: status `progress` (reservado, sem emissor), args/resultado da tool no payload, persistência dos tool calls no histórico.
- Compatível: clientes que ignoram a chave `toolCall` continuam funcionando (mesmo padrão do campo `sources`).

## Capabilities

### New Capabilities
- `tool-call-events`: emissão de eventos de ciclo de vida (start/end/error) de tool calls do LLM, correlacionados por id, no stream SSE do chat.

### Modified Capabilities
- `frontend-workspace`: o streaming da resposta passa a renderizar a timeline das tool calls e o dev mode de inspeção do SSE (requirements novos, ADDED: timeline de tool calls e dev mode).

> `chat` não ganha delta: o contrato do evento vive em `tool-call-events`, evitando conflito com a change `rag-sources-in-chat-response`, que ainda modifica o requirement de SSE do `chat`.

## Impact

- Backend (`app/backend-api`): `config/ChatClientConfig`, `service/ConversationMessageService` (`MessageStream`, `sendMessage`), novo pacote/classe do decorator em `tool/`. Sem mudança de schema, sem migration, sem dependência nova (Spring AI 2.0.0 já tem `ToolCallback`, `ToolContext`).
- Frontend (`app/frontend`): `pages/WorkspacePage.tsx`, `api/types.ts`, `ui.css`; novos `components/ToolTimeline.tsx`, `components/SseDebugPanel.tsx`, `dev/useDevMode.ts`.
- Docs: `API.md`.
- Risco: o `ToolContext` precisa chegar ao `ToolCallback.call(String, ToolContext)` no caminho `.stream()`; validar em spike no início das tasks.
