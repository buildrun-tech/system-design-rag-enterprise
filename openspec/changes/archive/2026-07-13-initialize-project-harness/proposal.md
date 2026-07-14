## Why

O projeto system-design-rag-enterprise não possui documentação de arquitetura, contrato de API, schema de banco de dados, nem configuração do harness de desenvolvimento (CLAUDE.md, openspec/config.yaml). Sem esses artefatos, qualquer implementação começa sem fundação, levando a inconsistências de design e retrabalho.

## What Changes

- Criação do `CLAUDE.md` na raiz com guia completo do codebase (stack, convenções, estrutura de módulos, comandos de desenvolvimento)
- Atualização do `openspec/config.yaml` com contexto do projeto para orientar geração de artefatos futuros
- Criação do `ARCHITECTURE.md` com diagrama AWS, ERD, DDL das tabelas, e contratos REST completos

## Capabilities

### New Capabilities

- `project-harness`: Configuração do ambiente de desenvolvimento — CLAUDE.md, openspec/config.yaml, e ARCHITECTURE.md com toda a documentação estrutural do sistema
- `auth`: Autenticação e autorização via AWS Cognito com provedores Google e GitHub; emissão e validação de JWT
- `notebook-management`: CRUD de notebooks — unidade lógica de agrupamento de sources pertencentes a um usuário
- `source-ingestion`: Upload de fontes (PDF, DOCX, Markdown, URL) para S3 com processamento assíncrono via SQS (async request-reply): extração de texto → chunking → embedding → pgvector
- `chat`: Interface de conversa com RAG; usuário seleciona quais sources estão ativas por sessão; histórico de conversa persistido por notebook; respostas streamadas via SSE
- `rag-retrieval`: Recuperação semântica de chunks via pgvector (similarity search) filtrado por notebook e sources selecionadas

### Modified Capabilities

## Impact

- Novos arquivos: `CLAUDE.md`, `ARCHITECTURE.md`, `openspec/config.yaml` (atualizado)
- Nenhum código de produção alterado nesta mudança — é estritamente documentação e configuração do harness
- Todas as capabilities listadas acima terão specs criadas como base para implementação futura
