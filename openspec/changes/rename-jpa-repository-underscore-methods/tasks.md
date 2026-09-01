## 1. SourceRepository

- [x] 1.1 Renomear `findByNotebook_Id` → `findByNotebookId`, `findByNotebook_IdAndStatus` → `findByNotebookIdAndStatus`, `findByIdAndNotebook_Id` → `findByIdAndNotebookId` em `SourceRepository.java`
- [x] 1.2 Atualizar call sites em `NotebookService.java`, `ConversationService.java`, `ConversationMessageService.java`
- [x] 1.3 Atualizar call sites em `SourceServiceTest.java`, `ConversationServiceTest.java`, `ConversationMessageServiceTest.java`

## 2. NotebookRepository

- [x] 2.1 Renomear `findByIdAndOwner_Id` → `findByIdAndOwnerId`, `findByOwner_Id` → `findByOwnerId` em `NotebookRepository.java`
- [x] 2.2 Atualizar call sites em `NotebookService.java`
- [x] 2.3 Atualizar call sites em `SourceServiceTest.java`

## 3. ConversationRepository

- [x] 3.1 Renomear `findByIdAndNotebook_Owner_Id` → `findByIdAndNotebookOwnerId`, `findByNotebook_IdAndNotebook_Owner_IdOrderByCreatedAtDesc` → `findByNotebookIdAndNotebookOwnerIdOrderByCreatedAtDesc` em `ConversationRepository.java`
- [x] 3.2 Atualizar call sites em `ConversationService.java`, `ConversationMessageService.java`
- [x] 3.3 Atualizar call sites em `ConversationServiceTest.java`, `ConversationMessageServiceTest.java`, `ConversationMessageApiIntegrationTest.java`

## 4. ConversationMessageRepository

- [x] 4.1 Renomear `findFirstByConversation_IdOrderByCreatedAtAsc` → `findFirstByConversationIdOrderByCreatedAtAsc`, `findTop10ByConversation_IdOrderByCreatedAtDesc` → `findTop10ByConversationIdOrderByCreatedAtDesc` em `ConversationMessageRepository.java`
- [x] 4.2 Atualizar call sites em `ConversationService.java`, `ConversationMessageService.java`
- [x] 4.3 Atualizar call sites em `ConversationServiceTest.java`, `ConversationMessageServiceTest.java`

## 5. Verificação

- [x] 5.1 Rodar `mvn test` no módulo `app/backend-api`: 65/66 passaram; a única falha (`SourceIngestionConsumerTest.processMarksFailedAndRethrowsWhenSourceMissing`) é pré-existente e não relacionada a este change (confirmado via `git stash` rodando o mesmo teste no código anterior ao rename — falha idêntica).
- [x] 5.2 Confirmado via `grep -rn "_Id\|_Owner\|_Notebook\|_Conversation" app/backend-api/src` — nenhuma referência ao nome antigo restante.
- [x] 5.3 Rodar o skill `java-quality-gate` sobre os arquivos alterados: `run-coverage.sh` → `STATUS: PASS`, `LINE_COVERAGE: 96.25%`. `run-mutation.sh` (PITest) não completou nesta sessão — outra sessão Claude Code rodava o mesmo `mvnw pitest-maven:mutationCoverage` concorrentemente no mesmo módulo, derrubando os minions por timeout (`Minion exited abnormally due to TIMED_OUT`). Por decisão do usuário, mutation gate foi pulado para este change (rename mecânico, sem lógica nova; coverage 96.25% já cobre as classes tocadas). Pendência: rodar `run-mutation.sh` isoladamente quando não houver outra sessão concorrente no repo.
