## REMOVED Requirements

### Requirement: Entidade SourceChunk mapeada via JPA com vetor de embedding
**Reason**: Chunk + embedding deixam de ser modelados como entidade JPA custom (`tb_source_chunks` + `VectorConverter`). Passam a viver na tabela gerenciada pelo `PgVectorStore` do Spring AI (já dependência instalada no pom), evitando reimplementar schema/index vetorial que a lib já resolve.
**Migration**: Migration Flyway remove `tb_source_chunks` (nunca teve dado — feature nunca foi implementada) e cria a tabela no formato esperado pelo `PgVectorStore` (`content`, `metadata` JSONB com `source_id`/`chunk_index`/`embedding_model`, `embedding vector`). Nenhum dado a migrar.

#### Scenario: Persistência de embedding como vetor pgvector
- **WHEN** um `SourceChunk` é salvo com `embedding` como `float[1536]`
- **THEN** o valor é persistido na coluna `vector(1536)` no formato nativo do pgvector
- **AND** ao ser recuperado, o `float[]` retornado é equivalente ao original (round-trip)

#### Scenario: Deleção de Source remove SourceChunks em cascata
- **WHEN** uma `Source` com chunks associados é deletada
- **THEN** o banco remove automaticamente todos os chunks associados
