## 1. Dependências

- [x] 1.1 Adicionar `spring-ai-rag` ao `app/backend-api/pom.xml` (gerenciado pelo `spring-ai-bom`, sem versão explícita)

## 2. Componente de histórico (SRP)

- [x] 2.1 Criar `ConversationHistoryProvider` (ou nome equivalente) encapsulando busca de histórico via `ConversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc` + conversão pra `List<Message>` (hoje inline em `ConversationMessageService.buildPromptMessages`/`toSpringAiMessage`)
- [x] 2.2 Expor método que monta `promptMessages` completo (SystemMessage + histórico + UserMessage) — é essa mesma lista, passada via `.messages(...)`, que o `RetrievalAugmentationAdvisor` usa como histórico pro `CompressionQueryTransformer` (não precisa de canal separado)
- [x] 2.3 Escrever teste unitário do `ConversationHistoryProvider` (histórico vazio, histórico parcial, ordenação cronológica)

## 3. RetrievalAugmentationAdvisor

- [x] 3.1 Em `ChatClientConfig`, criar bean `CompressionQueryTransformer` (usando `ChatClient.Builder` já configurado) — resolve referência ao histórico numa query standalone
- [x] 3.2 Criar bean `RewriteQueryTransformer` (mesmo `ChatClient.Builder`) — otimiza a query resultante pro vector store
- [x] 3.3 Criar bean `VectorStoreDocumentRetriever` com `topK` atual (5); sem `filterExpression` fixo no bean (filtro dinâmico continua vindo por `.param()` no request, ver grupo 4)
- [x] 3.4 Criar bean `RetrievalAugmentationAdvisor` com `queryTransformers(compressionQueryTransformer, rewriteQueryTransformer)` (nessa ordem) + `documentRetriever(vectorStoreDocumentRetriever)`, remover bean `QuestionAnswerAdvisor`
- [x] 3.5 Atualizar `ChatClientConfigTest` pros novos beans

## 4. Integração no fluxo de chat

- [x] 4.1 Atualizar `ConversationMessageService` pra injetar `RetrievalAugmentationAdvisor` e `ConversationHistoryProvider` no lugar de `QuestionAnswerAdvisor`
- [x] 4.2 Trocar `.advisors(a -> a.advisors(questionAnswerAdvisor).param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))` por `.advisors(a -> a.advisors(retrievalAugmentationAdvisor).param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression))` — `buildActiveSourcesFilter` não muda (continua retornando `String`)
- [x] 4.3 Atualizar testes de `ConversationMessageService` (mocks do advisor antigo, novos mocks/fakes do `ConversationHistoryProvider`)

## 5. Validação

- [ ] 5.1 Rodar `java-quality-gate` sobre o módulo `backend-api` após as mudanças — **bloqueada**: `mvn test` do módulo falha em `SourceIngestionConsumerTest.processMarksFailedAndRethrowsWhenSourceMissing`, pré-existente e não relacionada a esta change (`SourceIngestionConsumer.ingest:88` relança `SourceNotFoundException` envolvida em `RuntimeException`). Usuário decidiu pular por agora; revisitar depois de corrigir esse teste separadamente.
- [x] 5.2 Rodar suíte de testes completa (`rtk mvn test`) e confirmar que os cenários dos specs `rag-retrieval` e `chat` (MODIFIED Requirements) passam — rodado escopo desta change (`ConversationMessageServiceTest`, `ConversationHistoryProviderTest`, `ChatClientConfigTest`: 14/14 passam); suíte completa do módulo segue bloqueada pela falha pré-existente registrada em 5.1
- [ ] 5.3 Teste manual: enviar mensagem de acompanhamento numa conversa existente (ex: "e sobre isso?") e conferir, via log/debug, que a query final (pós compression+rewrite) referencia o histórico — **pendente**: usuário vai validar manualmente (precisa app local rodando com Postgres + chave OpenRouter)
