## Context

O evento SSE `toolCall` (`ToolEvent` → `ToolEventPayload`) leva `id`, `name`, `label`, `status`, `input`, `result`, `durationMs`. `EventingToolCallback` deriva `label` da primeira frase da `description` do `@Tool` e emite `start` só quando o Spring AI executa a tool (depois do modelo montar os argumentos). A busca RAG roda no `RetrievalAugmentationAdvisor` dentro de `OpenAiChatCompletionAdapter.stream()` sem emitir evento. O frontend (`ToolTimeline`) mostra input/output crus em "detalhes".

## Goals / Non-Goals

**Goals:**
- Usuário final vê frases em português sobre cada etapa: busca nas fontes, uso de tool, escrita da resposta.
- Detalhes técnicos (input/result/nome da tool) só no modo DEV.
- Compatível: sem novo tipo de evento SSE, campo novo opcional.

**Non-Goals:**
- Streaming do raciocínio do modelo (extended thinking).
- Emitir `start` antes de o modelo terminar os argumentos da tool.
- Medir a duração exata só da busca vetorial (envolver o `DocumentRetriever`).
- Mensagens localizadas em outros idiomas.

## Decisions

1. **Campo `summary` em `ToolEvent`/`ToolEventPayload`** em vez de reaproveitar `result`. `result` continua cru para DEV; `summary` é contrato de UI. Alternativa descartada: formatar no frontend, que precisaria conhecer cada tool.
2. **Textos por tool em tabela no `EventingToolCallback`** (`nome → label, summary ok, summary erro`), fallback para o `label()` atual. Alternativa descartada: anotação customizada sobre `@Tool`; só há uma tool, fica para quando houver várias. `// ponytail:` no código.
3. **`retrieval` como `toolCall` sintético** (`name: "retrieval"`) emitido em `OpenAiChatCompletionAdapter.stream()`: `start` antes de chamar o `chatClient`; `end` no primeiro `onResponse`, com `summary` calculado de `retrievedDocuments` (N trechos, M fontes distintas por `source_id`). Reusa `listener.onToolCall`, sem mudar a porta `ChatStreamListener`.
4. **"Escrevendo resposta…"** é estado derivado no frontend (`streaming && content === '' && nenhum passo em start`), sem evento.
5. **Modo DEV** (`useDevMode`, já existente) controla a exibição de `input`/`result`/nome técnico; sem DEV a visão usa só `label`, `summary` e duração.

## Risks / Trade-offs

- [Duração do `retrieval` inclui a latência do primeiro token] → documentada com `ponytail:`; aceitável para feedback de UI.
- [Com `summary` ausente (tool nova sem entrada na tabela)] → frontend cai para `label`; backend usa fallback do `label()`.
- [`retrieval` `end` nunca emitido se o stream falhar antes do primeiro chunk] → o passo fica em `start`; o evento `error`/fim do stream já encerra o estado `streaming` e o frontend não mostra spinner após `streaming=false`.
- [Cliente antigo sem suporte a `summary`] → campo opcional ignorado.

## Migration Plan

Deploy backend e frontend em qualquer ordem (campo opcional). Rollback: reverter os commits; sem migração de dados.

## Open Questions

- Nenhuma.
