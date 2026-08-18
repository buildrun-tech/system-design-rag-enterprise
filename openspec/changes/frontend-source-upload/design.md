## Context

`WorkspacePage.tsx` já faz `apiFetch<NotebookDetail>('/api/v1/notebooks/{id}')` uma vez pra pegar `sources` embutido. `apiFetch` (`api/client.ts`) já detecta `FormData` no body e pula `Content-Type` (deixa o browser setar `multipart/form-data; boundary=...`), então upload não precisa de mudança no client HTTP. Backend responde `202` com a `Source` criada em `PENDING`; processamento é assíncrono (SQS + embeddings), sem webhook — cliente precisa dar poll.

## Goals / Non-Goals

**Goals:**
- Upload dispara no `onChange` do `<input type="file">`, sem tela extra de confirmação
- Lista de sources reflete progresso (`PENDING` → `PROCESSING` → `READY`/`FAILED`) sem o usuário recarregar a página
- Delete remove source e para o polling dela

**Non-Goals:**
- Sem drag-and-drop, sem upload múltiplo simultâneo, sem preview de arquivo — YAGNI, um arquivo por vez cobre o caso de uso
- Sem fonte tipo URL (backend suporta, fora de escopo aqui)
- Sem WebSocket/SSE pra status de source — polling de 3s é simples o bastante pro volume esperado (poucas sources por notebook)

## Decisions

**Polling simples com `setInterval`, não SSE**
O workspace já usa SSE pro chat (`fetchEventSource`), mas source ingestion é passo único (não streaming token-a-token) — abrir uma segunda conexão persistente só pra status é over-engineering. `setInterval(3000)` que já limpa quando não sobra `PENDING`/`PROCESSING` resolve com menos código, mesmo padrão que outras partes do projeto (nenhuma outra tela usa long-poll/WS).

**Lista de sources via `GET /sources` dedicado no polling, não refetch do notebook inteiro**
Endpoint dedicado (`GET /api/v1/notebooks/{notebookId}/sources`) já existe e retorna só o array — mais barato que re-buscar o notebook completo a cada 3s. Fetch inicial continua vindo do detail do notebook (já carregado pra outros dados do workspace), só o polling usa o endpoint dedicado.

**Update otimista com rollback manual no delete**
Remove da lista local antes da resposta confirmar; se `DELETE` falhar, reinsere a source removida (guarda referência antes de tirar do array) e mostra erro. Evita esperar round-trip pra UI reagir, sem precisar de lib de state management pra isso.

**Ícones novos: `IconUpload`, `IconTrash`, `IconSpinner`**
Seguem o padrão SVG inline já usado em `icons.tsx` (stroke, 24x24 viewBox, `currentColor`). `IconSpinner` usa `stroke-dasharray`/`animation: spin` via CSS (`@keyframes`), sem lib de animação.

## Risks / Trade-offs

[Polling de 3s por múltiplas sources `PENDING` gera requests repetidos] → Mitigação: um único `GET /sources` por tick cobre todas as sources do notebook (não é 1 request por source), e o interval limpa assim que a lista não tem mais pendente.

[Rollback de delete otimista pode reordenar a lista se o usuário deletar/criar entre o clique e o erro] → Mitigação: guarda posição original (`index`) junto com a source ao remover, reinsere na mesma posição — comportamento aceitável, sem necessidade de merge complexo pra esse volume de dados.

[Backend do pipeline de ingestão (`implement-source-ingestion-pipeline`) pode não estar pronto ao testar] → Mitigação: frontend consome o contrato documentado em `API.md`, testável isoladamente com mock/msw se o backend não estiver disponível; sem acoplamento de código entre os dois changes.

## Migration Plan

Aditivo, sem dado/schema envolvido no frontend. Deploy normal.
