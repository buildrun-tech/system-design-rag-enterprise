## Why

Entre o início da pergunta e o primeiro token, o usuário vê só "Pensando…": a busca RAG não emite evento, o label da tool vem da `description` do `@Tool` (texto para o modelo, ex. "Retorna a data e hora atuais (fuso America/Sao_Paulo) em formato ISO-8601") e o result é cru (`2026-11-09T20:45:00-03:00`). Falta feedback em linguagem de usuário final sobre o que o sistema está fazendo.

## What Changes

- Evento SSE `toolCall` ganha o campo opcional `summary`: frase pronta em português para o usuário. Sem novo tipo de evento; `input`/`result` continuam existindo (uso DEV).
- Novo passo `retrieval` emitido no `toolCall`: `start` "Buscando nas suas fontes", `end` com `summary` "N trechos encontrados em M fontes" ou "Nenhum trecho relevante encontrado".
- Tabela `nome da tool → (label, summary ok, summary erro)` no backend; fallback para o label atual. Ex.: `currentDateTime` → "Consultando data e hora" / "Data e hora consultadas" / "Não foi possível consultar data e hora".
- Frontend: `ToolTimeline` mostra label, `summary` e duração, sem JSON; input/output crus e "detalhes" só com modo DEV ligado.
- Frontend: "Escrevendo resposta…" quando todos os passos terminaram e o conteúdo ainda está vazio.
- `API.md` atualizado com o campo `summary` e o passo `retrieval`.

## Capabilities

### New Capabilities
- `tool-progress-summary`: contrato SSE do `toolCall` com `summary` amigável, passo `retrieval` e textos por tool no backend.
- `tool-progress-display`: exibição no workspace das mensagens de progresso ao usuário final, com detalhes técnicos restritos ao modo DEV.

### Modified Capabilities

## Impact

- Backend: `ToolEvent`, `ToolEventPayload`, `EventingToolCallback`, `OpenAiChatCompletionAdapter`; testes `EventingToolCallbackTest`, `SseMessageStreamListenerTest`, testes do adapter.
- Frontend: `api/types.ts`, `components/ToolTimeline.tsx`, `pages/WorkspacePage.tsx`, `components/ui/ui.css`.
- Docs: `API.md`.
- Sem dependência nova; mudança compatível (campo opcional).
