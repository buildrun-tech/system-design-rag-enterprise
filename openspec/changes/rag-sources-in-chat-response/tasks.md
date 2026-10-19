## 1. Spike e banco

- [x] 1.1 Confirmar no Spring AI 2.0.0 onde `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` chega no stream (`chatResponse()` vs `chatClientResponse()`); registrar o resultado no design
- [x] 1.2 Criar `V4__add_message_sources.sql` (`sources JSONB NOT NULL DEFAULT '[]'` em `tb_conversation_messages`)
- [x] 1.3 Criar records `CitedSource(UUID sourceId, String name, List<CitedChunk> chunks)` e `CitedChunk(int chunkIndex, String snippet)` e mapear `sources` em `ConversationMessage` (`@JdbcTypeCode(SqlTypes.JSON)`, default lista vazia)

## 2. Backend

- [x] 2.1 `ConversationMessageService`: trocar `.content()` pela API escolhida em 1.1; extrair texto null-safe e guardar docs recuperados no `MessageStream`
- [x] 2.2 Agrupar docs por `source_id` (ordem da primeira ocorrência), montar `chunks` com `chunk_index` e snippet truncado (~300 chars, limite de palavra), resolver nomes via `sourceRepository.findAllById`, omitir ausentes e docs sem `source_id`
- [x] 2.3 `complete()`: persistir assistente com `sources` e enviar `{"done":true,"messageId":...,"sources":[...]}`
- [x] 2.4 `ConversationMessageResponse`: adicionar `sources` (`[]` para user/sem contexto)
- [x] 2.5 Testes: `ConversationMessageServiceTest` (agrupamento, truncamento do snippet, vazio, source ausente), `ConversationMessageControllerTest` (SSE done + GET), teste de integração com migration

## 3. Frontend

- [x] 3.1 Tipos: `CitedChunk`, `CitedSource` e `sources?` em `ConversationMessage`; tratar `sources` no evento `done` do `WorkspacePage.tsx`
- [x] 3.2 Renderizar chips "Fontes" sob a resposta do assistente; hover/foco mostra os snippets da source (texto puro, sem HTML); histórico via GET exibe o mesmo
- [ ] 3.3 Teste/verificação manual: resposta nova, recarregar página, resposta sem contexto, hover com teclado, source deletada

## 4. Documentação

- [x] 4.1 `API.md`: exemplo SSE `done` com `sources`, resposta de `GET /messages` e nota de compatibilidade
- [x] 4.2 `DOMAIN.md`: coluna `sources` no ERD e DDL de `conversation_messages`
- [x] 4.3 `openspec validate rag-sources-in-chat-response` sem erros
