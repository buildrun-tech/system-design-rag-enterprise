## ADDED Requirements

### Requirement: Exclusão de notebook
O sistema SHALL permitir apagar um notebook existente via `DELETE /api/v1/notebooks/{notebookId}`, mediante confirmação do usuário.

#### Scenario: Exclusão confirmada
- **WHEN** o usuário aciona a lixeira em um notebook da lista e confirma a exclusão
- **THEN** o sistema envia a requisição de exclusão e remove o notebook da lista exibida

#### Scenario: Exclusão cancelada
- **WHEN** o usuário aciona a lixeira e cancela a confirmação
- **THEN** o sistema não envia requisição e o notebook permanece na lista
