## Context

`tb_users.email` sem UNIQUE hoje. `UserUpsertService.resolve` só dedupe por `cognito_sub`. App em teste, sem dados de produção — dá pra recriar a tabela em vez de migrar dados existentes.

## Goals / Non-Goals

**Goals:**
- `email` único, case-insensitive, no banco.
- Um único `cognito_sub` por email.
- Sem quebra silenciosa: colisão de email vira erro claro, não corrupção de dado.

**Non-Goals:**
- Dedupe/merge de dados existentes (tabela é dropada, não migrada).
- Mudança de fluxo de login/OAuth.
- Permitir múltiplas contas Cognito ligadas ao mesmo email (fora de escopo — feature futura, não agora).

## Decisions

- **Normalizar email para lowercase antes de persistir**, e UNIQUE simples em `email` (não `UNIQUE(lower(email))`). Como a coluna já guarda o valor normalizado, o índice único comum resolve case-insensitivity sem functional index. Mais simples que expression index.
- **Migration dropa e recria `tb_users`** (`DROP TABLE tb_users CASCADE; CREATE TABLE ...`) em vez de `ALTER TABLE ... ADD CONSTRAINT`. App em teste, sem necessidade de preservar linhas — recriar é mais simples que normalizar dados existentes antes do ALTER. `CASCADE` remove FKs de `tb_notebooks.owner_id` — aceitável em ambiente de teste.
- **Colisão de email com `cognito_sub` diferente vira erro (409/conflito), não reaproveitamento silencioso.** `UserUpsertService.createOrRecover` já captura `DataIntegrityViolationException` do insert; ao falhar por email duplicado (não por `cognito_sub`), a busca por `findByCognitoSub` não encontra nada e a exceção original é relançada — isso já é o comportamento correto, só precisa ser mantido/verificado com o novo constraint. Não faz sentido silenciosamente linkar um segundo `cognito_sub` a um email existente: seria permitir login por qualquer provedor assumir a identidade de outro usuário.

## Risks / Trade-offs

- [Recriar tabela apaga usuários de teste existentes] → aceitável, app ainda em teste, sem dado de produção.
- [`DROP ... CASCADE` remove notebooks/dados dependentes de `tb_users`] → mesmo racional, ambiente de teste.
- [Erro genérico de constraint violation ao usuário final em caso de colisão de email] → fora de escopo desta change; `UserUpsertService` já relança a exceção, tratamento HTTP dela é responsabilidade de handler existente (não alterado aqui).
