## ADDED Requirements

### Requirement: Progresso em linguagem de usuário final
O sistema SHALL exibir, na mensagem do assistente em construção, cada passo `toolCall` com seu `label` enquanto roda e com `summary` e duração ao concluir, sem mostrar JSON, argumentos ou nomes técnicos de tools.

#### Scenario: Passo em andamento
- **WHEN** chega `toolCall` com `status: "start"`
- **THEN** o sistema mostra o `label` com indicador de progresso

#### Scenario: Passo concluído
- **WHEN** chega `toolCall` com `status: "end"` e `summary`
- **THEN** o sistema atualiza o mesmo passo exibindo o `summary` e a duração

#### Scenario: Passo sem summary
- **WHEN** o evento `end` não traz `summary`
- **THEN** o sistema exibe apenas o `label` e a duração

### Requirement: Detalhes técnicos apenas no modo DEV
O sistema SHALL exibir `input`, `result` e o nome técnico da tool somente quando o modo DEV estiver ligado.

#### Scenario: Modo DEV desligado
- **WHEN** o usuário expande um passo concluído com DEV desligado
- **THEN** nenhum input, output ou nome técnico é exibido

#### Scenario: Modo DEV ligado
- **WHEN** o modo DEV está ligado
- **THEN** cada passo oferece "detalhes" com Input e Output e mostra o nome técnico da tool

### Requirement: Indicador de escrita da resposta
O sistema SHALL exibir "Escrevendo resposta…" quando a mensagem estiver em streaming, sem conteúdo e com todos os passos concluídos.

#### Scenario: Gap após a tool
- **WHEN** o último passo termina e nenhum `token` chegou
- **THEN** o sistema mostra "Escrevendo resposta…" até o primeiro token

#### Scenario: Primeiro token chega
- **WHEN** chega o primeiro `token`
- **THEN** o indicador some e o texto é exibido
