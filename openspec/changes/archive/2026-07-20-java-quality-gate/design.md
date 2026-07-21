## Context

Hoje o repositório não possui nenhum mecanismo determinístico de qualidade de testes Java. O `app/backend-api/pom.xml` não tem JaCoCo nem PITest configurados. O fluxo SDD do OpenSpec (`openspec/config.yaml`, schema `spec-driven`) gera `tasks.md` a partir de proposal/design/specs, mas não força nenhuma etapa de verificação de testes — a decisão de "testei o suficiente" fica com o agente, sem checagem objetiva.

Esta mudança introduz uma skill (`java-quality-gate`) mais scripts determinísticos que rodam fora do julgamento do LLM: Maven/JaCoCo/PITest fazem a medição, o script só compara contra o limiar e retorna pass/fail com dados concretos (arquivo:linha, classe:linha:mutante).

## Goals / Non-Goals

**Goals:**
- Garantir 100% de line coverage (JaCoCo) e 100% de mutation score (PITest) em todo o código fonte Java de `app/backend-api` sempre que a skill for invocada.
- Verificação 100% determinística — scripts bash/python parseando relatórios XML/CSV, nunca o agente "decidindo" que a cobertura está boa.
- Loop de auto-correção: script falha → agente corrige → script roda de novo, até verde.
- Trava anti-loop-infinito com relatório claro quando o agente não progride.
- Mecanismo de exceção auditável para mutantes genuinamente equivalentes.
- Integração no fluxo SDD: toda change que mexe em código Java gera task de rodar o gate.

**Non-Goals:**
- Não cobre testes de integração (`@SpringBootTest`, Testcontainers) — apenas unitários (JUnit 5 + Mockito) e mutação sobre eles.
- Não define threshold parcial/gradual — é 100% ou falha (com exceção auditada via item de mutantes equivalentes).
- Não substitui CI — a skill roda local, dentro do fluxo do agente; integração com pipeline CI fica fora de escopo desta change.
- Não cobre outros módulos além de `app/backend-api` (frontend não é Java).

## Decisions

### D1. Scripts em duas camadas: bash (orquestração Maven) + python (parsing/relatório)
Bash roda `mvn` e captura o exit code / localização dos relatórios; python parseia XML (JaCoCo `jacoco.xml`, PITest `mutations.xml`) porque parsing de XML em bash puro é frágil. Alternativa considerada: tudo em python chamando `subprocess` para o Maven — descartada por adicionar uma camada de indireção sem ganho; bash já é o padrão do repo para scripts (`ARCHITECTURE.md`/`local-dev-environment`).

### D2. Escopo do gate: módulo inteiro, não diff
Decisão do usuário: gate roda sobre `app/backend-api` inteiro, não apenas arquivos tocados no diff da change atual. Simplifica o script (não precisa mapear diff→classe→teste) ao custo de rodar mais lento à medida que o módulo cresce. Aceito como trade-off consciente; revisão futura pode introduzir modo `--scope=diff` se o tempo de execução virar problema.

### D3. Scripts stateless — nenhum arquivo escrito em disco durante a execução
Os scripts não persistem nada no filesystem (sem `.state.json`, sem cache). Cada execução é uma função pura: lê o relatório do Maven/JaCoCo/PITest daquela rodada, calcula uma assinatura estável (hash SHA-256 da lista ordenada de gaps: `arquivo:linha` não cobertos + `classe:linha:mutante` sobreviventes) e imprime no stdout, em texto, o resultado (`STATUS: PASS|FAIL|STUCK`), a assinatura calculada e as instruções fixas de correção para cada gap.

O rastreio de repetição (quantas vezes seguidas a mesma assinatura apareceu) não é responsabilidade do script nem do disco — é responsabilidade do agente, dentro da própria conversa: a skill instrui o agente a guardar a última assinatura recebida e, a cada nova chamada do script, passá-la como argumento (`--previous-signature <hash> --attempt <N>`). O script recalcula a assinatura atual, compara com a recebida, e decide `FAIL` (assinatura mudou ou ainda não chegou em N=5) vs `STUCK` (assinatura igual por 5 tentativas consecutivas) — a decisão de PASS/FAIL/STUCK continua 100% determinística dentro do script, só o "onde a memória mora entre chamadas" muda de arquivo para o contexto do agente.

Alternativa descartada: persistir em arquivo `.quality-gate-state.json` — rejeitada por instrução explícita do usuário (nenhum artefato deve ser gerado em disco durante a execução da skill).

### D4. Comparação de assinatura, não de contagem
Assinatura = hash estável da lista ordenada de gaps (linhas não cobertas + mutantes sobreviventes), não apenas "número de gaps". Evita falso "sem progresso" quando o agente troca um gap por outro de mesmo tamanho (ex.: corrige 1 linha mas introduz falha de mutação em outra). A assinatura viaja como texto simples entre chamadas (via argumento de linha de comando), nunca em arquivo.

### D5. Exceção de mutação via PITest `excludedMethods`/`excludedClasses` no pom.xml
PITest nativamente suporta exclusão via configuração do plugin (`<excludedMethods>`, `<excludedClasses>`) — não precisa de anotação customizada. A skill instrui o agente a só adicionar uma entrada de exclusão depois que o script de trava (D3) já reportou aquele mutante específico como cenário travado; a skill deve exigir um comentário/justificativa ao lado da entrada no pom (auditoria manual, não bypass silencioso).

### D6. Rule no `openspec/config.yaml` aplicada na etapa `tasks`
`config.yaml` (schema `spec-driven`) ganha uma referência (rule) dizendo que, quando o proposal/design tocar em código Java (`app/backend-api`), o artefato `tasks.md` deve incluir uma task explícita "Rodar skill java-quality-gate" antes de considerar a change apply-ready. Isso é uma instrução textual para o agente que gera tasks — não é enforcement por código (o schema `spec-driven` não tem hook de validação executável nesta versão do OpenSpec CLI).

## Risks / Trade-offs

- **[Risco] 100% mutation score é ambicioso; mutantes equivalentes são inevitáveis em código real** → Mitigado por D5 (exceção auditável), mas só depois de passar pela trava — evita que o agente marque qualquer mutante difícil como "equivalente" sem tentar.
- **[Risco] Rodar PITest sobre módulo inteiro é lento (minutos)** → Aceito conscientemente (D2); se virar gargalo, revisão futura pode adicionar modo incremental.
- **[Risco] Sem arquivo de estado, o agente pode "esquecer" a assinatura/tentativa anterior entre uma chamada e outra (ex.: contexto muito longo, nova sessão)** → Script trata ausência de `--previous-signature`/`--attempt` como primeira tentativa (comportamento seguro por padrão: nunca reporta `STUCK` sem histórico); pior caso é perder a contagem de tentativas e continuar tentando por mais tempo, nunca falhar incorretamente.
- **[Risco] Rule no config.yaml é só uma instrução textual, não enforcement automático** → Aceito nesta versão; se o OpenSpec CLI ganhar hooks de validação de tasks no futuro, a rule pode virar check automático.

## Migration Plan

Não há dado ou sistema em produção afetado — mudança é apenas em tooling de desenvolvimento (skill, scripts, pom.xml, config.yaml). Não há rollback especial: reverter o commit remove a skill e os plugins Maven sem efeitos colaterais.

## Open Questions

- N=5 iterações para a trava é um ponto de partida; pode precisar ajuste depois de uso real.
- Se o projeto migrar para CI (GitHub Actions etc.), esta skill deve virar step de pipeline também — fora de escopo aqui, mas os mesmos scripts python (`check_coverage.py`, `check_mutation.py`) já são reaproveitáveis nesse cenário futuro.
