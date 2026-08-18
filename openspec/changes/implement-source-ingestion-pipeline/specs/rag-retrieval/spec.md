## MODIFIED Requirements

### Requirement: Similarity search filtrado por notebook e sources ativas
O sistema SHALL recuperar chunks semanticamente relevantes para uma query, filtrados às sources selecionadas pelo usuário na conversa, usando o `VectorStore` do Spring AI (`PgVectorStore.similaritySearch()`) em vez de SQL manual.

#### Scenario: Busca com sources ativas selecionadas
- **WHEN** o sistema recebe uma query de chat com lista de `activeSourceIds`
- **THEN** chama `VectorStore.similaritySearch(SearchRequest)` com filtro de metadata equivalente a `source_id IN (activeSourceIds)`
- **AND** retorna os top-K `Document`s mais relevantes (K configurável, padrão 5)

#### Scenario: Busca sem filtro de sources (todas as sources READY do notebook)
- **WHEN** a conversa foi criada sem seleção explícita de sources
- **THEN** o filtro de metadata do `SearchRequest` inclui todos os `source_id` com `status = READY` do notebook

#### Scenario: Nenhum chunk relevante encontrado
- **WHEN** a similarity search retorna resultados abaixo do threshold de relevância (`SearchRequest.similarityThreshold`)
- **THEN** o sistema prossegue sem contexto RAG (resposta sem grounding)
