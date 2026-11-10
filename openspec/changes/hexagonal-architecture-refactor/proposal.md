## Why

Backend (`app/backend-api`) organizado por camada técnica (`controller/service/repository/entity/dto`). Pontos de I/O (HTTP, SSE, SQS, S3, pgvector, OpenAI, Tika, Postgres) espalhados dentro dos `service/*`; entidades são `@Entity` JPA; `SseEmitter`, `MultipartFile`, `Page`, `ChatClient` e `OpenAiChatOptions` vazam para a regra de negócio. Difícil ver onde o sistema toca o mundo externo, testar regra sem Spring/Testcontainers e trocar provedor (OpenAI, S3, SQS) sem tocar no domínio.

## What Changes

- Reorganizar `tech.buildrun.notebooklm` em arquitetura hexagonal (ports and adapters), num único módulo Maven, separação por pacotes:
  - `core/domain`: entidades e regras de negócio como POJOs sem framework (Notebook, Source, Conversation, ConversationMessage, Quiz, User, enums, exceções de domínio, `PageResult`/`PageQuery`).
  - `core/port/in`: interfaces de caso de uso (o que o mundo pede ao core).
  - `core/port/out`: interfaces de saída (persistência, `FileStoragePort`, `IngestionQueuePort`, `ChunkStorePort`, `ChatCompletionPort`, `StructuredGenerationPort`, `ChunkEnrichmentPort`, `DocumentExtractionPort`).
  - `core/usecase`: substitui `service/`. **Uma classe por caso de uso** (ex: `CreateNotebookUseCase`, `UploadSourceUseCase`, `IngestSourceUseCase`, `SendMessageUseCase`, `GenerateQuizUseCase`), cada uma implementando uma interface de `port/in`.
  - `adapter/in/web` (controllers, dto, mapper, sse, security, error, logging) e `adapter/in/messaging` (`SqsIngestionListener`).
  - `adapter/out/persistence` (`*JpaEntity` + Spring Data + mapper + `*RepositoryAdapter`), `storage` (S3), `queue` (SQS), `vector` (pgvector), `ai` (OpenAI chat/quiz, enricher, Tika, tools).
  - `config`: wiring de beans e configuração (mesmo nome do pacote atual; passa a conter também o registro dos use cases).
- Entidade JPA separada do domínio (`JpaEntity` + mapper).
- Domínio rico, princípio *Tell, Don't Ask*: regras e transições de estado viram métodos das classes de `core.domain` (ex: `source.markReady()`, `quiz.complete(questions)`, `conversation.activate(sourceIds)`); use cases comandam o objeto em vez de ler estado e decidir fora. Sem setters públicos nem getters usados para decisão.
- Código sem comentários inline: comentários removidos; só Javadoc, e apenas quando extremamente necessário (contrato não óbvio, decisão não derivável do código).
- Streaming de chat via `MessageStreamListener` definido no core; `SseEmitter` só no adapter web.
- Teste ArchUnit proíbe `core..` de importar `org.springframework.web`, `jakarta.persistence`, `io.awspring`, `org.springframework.ai`, `org.springframework.data`.
- Migração strangler por capability: Quiz, Notebook/Conversation, Source+Ingestion, SendMessage+SSE; pacotes antigos removidos ao final.
- Contrato REST/SSE (`API.md`) e schema Flyway **inalterados**; frontend e infra não mudam. Testes `*ApiIntegrationTest` passam sem alterar asserts.
- Documentação (`CLAUDE.md`, `ARCHITECTURE.md`) atualizada com a nova estrutura.

Sem **BREAKING** externo. Breaking interno: todos os pacotes/FQCN Java mudam.

## Capabilities

### New Capabilities
- `hexagonal-architecture`: regras estruturais do backend: pacotes `core`/`adapter`/`config`, regra de dependência (adapter → core), ports in/out, um use case por classe, isolamento de framework no core, enforcement via ArchUnit.

### Modified Capabilities
- `jpa-persistence`: mapeamento JPA passa a viver em `adapter/out/persistence` (`*JpaEntity`), acessado só via ports de saída; domínio não carrega anotações JPA. Comportamento de schema, cascatas e `ddl-auto=validate` permanece.

## Impact

- Código: todo `app/backend-api/src/main/java/tech/buildrun/notebooklm/**` (movido/dividido) e `src/test/**` (testes reorganizados espelhando os pacotes; unitários de use case com fakes dos ports).
- `pom.xml`: adicionar `archunit-junit5` (escopo test).
- APIs/DB/infra/frontend: sem mudança.
- Changes OpenSpec em andamento (`add-tool-call-events-sse`, `add-conversation-quiz`, `rag-sources-in-chat-response`, `separate-ecs-task-definition-from-deploy`) tocam os mesmos arquivos; fechar/arquivar antes de aplicar este refactor.
- Risco principal: regressão no streaming SSE/RAG (última etapa) e custo de mappers duplicados (10 entidades).
