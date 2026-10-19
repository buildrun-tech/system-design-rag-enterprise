## Why

O RAG recupera chunks de várias sources, mas o usuário não vê quais documentos fundamentaram a resposta. Os docs recuperados se perdem porque o stream usa `.content()` e só devolve texto. Sem citação de fonte, a resposta não é verificável, e isso é o ponto central de um NotebookLM empresarial.

## What Changes

- Backend captura os documentos recuperados pelo `RetrievalAugmentationAdvisor` durante o stream (`.chatResponse()` no lugar de `.content()`) e agrupa os chunks por `source_id`.
- Evento SSE final passa a incluir as sources usadas: `{"done": true, "messageId": "...", "sources": [{"sourceId": "...", "name": "...", "chunks": [{"chunkIndex": 0, "snippet": "..."}]}]}`. Cada `snippet` é o trecho do chunk recuperado, truncado em ~300 caracteres.
- `name` vem do `SourceRepository` (lookup por ids), não do metadata do chunk.
- Sources usadas são persistidas na mensagem do assistente (nova coluna `sources` JSONB em `conversation_messages`, snapshot `[{sourceId, name, chunks:[{chunkIndex, snippet}]}]`).
- `GET /conversations/{id}/messages` passa a retornar `sources` em cada mensagem (`[]` para mensagens `user` e para respostas sem contexto).
- Frontend (`WorkspacePage.tsx`) trata `sources` no evento `done`, lê `sources` do histórico e exibe as fontes sob a resposta do assistente, com preview do trecho no hover.
- Documentação atualizada: `API.md`, `DOMAIN.md` (ERD + DDL), specs `chat` e `rag-retrieval`.
- Granularidade: uma entrada por source, com os chunks recuperados aninhados e snippet truncado para o hover. Score de relevância, highlight no documento e abrir o arquivo/URL completo ficam fora de escopo.
- Mudança aditiva, sem **BREAKING**: campo novo opcional no `done` e na resposta de mensagens.

## Capabilities

### New Capabilities
<!-- nenhuma: comportamento entra nas capabilities existentes -->

### Modified Capabilities
- `chat`: evento SSE `done` passa a carregar `sources`; mensagens do assistente persistem e expõem `sources`; cenário de `done` do spec atual (`{"done": true}`) é atualizado.
- `rag-retrieval`: retrieval passa a expor os documentos recuperados (source distinta) para o chamador, além de montar o prompt.

## Impact

- **Backend** (`app/backend-api`): `ConversationMessageService` (stream com `chatResponse()`, captura de `DOCUMENT_CONTEXT`, lookup de nomes), `ConversationMessage` (campo `sources` JSONB), `ConversationMessageResponse` (+ `sources`), nova migration `V4__add_message_sources.sql`, novo DTO de source citada.
- **Frontend** (`app/frontend`): `WorkspacePage.tsx` (tipos, merge no `done`, render das fontes).
- **Docs**: `API.md`, `DOMAIN.md`, `openspec/specs/chat`, `openspec/specs/rag-retrieval`.
- **Testes**: `ConversationMessageServiceTest`, `ConversationMessageControllerTest`, integração de mensagens.
- **Dependências**: nenhuma nova. A chave `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` depende da versão atual do Spring AI e deve ser validada no design.
- **Banco**: migration aditiva, coluna `sources JSONB NOT NULL DEFAULT '[]'`, sem backfill necessário.
