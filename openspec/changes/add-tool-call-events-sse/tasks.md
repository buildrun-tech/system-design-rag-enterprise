## 1. Spike

- [x] 1.1 Teste descartável (ou teste de integração) confirmando que, em `.stream()` com `toolContext(Map)`, o `ToolCallback.call(String, ToolContext)` recebe o mapa com o listener; se não receber, parar e rever o design (fallback: `ToolCallingManager` custom)

## 2. Backend

- [x] 2.1 Criar `ToolEvent` (record: `id`, `name`, `status`, `durationMs`) e o decorator de `ToolCallback` em `tool/` que lê o listener do `ToolContext`, emite start/end/error com duração e relança a exceção
- [x] 2.2 `ChatClientConfig`: trocar `defaultTools(new DateTimeTools())` por `defaultToolCallbacks(...)` com as tools embrulhadas
- [x] 2.3 `ConversationMessageService.MessageStream`: adicionar `onToolEvent` (guard `closed`, mesmo tratamento de erro de transporte do `onToken`) enviando `Map.of("toolCall", ...)`
- [x] 2.4 `sendMessage`: passar `.toolContext(Map.of(<chave>, stream::onToolEvent))` na chamada do `chatClient`
- [x] 2.5 Testes unitários do decorator: sucesso (start+end com mesmo id e duração), exceção (start+error e relança), sem listener (só delega)
- [x] 2.6 Teste em `ConversationMessageServiceTest`: evento de tool é enviado ao emitter e descartado após `closed`

## 3. Frontend

- [x] 3.1 `api/types.ts`: tipos `ToolCall` (`id`, `name`, `status`, `durationMs?`) e campo `toolCalls?` na mensagem de chat
- [x] 3.2 `WorkspacePage.tsx`: tratar `data.toolCall` no `onmessage` (inserir no `start`, atualizar por `id` em `end`/`error`)
- [x] 3.3 (substituída pela 3.4) chips de tool call

- [x] 3.4 `ToolTimeline`: timeline vertical acima do texto da mensagem (marcador por status, linha ligando os itens, rótulo `name` → texto com fallback, offset `+N.NNs` e `durationMs`); `at` medido no cliente e preservado no upsert; remover os chips e o CSS antigo
- [x] 3.5 `useDevMode` (hook com `localStorage` em try/catch) e botão DEV na barra do chat
- [x] 3.6 `SseDebugPanel`: log do stream (`open` com status/content-type, `event` com `data` bruto, `close`, `error`, tempo relativo, máx. 300, destaque dos `toolCall`), Copiar e Limpar; `handleSend` sempre coleta e substitui o log a cada envio
- [x] 3.7 Em dev mode, `ToolTimeline` mostra também `name` técnico e início do `id`

## 4. Docs e verificação

- [x] 4.1 `API.md`: documentar o evento `toolCall` na seção do endpoint de mensagens
- [x] 4.2 `rtk mvn test` no `backend-api` e `rtk npm run build` no frontend
- [ ] 4.3 Teste manual: perguntar a data/hora e ver `toolCall` start/end antes dos tokens, a timeline na mensagem e o log completo no dev mode (botão DEV)
