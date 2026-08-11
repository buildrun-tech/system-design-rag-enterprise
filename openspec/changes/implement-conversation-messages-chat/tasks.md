## 1. Config do ChatClient

- [x] 1.1 Criar `config/ChatClientConfig` (`@Configuration`) com bean `ChatClient` a partir do `ChatClient.Builder` autoconfigurado
- [ ] 1.2 Confirmar em ambiente local/`.env` que `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` apontam pro OpenRouter (pendente: requer secret real do usuário, placeholders vazios já existem em `.env`/`.env.example`)

## 2. DTO e repository

- [x] 2.1 Criar `dto/ConversationMessageCreateRequest(String content)` com `@NotBlank`
- [x] 2.2 Adicionar método em `ConversationMessageRepository` pra buscar últimas 10 mensagens de uma conversa (`findTop10By...OrderByCreatedAtDesc`)

## 3. Service — envio de mensagem

- [x] 3.1 Implementar `ConversationMessageService.sendMessage(conversationId, ownerId, content, SseEmitter)`: valida ownership da conversa (`CONVERSATION_NOT_FOUND` se não achar)
- [x] 3.2 Buscar últimas 10 mensagens existentes, reverter pra ordem ASC, mapear `MessageRole` → `UserMessage`/`AssistantMessage` do Spring AI
- [x] 3.3 Montar lista final de `Message`: `SystemMessage` fixo + histórico + `UserMessage(content)` novo
- [x] 3.4 Persistir mensagem do usuário (`role: user`) antes de disparar a chamada ao LLM
- [x] 3.5 Chamar `chatClient.prompt().messages(...).stream().content()`, subscrever em `Schedulers.boundedElastic()`
- [x] 3.6 `onNext`: enviar `data: {"token": chunk}` via `SseEmitter` + acumular resposta completa em `StringBuilder`
- [x] 3.7 `onComplete`: persistir `ConversationMessage(role: assistant, content: acumulado)`, enviar `data: {"done": true, "messageId": ...}`, `emitter.complete()`
- [x] 3.8 `onError`: enviar `data: {"error": "STREAM_ERROR"}`, `emitter.completeWithError(e)`, **não** persistir mensagem parcial do assistente
- [x] 3.9 Configurar timeout explícito do `SseEmitter` (ex: 60s) e callbacks `onTimeout`/`onCompletion` pra garantir cleanup

## 4. Controller

- [x] 4.1 Adicionar `POST` em `ConversationMessageController`, `produces = MediaType.TEXT_EVENT_STREAM_VALUE`, retorna `SseEmitter`
- [x] 4.2 Validar `@Valid @RequestBody ConversationMessageCreateRequest` (cai no `VALIDATION_ERROR` já existente no `GlobalExceptionHandler`)

## 5. Testes

- [x] 5.1 Teste unitário de `ConversationMessageService.sendMessage` com `ChatClient`/`ChatModel` mockado (`Flux.just(...)`), cobrindo: histórico incluído no prompt, persistência da mensagem do usuário antes da chamada, persistência da resposta do assistente após completar
- [x] 5.2 Teste de erro de stream: `ChatClient` mockado emitindo erro no `Flux`, verificar `STREAM_ERROR` enviado e nenhuma mensagem de assistente persistida
- [x] 5.3 Teste de integração cobrindo IDOR: usuário não consegue enviar mensagem em conversa de outro usuário (dois hops, mesmo padrão do `ConversationMessageApiIntegrationTest` existente)
- [x] 5.4 Teste de integração: `content` vazio/ausente retorna `400 VALIDATION_ERROR` sem chamar o LLM

## 6. Quality gate

- [x] 6.1 Rodar skill `java-quality-gate` (cobertura jacoco 80%, mutation testing pitest 80%) e resolver ou justificar qualquer STUCK mutant relacionado ao streaming assíncrono — coverage 94.89%, mutation 82.01% (PASS)
