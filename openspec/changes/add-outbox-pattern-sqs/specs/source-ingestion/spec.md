## MODIFIED Requirements

### Requirement: Upload de arquivo para notebook
O sistema SHALL aceitar upload de arquivos nos formatos PDF, DOCX, e Markdown para um notebook. O arquivo é armazenado no S3 e o processamento acontece de forma assíncrona. O evento de ingestão SHALL ser registrado no outbox na mesma transação que persiste a source, e publicado no SQS pelo relay.

#### Scenario: Upload de PDF bem-sucedido
- **WHEN** um usuário autenticado envia `POST /notebooks/{id}/sources` com arquivo PDF (multipart/form-data)
- **THEN** o sistema persiste metadados da source com `status: PENDING`
- **AND** faz upload do arquivo para o S3 com chave `{userId}/{notebookId}/{sourceId}/{filename}`
- **AND** registra evento de ingestão com `sourceId` no outbox na mesma transação
- **AND** retorna `202 Accepted` com `{"sourceId": "...", "status": "PENDING"}`
- **AND** o relay publica posteriormente a mensagem na fila SQS

#### Scenario: Upload de DOCX bem-sucedido
- **WHEN** um usuário envia `POST /notebooks/{id}/sources` com arquivo DOCX
- **THEN** o mesmo fluxo de upload e registro no outbox ocorre

#### Scenario: Upload de Markdown bem-sucedido
- **WHEN** um usuário envia `POST /notebooks/{id}/sources` com arquivo `.md`
- **THEN** o mesmo fluxo de upload e registro no outbox ocorre

#### Scenario: Formato de arquivo não suportado
- **WHEN** um usuário envia um arquivo com extensão não suportada (ex: `.xlsx`)
- **THEN** o sistema retorna `415 Unsupported Media Type`

#### Scenario: SQS indisponível no upload
- **WHEN** o SQS está indisponível durante o upload
- **THEN** o upload ainda retorna `202 Accepted`
- **AND** o evento permanece no outbox até o relay conseguir publicar
