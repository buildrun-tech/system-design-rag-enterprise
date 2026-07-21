## Why

Testes unitários e mutation testing hoje dependem do julgamento subjetivo do agente sobre "cobertura suficiente". Sem verificação determinística, código Java pode entrar em produção com linhas não testadas ou testes fracos (que passam mesmo com a lógica quebrada — mutantes sobreviventes). É preciso um gate objetivo, executado por script, que force 100% de line coverage e 100% de mutation score antes de considerar uma implementação Java completa.

## What Changes

- Nova skill `java-quality-gate` (`.claude/skills/java-quality-gate/SKILL.md`) que instrui o agente a: implementar testes unitários (JUnit 5 + Mockito) para código Java, e então rodar scripts determinísticos de verificação de cobertura (JaCoCo) e mutação (PITest).
- Scripts determinísticos (bash + python) em `.claude/skills/java-quality-gate/scripts/`:
  - `run-coverage.sh` / `check_coverage.py`: roda `mvn test` + `jacoco:report`, parseia o XML/CSV do JaCoCo, falha (exit code != 0) se line coverage < 100%, reporta arquivo:linha não coberto.
  - `run-mutation.sh` / `check_mutation.py`: roda `mvn org.pitest:pitest-maven:mutationCoverage`, parseia `mutations.xml`/csv, falha se mutation score < 100% (killed/total, contando NO_COVERAGE e SURVIVED como falha), reporta classe:linha:mutante sobrevivente.
- Fluxo de auto-correção guiado pela skill: quando um script falha, a skill instrui o agente a corrigir teste/implementação e rodar o script de novo — em loop, até gate verde.
- **Trava anti-loop**: os scripts persistem um estado local (ex: `.quality-gate-state.json` no scratchpad da change) rastreando o cenário da última falha (mesmo arquivo:linha, ou mesma classe:linha:mutante). Se o mesmo cenário se repetir por N iterações consecutivas (N=5, configurável), o script para o loop e retorna um relatório de "cenário travado" para o usuário decidir, em vez de repetir indefinidamente.
- **Exceção de mutação**: mutantes que passaram pela trava acima e foram manualmente confirmados como equivalentes/impossíveis de matar podem ser excluídos via configuração do PITest (`excludedMethods`/`excludedClasses` no `pom.xml`, ou anotação equivalente). A skill exige que essa exclusão só seja adicionada depois que a trava reportou o cenário — não é bypass livre.
- Configuração de plugins Maven no `app/backend-api/pom.xml`: `jacoco-maven-plugin` (com regra de check de 100% line coverage) e `org.pitest:pitest-maven` (com `mutationThreshold` 100).
- Integração com o fluxo SDD do OpenSpec: `openspec/config.yaml` passa a referenciar a skill `java-quality-gate` como rule aplicável na geração de `tasks.md`, garantindo que toda change que envolva código Java gere uma task explícita de rodar o quality gate antes de ser considerada completa.

## Capabilities

### New Capabilities
- `java-quality-gate`: verificação determinística e automatizada de 100% line coverage (JaCoCo) e 100% mutation score (PITest) para código Java, com loop de auto-correção guiado por skill e trava anti-loop-infinito.

### Modified Capabilities
(nenhuma — não há capability existente de testes/qualidade no `openspec/specs/` para alterar)

## Impact

- Novo arquivo: `.claude/skills/java-quality-gate/SKILL.md`
- Novos scripts: `.claude/skills/java-quality-gate/scripts/run-coverage.sh`, `check_coverage.py`, `run-mutation.sh`, `check_mutation.py`
- Modificado: `app/backend-api/pom.xml` (plugins JaCoCo e PITest)
- Modificado: `openspec/config.yaml` (rule de propose/tasks referenciando a skill)
- Nenhum impacto em API pública, banco de dados ou infraestrutura AWS.
