## 1. Configuração de plugins Maven

- [x] 1.1 Adicionar `jacoco-maven-plugin` ao `app/backend-api/pom.xml`, com execução `prepare-agent` + `report` no ciclo `test`, e regra de `check` exigindo `LINE` coverage mínimo de 100% (`minimum: 1.00`), falhando o build (`haltOnFailure=true`) quando não atingido.
- [x] 1.2 Adicionar `org.pitest:pitest-maven` ao `app/backend-api/pom.xml`, configurado com `mutationThreshold` 100, `targetClasses`/`targetTests` cobrindo os pacotes do projeto (`tech.buildrun.notebooklm.*`), e seções vazias (a preencher conforme item 6) para `excludedMethods`/`excludedClasses`.
- [x] 1.3 Confirmar que `spring-boot-starter-webmvc-test`/`spring-boot-starter-data-jpa-test` já trazem JUnit 5 e Mockito transitivamente; se não trouxerem Mockito, adicionar `mockito-core`/`mockito-junit-jupiter` explicitamente com escopo `test`. (Confirmado via `mvnw dependency:tree`: junit-jupiter 6.0.3 e mockito-core/mockito-junit-jupiter 5.23.0 já vêm transitivos por `spring-boot-starter-test`.)
- [x] 1.4 Rodar `./mvnw test` e `./mvnw org.pitest:pitest-maven:mutationCoverage` uma vez manualmente para validar que os plugins geram `target/site/jacoco/jacoco.xml` e `target/pit-reports/**/mutations.xml` corretamente antes de escrever os scripts. (JaCoCo 0.8.12 não suporta bytecode do Java 25/class file major 69 — atualizado para 0.8.15. PITest 1.17.0 idem — atualizado para 1.25.8, e adicionado `pitest-junit5-plugin` 1.2.3, sem o qual PITest não descobre testes JUnit 5. `jacoco.xml` confirmado gerado; `mutations.xml` só é gerado quando existem mutações candidatas — o skeleton atual só tem `NotebooklmApplication.main()`, sem lógica mutável, então PITest reporta "0 mutation test units" e pula a análise; adicionado `failWhenNoMutations=false` para o build não falhar nesse caso legítimo até existir código de negócio real.)

## 2. Scripts de verificação de coverage (stateless, sem gravar em disco)

- [x] 2.1 Criar `.claude/skills/java-quality-gate/scripts/run-coverage.sh`: roda `./mvnw test jacoco:report` em `app/backend-api`, localiza `target/site/jacoco/jacoco.xml` (artefato do próprio build Maven, não do script) e repassa o caminho pro parser python. O script em si não cria nenhum arquivo.
- [x] 2.2 Criar `.claude/skills/java-quality-gate/scripts/check_coverage.py`: aceita `--previous-signature <hash>` e `--attempt <N>` opcionais; parseia `jacoco.xml` em memória, calcula line coverage agregado (`LINE` counter, `covered/(covered+missed)`) e a assinatura SHA-256 do conjunto ordenado de `arquivo:linha` não cobertos; imprime no stdout `STATUS: PASS|FAIL|STUCK` + assinatura atual + (se FAIL/STUCK) lista de gaps com instrução fixa de correção. Nunca grava arquivo.
- [x] 2.3 Encadear `run-coverage.sh` para chamar `check_coverage.py` ao final, repassando os mesmos argumentos recebidos, e propagar a saída de texto e o exit code (0=PASS, 1=FAIL, 2=STUCK).

## 3. Scripts de verificação de mutação (stateless, sem gravar em disco)

- [x] 3.1 Criar `.claude/skills/java-quality-gate/scripts/run-mutation.sh`: roda `./mvnw org.pitest:pitest-maven:mutationCoverage` em `app/backend-api`, localiza o `mutations.xml` mais recente em `target/pit-reports/**` (artefato do PITest, não do script).
- [x] 3.2 Criar `.claude/skills/java-quality-gate/scripts/check_mutation.py`: aceita `--previous-signature <hash>` e `--attempt <N>` opcionais; parseia `mutations.xml` em memória, calcula mutation score (`killed/total`, `SURVIVED`/`NO_COVERAGE` contam como não-killed) e a assinatura SHA-256 do conjunto ordenado de mutantes não killed; imprime `STATUS: PASS|FAIL|STUCK` + assinatura + (se FAIL/STUCK) lista `classe:linha:descrição-do-mutante` com instrução fixa de correção. Nunca grava arquivo.
- [x] 3.3 Encadear `run-mutation.sh` para chamar `check_mutation.py` ao final, repassando os mesmos argumentos, e propagar saída de texto e exit code (0=PASS, 1=FAIL, 2=STUCK).

## 4. Módulo compartilhado de assinatura e trava

- [x] 4.1 Extrair para `.claude/skills/java-quality-gate/scripts/gate_signature.py` (compartilhado pelos dois checkers) a função que recebe a lista ordenada de gaps, calcula o hash SHA-256, e decide `PASS`/`FAIL`/`STUCK` comparando com `--previous-signature`/`--attempt` recebidos — sem nenhuma leitura ou escrita de arquivo próprio.
- [x] 4.2 Garantir que a ausência de `--previous-signature`/`--attempt` (primeira chamada, ou agente sem contexto anterior) é tratada como tentativa 1, nunca retorna `STUCK` sem histórico.

## 5. Skill java-quality-gate (criada via skill-creator, com progressive disclosure)

- [x] 5.1 Usar o skill-creator (`.claude/plugins/marketplaces/claude-plugins-official/plugins/skill-creator`) como guia de estrutura: `SKILL.md` enxuto (fluxo + quando usar), scripts em `scripts/` executáveis sem carregar conteúdo extra no contexto, e (se necessário) detalhes extensos movidos para `references/`.
- [x] 5.2 Escrever `SKILL.md` com frontmatter (`name: java-quality-gate`, `description` "pushy" cobrindo quando disparar: sempre que o agente terminar de implementar ou alterar código Java em `app/backend-api`).
- [x] 5.3 Corpo da skill: instrução para implementar/ajustar testes unitários JUnit 5 + Mockito; depois rodar `run-coverage.sh` e `run-mutation.sh`, explicando o protocolo de repassar `--previous-signature`/`--attempt` entre chamadas (a memória do loop vive na conversa do agente, não em disco).
- [x] 5.4 Documentar o loop de decisão a partir do `STATUS` retornado: `PASS` → segue para a próxima verificação (ou conclui); `FAIL` → corrige os gaps listados e chama o script de novo passando a assinatura/tentativa recebidas; `STUCK` → para o loop e reporta ao usuário o cenário travado, sem tentar de novo.
- [x] 5.5 Documentar a regra de exclusão de mutantes (`excludedMethods`/`excludedClasses` no pom.xml): só permitida depois que o mesmo mutante foi reportado como `STUCK`, sempre acompanhada de comentário de justificativa no pom.xml.
- [x] 5.6 Documentar a ordem de execução: coverage até PASS primeiro, depois mutação até PASS (mutação avaliada sobre testes que já satisfazem 100% de linha).

## 6. Integração com o fluxo SDD do OpenSpec

- [x] 6.1 Ler `openspec/config.yaml` atual para entender o formato de rules/schemas suportado pela versão instalada do OpenSpec CLI. (CLI `@fission-ai/openspec` 1.5.0, schema `spec-driven` v1: `config.yaml` só suporta as chaves `schema` e `context` — não existe uma seção dedicada de "rules" por etapa. O bloco `context` é injetado como contexto em toda geração de artefato, inclusive `tasks`, então é o lugar correto pra essa instrução.)
- [x] 6.2 Adicionar em `openspec/config.yaml` uma rule (na seção aplicável ao schema `spec-driven`, etapa de geração de `tasks`) referenciando a skill `java-quality-gate`, instruindo que changes com impacto em `app/backend-api` (código Java) devem incluir no `tasks.md` gerado uma task explícita de rodar essa skill antes de a change ser apply-ready. (Adicionada como seção `## Rule: java-quality-gate no tasks.md` dentro do bloco `context:`.)
- [x] 6.3 Validar a mudança em `config.yaml` com `openspec validate` (ou comando equivalente da CLI instalada) para garantir que o YAML continua válido para o schema. (`openspec schema validate spec-driven` → válido; `openspec validate java-quality-gate` → válido.)

## 7. Validação final

- [x] 7.1 Rodar a skill de ponta a ponta contra pelo menos uma classe Java existente em `app/backend-api` (mesmo que trivial) para confirmar que o loop de coverage e mutação funciona e que a trava dispara corretamente em um cenário simulado (ex.: forçar um teste incompleto de propósito). (Rodado `run-coverage.sh` contra `NotebooklmApplication` real: 1ª chamada retornou `FAIL` com 33.33% de line coverage e os gaps `main():10,11`; repassando `--previous-signature`/`--attempt` 5 vezes seguidas com a mesma assinatura, a 4ª repetição — `--attempt 5` — retornou `STATUS: STUCK` como esperado. Verificação de mutação (`run-mutation.sh`) não pôde ser exercitada de ponta a ponta por falta de classe com lógica mutável no skeleton atual — ver nota da task 1.4; o script em si (`check_mutation.py`) foi validado isoladamente com XML fake na sessão anterior.)
- [x] 7.2 Confirmar que `openspec validate --change java-quality-gate` passa sem erros antes de considerar a change pronta para `/opsx:apply`. (Comando correto na CLI instalada é `openspec validate java-quality-gate`, sem `--change`; passou.)
