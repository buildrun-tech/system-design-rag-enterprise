## 1. Spike structured output

- [x] 1.1 Confirmar no Spring AI 2.0.0 como enviar `response_format: json_schema` e `provider.require_parameters=true` ao OpenRouter via `OpenAiChatOptions` (feito: `ResponseFormat` + `extraBody`; registrado em `design.md`)

## 2. Enrich na ingestão

- [x] 2.1 Criar `ChunkEnrichmentService` (`KeywordMetadataEnricher` + `SummaryMetadataEnricher` com `SummaryType.CURRENT` e `MetadataMode.NONE`, sequencial) e teste unitário com `ChatModel` mockado
- [x] 2.2 Metadata fora do embedding: verificado, `TokenCountBatchingStrategy` padrão usa `MetadataMode.NONE`; nada a configurar
- [x] 2.3 Integrar o enrich em `SourceIngestionConsumer.ingest` antes de `vectorStore.add`; falha propaga e marca `FAILED`
- [x] 2.4 Atualizar `SourceIngestionConsumerTest` (metadata presente; falha de enrich gera `FAILED`)
- [x] 2.5 `visibility_timeout_seconds = 180` na `ingest_queue` (`infra/main.tf`)

## 3. Filtro de sources ativas compartilhado

- [x] 3.1 Extrair resolução de ids de sources ativas de `ConversationMessageService` para helper reutilizável (sem mudar o construtor) e manter testes existentes verdes

## 4. Quiz backend

- [x] 4.1 Migration `V5` com `tb_quizzes` (id, conversation_id FK cascade, status, result JSONB, error_message, created_at, updated_at); entidade `Quiz`, `QuizStatus`, `QuizRepository`
- [x] 4.2 DTOs (`QuizCreatedResponse`, `QuizResponse`, `QuizQuestion`) e record do schema de saída do LLM
- [x] 4.3 Repositório JDBC de chunks: buscar chunks sem `excerpt_keywords`, atualizar `metadata` por id, agregar top-5 keywords por `source_id`
- [x] 4.4 Worker de geração: backfill, seleção de tópicos, `similaritySearch` por tópico com dedupe, chamada com structured output, validação (5×4, índice 0..3), 1 retry, embaralhamento; transições `PROCESSING`/`COMPLETED`/`FAILED`; testes (retry, falha persistente, remapeamento de `correctIndex`, nada a enriquecer)
- [x] 4.5 `QuizService`: criar job (`PENDING`) e disparar worker em executor de threads virtuais; consulta com ownership, 404 e regra de job órfão (10 min); teste
- [x] 4.6 `QuizController`: `POST /api/v1/conversations/{id}/quizzes` (202) e `GET .../quizzes/{quizId}`; exceção `QuizNotFoundException` mapeada para 404 no `GlobalExceptionHandler`; teste de controller

## 5. Frontend

- [x] 5.1 Tipos de quiz em `api/types.ts`
- [x] 5.2 `QuizPanel`: botão "Gerar quiz", `POST`, polling de 2s com limpeza (troca de conversation/desmontagem), estados idle/generating/failed/ready
- [x] 5.3 Renderização do quiz: opções, trava ao responder, feedback de acerto/erro, justificativa, pontuação e "Gerar novo quiz"
- [x] 5.4 Integrar o painel na coluna direita do `WorkspacePage` (grid de 3 colunas, empilha abaixo de 900px) e estilos em `ui.css`
- [x] 5.5 `npm run build` e `npm run lint` sem erros

## 6. Documentação e verificação

- [x] 6.1 Documentar os endpoints em `API.md` e o fluxo de enrich/backfill/quiz assíncrono em `ARCHITECTURE.md`
- [ ] 6.2 Rodar `rtk mvn test` e o java-quality-gate
