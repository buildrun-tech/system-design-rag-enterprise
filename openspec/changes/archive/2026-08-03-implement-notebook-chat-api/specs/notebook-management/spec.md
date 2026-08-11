## MODIFIED Requirements

### Requirement: Listar notebooks do usuário
O sistema SHALL retornar, de forma paginada, apenas os notebooks pertencentes ao usuário autenticado, ordenados por `created_at` decrescente por padrão.

#### Scenario: Listagem paginada de notebooks do usuário
- **WHEN** um usuário autenticado envia `GET /notebooks?page=0&size=20`
- **THEN** o sistema retorna `200 OK` com uma página contendo `content` (array de notebooks do usuário), `totalElements`, `totalPages`, `number`, e `size`
- **AND** notebooks de outros usuários NÃO são incluídos na resposta

#### Scenario: Listagem sem parâmetros de paginação
- **WHEN** um usuário autenticado envia `GET /notebooks` sem `page`/`size`
- **THEN** o sistema aplica página padrão (`page=0`, `size=20`, `sort=createdAt,desc`)

#### Scenario: Usuário sem notebooks
- **WHEN** um usuário autenticado sem notebooks envia `GET /notebooks`
- **THEN** o sistema retorna `200 OK` com `content: []` e `totalElements: 0`
