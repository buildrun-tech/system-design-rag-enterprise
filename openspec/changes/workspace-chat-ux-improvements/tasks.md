## 1. Dependências

- [x] 1.1 Adicionar `react-markdown` e `remark-gfm` em `app/frontend/package.json`

## 2. Ícones

- [x] 2.1 Adicionar `IconArrowLeft` em `app/frontend/src/components/icons.tsx` (mesmo padrão dos ícones existentes)

## 3. Navegação de volta (workspace)

- [x] 3.1 Passar botão "voltar" (`IconArrowLeft` + `navigate('/notebooks')`) como `children` do `Navbar` em `WorkspacePage.tsx`

## 4. Exclusão de notebook

- [x] 4.1 Adicionar botão lixeira (`IconTrash`) em cada card de `NotebooksPage.tsx`, com `window.confirm` antes de disparar
- [x] 4.2 Implementar `handleDeleteNotebook`: `DELETE /api/v1/notebooks/{id}` via `apiFetch`, remoção otimista da lista com rollback em erro (mesmo padrão de `handleDeleteSource` em `WorkspacePage.tsx`)

## 5. Markdown nas mensagens do chat

- [x] 5.1 Substituir `{message.content}` cru por `<ReactMarkdown remarkPlugins={[remarkGfm]}>` em `WorkspacePage.tsx` (sem `rehype-raw`)
- [x] 5.2 Ajustar CSS de `.message-bubble` para markdown renderizado (parágrafos, listas, `pre`/`code`, links) sem quebrar o layout de bolha existente

## 6. Spinner de espera pela primeira resposta

- [x] 6.1 Renderizar `IconSpinner` na bolha do assistente quando `message.streaming && message.content === ''`

## 7. Criação e troca de conversa

- [x] 7.1 Adicionar estado de lista de conversas (`conversations`) em `WorkspacePage.tsx`, carregado via `GET /api/v1/notebooks/{notebookId}/conversations`
- [x] 7.2 Adicionar seletor (dropdown) de conversas no header do `chat-panel`, exibindo a conversa ativa
- [x] 7.3 Implementar troca de conversa: abortar SSE em andamento (`abortRef.current?.abort()`), setar `conversation` selecionado, recarregar `GET /api/v1/conversations/{id}/messages`
- [x] 7.4 Adicionar botão "novo chat": `POST /api/v1/notebooks/{notebookId}/conversations`, adiciona à lista, torna ativa e limpa mensagens

## 8. Validação manual

- [ ] 8.1 `rtk npm run dev` no frontend, testar: markdown renderiza (negrito/lista/código), voltar navega para `/notebooks`, apagar notebook remove da lista, novo chat + troca de conversa funcionam, spinner aparece antes do primeiro token — **pendente**: sem `docker-compose` local disponível nesta sessão (deletado no working tree) e requer auth Cognito real; rodar manualmente com ambiente local subido
- [x] 8.2 `rtk npm run build` para garantir typecheck limpo
