## Purpose

Permitir que o backend-api rode localmente seguindo 12-factor config (todo valor de datasource, AWS e LLM externalizado via variável de ambiente, sem hardcode), com uma stack Docker Compose local (Postgres+pgvector e emulador Floci de S3/SQS/Cognito) e documentação das variáveis necessárias, preparando o terreno para deploy futuro em cloud (ECS/RDS) e para specs futuras de integração com serviços AWS.

## Requirements

### Requirement: Configuração externalizada via variável de ambiente
O backend-api SHALL ler todo valor de configuração de datasource, AWS e provider LLM a partir de variável de ambiente, com valor padrão de desenvolvimento quando aplicável, sem nenhum valor sensível ou específico de ambiente hardcoded em `application.yml`.

#### Scenario: Subir aplicação sem nenhuma env var setada
- **WHEN** o backend-api é iniciado sem nenhuma variável de ambiente customizada
- **THEN** a aplicação usa os valores padrão de desenvolvimento (ex: datasource apontando para `localhost:5432`) e sobe com sucesso

#### Scenario: Sobrescrever configuração via variável de ambiente
- **WHEN** uma variável como `SPRING_DATASOURCE_URL` é definida no ambiente antes de subir a aplicação
- **THEN** a aplicação usa o valor da variável de ambiente em vez do default, sem exigir alteração de código ou rebuild

### Requirement: Stack Postgres+pgvector local via Docker Compose
O repositório SHALL fornecer um `docker-compose.yml` que sobe um container Postgres com a extensão pgvector habilitada, usando a versão mais recente disponível da imagem `pgvector/pgvector`.

#### Scenario: Subir stack local pela primeira vez
- **WHEN** o desenvolvedor executa `docker compose up` pela primeira vez
- **THEN** o container Postgres sobe com a extensão `vector` disponível e o schema do domínio (tabelas `users`, `notebooks`, `sources`, `source_chunks`, `conversations`, `conversation_active_sources`, `conversation_messages`) já criado via script de bootstrap

#### Scenario: Healthcheck do container Postgres
- **WHEN** o container Postgres está inicializando
- **THEN** o Docker Compose expõe um healthcheck que só reporta o serviço como saudável depois que o Postgres aceita conexões

### Requirement: Emulador de serviços AWS local via Floci
O repositório SHALL fornecer, no mesmo `docker-compose.yml`, um serviço Floci (`floci/floci:latest`) emulando S3, SQS e Cognito localmente, disponível em `http://localhost:4566`, para que a próxima spec (integração de código com esses serviços) já encontre o emulador pronto.

#### Scenario: Subir o emulador junto com a stack local
- **WHEN** o desenvolvedor executa `docker compose up`
- **THEN** o serviço Floci sobe e expõe os endpoints emulados de S3, SQS e Cognito na porta `4566`

#### Scenario: Nenhuma integração de código ainda
- **WHEN** o backend-api é iniciado nesta mudança
- **THEN** nenhum client AWS SDK real é instanciado apontando para o Floci — essa integração é adicionada em uma spec futura (`source-ingestion`/`auth`); esta mudança só garante que o serviço está disponível

### Requirement: Documentação das variáveis de ambiente necessárias
O repositório SHALL fornecer um arquivo `.env.example` listando todas as variáveis de ambiente consumidas pelo backend-api, sem valores reais ou sensíveis.

#### Scenario: Novo desenvolvedor configura o ambiente local
- **WHEN** um desenvolvedor clona o repositório pela primeira vez
- **THEN** ele encontra em `.env.example` a lista completa de variáveis necessárias (`AWS_REGION`, `AWS_ENDPOINT_URL`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `S3_BUCKET_NAME`, `SQS_INGESTION_QUEUE_URL`, `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) sem precisar ler o código-fonte — incluindo `AWS_ENDPOINT_URL=http://localhost:4566` apontando pro Floci em desenvolvimento
