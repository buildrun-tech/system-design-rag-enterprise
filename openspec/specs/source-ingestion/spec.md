## Purpose

Permitir upload de fontes (PDF, DOCX, Markdown, URL) para um notebook, armazenadas no S3, com processamento assíncrono via SQS (async request-reply) que executa extração de texto, chunking, geração de embeddings e persistência em pgvector, mantendo o status de processamento visível ao usuário do início ao fim do ciclo.

## Requirements

### Requirement: Upload de arquivo para notebook
O sistema SHALL aceitar upload de arquivos nos formatos PDF, DOCX, e Markdown para um notebook. O arquivo é armazenado no S3 e o processamento acontece de forma assíncrona.

#### Scenario: Upload de PDF bem-sucedido
- **WHEN** um usuário autenticado envia `POST /notebooks/{id}/sources` com arquivo PDF (multipart/form-data)
- **THEN** o sistema persiste metadados da source com `status: PENDING`
- **AND** faz upload do arquivo para o S3 com chave `{userId}/{notebookId}/{sourceId}/{filename}`
- **AND** publica mensagem na fila SQS com `sourceId`
- **AND** retorna `202 Accepted` com `{"sourceId": "...", "status": "PENDING"}`

#### Scenario: Upload de DOCX bem-sucedido
- **WHEN** um usuário envia `POST /notebooks/{id}/sources` com arquivo DOCX
- **THEN** o mesmo fluxo de upload e enfileiramento ocorre

#### Scenario: Upload de Markdown bem-sucedido
- **WHEN** um usuário envia `POST /notebooks/{id}/sources` com arquivo `.md`
- **THEN** o mesmo fluxo de upload e enfileiramento ocorre

#### Scenario: Formato de arquivo não suportado
- **WHEN** um usuário envia um arquivo com extensão não suportada (ex: `.xlsx`)
- **THEN** o sistema retorna `415 Unsupported Media Type`

### Requirement: Adição de URL como source
O sistema SHALL aceitar uma URL de página web como source. O conteúdo da página é fetched e tratado como texto.

#### Scenario: Adição de URL válida
- **WHEN** um usuário envia `POST /notebooks/{id}/sources` com `{"type": "url", "url": "https://..."}`
- **THEN** o sistema cria um registro de source com `type: URL` e `status: PENDING`
- **AND** publica mensagem na fila SQS para processamento assíncrono
- **AND** retorna `202 Accepted` com `{"sourceId": "...", "status": "PENDING"}`

### Requirement: Processamento assíncrono de sources (Async Request-Reply)
O sistema SHALL processar sources de forma assíncrona via SQS. O consumer SHALL executar extração de texto, chunking, embedding, e persistência em pgvector.

#### Scenario: Processamento bem-sucedido de PDF
- **WHEN** o consumer SQS recebe mensagem com `sourceId` de um PDF
- **THEN** baixa o arquivo do S3
- **AND** extrai texto via Apache Tika
- **AND** realiza chunking em blocos de ~512 tokens com overlap de ~50 tokens
- **AND** gera embeddings via Spring AI EmbeddingModel para cada chunk
- **AND** persiste chunks e vetores na tabela `source_chunks`
- **AND** atualiza `source.status = READY`

#### Scenario: Falha no processamento
- **WHEN** o processamento de uma source falha (ex: PDF corrompido, URL inacessível)
- **THEN** o consumer atualiza `source.status = FAILED` com mensagem de erro
- **AND** a mensagem SQS é enviada para Dead Letter Queue após as retentativas

### Requirement: Consulta de status de processamento
O sistema SHALL permitir consultar o status de processamento de uma source.

#### Scenario: Consulta de status
- **WHEN** um usuário envia `GET /notebooks/{notebookId}/sources/{sourceId}`
- **THEN** o sistema retorna `200 OK` com `{"sourceId": "...", "status": "PENDING|PROCESSING|READY|FAILED", "errorMessage": null|"..."}`

### Requirement: Listar sources de um notebook
O sistema SHALL retornar todas as sources de um notebook com seus status.

#### Scenario: Listagem de sources
- **WHEN** um usuário envia `GET /notebooks/{notebookId}/sources`
- **THEN** o sistema retorna `200 OK` com array de sources e seus status

### Requirement: Deletar source
O sistema SHALL permitir deletar uma source, removendo o arquivo do S3 e todos os chunks associados do pgvector.

#### Scenario: Deleção de source
- **WHEN** o dono do notebook envia `DELETE /notebooks/{notebookId}/sources/{sourceId}`
- **THEN** o sistema remove o registro da source, os chunks do pgvector, e o arquivo do S3
- **AND** retorna `204 No Content`
