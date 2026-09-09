## Why

Query de busca hoje vai crua (texto literal do usuário) pro similarity search. Em conversas com múltiplos turnos, mensagens como "e sobre isso?" ou "compara com o anterior" não carregam contexto suficiente pra recuperar chunks relevantes — retrieval degrada exatamente quando a conversa fica mais natural. Pre-retrieval query rewriting (reescrever a query usando o histórico antes de buscar no vector store) resolve isso.

## What Changes

- Adiciona dependency `spring-ai-rag` (contém `RetrievalAugmentationAdvisor`, `RewriteQueryTransformer`, `VectorStoreDocumentRetriever`).
- Substitui `QuestionAnswerAdvisor` por `RetrievalAugmentationAdvisor` configurado com `CompressionQueryTransformer` + `RewriteQueryTransformer` (pre-retrieval, em cadeia) + `VectorStoreDocumentRetriever` (retrieval) + `QueryAugmenter` (post-retrieval). **BREAKING** (interno): bean `QuestionAnswerAdvisor` deixa de existir, consumidores passam a injetar `RetrievalAugmentationAdvisor`.
  - `RewriteQueryTransformer` sozinho **não usa histórico** (só otimiza o texto da query isolada) — não resolve "e sobre isso?". Pra cumprir a motivação, `CompressionQueryTransformer` roda antes (resolve referência ao histórico, produz query standalone) e `RewriteQueryTransformer` roda depois (otimiza a query resultante pro vector store). Duas chamadas LLM extra por mensagem (compress + rewrite), além da chamada principal.
- Filtro dinâmico de sources ativas (`source_id in [...]`) continua passado por request como string via `.param()` — só troca a chave de `QuestionAnswerAdvisor.FILTER_EXPRESSION` pra `VectorStoreDocumentRetriever.FILTER_EXPRESSION` (mesmo mecanismo, `VectorStoreDocumentRetriever` aceita string ou `Filter.Expression` no context da `Query`). Sem mudança de tipo em `buildActiveSourcesFilter`.
- Extrai a lógica de carregar e montar histórico de conversa (hoje inline em `ConversationMessageService.buildPromptMessages`) pra uma classe própria (SRP), reusada tanto pra montar o prompt principal quanto pra alimentar `Query.history()` do `RewriteQueryTransformer`.
- Sem `ChatMemory`/`ChatMemoryRepository`: histórico continua vindo da tabela `conversation_messages` já existente via JPA — nenhuma infra nova.

## Capabilities

### New Capabilities
(nenhuma)

### Modified Capabilities
- `rag-retrieval`: similarity search passa a rodar sobre uma query reescrita (não mais a query crua do usuário); filtro de sources ativas passa a ser resolvido dentro do `DocumentRetriever` em vez de parâmetro por request do advisor antigo.
- `chat`: montagem do histórico pro prompt e para o rewrite passa a ser responsabilidade de um componente dedicado, não muda o comportamento observável do endpoint (mesmo histórico, mesma persistência).

## Impact

- `app/backend-api/pom.xml`: nova dependency `spring-ai-rag`.
- `ChatClientConfig`: bean `QuestionAnswerAdvisor` sai, entra `RetrievalAugmentationAdvisor`.
- `ConversationMessageService`: remove lógica de histórico inline, passa a usar a nova classe de histórico; troca advisor usado no `chatClient.prompt()`.
- Novo componente de histórico de conversa (nome a definir em design.md).
- Testes: `ChatClientConfigTest`, testes de `ConversationMessageService` que hoje mockam `QuestionAnswerAdvisor`.
- Custo/latência: duas chamadas LLM adicionais por mensagem (compression + rewrite), antes da chamada principal.
