## 0. Pré-requisitos

- [ ] 0.1 Fechar/arquivar changes in-progress que tocam o backend (`add-tool-call-events-sse`, `add-conversation-quiz`, `rag-sources-in-chat-response`) e registrar baseline: `rtk mvn test` verde na branch
- [x] 0.2 Criar branch `refactor/hexagonal` a partir de `diario/dia-20`

## 1. Esqueleto e ArchUnit

- [x] 1.1 Adicionar `archunit-junit5` (test scope) ao `pom.xml`
- [x] 1.2 Criar pacotes `core.domain`, `core.port.in`, `core.port.out`, `core.usecase`, `adapter.in.web`, `adapter.in.messaging`, `adapter.out.*`; manter `config` (com `package-info` ou primeira classe)
- [x] 1.3 Criar `HexagonalArchitectureTest` com regras: core sem `org.springframework.web|data|ai|security`, `jakarta.persistence|servlet`, `org.hibernate`, `io.awspring`; core não depende de adapter/config; adapter.in não depende de `core.usecase` nem de adapter.out; adapters não dependem entre si; use cases terminam em `UseCase`
- [x] 1.4 Aplicar regras com lista de exceções legadas (pacotes ainda não migrados), a ser reduzida a cada etapa
- [x] 1.5 Criar `PageQuery` e `PageResult<T>` em `core.domain.shared`
- [x] 1.6 Escolher e adicionar verificação automática de comentários inline em `src/main/java` (teste JUnit com varredura ou Checkstyle), inicialmente restrita aos pacotes novos; incluir regra ArchUnit: `core.domain..` sem métodos públicos `set*`
- [x] 1.7 Criar `UseCaseConfig` em `config`: classe de registro de use cases por `@Bean` explícito e definir estratégia de transação (decisão D3)

## 2. Capability Quiz (piloto)

- [x] 2.1 Criar `Quiz`, `QuizStatus`, `QuizQuestion` em `core.domain.quiz` (POJO, sem JPA) com comportamento (Tell, Don't Ask): `Quiz.create(...)`, `complete(questions)`, `fail(reason)`, sem setters; factory `restore(...)` para mappers
- [x] 2.2 Criar ports: `QuizRepositoryPort`, `StructuredGenerationPort`, `ChunkStorePort` (parte de amostragem), `ChunkEnrichmentPort`, `GenerateQuizPort`, `GetQuizPort`
- [x] 2.3 Criar `GenerateQuizUseCase` e `GetQuizUseCase` (lógica de `QuizService` + `QuizGenerator`, sem Spring AI)
- [x] 2.4 Criar `QuizJpaEntity`, repository Spring Data, mapper e `QuizRepositoryAdapter`
- [x] 2.5 Criar `OpenAiQuizGenerationAdapter` (`OpenAiChatOptions`, `ResponseFormat`, `BeanOutputConverter`, `QuizOutput`) e `SpringAiChunkEnrichmentAdapter` (de `ChunkEnrichmentService`)
- [x] 2.6 Criar `PgVectorChunkStoreAdapter` (amostragem/consulta de chunks, mover `ChunkMetadataRepository` sem alterar SQL)
- [x] 2.7 Mover `QuizController` para `adapter.in.web.controller` usando `GenerateQuizPort`/`GetQuizPort`; mover DTOs `QuizResponse`/`QuizCreatedResponse` para `adapter.in.web.dto` com mapper
- [x] 2.8 Migrar testes: unitários de use case com fakes dos ports, round-trip do mapper, `QuizControllerTest`, adapter de IA com mock
- [x] 2.9 Remover comentários inline das classes migradas (informação indispensável vira Javadoc)
- [x] 2.10 Remover `QuizService`, `QuizGenerator`, `entity.Quiz*`, `QuizRepository`, DTOs antigos; atualizar exceções legadas do ArchUnit
- [x] 2.11 Decidir D3 (transação) com base no caso real e registrar em `design.md`
- [x] 2.12 `rtk mvn test` verde; `*ApiIntegrationTest` de quiz sem mudança de asserts

## 3. Capability User, Notebook e Conversation (CRUD)

- [x] 3.1 Criar `User`, `Notebook`, `Conversation` (com `activeSourceIds`), `ConversationMessage`, `MessageRole` em `core.domain` com comportamento e sem setters (`Notebook.rename/describe/ownedBy`, `Conversation.activate/append/belongsTo`, `User` com factory); invariantes e checagem de dono dentro do domínio; mover exceções de domínio para `core.domain.exception`
- [x] 3.2 Criar ports de saída: `UserRepositoryPort`, `NotebookRepositoryPort`, `ConversationRepositoryPort`, `ConversationMessageRepositoryPort`
- [x] 3.3 Criar ports de entrada e use cases de notebook: `CreateNotebookUseCase`, `PatchNotebookUseCase`, `ListNotebooksUseCase` (usa `PageQuery`/`PageResult`), `GetNotebookUseCase`, `DeleteNotebookUseCase`
- [x] 3.4 Criar use cases de conversation: `CreateConversationUseCase`, `ListConversationsUseCase`, `GetConversationUseCase`, `DeleteConversationUseCase`, `UpdateActiveSourcesUseCase` (conforme endpoints atuais), validação de `InvalidSourceIdsException` no core
- [x] 3.5 Criar `UpsertUserUseCase` (de `UserUpsertService`)
- [x] 3.6 Criar `*JpaEntity`, repositories Spring Data, mappers (Conversation carrega active sources dentro da transação) e `*RepositoryAdapter` para User, Notebook, Conversation, ConversationMessage
- [x] 3.7 Mover `NotebookController`, `ConversationController`, `HelloController` para `adapter.in.web.controller`; DTOs para `adapter.in.web.dto` + mappers
- [x] 3.8 Mover `security/*` para `adapter.in.web.security` (filtro chama `UpsertUserPort`); `GlobalExceptionHandler`/`ErrorResponse` para `adapter.in.web.error`; filtro e aspect de logging para `adapter.in.web.logging`
- [x] 3.9 Migrar testes: entity tests viram testes de domínio puros; `CascadeDeleteTest` e repositórios contra adapters; use cases com fakes
- [x] 3.10 Remover comentários inline das classes migradas (informação indispensável vira Javadoc)
- [x] 3.11 Remover classes legadas correspondentes e atualizar exceções do ArchUnit
- [x] 3.12 `rtk mvn test` verde; `NotebookApiIntegrationTest` e `ConversationApiIntegrationTest` sem mudança de asserts

## 4. Capability Source e Ingestion

- [x] 4.1 Criar `Source`, `SourceStatus`, `SourceType` em `core.domain.source` com transições de status como comportamento (`markProcessing()`, `markReady()`, `markFailed(reason)`, transição inválida lança exceção de domínio) e sem setters
- [x] 4.2 Criar ports: `SourceRepositoryPort`, `FileStoragePort`, `IngestionQueuePort`, `DocumentExtractionPort`, `ChunkStorePort` (index, deleteBySource), `UploadSourcePort`, `ListSourcesPort`, `DeleteSourcePort`, `IngestSourcePort`
- [x] 4.3 Criar `UploadSourceUseCase` (sem `MultipartFile`: recebe nome, tipo, tamanho e `InputStream`/bytes), `ListSourcesUseCase`, `DeleteSourceUseCase`
- [x] 4.4 Criar `IngestSourceUseCase`: get arquivo, extrair e dividir, enriquecer, indexar chunks, atualizar status (READY/FAILED), propagando exceção ao listener
- [x] 4.5 Criar `SourceJpaEntity`, mapper e `SourceRepositoryAdapter`; `S3FileStorageAdapter`; `SqsIngestionQueueAdapter` (+ `IngestionMessage`); `TikaDocumentExtractionAdapter` (Tika + `TokenTextSplitter`); completar `PgVectorChunkStoreAdapter`
- [x] 4.6 Criar `SqsIngestionListener` em `adapter.in.messaging` (`@SqsListener`, `Visibility`, ack) chamando `IngestSourcePort`
- [x] 4.7 Mover `SourceController` e DTO `SourceResponse` para `adapter.in.web` (converte `MultipartFile` para o comando do use case; valida tipo suportado)
- [x] 4.8 Migrar testes: `SourceServiceTest` e `SourceIngestionConsumerTest` viram testes de use case com fakes; testes de adapters S3/SQS com mock; `ChunkMetadataRepositoryIntegrationTest` para o adapter de vetor
- [x] 4.9 Remover comentários inline das classes migradas (informação indispensável, como ack/visibility do SQS, vira Javadoc)
- [x] 4.10 Remover `SourceService`, `SourceIngestionConsumer`, `entity.Source*`, `SourceRepository`, `ChunkMetadataRepository` antigos; atualizar exceções do ArchUnit
- [x] 4.11 `rtk mvn test` verde; `SourceApiIntegrationTest` sem mudança de asserts

## 5. Capability SendMessage e SSE (chat RAG)

- [x] 5.1 Criar `CitedSource`, `CitedChunk` em `core.domain.conversation`; definir `MessageStreamListener` (`onToken`, `onToolCall`, `onDone`, `onError`) e `ToolEvent` de domínio em `core.port.out`
- [x] 5.2 Criar `ChatCompletionPort` (histórico, sourceIds ativos, pergunta, listener) e `SendMessagePort`
- [x] 5.3 Criar `SendMessageUseCase`: carrega conversa, comanda `conversation.append(USER, content)` (dono e regras dentro do domínio), persiste mensagem do usuário, chama `ChatCompletionPort.stream`, persiste mensagem do assistente com fontes citadas, notifica listener
- [x] 5.4 Criar `OpenAiChatCompletionAdapter` com `ChatClient`, `RetrievalAugmentationAdvisor`, query transformers e retriever (mover de `ChatClientConfig`/`ConversationMessageService`), filtro por source ids e emissão de tool events
- [x] 5.5 Mover `ConversationHistoryProvider` para mapper em `adapter.out.ai.history`; mover `DateTimeTools`, `EventingToolCallback`, `ToolEvent` para `adapter.out.ai.tool`
- [x] 5.6 Criar `SseMessageStreamListener` em `adapter.in.web.sse` preservando eventos `token`, `toolCall`, `done` (com sources) e `error` `STREAM_ERROR`, conforme `API.md`
- [x] 5.7 Mover `ConversationMessageController` para `adapter.in.web.controller` (cria `SseEmitter`, passa listener ao `SendMessagePort`); DTOs para `adapter.in.web.dto`
- [x] 5.8 Migrar testes: `ConversationMessageServiceTest` vira teste de use case com fake de `ChatCompletionPort`; `ChatClientConfigTest` e `EventingToolCallbackTest` para o adapter ai; `ConversationMessageControllerTest` e `ConversationMessageApiIntegrationTest` sem mudança de asserts
- [x] 5.9 Remover comentários inline das classes migradas (ordem dos eventos SSE e semântica de tool callback viram Javadoc quando indispensáveis)
- [x] 5.10 Remover `ConversationMessageService`, `ConversationHistoryProvider`, `ActiveSources`, `ChatClientConfig` antigos; atualizar exceções do ArchUnit
- [ ] 5.11 Teste manual local: stream de chat com tokens, tool call, citações e erro; comparar eventos com `API.md`
- [x] 5.12 `rtk mvn test` verde

## 6. Limpeza e documentação

- [x] 6.1 Confirmar pacotes legados (`controller`, `service`, `repository`, `entity`, `dto`, `security`, `tool`, `exception`) vazios/removidos (`config` permanece como pacote de wiring) e lista de exceções do ArchUnit vazia
- [x] 6.2 Mover `ChatClientConfig`, `SecurityConfig`, `WebMvcConfig` e wiring final de use cases para `config` (filtros/aspect de logging já foram para `adapter.in.web.logging`)
- [x] 6.3 Atualizar `ARCHITECTURE.md` e `CLAUDE.md` com a estrutura hexagonal (tree view, regra de dependência, convenção `<Verbo><Objeto>UseCase`/`Port`)
- [x] 6.4 Ampliar a verificação de comentários inline para todo `src/main/java` e garantir zero ocorrências; confirmar ausência de `set*` públicos em `core.domain`
- [x] 6.5 Atualizar `openspec/config.yaml`/specs afetadas se necessário e validar com `openspec validate hexagonal-architecture-refactor`

## 7. Quality gate Java

- [x] 7.1 Executar a skill `java-quality-gate` (line coverage e mutation score) após cada etapa 2 a 5 e uma execução final completa após a etapa 6
- [x] 7.2 Corrigir lacunas de teste apontadas (use cases novos, mappers e adapters) até o gate passar
