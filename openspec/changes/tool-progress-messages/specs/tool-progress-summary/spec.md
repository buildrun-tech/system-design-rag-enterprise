## ADDED Requirements

### Requirement: Evento toolCall com summary amigável
O sistema SHALL incluir no evento SSE `toolCall` o campo opcional `summary`, uma frase em português para o usuário final, sem expor parâmetros ou resultados técnicos. Os campos existentes (`id`, `name`, `label`, `status`, `input`, `result`, `durationMs`) SHALL permanecer inalterados.

#### Scenario: Tool concluída com texto amigável
- **WHEN** a tool `currentDateTime` termina com sucesso
- **THEN** o sistema envia `toolCall` com `status: "end"`, `label: "Consultando data e hora"` e `summary: "Data e hora consultadas"`
- **AND** `result` continua com o valor cru da tool

#### Scenario: Tool com erro
- **WHEN** a execução da tool `currentDateTime` lança exceção
- **THEN** o sistema envia `toolCall` com `status: "error"` e `summary: "Não foi possível consultar data e hora"`

#### Scenario: Tool sem entrada na tabela de textos
- **WHEN** uma tool sem textos amigáveis cadastrados é executada
- **THEN** o sistema usa o label derivado da `description` e omite `summary`

### Requirement: Passo de busca nas fontes no stream
O sistema SHALL emitir, antes do primeiro token, um `toolCall` com `name: "retrieval"` e `label: "Buscando nas suas fontes"`: `status: "start"` ao iniciar a resposta e `status: "end"` quando o contexto recuperado for conhecido, com `summary` descrevendo o resultado.

#### Scenario: Trechos encontrados
- **WHEN** a busca recupera 4 trechos de 2 fontes distintas
- **THEN** o sistema envia `retrieval` `end` com `summary: "4 trechos encontrados em 2 fontes"` e `durationMs`

#### Scenario: Nenhum trecho
- **WHEN** a busca não recupera documentos
- **THEN** o sistema envia `retrieval` `end` com `summary: "Nenhum trecho relevante encontrado"`

#### Scenario: Ordem dos eventos
- **WHEN** uma mensagem é enviada
- **THEN** o `start` de `retrieval` chega antes de qualquer evento `token` e o `end` não depois do primeiro `token`
