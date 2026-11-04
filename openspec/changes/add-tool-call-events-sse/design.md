## Context

`ChatClientConfig` registra `DateTimeTools` via `defaultTools(...)`. O loop de tools roda dentro do model do Spring AI: os chunks de tool call são consumidos internamente e o subscriber de `.stream().chatResponse()` em `ConversationMessageService.sendMessage` só vê o texto final. A tool executa em thread `boundedElastic`, entre chunks de texto, no mesmo fluxo reativo.

`MessageStream` (classe interna) é dono do `SseEmitter`: serializa os envios, tem o flag `closed` e trata `IOException`. O frontend (`WorkspacePage.tsx`) decide o tipo de evento pela chave presente (`token` / `done` / `error`) e ignora chaves desconhecidas.

## Goals / Non-Goals

**Goals:**
- Frontend recebe `start` e `end`/`error` de cada tool call, com `id`, `name` e `durationMs`.
- Zero impacto no RAG, na persistência e no contrato dos eventos existentes.
- Funciona para toda tool registrada, inclusive futuras, sem tocar na tool.

**Non-Goals:**
- Status `progress` (nenhuma tool atual é longa; o canal permite adicionar depois).
- Enviar args ou resultado da tool ao browser (risco de vazar dado).
- Persistir tool calls no histórico (`conversation_messages` não muda).
- Controlar o loop de tools (limite de iterações, veto): exigiria `ToolCallAdvisor`.

## Decisions

**1. Decorator de `ToolCallback` (opção A), não `ToolCallAdvisor`.**
Wrap de cada callback mede start/end por tool, sem mexer no loop. `ToolCallAdvisor` reexecutaria os advisors seguintes (RAG, rewrite) a cada iteração e só dá granularidade de lote. Alternativas descartadas: `ToolCallingManager` custom (timing por lote), Observations (sem vínculo com a request).

**2. Listener da request via `ToolContext`.**
`sendMessage` passa `.toolContext(Map.of("toolEvents", listener))`. O decorator lê o listener em `call(json, ctx)`. Sem ThreadLocal, sem estado global; se a chave não existe (ex.: quiz, outros usos do client) o decorator só delega.

**3. Registro em `ChatClientConfig`.**
`defaultTools(new DateTimeTools())` vira `defaultToolCallbacks(wrap(ToolCallbacks.from(new DateTimeTools())))`. Um único ponto cobre todas as tools; os builders clonados dos transformers continuam sem tools.

**4. Emissão passa por `MessageStream.onToolEvent`.**
Reusa o guard `closed` do `onToken`, mas engole `IOException`/`IllegalStateException` em vez de cancelar: cancelar descartaria a subscription e poderia interromper a thread da tool. O próximo token/`done` falha pelo mesmo transporte e encerra o stream. `SseEmitter.send` é sincronizado; a tool roda sequencialmente no fluxo, então a ordem tool-event/token é preservada.

**5. Contrato SSE por chave, sem `event:` nomeado.**
`data: {"toolCall": {"id","name","status","durationMs"?}}`, mesmo estilo de `token`/`done`. `id` é um UUID gerado pelo decorator por execução (o `ToolCallback.call` não recebe o id do LLM). Falha da tool emite `status: "error"` e relança a exceção (o Spring AI continua tratando o erro normalmente).

**6. Frontend: timeline efêmera em estado.**
Cada mensagem do assistente em construção ganha `toolCalls[]`, atualizada por `id` (upsert). O offset do `start` (`at`, ms desde o início do stream) é medido no cliente, mantido nos updates `end`/`error`, e não vem do servidor. Renderizado por `ToolTimeline` (lista vertical com linha e marcadores) acima do texto, já que as tools rodam antes da resposta final. Não é persistido: ao recarregar o histórico não existe.

**7. Dev mode no frontend, sem backend.**
`useDevMode` (hook, `localStorage` em try/catch, mesmo estilo de `useTheme`) liga um botão DEV na barra do chat. `handleSend` sempre coleta o log do stream (`open`, `event` com `event.data` bruto, `close`, `error`, tempo relativo; últimos 300 itens, substituído a cada envio), então ativar o dev mode depois ainda mostra tudo. `SseDebugPanel` só renderiza com o dev mode ativo, com Copiar e Limpar; em dev mode a timeline mostra também `name` e `id`. Alternativas descartadas: flag por query string (some ao navegar) e endpoint de debug no backend (desnecessário, o log vem do próprio cliente).

## Risks / Trade-offs

- [`ToolContext` pode não chegar ao `ToolCallback` no caminho `.stream()`] → spike na task 1.1 antes de qualquer outra coisa; se falhar, cair para `ToolCallingManager` custom.
- [Valor `Consumer` dentro do `toolContext` pode ser serializado/logado por observations] → o listener não implementa `toString` com dados sensíveis; confirmar no spike que nada tenta serializar o mapa.
- [Tool lançando exceção] → decorator emite `error` e relança; teste cobre.
- [Evento chega depois de `closed`] → `onToolEvent` descarta silenciosamente, igual `onToken`.
- [Sem persistência, histórico recarregado não mostra tool calls] → aceito; reavaliar com coluna `tool_calls jsonb` se virar requisito.
- [Log de dev mode guarda `data` bruto, incluindo o texto da resposta] → fica só em memória do browser, limitado a 300 itens e substituído a cada envio.

## Open Questions

- Rótulo amigável por tool: o frontend mapeia `name` → texto (hoje só `currentDateTime`) e cai no próprio `name`. Se o número de tools crescer, avaliar `label` vindo do backend.
