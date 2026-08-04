## Purpose

Fornecer a tela de listagem e criação de notebooks do usuário autenticado, permitindo visualizar os notebooks existentes, criar novos e navegar para o workspace de cada um.

## Requirements

### Requirement: Listagem de notebooks
O sistema SHALL exibir, na Tela 2, todos os notebooks do usuário autenticado, obtidos de `GET /api/v1/notebooks`.

#### Scenario: Usuário com notebooks existentes
- **WHEN** o usuário autenticado acessa a tela de notebooks
- **THEN** o sistema lista cada notebook com nome e ação "abrir"

#### Scenario: Usuário sem notebooks
- **WHEN** o usuário autenticado acessa a tela de notebooks e não possui nenhum
- **THEN** o sistema exibe a lista vazia sem erro

### Requirement: Criação de notebook
O sistema SHALL permitir criar um notebook informando nome (obrigatório) via `POST /api/v1/notebooks`.

#### Scenario: Criação bem-sucedida
- **WHEN** o usuário aciona "criar" e informa um nome válido
- **THEN** o sistema envia a requisição de criação e adiciona o novo notebook à lista exibida

#### Scenario: Nome inválido
- **WHEN** o usuário tenta criar um notebook com nome vazio
- **THEN** a API retorna `400 VALIDATION_ERROR` e o sistema exibe a mensagem de erro sem criar o notebook

### Requirement: Navegação para o workspace do notebook
O sistema SHALL navegar para a Tela 3 (workspace) do notebook selecionado ao acionar "abrir".

#### Scenario: Abrir notebook
- **WHEN** o usuário clica em "abrir" em um notebook da lista
- **THEN** o sistema navega para a rota do workspace daquele notebook (`/notebooks/{id}`)
