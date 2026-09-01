# system-design-rag-enterprise

NotebookLM empresarial simplificado — plataforma RAG onde usuários organizam documentos em notebooks e conversam com eles via IA.

## Documentação do Projeto

```
/
├── CLAUDE.md          ← este arquivo: visão geral, stack, estrutura
├── ARCHITECTURE.md    ← infraestrutura AWS, serviços e fluxos de dados
├── DOMAIN.md          ← entidades, regras de negócio, ERD, DDL
├── API.md             ← contratos REST (endpoints, request/response, SSE)
│
├── openspec/          ← design docs (proposals, specs, tasks por change)
│   ├── config.yaml
│   ├── specs/         ← specs por capability
│   └── changes/       ← histórico de changes
```

## Rules
- Always invoke the agent skill `/caveman full`, `/ponytail full` and `/java-springboot` before starting any task


## Tools Tokenomics

Duas ferramentas, critério de escolha: exploração/entendimento de código → CodeGraph; operação pontual (arquivo, teste, git, build...) → RTK. Exige `.codegraph/` no repo pro CodeGraph; se não existir, pula direto pra RTK.

### CodeGraph — comandos
| comando | uso |
|---|---|
| `codegraph_explore "<símbolos/pergunta>"` (MCP) ou `codegraph explore "..."` | pergunta aberta, "como X funciona", ler símbolo antes de editar — 1a escolha padrão |
| `codegraph node <symbol>` | source + trail de caller/callee de 1 símbolo específico |
| `codegraph callers <symbol>` | quem chama esse símbolo |
| `codegraph callees <symbol>` | o que esse símbolo chama |
| `codegraph impact <symbol>` | blast radius: o que quebra se eu mudar isso |
| `codegraph affected [files...]` | quais testes cobrem os arquivos alterados |
| `codegraph query <search>` | busca de símbolo por nome/termo |
| `codegraph status` / `codegraph files` | saúde do índice / estrutura do projeto |

### RTK — comandos
Operação pontual: sempre prefixar com `rtk`, inclusive em cadeias `&&`. Referência completa: `~/.claude/RTK.md` (global, já carregado).

| comando | uso |
|---|---|
| `rtk mvn test` / `rtk mvn clean install` | backend Java — testes / build (backend-api) |
| `rtk npm run dev` / `rtk npm run build` | frontend — dev server / build |
| `rtk docker compose up` / `rtk docker ps` / `rtk docker logs <c>` | subir/inspecionar containers locais (`app/local/docker-compose.yml`) |
| `rtk git status` / `rtk git diff` / `rtk git commit` | git |
| `rtk read <file>` | ler arquivo específico |
| `rtk grep <pattern>` / `rtk find <pattern>` | busca pontual de texto/arquivo |