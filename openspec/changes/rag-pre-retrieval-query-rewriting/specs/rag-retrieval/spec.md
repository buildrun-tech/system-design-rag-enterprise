## MODIFIED Requirements

### Requirement: Similarity search filtrado por notebook e sources ativas
O sistema SHALL recuperar chunks semanticamente relevantes para uma query reescrita (pre-retrieval query rewriting), filtrados ao notebook e às sources selecionadas pelo usuário na conversa.

#### Scenario: Query reescrita antes da busca
- **WHEN** o sistema recebe uma mensagem de usuário numa conversa com histórico
- **THEN** a mensagem passa por `CompressionQueryTransformer` (resolve referências ao histórico numa query standalone) e em seguida por `RewriteQueryTransformer` (otimiza a query pro vector store), antes de qualquer busca

#### Scenario: Busca com sources ativas selecionadas
- **WHEN** o sistema recebe uma query (já reescrita) de chat com lista de `activeSourceIds`
- **THEN** realiza similarity search no pgvector usando cosine distance (`<=>`)
- **AND** o filtro WHERE inclui `source_id IN (activeSourceIds)`
- **AND** retorna os top-K chunks mais relevantes (K configurável, padrão 5)

#### Scenario: Busca sem filtro de sources (todas as sources READY do notebook)
- **WHEN** a conversa foi criada sem seleção explícita de sources
- **THEN** o filtro WHERE inclui todos os `source_id` com `status = READY` do notebook

#### Scenario: Nenhum chunk relevante encontrado
- **WHEN** a similarity search retorna resultados abaixo do threshold de relevância
- **THEN** o sistema prossegue sem contexto RAG (resposta sem grounding)

#### Scenario: Query sem histórico suficiente pra reescrever
- **WHEN** a conversa não tem histórico anterior (primeira mensagem)
- **THEN** o `CompressionQueryTransformer` recebe histórico vazio e devolve a query original, que segue pro `RewriteQueryTransformer` normalmente
