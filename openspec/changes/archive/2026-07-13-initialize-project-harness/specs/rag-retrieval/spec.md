## ADDED Requirements

### Requirement: Similarity search filtrado por notebook e sources ativas
O sistema SHALL recuperar chunks semanticamente relevantes para uma query, filtrados ao notebook e às sources selecionadas pelo usuário na conversa.

#### Scenario: Busca com sources ativas selecionadas
- **WHEN** o sistema recebe uma query de chat com lista de `activeSourceIds`
- **THEN** realiza similarity search no pgvector usando cosine distance (`<=>`)
- **AND** o filtro WHERE inclui `source_id IN (activeSourceIds)`
- **AND** retorna os top-K chunks mais relevantes (K configurável, padrão 5)

#### Scenario: Busca sem filtro de sources (todas as sources READY do notebook)
- **WHEN** a conversa foi criada sem seleção explícita de sources
- **THEN** o filtro WHERE inclui todos os `source_id` com `status = READY` do notebook

#### Scenario: Nenhum chunk relevante encontrado
- **WHEN** a similarity search retorna resultados abaixo do threshold de relevância
- **THEN** o sistema prossegue sem contexto RAG (resposta sem grounding)

### Requirement: Montagem de prompt com contexto RAG
O sistema SHALL montar o prompt do LLM incluindo os chunks recuperados como contexto, o histórico de conversa, e a mensagem atual do usuário.

#### Scenario: Prompt com chunks recuperados
- **WHEN** chunks relevantes são recuperados
- **THEN** o prompt inclui seção `Context` com o texto dos chunks formatados
- **AND** inclui as últimas N mensagens do histórico (padrão: últimas 10)
- **AND** inclui a mensagem atual do usuário como última entrada

#### Scenario: Prompt sem chunks (zero context)
- **WHEN** nenhum chunk relevante é encontrado ou não há sources ativas
- **THEN** o prompt é montado apenas com histórico e mensagem atual, sem seção `Context`
