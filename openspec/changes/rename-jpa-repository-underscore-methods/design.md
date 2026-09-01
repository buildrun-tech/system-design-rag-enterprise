## Context

4 repository interfaces do Spring Data JPA usam manual property path (`Notebook_Id`, `Owner_Id`, `Notebook_Owner_Id`, `Conversation_Id`) pra navegar propriedade aninhada. Exploração confirmou (lendo `User`, `Notebook`, `Source`, `Conversation`, `ConversationMessage`) que nenhuma entidade tem propriedade plana colidindo com o path aninhado — o algoritmo padrão de resolução do Spring Data (`PropertyPath`, greedy left-to-right com backtrack) resolve sozinho sem o underscore.

## Goals / Non-Goals

**Goals:**
- Métodos derived query em camelCase puro, sem underscore, seguindo convenção de nomenclatura Java.
- Zero mudança de comportamento/SQL gerado.

**Non-Goals:**
- Não mexe em nomes de coluna de banco (`snake_case` em `@Column`/`@JoinColumn` continua, é convenção SQL, fora de escopo).
- Não mexe em constantes `UPPER_SNAKE_CASE` (convenção Java correta pra constante).
- Não adiciona `@Query` manual — só remove underscore onde a resolução automática já funciona.

## Decisions

- **Rename direto, sem `@Query` de fallback**: os 4 métodos mais arriscados (`findByIdAndNotebook_Owner_Id`, `findByNotebook_IdAndNotebook_Owner_Id...`) resolvem automático porque nenhuma entidade tem propriedade `notebookOwner` ou `notebookId` plana. Confirmado por inspeção manual das entidades, não por teste isolado — a suíte de testes existente cobre a mesma query.
- **Ordem de edição**: repository → service (call site) → teste, por classe, pra manter compilação verde a cada passo (não é obrigatório mas evita diff gigante quebrado no meio).
- **Sem alias/depreciação**: método antigo é deletado, não mantido como forwarding — é rename interno, não API pública.

## Risks / Trade-offs

- [Path aninhado resolve pro lugar errado silenciosamente] → Mitigação: Spring Data valida todo método derived query no boot do `ApplicationContext`; resolução errada ou ambígua lança `PropertyReferenceException` e quebra o build/teste imediatamente, não em produção.
- [Miss de um call site em teste] → Mitigação: `mvn test` roda toda a suíte após o rename; compilação falha se sobrar referência ao nome antigo.
