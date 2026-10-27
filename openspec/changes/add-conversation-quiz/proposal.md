## Why

Usuário conversa com os documentos mas não tem como testar o que aprendeu. Um quiz gerado dinamicamente a partir das sources ativas da conversation fecha esse ciclo de estudo. Para escolher bons tópicos sem ler o corpus inteiro, cada chunk passa a carregar keywords e resumo gerados por LLM.

## What Changes

- Ingestão passa a enriquecer cada chunk com `KeywordMetadataEnricher` (`excerpt_keywords`) e `SummaryMetadataEnricher` (`section_summary`, só `CURRENT`) antes do `vectorStore.add`. Enrich é obrigatório: falha de enrich falha a ingestão da source (`FAILED`).
- Geração de quiz totalmente assíncrona: `POST /api/v1/conversations/{id}/quizzes` cria um job (`202 Accepted`, `quizId`, `status`) e `GET /api/v1/conversations/{id}/quizzes/{quizId}` é consultado por polling até `COMPLETED` (5 perguntas × 4 opções, 1 correta, com justificativa) ou `FAILED`. Regenerável a cada clique (cada POST cria um novo job).
- Nova tabela `tb_quizzes` (status + resultado JSONB) para o estado do job, que sobrevive a múltiplas instâncias do ECS.
- Novo `QuizService`/worker: backfill sob demanda de chunks sem metadata → agregação (count) das keywords dos chunks das sources ativas → top tópicos → `similaritySearch` por tópico → 1 chamada LLM com structured output (JSON schema via OpenRouter) → validação + embaralhamento das opções.
- Frontend: painel lateral direito no workspace com botão "Gerar quiz", polling do status e renderização interativa do quiz (seleção de opção, feedback de acerto/erro, justificativa, pontuação).
- `visibility_timeout_seconds` da fila de ingestão passa a 180s (3 min).
- Chunks ingeridos antes desta change (sem `excerpt_keywords`) são enriquecidos e atualizados in-place na primeira solicitação de quiz que os envolva.
- Embedding dos chunks passa a excluir `excerpt_keywords`/`section_summary` do texto embedado, para o retrieval do chat não mudar e chunks backfilled ficarem consistentes com os novos.

## Capabilities

### New Capabilities
- `conversation-quiz`: geração de quiz (5 perguntas, 4 opções, justificativa) a partir das sources ativas da conversation, com backfill de metadata e structured output.

### Modified Capabilities
- `source-ingestion`: pipeline de ingestão passa a enriquecer chunks com keywords e summary; falha de enrich marca a source como `FAILED`.

## Impact

- Código backend: migration `V5`, entidade/repositório `Quiz`, `QuizController`, `SourceIngestionConsumer`, novo `ChunkEnrichmentService` (compartilhado por ingestão e backfill), novo `QuizService`/`QuizController`/DTOs, extração de `buildActiveSourcesFilter` de `ConversationMessageService` para componente compartilhado.
- Banco: nova tabela `tb_quizzes`; `vector_store.metadata` (JSON) ganha chaves novas, atualizada via `JdbcTemplate`.
- Infra: `infra/main.tf` define `visibility_timeout_seconds = 180` na `ingest_queue`.
- Frontend: `QuizPanel`, tipos em `api/types.ts`, estilos em `ui.css`, grid do `WorkspacePage` com 3 colunas.
- Custo: +2 chamadas LLM por chunk na ingestão e no backfill; +1 chamada por quiz.
- API: novos endpoints em `API.md`.
- Dependências: nenhuma nova (`spring-ai-*` já presentes).
