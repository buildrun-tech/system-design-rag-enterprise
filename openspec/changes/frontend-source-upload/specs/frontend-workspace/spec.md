## ADDED Requirements

### Requirement: Upload de arquivo como source
O sistema SHALL permitir ao usuário selecionar um arquivo via file picker nativo e enviá-lo como source do notebook via `POST /api/v1/notebooks/{notebookId}/sources` (multipart), adicionando a source retornada (status `PENDING`) à lista imediatamente.

#### Scenario: Upload bem-sucedido
- **WHEN** o usuário seleciona um arquivo no input de upload
- **THEN** o sistema envia o arquivo via multipart e insere a source retornada, em status `PENDING`, no topo da lista de sources

#### Scenario: Upload rejeitado pelo backend
- **WHEN** o backend responde com erro (`400`, `404`, `415`) ao tentar criar a source
- **THEN** o sistema exibe uma mensagem de erro inline e não adiciona nenhuma source à lista

### Requirement: Atualização de status de source via polling
O sistema SHALL consultar `GET /api/v1/notebooks/{notebookId}/sources` a cada 3 segundos enquanto houver ao menos uma source em status `PENDING` ou `PROCESSING`, atualizando o status exibido, e SHALL parar de consultar quando nenhuma source estiver mais `PENDING`/`PROCESSING`.

#### Scenario: Source concluindo processamento
- **WHEN** uma source em `PROCESSING` passa a `READY` no backend
- **THEN** o próximo ciclo de polling atualiza o badge de status da source para `READY` sem exigir reload da página

#### Scenario: Nenhuma source pendente
- **WHEN** todas as sources do notebook estão em `READY` ou `FAILED`
- **THEN** o sistema não faz requisições periódicas de status

### Requirement: Remoção de source
O sistema SHALL permitir deletar uma source via `DELETE /api/v1/notebooks/{notebookId}/sources/{sourceId}`, removendo-a da lista local de forma otimista e revertendo a remoção caso a chamada falhe.

#### Scenario: Delete bem-sucedido
- **WHEN** o usuário aciona o botão de deletar em uma source
- **THEN** o sistema remove a source da lista imediatamente e a chamada `DELETE` é feita em paralelo

#### Scenario: Delete falha no backend
- **WHEN** a chamada `DELETE` retorna erro
- **THEN** o sistema reinsere a source na mesma posição da lista e exibe mensagem de erro inline
