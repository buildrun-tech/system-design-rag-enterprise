## MODIFIED Requirements

### Requirement: Criação automática de perfil de usuário no primeiro acesso
O sistema SHALL criar automaticamente um registro de usuário na tabela `users` na primeira vez que um JWT válido é recebido, usando o `sub` do Cognito como identificador único. A resolução SHALL ocorrer em um filtro global (`OncePerRequestFilter`) registrado imediatamente após a validação do JWT na cadeia de segurança, de forma que nenhuma rota autenticada precise repetir a lógica de lookup/criação. O usuário resolvido SHALL ficar disponível para os controllers via injeção (`@CurrentUser`), sem novo lookup ao banco. O `email` SHALL ser normalizado para lowercase antes de persistir, e SHALL ser único na tabela `users`: no máximo um `cognito_sub` por email.

#### Scenario: Primeiro acesso de um novo usuário
- **WHEN** um JWT válido é recebido de um usuário cujo `sub` não existe na tabela `users`
- **THEN** o sistema cria um registro em `users` com `cognito_sub`, `email` (normalizado para lowercase), e `name` extraídos do token
- **AND** a requisição original prossegue normalmente
- **AND** o `User` criado fica disponível para o controller sem novo lookup ao banco

#### Scenario: Acessos subsequentes de um usuário existente
- **WHEN** um JWT válido é recebido de um usuário cujo `sub` já existe na tabela `users`
- **THEN** o sistema reaproveita o registro existente sem criar duplicata
- **AND** a requisição prossegue normalmente

#### Scenario: Duas requisições concorrentes do mesmo usuário novo
- **WHEN** dois requests simultâneos chegam com o mesmo `sub` que ainda não existe na tabela `users`
- **THEN** apenas um registro é criado (a constraint `UNIQUE` em `cognito_sub` impede duplicata)
- **AND** o request que perde a corrida de inserção recupera o registro já criado pelo outro, sem erro

#### Scenario: Emails com capitalização diferente são tratados como o mesmo usuário
- **WHEN** um JWT válido traz um email que difere apenas em maiúsculas/minúsculas de um email já cadastrado (ex: `User@Example.com` vs `user@example.com`)
- **THEN** o sistema normaliza o email para lowercase antes de comparar/persistir
- **AND** a constraint `UNIQUE` em `email` impede a criação de um segundo registro para o mesmo endereço

#### Scenario: Novo `cognito_sub` tenta usar email já cadastrado em outro usuário
- **WHEN** um JWT válido é recebido com um `sub` que não existe na tabela `users`, mas com `email` já associado a outro `cognito_sub`
- **THEN** o insert falha por violação da constraint `UNIQUE` de `email`
- **AND** o sistema não reaproveita silenciosamente o registro existente (a exceção é propagada, não mascarada como sucesso)
