## Why

Repository interfaces do Spring Data JPA usam a sintaxe de "manual property path" (`Notebook_Id`, `Owner_Id`, `Conversation_Id`) em métodos derived query. Exploração confirmou que nenhuma entidade (`User`, `Notebook`, `Source`, `Conversation`, `ConversationMessage`) tem propriedade plana colidindo com o path aninhado, então o underscore é desnecessário: Spring Data resolve o path automaticamente. Remover deixa os nomes de método alinhados ao padrão de nomenclatura Java (camelCase, sem underscore).

## What Changes

- Renomear os 11 métodos derived query afetados em `SourceRepository`, `NotebookRepository`, `ConversationRepository` e `ConversationMessageRepository`, removendo o underscore de path aninhado.
- Atualizar todos os call sites em `NotebookService`, `SourceService`, `ConversationService`, `ConversationMessageService`.
- Atualizar todos os call sites em testes unitários, de repository e de integração que fazem mock/stub ou chamam esses métodos diretamente.
- Sem mudança de comportamento: mesma query gerada, mesmo resultado.

## Capabilities

### New Capabilities

(nenhuma — rename mecânico, não introduz capability nova)

### Modified Capabilities

- `jpa-persistence`: adiciona requisito normativo de nomenclatura para métodos derived query (sem underscore de manual property path quando a resolução automática não é ambígua).

## Impact

- Código: `repository/SourceRepository.java`, `repository/NotebookRepository.java`, `repository/ConversationRepository.java`, `repository/ConversationMessageRepository.java`
- Código: `service/NotebookService.java`, `service/SourceService.java`, `service/ConversationService.java`, `service/ConversationMessageService.java`
- Testes: `test/.../service/ConversationServiceTest.java`, `test/.../service/SourceServiceTest.java`, `test/.../service/ConversationMessageServiceTest.java`, `test/.../controller/ConversationMessageApiIntegrationTest.java`, `test/.../repository/CascadeDeleteTest.java`
- Dependências/APIs externas: nenhuma. Contratos REST inalterados.
- Risco: baixo. Falha de resolução de path aninhado (se houver) quebra o boot do `ApplicationContext` (`PropertyReferenceException`), detectável na suíte de testes antes de mergear.
