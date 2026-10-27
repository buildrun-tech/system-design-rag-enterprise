## Context

Ingestão hoje: Tika → `TokenTextSplitter(512)` → tag `source_id`/`embedding_model` → `vectorStore.add`, dentro de `@Transactional` no listener SQS (`SourceIngestionConsumer`). `vector_store.metadata` é `JSON` (não JSONB), sem índice. Filtro de sources ativas vive em `ConversationMessageService.buildActiveSourcesFilter`. Chat usa `ChatClient` com starter `openai` apontando para OpenRouter (Spring AI 2.0.0, Spring Boot 4.1, Java 25).

## Goals / Non-Goals

**Goals:**
- Enriquecer todo chunk (novo e legado) com keywords e summary.
- Endpoint de quiz síncrono, regenerável, com saída estruturada validada.
- Mínimo de chamadas LLM no fluxo do quiz (1 de geração).

**Non-Goals:**
- Persistir quizzes, histórico ou ranking.
- Job de backfill em batch; só sob demanda.
- Normalização semântica de sinônimos de keywords.
- Paralelizar enrich (decisão de escopo desta change; ver Risks).
- Fila/worker externo (SQS) para o quiz: execução em thread virtual da própria instância.
- Cancelar jobs em andamento e limitar jobs concorrentes por conversation.

## Decisions

1. **`ChunkEnrichmentService` único** (ingestão e backfill). Recebe `List<Document>`, aplica `KeywordMetadataEnricher` + `SummaryMetadataEnricher(CURRENT)` e devolve documentos com metadata. Sequencial. *Alternativa:* dois caminhos separados — rejeitado, divergiria.
2. **Enrich obrigatório na ingestão**: exceção propaga e `markFailed` existente trata. Sem degradação silenciosa, para o quiz poder assumir keywords presentes em chunks novos.
3. **Metadata fora do embedding**: verificado no spike que o `TokenCountBatchingStrategy` padrão usa `MetadataMode.NONE`, então `excerpt_keywords`/`section_summary` não entram no vetor; retrieval do chat inalterado e chunks backfilled equivalentes aos novos. Nada a configurar. O `SummaryMetadataEnricher` é criado com `MetadataMode.NONE` para o prompt de resumo não incluir metadata.
4. **Backfill in-place**: `SELECT id, content, metadata FROM vector_store WHERE metadata->>'source_id' IN (...) AND metadata->>'excerpt_keywords' IS NULL`; enrich; `UPDATE vector_store SET metadata = (metadata::jsonb || ?::jsonb)::json WHERE id = ?` via `JdbcTemplate`. Embedding intocado. Idempotente, então duas requisições concorrentes só duplicam custo. *Alternativa:* reingestão da source — rejeitada, cara e destrutiva.
5. **Agregação de keywords em SQL**: `unnest(string_to_array(metadata->>'excerpt_keywords', ','))` com `lower(trim())`, `GROUP BY`, `ORDER BY count DESC LIMIT 5`. `excerpt_keywords` é string separada por vírgula, não array. Scan por sources ativas, aceitável na escala de notebook.
6. **Contexto de geração**: por tópico, `vectorStore.similaritySearch(topic, topK=3, filtro de sources)`; dedupe por id do chunk. Texto cru dos chunks vai ao prompt, não summaries (evita deriva). Nenhuma chamada LLM extra para "entender contexto".
7. **Structured output**: `ChatClient` com `response_format: json_schema` (`strict`) para um record `Quiz`, e `provider.require_parameters=true` do OpenRouter para não rotear a provider sem suporte. Mapeamento exato das options no Spring AI 2.0.0 (native structured output / `extraBody`) deve ser confirmado em spike na primeira task; fallback: `.entity(Quiz.class)` (schema no prompt). Invariantes (5×4, índice 0..3) validadas em Java porque strict mode não garante `minItems/maxItems`; 1 retry.
8. **Embaralhar opções no servidor** após validar, remapeando `correctIndex` (LLMs enviesam posição da resposta).
9. **Filtro de sources ativas extraído** para componente compartilhado (`ActiveSourcesFilter`) usado por chat e quiz. Sources ativas = marcadas na conversation, ou todas `READY` do notebook se nenhuma marcada.
10. **Assíncrono com job em banco**: `POST` insere linha `tb_quizzes` (`PENDING`), dispara a geração em executor de threads virtuais (`SimpleAsyncTaskExecutor` virtual) e responde `202`. O worker move para `PROCESSING` → `COMPLETED` (resultado em `result JSONB`) ou `FAILED` (`error_message`). `GET` lê a linha (ownership via conversation → notebook → owner). Resolve o limite de 30s do API Gateway e o backfill longo no clique. *Alternativas:* SSE (conexão presa, mesmo limite de proxy), SQS (infra extra sem necessidade). Estado no banco, não em memória, porque há mais de uma task ECS.
11. **Job órfão**: se a instância cair no meio, a linha fica em `PENDING`/`PROCESSING`. O `GET` trata jobs com `updated_at` mais antigo que 10 min como `FAILED` ("tempo excedido") — checagem preguiçosa, sem scheduler.
12. **Histórico não é exposto**: cada geração é um job novo; a UI só consulta o job que ela criou. Linhas antigas ficam (cascade com a conversation); sem limpeza nesta change.
13. **Frontend**: coluna direita `QuizPanel` no `WorkspacePage` (grid `1fr 2fr 1fr`; empilha abaixo de 900px). Estado local: `idle → generating (polling a cada 2s) → ready | failed`. Polling por `setInterval` limpo ao trocar de conversation/desmontar. Todas as perguntas são listadas; ao escolher uma opção, trava a pergunta, mostra acerto/erro e `explanation`; rodapé com pontuação e "Gerar novo quiz". Gabarito vem no payload, então a correção é local.
14. **SQS visibility timeout = 180s** na `ingest_queue` (`infra/main.tf`), conforme pedido.

## Risks / Trade-offs

- [Backfill no job pode levar minutos em source grande (2 chamadas LLM × chunks, sequencial)] → agora assíncrono, o usuário só vê "gerando"; ponytail: sequencial; upgrade para lotes paralelos se medido lento.
- [Ingestão mais lenta e cara; com visibility timeout de 180s, uma source cuja ingestão passe de 3 min é reentregue e processada em duplicidade (chunks duplicados no `vector_store`)] → timeout de 180s definido por decisão do usuário; ceiling conhecido, upgrade: paralelizar o enrich ou estender a visibilidade durante o processamento.
- [Jobs de quiz perdidos em restart] → decisão 11.
- [Cliques repetidos criam jobs concorrentes e custo duplicado] → UI desabilita o botão durante a geração; backend não limita.
- [Keywords livres do LLM fragmentam contagem (sinônimos, idioma)] → normalização lowercase/trim apenas; dedupe de chunks cobre sobreposição.
- [Jobs concorrentes duplicam backfill] → idempotente; custo duplicado aceito.
- [Saída estruturada ignorada por modelo/provider sem suporte] → `require_parameters`, validação Java, retry, `502` se persistir.
- [Conversation grande retorna chunks demais] → topK fixo e 5 tópicos limitam prompt a ≤15 chunks de ~512 tokens.

## Migration Plan

- Migration `V5` cria `tb_quizzes`. Deploy do código; chunks legados são enriquecidos na primeira geração de quiz.
- Rollback: reverter deploy; tabela e chaves extras em `metadata` ficam inertes.

## Open Questions

- Modelo dedicado e mais barato para enrich vs. mesmo modelo do chat (default: mesmo `ChatModel`).
- ~~API exata de structured output no Spring AI 2.0.0~~ Resolvido no spike: `OpenAiChatOptions.responseFormat(ResponseFormat{type=JSON_SCHEMA, jsonSchema})` e `extraBody(Map.of("provider", Map.of("require_parameters", true)))` existem; o schema vem de `BeanOutputConverter`.
