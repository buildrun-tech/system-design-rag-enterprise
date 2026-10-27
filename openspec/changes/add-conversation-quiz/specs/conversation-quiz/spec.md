## ADDED Requirements

### Requirement: Criação assíncrona de quiz
O sistema SHALL expor `POST /api/v1/conversations/{id}/quizzes` que cria um job de geração de quiz e responde imediatamente `202 Accepted` com `quizId` e `status: PENDING`, sem aguardar a geração. Cada chamada SHALL criar um novo job (quiz regenerável a cada clique). O quiz SHALL ser baseado somente nos chunks das sources ativas da conversation (ou, se nenhuma estiver marcada, em todas as sources `READY` do notebook).

#### Scenario: Job criado
- **WHEN** um usuário autenticado dono da conversation envia `POST /api/v1/conversations/{id}/quizzes`
- **THEN** o sistema persiste o job com `status: PENDING`
- **AND** retorna `202 Accepted` com `{"quizId": "...", "status": "PENDING"}`
- **AND** inicia a geração em segundo plano

#### Scenario: Regeneração a cada clique
- **WHEN** o usuário envia o `POST` duas vezes seguidas
- **THEN** dois jobs distintos são criados, cada um com seu `quizId`

#### Scenario: Conversation de outro usuário
- **WHEN** o usuário envia o `POST` para conversation que não é sua
- **THEN** o sistema retorna `404 Not Found`

### Requirement: Consulta do job de quiz
O sistema SHALL expor `GET /api/v1/conversations/{id}/quizzes/{quizId}` retornando `quizId`, `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`) e, quando `COMPLETED`, `questions` com exatamente 5 itens, cada um com `question`, `options` (4 strings), `correctIndex` (0 a 3) e `explanation`. Quando `FAILED`, SHALL retornar `errorMessage`. Um job em `PENDING` ou `PROCESSING` sem atualização há mais de 10 minutos SHALL ser retornado como `FAILED`.

#### Scenario: Job em andamento
- **WHEN** o job está sendo processado
- **THEN** o `GET` retorna `200 OK` com `status: PROCESSING` e sem `questions`

#### Scenario: Job concluído
- **WHEN** o job terminou com sucesso
- **THEN** o `GET` retorna `status: COMPLETED` com 5 perguntas válidas

#### Scenario: Job falhou
- **WHEN** a geração falhou
- **THEN** o `GET` retorna `status: FAILED` com `errorMessage`

#### Scenario: Job órfão
- **WHEN** o job está `PROCESSING` com `updated_at` há mais de 10 minutos
- **THEN** o `GET` retorna `status: FAILED`

#### Scenario: Job inexistente ou de outra conversation
- **WHEN** o `quizId` não existe, ou pertence a outra conversation, ou a conversation não é do usuário
- **THEN** o sistema retorna `404 Not Found`

### Requirement: Seleção de tópicos por frequência de keywords
O worker SHALL escolher os tópicos das perguntas agregando a contagem de `excerpt_keywords` dos chunks das sources consideradas (normalizadas com lowercase e trim), tomando as keywords mais frequentes. Para cada tópico, o worker SHALL recuperar por similaridade chunks restritos às mesmas sources e usá-los como contexto de geração.

#### Scenario: Tópicos mais frequentes direcionam as perguntas
- **WHEN** o quiz é gerado para sources cujas keywords mais frequentes são A, B e C
- **THEN** o contexto enviado ao LLM é composto por chunks recuperados para esses tópicos
- **AND** todos os chunks pertencem às sources consideradas

#### Scenario: Sem conteúdo
- **WHEN** as sources consideradas não possuem nenhum chunk
- **THEN** o job termina em `FAILED` com mensagem indicando ausência de conteúdo

### Requirement: Backfill de metadata sob demanda
Antes de agregar keywords, o worker SHALL identificar chunks das sources consideradas sem `excerpt_keywords`, enriquecê-los com o mesmo pipeline da ingestão e atualizar `metadata` em `vector_store` sem alterar o embedding. Falha no backfill SHALL terminar o job em `FAILED`.

#### Scenario: Chunks legados são enriquecidos
- **WHEN** o job roda e parte dos chunks das sources não possui `excerpt_keywords`
- **THEN** esses chunks são enriquecidos e persistidos com `excerpt_keywords` e `section_summary`
- **AND** a agregação de keywords considera os chunks recém-enriquecidos

#### Scenario: Nada a enriquecer
- **WHEN** todos os chunks já possuem `excerpt_keywords`
- **THEN** nenhuma chamada de enriquecimento é feita

#### Scenario: Falha no backfill
- **WHEN** a chamada LLM de enriquecimento falha durante o backfill
- **THEN** o job termina em `FAILED`
- **AND** chunks já atualizados permanecem enriquecidos

### Requirement: Saída estruturada e validação
O worker SHALL solicitar ao LLM saída em JSON Schema (structured output) via OpenRouter e SHALL validar o resultado (5 perguntas, 4 opções, `correctIndex` entre 0 e 3, textos não vazios). Em caso de saída inválida, SHALL tentar novamente uma vez. O worker SHALL embaralhar as opções de cada pergunta, ajustando `correctIndex`, antes de persistir.

#### Scenario: Saída inválida com retry bem-sucedido
- **WHEN** o LLM retorna JSON com 4 perguntas na primeira tentativa e 5 válidas na segunda
- **THEN** o job termina em `COMPLETED` com o quiz da segunda tentativa

#### Scenario: Saída inválida persistente
- **WHEN** as duas tentativas retornam saída inválida
- **THEN** o job termina em `FAILED`

#### Scenario: Opções embaralhadas
- **WHEN** o LLM retorna a opção correta no índice 0
- **THEN** as opções são embaralhadas e `correctIndex` aponta para a mesma opção correta na nova posição

## Frontend

### Requirement: Painel lateral direito de quiz
O frontend SHALL exibir, no workspace, um painel lateral direito com o botão "Gerar quiz". Ao clicar, SHALL chamar o `POST`, consultar o `GET` a cada 2 segundos enquanto o status for `PENDING` ou `PROCESSING` e interromper o polling em `COMPLETED` ou `FAILED`, ao trocar de conversation ou ao desmontar a página.

#### Scenario: Geração em andamento
- **WHEN** o usuário clica em "Gerar quiz"
- **THEN** o botão fica desabilitado e o painel mostra indicador de carregamento
- **AND** o frontend consulta o status a cada 2 segundos

#### Scenario: Falha na geração
- **WHEN** o job retorna `FAILED` ou a requisição falha
- **THEN** o painel mostra a mensagem de erro e o botão "Gerar quiz" volta a ficar disponível

### Requirement: Renderização interativa do quiz
O frontend SHALL renderizar as 5 perguntas do contrato do backend. Ao selecionar uma opção, SHALL travar a pergunta, indicar acerto ou erro, destacar a opção correta e exibir a `explanation`. Ao responder todas, SHALL exibir a pontuação e o botão "Gerar novo quiz".

#### Scenario: Resposta correta
- **WHEN** o usuário seleciona a opção cujo índice é `correctIndex`
- **THEN** a opção é marcada como correta e a justificativa é exibida

#### Scenario: Resposta incorreta
- **WHEN** o usuário seleciona outra opção
- **THEN** a opção escolhida é marcada como incorreta, a correta é destacada e a justificativa é exibida

#### Scenario: Quiz finalizado
- **WHEN** as 5 perguntas foram respondidas
- **THEN** o painel exibe a pontuação (acertos de 5) e o botão "Gerar novo quiz"
