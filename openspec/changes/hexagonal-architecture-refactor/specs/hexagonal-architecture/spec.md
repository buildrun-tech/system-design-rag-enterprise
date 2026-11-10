## ADDED Requirements

### Requirement: Estrutura de pacotes core, adapter e config
O backend SHALL organizar `tech.buildrun.notebooklm` em três raízes: `core` (domain, port.in, port.out, usecase), `adapter` (in, out) e `config` (wiring/configuração Spring). Os pacotes legados `controller`, `service`, `repository`, `entity`, `dto`, `security`, `tool` e `exception` SHALL NOT existir ao final da migração.

#### Scenario: Pacotes legados removidos
- **WHEN** a migração de todas as capabilities termina
- **THEN** nenhuma classe de produção reside nos pacotes legados listados

#### Scenario: Classe nova cai na raiz correta
- **WHEN** uma nova classe de produção é criada
- **THEN** ela reside em `core.*`, `adapter.in.*`, `adapter.out.*` ou `config`

### Requirement: Core isolado de frameworks de I/O
Classes em `core..` SHALL NOT depender de `org.springframework.web`, `jakarta.persistence`, `org.hibernate`, `org.springframework.data`, `io.awspring`, `org.springframework.ai`, `jakarta.servlet` nem `org.springframework.security`. Esta regra SHALL ser verificada por teste ArchUnit executado no build.

#### Scenario: Core importa framework proibido
- **WHEN** uma classe em `core..` importa `jakarta.persistence.Entity`
- **THEN** o teste ArchUnit falha e o build quebra

#### Scenario: Core limpo
- **WHEN** o teste ArchUnit roda sobre o código atual
- **THEN** nenhuma classe de `core..` viola as dependências proibidas

### Requirement: Regra de dependência adapter para core
Classes em `core..` SHALL NOT depender de classes em `adapter..` nem `config`. Adapters SHALL depender do core apenas via `core.port` e `core.domain`. Adapters SHALL NOT depender uns dos outros.

#### Scenario: Core referencia adapter
- **WHEN** uma classe de `core.usecase` importa uma classe de `adapter.out`
- **THEN** o teste ArchUnit falha

#### Scenario: Adapter in acessa adapter out
- **WHEN** um controller em `adapter.in.web` importa um `*RepositoryAdapter`
- **THEN** o teste ArchUnit falha

### Requirement: Um caso de uso por classe
Cada caso de uso SHALL ser uma classe única em `core.usecase`, nomeada `<Verbo><Objeto>UseCase`, que implementa exatamente uma interface de `core.port.in`. Adapters de entrada SHALL invocar casos de uso somente pela interface de `port.in`.

#### Scenario: Criação de notebook
- **WHEN** o controller recebe `POST /notebooks`
- **THEN** ele invoca a interface `CreateNotebookPort` (port.in) implementada por `CreateNotebookUseCase`

#### Scenario: Controller não conhece implementação
- **WHEN** o teste ArchUnit inspeciona `adapter.in..`
- **THEN** nenhuma classe depende de `core.usecase..`

### Requirement: Todo I/O externo passa por port de saída
Acesso a Postgres, S3, SQS, pgvector, OpenAI, Tika e relógio/tools SHALL ocorrer somente em `adapter.out`, atrás de interfaces em `core.port.out`. Casos de uso SHALL receber ports por construtor.

#### Scenario: Upload de source
- **WHEN** `UploadSourceUseCase` executa
- **THEN** persiste via `SourceRepositoryPort`, grava o arquivo via `FileStoragePort` e publica via `IngestionQueuePort`, sem referência a `S3Template` ou `SqsTemplate`

#### Scenario: Use case testável sem Spring
- **WHEN** um teste unitário instancia um use case com fakes dos ports
- **THEN** executa sem contexto Spring nem Testcontainers

### Requirement: Streaming de chat desacoplado de SSE
O core SHALL definir `MessageStreamListener` (`onToken`, `onToolCall`, `onDone`, `onError`) usado por `SendMessageUseCase`. `SseEmitter` SHALL existir apenas em `adapter.in.web`, que implementa o listener.

#### Scenario: Envio de mensagem em streaming
- **WHEN** o cliente envia mensagem e o modelo emite tokens
- **THEN** o use case chama `onToken` no listener e o adapter web escreve eventos SSE com o mesmo formato de `API.md`

#### Scenario: Erro durante o stream
- **WHEN** o `ChatCompletionPort` falha no meio do stream
- **THEN** o listener recebe `onError` e o cliente recebe o evento `{"error":"STREAM_ERROR"}`

### Requirement: Entidades de domínio livres de persistência
Classes em `core.domain` SHALL ser POJOs sem anotações JPA/Hibernate/Jackson. Mapeamento domínio para persistência SHALL ocorrer em mappers de `adapter.out.persistence`.

#### Scenario: Round-trip de mapper
- **WHEN** um objeto de domínio é convertido para `*JpaEntity` e de volta
- **THEN** todos os campos de negócio são preservados

### Requirement: Paginação própria do core
O core SHALL expor `PageQuery` e `PageResult<T>` e SHALL NOT usar `Page` ou `Pageable` do Spring Data em ports ou use cases.

#### Scenario: Listagem paginada de notebooks
- **WHEN** `GET /notebooks?page=0&size=20` é chamado
- **THEN** o controller converte para `PageQuery`, o use case retorna `PageResult`, e a resposta HTTP mantém o formato atual

### Requirement: Comportamento externo preservado
O refactor SHALL NOT alterar contratos REST/SSE de `API.md`, o schema Flyway nem o comportamento observável. Os testes `*ApiIntegrationTest` existentes SHALL passar sem alteração de asserts.

#### Scenario: Suíte de integração após migração de capability
- **WHEN** uma capability é migrada
- **THEN** os `*ApiIntegrationTest` correspondentes passam com os mesmos asserts

### Requirement: Domínio rico seguindo Tell, Don't Ask
Classes de `core.domain` SHALL encapsular estado e regras de negócio em métodos de comportamento (ex: `markProcessing()`, `markReady()`, `markFailed(reason)`, `rename(name)`, `activate(sourceIds)`, `complete(questions)`). Setters públicos SHALL NOT existir. Casos de uso SHALL comandar o objeto de domínio e SHALL NOT ler estado para decidir e mutar fora dele. Invariantes SHALL ser validadas dentro do domínio e violações SHALL lançar exceção de domínio.

#### Scenario: Transição de status de source
- **WHEN** `IngestSourceUseCase` conclui a extração com sucesso
- **THEN** chama `source.markReady()` e não faz `if (source.getStatus() == ...)` seguido de `setStatus`

#### Scenario: Transição inválida
- **WHEN** `markReady()` é chamado numa source com status `FAILED`
- **THEN** o domínio lança exceção de domínio e o estado permanece inalterado

#### Scenario: Sem setters públicos
- **WHEN** o teste ArchUnit inspeciona `core.domain..`
- **THEN** nenhuma classe expõe método público iniciado por `set`

### Requirement: Código sem comentários inline
O código de produção do backend SHALL NOT conter comentários inline (`//` e `/* */`). Documentação SHALL ser Javadoc (`/** */`) e somente quando extremamente necessária: contrato não óbvio de port, decisão não derivável do código ou restrição externa. Comentários existentes SHALL ser removidos; os que carregam informação indispensável SHALL ser convertidos em Javadoc na classe ou método correspondente.

#### Scenario: Comentário inline removido na migração
- **WHEN** uma classe é migrada para a nova estrutura
- **THEN** a versão migrada não contém comentários `//` nem `/* */`

#### Scenario: Informação indispensável preservada
- **WHEN** um comentário antigo explicava uma restrição não óbvia (ex: semântica de visibility do SQS)
- **THEN** a informação aparece como Javadoc no método ou classe, e não como comentário inline

#### Scenario: Verificação automatizada
- **WHEN** o build roda a verificação de comentários
- **THEN** falha se encontrar comentário inline em `src/main/java`

