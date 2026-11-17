## ADDED Requirements

### Requirement: Log padronizado por chamada externa
Cada chamada a sistema externo feita pelos adapters `out` (OpenAI/OpenRouter, S3, SQS publish, pgvector) SHALL emitir um evento com `integration`, `operation`, `outcome` (`ok` ou `error`) e `durationMs`. Em `outcome=error` o nível SHALL ser `ERROR` e a causa SHALL ir em `stack_trace`. A exceção original SHALL ser relançada.

#### Scenario: Chamada bem-sucedida
- **WHEN** o adapter de chat chama o modelo com sucesso
- **THEN** um evento tem `integration=openrouter`, `operation`, `outcome=ok` e `durationMs`

#### Scenario: Chamada falha
- **WHEN** o download do S3 lança exceção
- **THEN** um evento `ERROR` tem `integration=s3`, `outcome=error`, `durationMs` e `stack_trace`, e a exceção chega ao chamador

#### Scenario: Core sem acoplamento
- **WHEN** o ArchUnit roda
- **THEN** `core` não depende do helper de log de chamadas externas

### Requirement: Duração do enrichment de ingestão
A ingestão SHALL logar `Source enrichment` com `sourceId`, `chunks` e `durationMs` do enrichment.

#### Scenario: Ingestão concluída
- **WHEN** uma source de 8 chunks termina o enrichment
- **THEN** existe um evento `Source enrichment` com `chunks=8` e `durationMs`
