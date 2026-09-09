## MODIFIED Requirements

### Requirement: Histórico de conversa persistido
O sistema SHALL persistir todas as mensagens (usuário e assistente) no banco de dados, vinculadas à conversa, e centralizar a recuperação desse histórico num componente único reusado tanto pra montagem do prompt quanto pro pre-retrieval query rewriting.

#### Scenario: Persistência de mensagem do usuário
- **WHEN** o usuário envia uma mensagem
- **THEN** a mensagem é persistida em `conversation_messages` com `role: user`

#### Scenario: Persistência de resposta do assistente
- **WHEN** o LLM finaliza o streaming da resposta
- **THEN** a resposta completa é persistida em `conversation_messages` com `role: assistant`

#### Scenario: Recuperação do histórico para contexto
- **WHEN** o sistema monta o prompt para o LLM
- **THEN** as últimas N mensagens da conversa são incluídas no prompt como histórico
- **AND** essa recuperação é feita por um componente dedicado de histórico de conversa, sem lógica de acesso a dados duplicada em outros pontos do fluxo de chat

#### Scenario: Histórico reusado pelo query rewriting
- **WHEN** o sistema reescreve a query do usuário antes do retrieval
- **THEN** o mesmo componente de histórico usado pra montar o prompt fornece o histórico consumido pelo `RewriteQueryTransformer`
