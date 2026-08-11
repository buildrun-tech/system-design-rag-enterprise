## Why

Dia 5/6 já entregaram entities, repositories e `GET /conversations/{id}/messages`, mas não existe rota pra enviar mensagem — o chat não funciona ainda. RAG (source-ingestion + pgvector similarity search) ainda não está pronto neste momento do projeto, então a spec `chat` completa (que exige contexto RAG em toda mensagem) não é implementável hoje sem bloquear o chat inteiro. Este change entrega um ChatClient simples (Spring AI + OpenRouter via API OpenAI-compatible), sem Tools e sem RAG, pra desbloquear a experiência de conversa; retrieval entra em change futuro.

## What Changes

- Implementa `POST /api/v1/conversations/{conversationId}/messages`: persiste mensagem do usuário, monta prompt com histórico (últimas 10 mensagens) + system message fixo, chama LLM via Spring AI `ChatClient` em modo streaming, retorna SSE token-a-token, persiste resposta do assistente ao final
- Adiciona `ChatClientConfig` (`@Configuration`) — bean `ChatClient` a partir do `ChatClient.Builder` autoconfigurado pelo `spring-ai-starter-model-openai` já presente no `pom.xml`
- Adiciona `ConversationMessageCreateRequest` DTO com validação `@NotBlank` em `content`
- Adiciona método de repository pra buscar as últimas N mensagens de uma conversa
- Trata erro de stream do LLM (`data: {"error": "STREAM_ERROR"}`) sem persistir mensagem parcial do assistente
- **BREAKING**: nenhuma — endpoint é novo, não existe contrato prévio pra quebrar
- **Escopo explicitamente fora**: sem Tools/function calling, sem RAG (nenhuma similarity search no pgvector, nenhum contexto de `source_chunks` no prompt), sem aviso de "nenhuma source disponível" (não se aplica — RAG não roda)

## Capabilities

### New Capabilities
(nenhuma — capability já existe em `openspec/specs/chat/spec.md`)

### Modified Capabilities
- `chat`: o requirement "Enviar mensagem e receber resposta via SSE" é reduzido temporariamente — a resposta do LLM não usa similarity search nem contexto de sources ativas; usa apenas o histórico de mensagens da própria conversa. O cenário "Mensagem com RAG bem-sucedido" e "Mensagem sem sources ativas ou READY" da spec atual não se aplicam ainda; serão restaurados quando RAG for implementado em change futuro.

## Impact

- Código novo: `config/ChatClientConfig`, `dto/ConversationMessageCreateRequest`, método `create()` em `ConversationMessageController`, método `sendMessage()` em `ConversationMessageService`
- `ConversationMessageRepository` ganha método de busca das últimas N mensagens
- `GlobalExceptionHandler`: nenhuma mudança (erros de validação e `CONVERSATION_NOT_FOUND` já cobertos)
- Runtime: precisa `SPRING_AI_OPENAI_BASE_URL=https://openrouter.ai/api/v1`, `SPRING_AI_OPENAI_API_KEY` e `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` (ex: `openai/gpt-4o-mini`) configurados via env/`​.env` — sem mudança de código de config, `application.yml` já lê essas vars
- Sem mudança de schema — `tb_conversation_messages` já cobre o necessário
- `API.md`: nenhuma mudança de contrato (SSE já documentado igual ao que será implementado)
