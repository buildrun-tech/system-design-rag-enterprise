## 1. Backend: contrato do evento

- [x] 1.1 Adicionar `String summary` a `ToolEvent` e `ToolEventPayload` (NON_NULL) e ajustar construtores/usos
- [x] 1.2 Tabela `nome da tool → (label, summary ok, summary erro)` em `EventingToolCallback` com fallback para `label()`; preencher `summary` em `end`/`error`
- [x] 1.3 Testes de `EventingToolCallback`: textos amigáveis, erro, tool fora da tabela
- [x] 1.4 Teste de `SseMessageStreamListener` com `summary` no payload

## 2. Backend: passo retrieval

- [x] 2.1 Em `OpenAiChatCompletionAdapter.stream()`, emitir `retrieval` `start` ("Buscando nas suas fontes") antes de chamar o `chatClient`
- [x] 2.2 No primeiro `onResponse`, emitir `retrieval` `end` com `summary` ("N trechos encontrados em M fontes" / "Nenhum trecho relevante encontrado") e `durationMs`; comentário `ponytail:` sobre a latência incluída
- [x] 2.3 Testes do adapter: com documentos, sem documentos, ordem `retrieval` antes de `token`
- [x] 2.4 `rtk mvn test` em `app/backend-api` (incluindo ArchUnit)

## 3. Frontend

- [x] 3.1 Adicionar `summary?: string` a `ToolCall` em `api/types.ts`
- [x] 3.2 `ToolTimeline`: mostrar label / summary / duração sem JSON; `input`, `output`, "detalhes" e nome técnico só com `dev`
- [x] 3.3 `WorkspacePage`: "Escrevendo resposta…" (shimmer) quando streaming, conteúdo vazio e nenhum passo em `start`
- [x] 3.4 `rtk npx tsc -b` e `rtk npm run build` em `app/frontend`

## 4. Docs e verificação

- [x] 4.1 Atualizar `API.md` (campo `summary`, passo `retrieval`, exemplo de eventos)
- [ ] 4.2 Verificação manual com DEV aberto: pergunta sobre documento e "que dia é hoje?"; conferir `summary` no log SSE e a sequência de mensagens na UI
