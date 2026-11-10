## Context

Backend `app/backend-api`: Spring Boot, módulo Maven único, pacote `tech.buildrun.notebooklm`, organizado por camada técnica (6 controllers, 10 services, 7 repositories, 10 entities JPA, 14 DTOs, tools, security, config). I/O misturado na regra de negócio:

- `SourceService`: `MultipartFile`, `S3Template`, `SqsTemplate`, `VectorStore`.
- `SourceIngestionConsumer` (`@SqsListener` em `service/`): S3, Tika, splitter, `VectorStore`.
- `ConversationMessageService`: `SseEmitter`, `ChatClient`, `RetrievalAugmentationAdvisor`.
- `QuizGenerator`: `OpenAiChatOptions`, `VectorStore`, `ChatClient`.
- `NotebookService`: `Page`/`Pageable`.
- `ConversationHistoryProvider`: tipos `Message` do Spring AI.
- `entity/*`: `@Entity` JPA + Hibernate.

Suíte atual: muitos `*ApiIntegrationTest` (Testcontainers) + unitários de service/entity. Há 4 changes OpenSpec in-progress tocando os mesmos arquivos.

## Goals / Non-Goals

**Goals:**
- Pontos de I/O visíveis: todo contato externo em `adapter/*`, atrás de ports.
- `core` com regra de negócio e entidades, sem framework de I/O, testável sem Spring.
- Um caso de uso = uma classe.
- Regra de dependência garantida por ArchUnit no build.
- Zero mudança de contrato REST/SSE, schema e comportamento.

**Non-Goals:**
- Múltiplos módulos Maven (só pacotes).
- Mudar API, schema, frontend, infra, provedores (OpenAI, S3, SQS continuam).
- Novas features, CQRS, eventos de domínio, DDD tático completo (aggregates/value objects extensos).
- Reescrever lógica de negócio: mover e isolar, não reinventar.

## Decisions

### D1. Pacotes dentro de um módulo, enforcement por ArchUnit
Um módulo Maven, ArchUnit (`archunit-junit5`, test scope) valida dependências. Alternativa: módulos `core`/`adapters`/`config`: compilador garante, mas custo de build/POM e Spring Boot repackage maior para um app de módulo único. Reavaliar se ArchUnit se mostrar frágil.

### D2. Estrutura-alvo

```
tech.buildrun.notebooklm
├── NotebooklmApplication
├── core
│   ├── domain
│   │   ├── notebook/      Notebook
│   │   ├── source/        Source, SourceStatus, SourceType
│   │   ├── conversation/  Conversation, ConversationMessage, MessageRole, CitedSource, CitedChunk
│   │   ├── quiz/          Quiz, QuizStatus, QuizQuestion
│   │   ├── user/          User
│   │   ├── shared/        PageQuery, PageResult<T>
│   │   └── exception/     *NotFoundException, InvalidSourceIdsException, UnsupportedFileTypeException
│   ├── port
│   │   ├── in             CreateNotebookPort, ListNotebooksPort, ..., SendMessagePort, GenerateQuizPort, IngestSourcePort, UpsertUserPort
│   │   └── out
│   │       ├── persistence  NotebookRepositoryPort, SourceRepositoryPort, ConversationRepositoryPort,
│   │       │                ConversationMessageRepositoryPort, QuizRepositoryPort, UserRepositoryPort
│   │       ├── FileStoragePort, IngestionQueuePort, ChunkStorePort,
│   │       ├── ChatCompletionPort, StructuredGenerationPort,
│   │       ├── ChunkEnrichmentPort, DocumentExtractionPort
│   │       └── MessageStreamListener   (callback owned by core)
│   └── usecase            CreateNotebookUseCase, PatchNotebookUseCase, ListNotebooksUseCase, GetNotebookUseCase,
│                          DeleteNotebookUseCase, CreateConversationUseCase, ..., UploadSourceUseCase,
│                          ListSourcesUseCase, DeleteSourceUseCase, IngestSourceUseCase,
│                          SendMessageUseCase, GenerateQuizUseCase, GetQuizUseCase, UpsertUserUseCase
├── adapter
│   ├── in
│   │   ├── web            controller/, dto/, mapper/, sse/, security/, error/, logging/
│   │   └── messaging      SqsIngestionListener
│   └── out
│       ├── persistence    entity/ (*JpaEntity), repository/ (Spring Data), mapper/, *RepositoryAdapter
│       ├── storage        S3FileStorageAdapter
│       ├── queue          SqsIngestionQueueAdapter, IngestionMessage
│       ├── vector         PgVectorChunkStoreAdapter (VectorStore + JDBC de metadata)
│       └── ai             OpenAiChatCompletionAdapter, OpenAiQuizGenerationAdapter,
│                          SpringAiChunkEnrichmentAdapter, TikaDocumentExtractionAdapter,
│                          history/ (domínio→Message), tool/ (DateTimeTools, EventingToolCallback, ToolEvent), QuizOutput
└── config                 ChatClientConfig, SecurityConfig, WebMvcConfig, UseCaseConfig (beans de use cases)
```

Naming: interface `port.in` = `<Verbo><Objeto>Port`; implementação `usecase` = `<Verbo><Objeto>UseCase`. Alternativa: interface `XUseCase` + impl `XService`: contraria a decisão de "classe = use case". Mantemos o sufixo `Port` em `port.in` para evitar colisão.

### D3. Use cases sem anotação Spring
Classes de `core.usecase` não usam `@Service`/`@Transactional` do Spring (core não importa Spring). Registro como beans em `config` (`@Bean`). Transação: `@Transactional` não pode estar no core. Decisão: decorar a fronteira em `config` via bean wrapper transacional (`TransactionTemplate`) ou aplicar `@Transactional` nos `*RepositoryAdapter` e aceitar transações por chamada de port. Escolha: `TransactionalUseCaseConfig` em `config` aplicando `TransactionTemplate` a use cases que escrevem em mais de um port de persistência (ex: `DeleteSourceUseCase`, `GenerateQuizUseCase`); demais usam a transação do adapter. Alternativa descartada: permitir `org.springframework.transaction` no core (vazamento pequeno, mas quebra o critério "core sem Spring"). Ponto aberto: validar na etapa Quiz.

### D4. Entidade JPA separada do domínio
`*JpaEntity` + mapper manual (sem MapStruct, YAGNI). Mapper reconstrói o domínio por factory estática `restore(...)` (pública, pois mapper está em outro pacote; Javadoc indica uso exclusivo dos adapters de persistência). Custo: ~10 classes extras + mappers. Ganho: domínio puro, sem lazy/proxy/Hibernate no core. Relação M:N de `Conversation` com `Source` vira `Set<UUID> activeSourceIds` no domínio. Flyway e `ddl-auto=validate` intactos.

### D5. SSE via `MessageStreamListener`
Core define `MessageStreamListener { onToken; onToolCall; onDone(sources, messageId); onError }`. `SendMessageUseCase` recebe o listener, monta o contexto (histórico, sourceIds ativos) e chama `ChatCompletionPort.stream(request, listener)`. `adapter.in.web.sse.SseMessageStreamListener` implementa com `SseEmitter`, preservando os eventos `token`, `toolCall`, `done`, `error`. O pipeline RAG (advisor, query transformers, retriever, tools) fica inteiro em `OpenAiChatCompletionAdapter`; `ConversationHistoryProvider` vira mapper do adapter. Persistência da mensagem do assistente fica no use case (via `ConversationMessageRepositoryPort`), disparada em `onDone` interno do fluxo.

### D6. Ingestão
`SqsIngestionListener` (adapter in) só desserializa `IngestionMessage` e chama `IngestSourcePort`. `IngestSourceUseCase` orquestra: `FileStoragePort.get`, `DocumentExtractionPort.extractAndSplit`, `ChunkEnrichmentPort`, `ChunkStorePort.index`, atualiza status via `SourceRepositoryPort`. Semântica de ack/visibility do SQS permanece no listener: exceção do use case propaga para o listener decidir.

### D7. Quiz
`GenerateQuizUseCase` usa `ChunkStorePort.sampleChunks(sourceIds)` e `StructuredGenerationPort.generateQuiz(chunks, spec)` retornando `Quiz`/`QuizQuestion` de domínio. `OpenAiQuizGenerationAdapter` encapsula `OpenAiChatOptions`, `ResponseFormat`, `BeanOutputConverter`, `QuizOutput`.

### D8. Paginação própria
`PageQuery(page, size)` e `PageResult<T>(items, page, size, totalElements)`; conversão no controller e no adapter de persistência.

### D9. Security e usuário atual
`CurrentUser`, `CurrentUserArgumentResolver` e `UserUpsertFilter` ficam em `adapter.in.web.security`; filtro chama `UpsertUserPort`. Controllers passam `ownerId` (UUID) para os use cases; core não conhece JWT.

### D10. Migração strangler
Pacotes novos coexistem com os antigos. Cada capability migra inteira e deleta o código antigo correspondente, com suíte verde a cada etapa. Ordem: (0) ArchUnit + esqueleto, (1) Quiz, (2) User + Notebook + Conversation (CRUD), (3) Source + Ingestion, (4) SendMessage + SSE, (5) limpeza e docs. Regras ArchUnit são ativadas por pacote migrado (lista de exceções legadas encolhe até zero).

### D11. Testes
- Use cases: unitários com fakes/mocks dos ports (sem Spring).
- Adapters out: integração com Testcontainers (persistence, vector) e mocks (S3/SQS/OpenAI).
- Adapters in: `@WebMvcTest`/integração existentes, asserts inalterados.
- Testes de entity atuais migram para `core.domain` (puros) e para mapper tests.
- Quality gate Java (`java-quality-gate`: coverage/mutation) continua valendo por task.

### D12. Domínio rico: Tell, Don't Ask
Entidades de `core.domain` expõem comportamento, não estado mutável. Sem setters; construção por factory (`create(...)`) que valida invariantes; reconstrução por `restore(...)`. Exemplos:
- `Source.markProcessing()`, `markReady()`, `markFailed(reason)` com transições válidas (PENDING→PROCESSING→READY|FAILED); transição inválida lança exceção de domínio.
- `Notebook.rename(name)`, `describe(text)`, `ownedBy(ownerId)` (autorização do dono dentro do domínio, substituindo checagens `if (!owner.equals(...))` espalhadas em services).
- `Conversation.activate(Set<UUID> sourceIds)`, `append(role, content)`, `belongsTo(notebookId)`.
- `Quiz.complete(questions)`, `fail(reason)`.
Getters ficam só para leitura por mappers/DTOs (projeção), nunca para decisão no use case. Use case vira: carregar via port, comandar o objeto, salvar via port. Regras hoje em `service/*` migram para o domínio ao mover cada capability; os testes de `entity/*` viram testes de comportamento do domínio. Alternativa descartada: anêmico + use cases com regra (mais simples, mas repete o `ask` e espalha invariantes).

### D13. Sem comentários inline
Na migração de cada classe: apagar comentários `//` e `/* */`; nomes de método/classe e testes carregam a intenção. Só vira Javadoc (`/** */`) o que for extremamente necessário (ex: ack/visibility do SQS no listener, ordem de eventos SSE, uso exclusivo de `restore`). Verificação automatizada barata: teste/regra de build que falha se `src/main/java` contiver `//` ou `/*` fora de `/**` (grep via teste JUnit ou Maven Enforcer/Checkstyle `TodoComment`/`TrailingComment`; escolher o mais simples na etapa 1). Licenças/headers não existem no projeto.

## Risks / Trade-offs

- [Regressão no streaming SSE/RAG] → migra por último; testes `ConversationMessage*` inalterados; teste manual de eventos `token/toolCall/done/error`.
- [Duplicação domínio vs JpaEntity, mappers] → mapper manual simples + teste de round-trip por entidade.
- [Transações fora do core (D3)] → `TransactionTemplate` em `config`; testes de integração cobrindo rollback em delete de source e geração de quiz.
- [Conflito com changes in-progress] → arquivar/fechar os 4 changes antes de aplicar; branch dedicada e merges pequenos por capability.
- [Lazy loading: `Conversation.activeSources`, cascatas] → mapper carrega o necessário dentro da transação do adapter; cascatas continuam por `ON DELETE CASCADE` no banco.
- [Domínio rico exige mover regra de `service/*` para entidades e reescrever testes de entity] → migrar por capability, teste de comportamento antes de remover o service.
- [Mais classes e boilerplate (um use case por classe)] → aceito pelo objetivo de clareza; sem interfaces extras além de port.in/port.out.
- [ArchUnit frágil a refactors] → regras simples por pacote/import, sem regras de nomenclatura rígidas além de `*UseCase`.
- [Paridade de comportamento do `ChunkMetadataRepository` (JDBC)] → move para `adapter.out.vector` sem mudar SQL.

## Migration Plan

1. Branch `refactor/hexagonal` a partir da branch atual, PR por etapa (D10).
2. Cada etapa: criar classes novas → migrar chamadas → remover antigas → `rtk mvn test` verde → quality gate Java.
3. Rollback: reverter PR da etapa; sem migração de dados nem de schema, deploy é só artefato.

## Open Questions

- D3: `TransactionTemplate` em `config` vs `@Transactional` nos adapters. Decidir na etapa Quiz com um caso real.
- Tools de IA (`DateTimeTools`): ficam no adapter ai (decidido) ou precisam de `ToolPort`? Manter no adapter até surgir regra de negócio na tool.
- Registrar use cases por `@Bean` explícito ou `@ComponentScan` com filtro de `core.usecase` em `config`? Preferência: `@Bean` explícito para manter core sem anotação.
