## ADDED Requirements

### Requirement: Timeline de tool calls durante o streaming
O sistema SHALL exibir, na mensagem do assistente em construção e acima do texto, uma timeline vertical com as tool calls recebidas via eventos SSE `toolCall`, em ordem de chegada, diferenciando em andamento, concluída e com erro.

#### Scenario: Tool em andamento
- **WHEN** o servidor envia `data: {"toolCall": {"id": "...", "name": "...", "status": "start"}}`
- **THEN** o sistema adiciona um item à timeline com marcador de execução e o offset (`+N.NNs`) desde o início do stream

#### Scenario: Tool concluída
- **WHEN** o servidor envia `toolCall` com `status: "end"` e o mesmo `id`
- **THEN** o sistema atualiza o item existente para concluído, exibindo `durationMs`, sem criar um segundo item e sem alterar o offset do start

#### Scenario: Tool com erro
- **WHEN** o servidor envia `toolCall` com `status: "error"`
- **THEN** o sistema atualiza o item para erro, e a mensagem do assistente segue normalmente

#### Scenario: Múltiplas tools
- **WHEN** o stream contém mais de uma tool call
- **THEN** a timeline lista cada uma na ordem de chegada, ligadas por uma linha vertical

#### Scenario: Tool call não persiste no histórico
- **WHEN** o usuário reabre uma conversa com mensagens prévias
- **THEN** o sistema não exibe timeline nas mensagens carregadas

### Requirement: Dev mode para inspeção de SSE
O sistema SHALL oferecer um dev mode, ativável por um botão na barra do chat, que exibe um painel com o log bruto do stream SSE da última mensagem enviada.

#### Scenario: Ativar dev mode
- **WHEN** o usuário aciona o botão DEV
- **THEN** o sistema exibe o painel de log abaixo das mensagens e marca o botão como ativo
- **AND** a preferência é lembrada no navegador entre sessões (sem falhar se o armazenamento estiver indisponível)

#### Scenario: Log do stream
- **WHEN** o usuário envia uma mensagem com o dev mode ativo
- **THEN** o painel lista, com o tempo decorrido (`+N.NNNs`), a abertura da conexão (status HTTP e content-type), cada evento SSE com o `data` bruto, erros e o fechamento
- **AND** os eventos `toolCall` aparecem destacados

#### Scenario: Log sempre coletado
- **WHEN** o dev mode é ativado depois do envio da mensagem
- **THEN** o painel exibe o log completo daquele stream

#### Scenario: Nova mensagem
- **WHEN** o usuário envia uma nova mensagem
- **THEN** o log anterior é substituído pelo do novo stream

#### Scenario: Limpar e copiar
- **WHEN** o usuário aciona Limpar ou Copiar no painel
- **THEN** o log é esvaziado, ou copiado como texto para a área de transferência

#### Scenario: Detalhe na timeline
- **WHEN** o dev mode está ativo
- **THEN** cada item da timeline exibe também o `name` técnico da tool e o início do `id`

#### Scenario: Dev mode desativado
- **WHEN** o dev mode está desativado
- **THEN** o painel e os detalhes técnicos não são exibidos
