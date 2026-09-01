## Why

`tb_users.email` não tem constraint UNIQUE (nem no schema SQL, nem na entidade JPA). `UserUpsertService.resolve` só deduplica por `cognito_sub`, então dois provedores/signups diferentes podem criar linhas com o mesmo email — corrompendo a premissa de que email identifica um único usuário. App ainda em fase de teste: dá pra recriar a tabela sem migração de dados.

## What Changes

- **BREAKING**: `tb_users` recriada com `email` normalizado (lowercase) e `UNIQUE` case-insensitive.
- `UserUpsertService.resolve` passa a checar colisão de email antes de criar usuário novo, garantindo um único `cognito_sub` por email.
- Nova migration Flyway `V3__add_users_email_unique.sql` que dropa e recria `tb_users` (dev/teste, sem dados a preservar).
- `User.java` normaliza email para lowercase na construção e mapeia a unique constraint.

## Capabilities

### New Capabilities
(nenhuma)

### Modified Capabilities
- `auth`: requirement "Criação automática de perfil de usuário no primeiro acesso" passa a garantir email único (case-insensitive) e a tratar colisão de email como erro/reaproveitamento, não mais apenas dedupe por `cognito_sub`.

## Impact

- `app/backend-api/src/main/resources/db/migration/V3__add_users_email_unique.sql` (nova)
- `app/backend-api/src/main/java/tech/buildrun/notebooklm/entity/User.java`
- `app/backend-api/src/main/java/tech/buildrun/notebooklm/security/UserUpsertService.java`
- `app/local/init.sql` (dev seed, se referenciar `tb_users`)
