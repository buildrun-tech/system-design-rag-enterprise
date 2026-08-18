## 1. Tipos e ícones

- [x] 1.1 Adicionar `IconUpload`, `IconTrash`, `IconSpinner` em `icons.tsx` (`IconSpinner` com `@keyframes spin` em `ui.css`)

## 2. Upload

- [x] 2.1 Adicionar `<input type="file" hidden>` + botão de trigger no header do painel de sources em `WorkspacePage.tsx`
- [x] 2.2 No `onChange`, montar `FormData` (`file`) e chamar `apiFetch<Source>(POST /sources, formData)`, inserir resultado no topo da lista local
- [x] 2.3 Exibir erro inline (`role="alert"`) se o POST falhar

## 3. Polling de status

- [x] 3.1 Adicionar `useEffect` com `setInterval(3000)` que roda `GET /sources` e substitui a lista local, só enquanto houver `PENDING`/`PROCESSING`
- [x] 3.2 Limpar o interval quando não sobrar `PENDING`/`PROCESSING` e no unmount do componente

## 4. Delete

- [x] 4.1 Adicionar botão de lixeira por source; remove otimista da lista local (guardando índice original) e chama `DELETE /sources/{id}`
- [x] 4.2 Em caso de erro no DELETE, reinserir a source na posição original e mostrar erro inline

## 5. Verificação

- [x] 5.1 `tsc --noEmit` e `vite build` limpos
- [x] 5.2 Backend de ingestão não está rodando neste ambiente (só postgres/floci ativos, API em localhost:8080 fora do ar) — validado por leitura de código (paths, multipart, polling) e build/type-check; sem teste end-to-end real de upload/delete contra o backend.
