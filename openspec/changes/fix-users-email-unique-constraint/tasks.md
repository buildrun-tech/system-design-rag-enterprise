## 1. Migration

- [x] 1.1 Criar `V3__add_users_email_unique.sql`: `DROP TABLE tb_users CASCADE`, recriar `tb_users` com `email VARCHAR(320) NOT NULL UNIQUE` (idêntico ao V1, exceto UNIQUE em email)
- [x] 1.2 Atualizar `app/local/init.sql` se referenciar `tb_users` (seed de dev) — não referencia, sem mudança necessária

## 2. Backend

- [x] 2.1 `User.java`: normalizar `email` para lowercase no construtor, adicionar `unique = true` no `@Column`
- [x] 2.2 `UserUpsertService.createOrRecover`: confirmar que colisão de email (constraint diferente de `cognito_sub`) propaga a exceção em vez de mascarar como sucesso — já correto, coberto por teste novo
- [x] 2.3 Rodar `java-quality-gate` sobre as alterações — coverage PASS (96.25%); mutation pulado a pedido do usuário (bug pré-existente em `SourceIngestionConsumerTest`, fora de escopo, atrapalhou runs consecutivos do PIT)

## 3. Verificação

- [ ] 3.1 Subir app local, recriar banco (`flyway migrate` ou restart com volume limpo), confirmar `tb_users` criada com constraint UNIQUE em `email`
- [ ] 3.2 Teste manual: dois logins com emails que diferem só em case → mesmo usuário
- [ ] 3.3 Teste manual: `cognito_sub` novo com email já existente → erro, não duplicata silenciosa
