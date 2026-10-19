## ADDED Requirements

### Requirement: Expor sources dos chunks recuperados
O sistema SHALL disponibilizar, para cada resposta gerada com contexto RAG, a lista de sources distintas, com os chunks (índice e trecho) efetivamente recuperados e enviados ao LLM.

#### Scenario: Chunks de múltiplas sources
- **WHEN** a similarity search retorna chunks de sources A, B e A
- **THEN** o sistema expõe as sources `[A, B]`, sem duplicar, na ordem de relevância da primeira ocorrência
- **AND** a source A agrupa seus dois chunks

#### Scenario: Source removida durante o stream
- **WHEN** uma source recuperada deixa de existir antes da resolução do nome
- **THEN** o sistema omite essa source da lista

#### Scenario: Chunk sem source_id
- **WHEN** um documento recuperado não possui `source_id` no metadata
- **THEN** o sistema ignora esse documento na lista de sources
