## ADDED Requirements

### Requirement: Criar notebook
O sistema SHALL permitir que um usuário autenticado crie um notebook com nome e descrição opcional. O notebook é automaticamente associado ao usuário criador.

#### Scenario: Criação bem-sucedida de notebook
- **WHEN** um usuário autenticado envia `POST /notebooks` com `{"name": "Meu Notebook"}`
- **THEN** o sistema cria um notebook com `owner_id` igual ao `sub` do JWT
- **AND** retorna `201 Created` com o notebook criado incluindo `id`, `name`, `created_at`

#### Scenario: Nome de notebook ausente
- **WHEN** um usuário envia `POST /notebooks` sem o campo `name`
- **THEN** o sistema retorna `400 Bad Request` com `{"error": "name is required"}`

### Requirement: Listar notebooks do usuário
O sistema SHALL retornar apenas os notebooks pertencentes ao usuário autenticado, ordenados por `created_at` decrescente.

#### Scenario: Listagem de notebooks do usuário
- **WHEN** um usuário autenticado envia `GET /notebooks`
- **THEN** o sistema retorna `200 OK` com array de notebooks do usuário
- **AND** notebooks de outros usuários NÃO são incluídos na resposta

#### Scenario: Usuário sem notebooks
- **WHEN** um usuário autenticado sem notebooks envia `GET /notebooks`
- **THEN** o sistema retorna `200 OK` com array vazio `[]`

### Requirement: Buscar notebook por ID
O sistema SHALL retornar os detalhes de um notebook específico, incluindo suas sources associadas.

#### Scenario: Notebook encontrado e pertence ao usuário
- **WHEN** um usuário autenticado envia `GET /notebooks/{id}` para um notebook seu
- **THEN** o sistema retorna `200 OK` com detalhes do notebook e lista de sources

#### Scenario: Notebook não pertence ao usuário
- **WHEN** um usuário tenta acessar `GET /notebooks/{id}` de um notebook de outro usuário
- **THEN** o sistema retorna `404 Not Found` (não deve revelar a existência do recurso)

### Requirement: Atualizar notebook
O sistema SHALL permitir atualizar nome e descrição de um notebook existente do usuário.

#### Scenario: Atualização bem-sucedida
- **WHEN** o dono do notebook envia `PATCH /notebooks/{id}` com campos a atualizar
- **THEN** o sistema atualiza os campos e retorna `200 OK` com notebook atualizado

### Requirement: Deletar notebook
O sistema SHALL permitir que o dono delete um notebook. A deleção SHALL remover em cascata todas as sources, chunks, e histórico de conversa associados.

#### Scenario: Deleção bem-sucedida
- **WHEN** o dono do notebook envia `DELETE /notebooks/{id}`
- **THEN** o sistema remove o notebook e todos os dados associados em cascata
- **AND** retorna `204 No Content`
