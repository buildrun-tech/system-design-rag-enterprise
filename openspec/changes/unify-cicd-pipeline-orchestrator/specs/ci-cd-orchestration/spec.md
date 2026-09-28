## ADDED Requirements

### Requirement: Entrypoint único de CI/CD
O sistema SHALL expor um único workflow (`pipeline.yml`) disparado por `pull_request` e `push` em `develop` e `main`, que orquestra as trilhas infra, backend e frontend chamando os demais workflows como reutilizáveis (`workflow_call`). Os workflows de trilha SHALL NOT ter gatilho próprio de `push` ou `pull_request`.

#### Scenario: Push em develop dispara só o orquestrador
- **WHEN** um push em `develop` altera arquivos do repositório
- **THEN** apenas o workflow `pipeline` é criado; `infra`, `backend` e `frontend` só aparecem como jobs chamados dentro dele

#### Scenario: Destroy manual continua standalone
- **WHEN** um usuário dispara `workflow_dispatch` do workflow `infra`
- **THEN** o job de destroy roda com o gate de `infra/destroy_config.json` e a aprovação do Environment, sem passar pelo `pipeline`

### Requirement: Execução apenas das trilhas afetadas
O sistema SHALL calcular, em um job `changes`, quais trilhas (`infra`, `backend`, `frontend`) tiveram arquivos relevantes alterados e SHALL executar somente essas trilhas, tanto em `pull_request` quanto em `push`.

#### Scenario: Commit muda só frontend
- **WHEN** um push ou PR altera apenas arquivos em `app/frontend/**`
- **THEN** somente a trilha frontend executa; infra e backend ficam `skipped`

#### Scenario: Commit muda só infra
- **WHEN** um push ou PR altera apenas arquivos em `infra/**`
- **THEN** somente a trilha infra executa; backend e frontend ficam `skipped`

#### Scenario: Mudança no próprio orquestrador
- **WHEN** um commit altera `.github/workflows/pipeline.yml`
- **THEN** as três trilhas executam

### Requirement: Deploy de aplicação só depois do terraform apply
O sistema SHALL executar a trilha de push de backend e a de frontend (build, promoção e deploy) apenas depois que a trilha infra do mesmo commit terminar com `success` ou `skipped`.

#### Scenario: Commit muda infra e app
- **WHEN** um push em `develop` altera `infra/**` e `app/backend-api/**`
- **THEN** o deploy do backend só inicia depois do `terraform apply` concluir com sucesso, e lê os outputs do state já atualizado

#### Scenario: Commit não muda infra
- **WHEN** um push altera apenas `app/backend-api/**` (trilha infra `skipped`)
- **THEN** o deploy do backend executa normalmente, sem esperar nem exigir `apply`

#### Scenario: Apply falha
- **WHEN** o `terraform apply` termina com `failure`
- **THEN** as trilhas de push de backend e frontend não executam

#### Scenario: Apply cancelado
- **WHEN** o `terraform apply` é cancelado (ex.: aprovação do Environment `prod` rejeitada)
- **THEN** as trilhas de push de backend e frontend não executam

#### Scenario: Prod aguarda aprovação da infra
- **WHEN** um push em `main` altera `infra/**` e `app/**`
- **THEN** o run fica aguardando a aprovação do Environment `prod` do `apply` e só depois inicia o deploy de app

### Requirement: CI de pull request independente entre trilhas
O sistema SHALL executar as verificações de PR (`ci`/`plan` de infra, CI de backend, CI de frontend) sem dependência entre trilhas, de modo que a falha de uma não impeça a execução das outras.

#### Scenario: Plan de infra falha
- **WHEN** o `terraform plan` de uma PR falha e a PR também altera `app/backend-api/**`
- **THEN** o CI do backend executa e reporta seu próprio resultado

#### Scenario: Deploy não roda em PR
- **WHEN** um pull request é aberto ou atualizado
- **THEN** nenhum job de push, promoção ou deploy é executado

### Requirement: Promoção do frontend independente do status do run de dev
O sistema SHALL localizar o artefato `dist/` de `develop` pelo nome `frontend-dist-<sha>` na API de artifacts do repositório, ignorando o status agregado do run que o gerou.

#### Scenario: Run de develop com outra trilha falha
- **WHEN** o run de `develop` para o sha gerou o artifact `frontend-dist-<sha>` mas outra trilha do mesmo run falhou
- **THEN** o push em `main` para o mesmo sha ainda encontra e promove esse artifact

#### Scenario: Artifact ausente ou expirado
- **WHEN** não existe artifact `frontend-dist-<sha>` não expirado
- **THEN** o job de promoção falha explicitamente, sem rebuild automático
