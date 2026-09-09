## Context

Hoje `ChatClientConfig` expõe um `QuestionAnswerAdvisor` (módulo `spring-ai-vector-store-advisor`) usado direto em `ConversationMessageService.sendMessage`. Filtro de sources ativas é passado por request via `.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression)`. Histórico (últimas 10 mensagens) é buscado via `ConversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc` dentro de `buildPromptMessages`, inline no service, e usado só pra montar a lista de `Message` do prompt — não existe abstração de "histórico de conversa" reusável.

Objetivo: reescrever a query do usuário (resolvendo referências ao histórico) antes do similarity search, sem adotar `ChatMemory` (projeto é MVP, já tem `conversation_messages` como fonte de verdade, não vale duplicar armazenamento).

## Goals / Non-Goals

**Goals:**
- Pre-retrieval query rewriting via `RewriteQueryTransformer` usando o histórico da conversa.
- Manter filtro dinâmico de sources ativas por conversa dentro do novo pipeline.
- Extrair a lógica de carregar/montar histórico numa classe única (SRP), reusada pro prompt principal E pro `Query.history()` do rewrite.
- Não introduzir `ChatMemory`/tabela nova — reusar `conversation_messages` via JPA.

**Non-Goals:**
- Não migrar persistência de histórico pra `ChatMemory`.
- Não mudar o schema de `conversation_messages`.
- Não mudar contrato de API (`POST /conversations/{id}/messages` continua igual).
- Não adicionar reranking, multi-query expansion ou compression (fica pra change futura, se necessário).

## Decisions

> **Nota de implementação** (verificado lendo o source de `spring-ai-rag-2.0.0`): duas premissas iniciais deste design estavam erradas e foram corrigidas abaixo — `RewriteQueryTransformer` **não** lê `Query.history()` (só `{target}`+`{query}`), e `VectorStoreDocumentRetriever.filterExpression` é `Filter.Expression` fixo ou `Supplier<Filter.Expression>` (sem acesso à `Query`), não `Function<Query, Filter.Expression>`. Decisões 1-4 refletem o comportamento real.

### 1. `RetrievalAugmentationAdvisor` com cadeia `CompressionQueryTransformer` → `RewriteQueryTransformer`
Adiciona dependency `spring-ai-rag` (traz `org.springframework.ai.rag.*`). `RewriteQueryTransformer` sozinho não usa histórico — só otimiza o texto da query isolada pro vector store. Pra resolver referências ao histórico ("e sobre isso?"), precisa de `CompressionQueryTransformer` (usa `{history}`+`{query}` → query standalone) rodando **antes** do rewrite. `queryTransformers` do `RetrievalAugmentationAdvisor` é uma lista executada em ordem:

```
RetrievalAugmentationAdvisor.builder()
    .queryTransformers(compressionQueryTransformer, rewriteQueryTransformer)
    .documentRetriever(vectorStoreDocumentRetriever)
    .build()   // queryAugmenter default (ContextualQueryAugmenter) já serve
```

Alternativa considerada: só `RewriteQueryTransformer` (pedido original). Rejeitada após leitura do source — não cumpre a motivação (histórico ignorado). Decisão confirmada com o usuário: encadear os dois, aceitando o custo de mais uma chamada LLM.

### 2. Filtro dinâmico de sources ativas — mesmo mecanismo de hoje, chave nova
`RetrievalAugmentationAdvisor.before()` monta a `Query` com `context = chatClientRequest.context()` (o mesmo mapa populado por `.advisors(a -> a.param(...))` no `ChatClient`). `VectorStoreDocumentRetriever.computeRequestFilterExpression` lê `query.context().get(VectorStoreDocumentRetriever.FILTER_EXPRESSION)` — aceita `String` (parseada via `FilterExpressionTextParser`) ou `Filter.Expression` direto. Ou seja: **nenhuma mudança** em `buildActiveSourcesFilter` (continua retornando a mesma string `source_id in [...]`), só troca a chave do `.param()`:

```
.advisors(a -> a.advisors(retrievalAugmentationAdvisor)
    .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression))
```

O `filterExpression(Supplier<Filter.Expression>)` do builder do `VectorStoreDocumentRetriever` fica só como default (`null` → sem filtro), nunca usado em produção porque toda call passa filtro via `.param()`.

### 3. Nova classe `ConversationHistoryProvider` (nome sugerido), SRP
Extrai de `ConversationMessageService.buildPromptMessages` a parte de buscar+converter histórico:

```java
class ConversationHistoryProvider {
    List<Message> buildPromptMessages(UUID conversationId, String systemPrompt, String newUserContent);
}
```

Não precisa popular `Query.history()` manualmente: `RetrievalAugmentationAdvisor.before()` já faz isso sozinho, lendo `chatClientRequest.prompt().getInstructions()` — ou seja, a mesma lista de `Message` (system + histórico + user) que `ConversationMessageService` já passa via `.messages(promptMessages)` chega automaticamente no `CompressionQueryTransformer` como histórico. `ConversationHistoryProvider` existe só pra SRP (tirar a responsabilidade de acesso a dado do service), não pra alimentar um canal novo.

Sem `ChatMemory`: `ConversationHistoryProvider` usa `ConversationMessageRepository` (já existe), sem tabela nova, sem dependency nova.

### 4b. `ContextualQueryAugmenter.allowEmptyContext(true)`
Descoberto na implementação: o `queryAugmenter` default do `RetrievalAugmentationAdvisor` (`ContextualQueryAugmenter`) tem `allowEmptyContext=false` — quando não há chunks recuperados, ele substitui a query por uma mensagem fixa ("está fora da minha base de conhecimento") em vez de deixar passar sem contexto. Isso quebraria o requirement existente `rag-retrieval`/"Nenhum chunk relevante encontrado". Bean configurado com `allowEmptyContext(true)` pra preservar o comportamento atual.

### 4. Custo de duas chamadas LLM extra por mensagem
`CompressionQueryTransformer` e `RewriteQueryTransformer` cada um precisa de um `ChatClient.Builder` — duas chamadas extra ao modelo antes da chamada principal. Decisão: mesmo modelo/provider já configurado (OpenRouter via `ChatClient.Builder` default) pros dois, sem modelo dedicado mais barato — simplifica configuração, revisita se latência/custo virar problema real (medir antes de otimizar).

## Risks / Trade-offs

- [Latência extra por mensagem, duas chamadas LLM a mais (compress + rewrite)] → aceitável em MVP; se virar problema, trocar por modelo mais rápido/barato só pra esses dois transformers (mudança isolada nos beans), ou remover o `RewriteQueryTransformer` e ficar só com compression (já produz query standalone).
- [`RewriteQueryTransformer` pode reescrever mal a query em conversas muito longas ou ambíguas, degradando retrieval em vez de melhorar] → sem fallback automático nesta change; monitorar manualmente, considerar comparação A/B ou fallback pra query original numa iteração futura.
- [Mudança de filtro de string pra `Function<Query, Filter.Expression>` muda o contrato interno do filtro (não é mais passado por `.param()` no request)] → cobrir com teste equivalente ao `buildActiveSourcesFilterTest` já existente, validando os mesmos casos (com sources ativas, sem sources ativas, nenhuma source).

## Migration Plan

1. Adicionar dependency `spring-ai-rag` no `pom.xml`.
2. Criar `ConversationHistoryProvider`, extraindo lógica de `ConversationMessageService`.
3. Trocar bean `QuestionAnswerAdvisor` por `RetrievalAugmentationAdvisor` em `ChatClientConfig` (compression + rewrite transformers em cadeia, document retriever com filtro via `.param()`).
4. Atualizar `ConversationMessageService` pra usar o novo advisor (troca chave do filtro pra `VectorStoreDocumentRetriever.FILTER_EXPRESSION`) e a nova classe de histórico.
5. Atualizar testes (`ChatClientConfigTest`, testes de `ConversationMessageService`) pros novos beans/colaboradores.
6. Rollback: reverter commit único (sem migração de dado, sem mudança de schema — reversível direto).

## Open Questions

- Nome final da classe de histórico (`ConversationHistoryProvider` é sugestão, ajustar na implementação se convenção do projeto pedir outro sufixo).
- Vale medir/logar a query reescrita (antes/depois) pra depuração de qualidade de retrieval? Não decidido nesta change.
