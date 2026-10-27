## MODIFIED Requirements

### Requirement: Processamento assíncrono de sources (Async Request-Reply)
O sistema SHALL processar sources de forma assíncrona via SQS. O consumer SHALL executar extração de texto, chunking, enriquecimento de metadata (keywords e summary), embedding, e persistência em pgvector. O enriquecimento é obrigatório: falha nele SHALL falhar o processamento da source.

#### Scenario: Processamento bem-sucedido de PDF
- **WHEN** o consumer SQS recebe mensagem com `sourceId` de um PDF
- **THEN** baixa o arquivo do S3
- **AND** extrai texto via Apache Tika
- **AND** realiza chunking em blocos de ~512 tokens com overlap de ~50 tokens
- **AND** enriquece cada chunk com `excerpt_keywords` (KeywordMetadataEnricher) e `section_summary` (SummaryMetadataEnricher, apenas resumo do chunk atual)
- **AND** gera embeddings via Spring AI EmbeddingModel para cada chunk, sem incluir `excerpt_keywords` e `section_summary` no texto embedado
- **AND** persiste chunks e vetores na tabela `source_chunks`
- **AND** atualiza `source.status = READY`

#### Scenario: Falha no processamento
- **WHEN** o processamento de uma source falha (ex: PDF corrompido, URL inacessível)
- **THEN** o consumer atualiza `source.status = FAILED` com mensagem de erro
- **AND** a mensagem SQS é enviada para Dead Letter Queue após as retentativas

#### Scenario: Falha no enriquecimento
- **WHEN** a chamada LLM de enriquecimento falha para algum chunk da source
- **THEN** nenhum chunk da source é persistido
- **AND** o consumer atualiza `source.status = FAILED` com mensagem de erro
