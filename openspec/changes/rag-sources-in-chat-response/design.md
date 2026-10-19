## Context

Fluxo atual: `ConversationMessageService.sendMessage` chama `chatClient.prompt()...stream().content()` com o `RetrievalAugmentationAdvisor` (topK=5, filtro `source_id in [...]`). `.content()` devolve `Flux<String>`, então os `Document` recuperados são descartados. O SSE emite `{token}` e, no fim, `{done, messageId}`. `tb_conversation_messages` guarda `role` e `content`. O chunk no pgvector já carrega `metadata.source_id` (`SourceIngestionConsumer`). Spring AI 2.0.0.

Frontend (`WorkspacePage.tsx`) faz merge do evento `done` na mensagem em streaming e carrega histórico via `GET /conversations/{id}/messages`.

## Goals / Non-Goals

**Goals:**
- Informar ao frontend quais sources e quais trechos fundamentaram cada resposta do assistente, no stream e no histórico, permitindo preview no hover.
- Contrato aditivo, compatível com clientes que ignoram o campo.

**Non-Goals:**
- Score de relevância, highlight no documento, link/abertura do arquivo ou URL completa da source.
- Backfill de mensagens antigas (ficam com `sources = []`).

## Decisions

### D1. Capturar docs via `.chatResponse()` + `DOCUMENT_CONTEXT`
Trocar `.stream().content()` por `.stream().chatResponse()`. O texto vem de `response.getResult().getOutput().getText()` (null-safe); os docs vêm de `response.getMetadata()` / contexto da chamada na chave `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`. Guardar o último valor não vazio visto em `MessageStream`.

- Alternativa: segunda similarity search só para citar. Rejeitada: custo extra e pode divergir do que o LLM realmente recebeu.
- Alternativa: ler `source_id` do prompt. Rejeitada: acoplado ao texto.
- Spike (1.1, resolvido na 2.0.0): `BaseAdvisor.adviseStream` só chama `after()` no chunk com finish reason; ali o advisor copia os docs para `ChatResponse.getMetadata()` sob `DOCUMENT_CONTEXT` (`List<Document>`). Logo `.chatResponse()` basta, mas o texto do chunk final pode vir vazio: ler tokens de todo chunk e metadata de qualquer chunk que a tenha, sem assumir que o último traz texto.

### D2. Granularidade: source com chunks aninhados `[{sourceId, name, chunks:[{chunkIndex, snippet}]}]`
Agrupar os docs recuperados por `metadata.source_id`, preservando a ordem da primeira ocorrência (ordem de relevância); dentro da source, chunks na ordem de recuperação. `snippet` = texto do `Document` truncado em ~300 caracteres, cortando em limite de palavra (constante no serviço). `chunkIndex` vem de `metadata.chunk_index`. Nome resolvido por `sourceRepository.findAllById(ids)`. Source que sumiu entre a busca e o lookup é omitida.

- Alternativa: só referência + endpoint `GET /sources/{id}/chunks/{index}` no hover. Rejeitada: endpoint, auth e query extras; preview some se a source for deletada.
- Alternativa: gravar `source_name` no metadata do chunk. Rejeitada: exige reingestão e fica stale em rename.
- Snippet é o trecho que o LLM recebeu, não o documento inteiro.

### D3. Persistência: coluna `sources JSONB` em `tb_conversation_messages`
Migration `V4__add_message_sources.sql`: `ALTER TABLE tb_conversation_messages ADD COLUMN sources JSONB NOT NULL DEFAULT '[]'::jsonb`. Snapshot `[{sourceId, name, chunks:[{chunkIndex, snippet}]}]` na mensagem do assistente. Mapeamento JPA com `@JdbcTypeCode(SqlTypes.JSON)` em `List<CitedSource>` (records `CitedSource` e `CitedChunk`), sem biblioteca nova.

- Alternativa: tabela `message_sources` (FK). Rejeitada: o snapshot é pequeno, imutável e não precisa de join; FK com CASCADE apagaria a citação quando a source fosse deletada, e o `name` do snapshot preserva o histórico.
- Trade-off: o JSONB cresce (até topK=5 snippets de ~300 chars, ~1.5 KB por resposta). Aceito.
- Trade-off: `sourceId` pode ficar órfão após delete da source. Frontend trata como texto simples.

### D4. SSE: `sources` dentro do evento `done`
`{"done": true, "messageId": "...", "sources": [...]}`. Sem evento extra. O `complete()` persiste a mensagem com sources e envia o `done` com a mesma lista. Falha ao persistir segue o caminho atual (`STREAM_ERROR`).

### D5. Resposta REST
`ConversationMessageResponse` ganha `List<CitedSourceResponse> sources` (`[]` para `user` e para assistente sem contexto). Reutiliza os mesmos records do SSE para um contrato único.

### D6. Frontend
Tipos `CitedChunk { chunkIndex: number; snippet: string }` e `CitedSource { sourceId: string; name: string; chunks: CitedChunk[] }`. `ConversationMessage` ganha `sources?: CitedSource[]`. No `done`, merge de `data.sources`. UI: lista de chips "Fontes" sob a resposta do assistente quando `sources.length > 0`. Hover (e foco por teclado) no chip mostra os snippets da source via popover CSS puro/`title` acessível, sem biblioteca nova. Snippet renderizado como texto, nunca como HTML.

## Risks / Trade-offs

- [Contexto RAG não chega no stream da 2.0.0] → spike D1 antes de implementar; fallback `chatClientResponse()`.
- [Snippet exibido no DOM] → renderizar como texto (React escapa por padrão); não usar `dangerouslySetInnerHTML`.
- [`name` no snapshot desatualiza após rename] → aceito; histórico reflete o nome da época.
- [`sourceId` órfão após delete da source] → frontend não assume que o id existe.
- [Resposta sem grounding (`allowEmptyContext=true`) exibida sem fontes] → `sources: []` é o sinal correto; UI não mostra seção.
- [Doc sem `source_id` no metadata] → ignorado na dedup (defensivo).

## Migration Plan

1. Deploy da migration V4 (aditiva, default `'[]'`, sem lock longo em tabela pequena).
2. Deploy do backend. Frontend antigo ignora `sources`.
3. Deploy do frontend.
Rollback: reverter app; coluna extra é inofensiva. Drop da coluna só se necessário.
