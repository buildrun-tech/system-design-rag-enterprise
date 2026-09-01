## ADDED Requirements

### Requirement: Métodos derived query sem underscore de path manual
O sistema SHALL nomear métodos derived query do Spring Data JPA em camelCase puro, sem a sintaxe de manual property path (underscore, ex. `Notebook_Id`), sempre que a resolução automática de path aninhado do Spring Data não for ambígua (nenhuma propriedade plana da entidade colide com o path aninhado).

#### Scenario: Busca de sources por notebook usa nome sem underscore
- **WHEN** `SourceRepository.findByNotebookIdAndStatus(notebookId, READY)` é chamado
- **THEN** o Spring Data resolve automaticamente para o path aninhado `notebook.id` e retorna as sources daquele notebook com `status = READY`

#### Scenario: Busca de conversa por dono via notebook usa nome sem underscore
- **WHEN** `ConversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)` é chamado
- **THEN** o Spring Data resolve automaticamente para o path aninhado `notebook.owner.id` e retorna a `Conversation` correspondente se o dono confere

#### Scenario: Ambiguidade de path aninhado falha no startup, não em runtime
- **WHEN** um nome de método derived query sem underscore é ambíguo (existe tanto uma propriedade plana quanto um path aninhado com o mesmo nome resolvido)
- **THEN** o Spring Boot falha ao subir o `ApplicationContext` com `PropertyReferenceException`, antes de aceitar tráfego
