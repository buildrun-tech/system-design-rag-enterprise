## Purpose

Fornecer um gate determinístico e automatizado de qualidade para código Java, garantindo 100% de line coverage (JaCoCo) e 100% de mutation score (PITest) antes de uma implementação Java ser considerada completa, eliminando o julgamento subjetivo do agente sobre "cobertura suficiente" e prevenindo testes fracos que passam mesmo com lógica quebrada.

## Requirements

### Requirement: Verificação determinística de line coverage sem escrita em disco
O sistema SHALL fornecer um script (`run-coverage.sh` + `check_coverage.py`) que executa `mvn test` e `jacoco:report` sobre `app/backend-api`, parseia o relatório JaCoCo gerado em memória, e imprime no stdout um bloco de texto com `STATUS: PASS` ou `STATUS: FAIL` conforme o line coverage agregado do módulo seja 100% ou menor. O script SHALL NOT criar, gravar ou persistir nenhum arquivo próprio no disco (relatórios do Maven/JaCoCo em `target/` são artefato do build, não do script). Ao reportar FAIL, o script SHALL incluir no texto de saída, para cada arquivo:linha não coberto, uma instrução fixa de correção (ex.: "adicione um teste unitário que exercite esta linha").

#### Scenario: Coverage completo
- **WHEN** todo o código fonte Java em `app/backend-api` tem 100% de linhas cobertas pelos testes unitários
- **THEN** o script imprime `STATUS: PASS` com "line coverage: 100%" e não grava nenhum arquivo próprio no disco

#### Scenario: Coverage incompleto
- **WHEN** existe pelo menos uma linha de código fonte não exercitada pelos testes
- **THEN** o script imprime `STATUS: FAIL`, lista cada arquivo:linha não coberto e a instrução fixa de correção correspondente, sem gravar nenhum arquivo próprio no disco

### Requirement: Verificação determinística de mutation score sem escrita em disco
O sistema SHALL fornecer um script (`run-mutation.sh` + `check_mutation.py`) que executa PITest (`org.pitest:pitest-maven:mutationCoverage`) sobre `app/backend-api`, parseia `mutations.xml` em memória, e imprime no stdout um bloco de texto com `STATUS: PASS` ou `STATUS: FAIL` conforme o mutation score (mutantes killed / total, contando SURVIVED e NO_COVERAGE como falha) seja 100% ou menor. O script SHALL NOT criar, gravar ou persistir nenhum arquivo próprio no disco. Ao reportar FAIL, o script SHALL incluir no texto de saída, para cada mutante não morto, classe:linha:descrição do mutante e uma instrução fixa de correção (ex.: "escreva um teste que diferencie o comportamento original do mutante nesta linha").

#### Scenario: Mutation score completo
- **WHEN** todos os mutantes gerados pelo PITest sobre o código de `app/backend-api` são mortos pelos testes (excluindo mutantes explicitamente configurados como excluídos no pom.xml)
- **THEN** o script imprime `STATUS: PASS` com "mutation score: 100%" e não grava nenhum arquivo próprio no disco

#### Scenario: Mutante sobrevivente
- **WHEN** existe pelo menos um mutante SURVIVED ou NO_COVERAGE não coberto por exclusão configurada
- **THEN** o script imprime `STATUS: FAIL`, lista classe:linha:mutante e a instrução fixa de correção para cada mutante sobrevivente, sem gravar nenhum arquivo próprio no disco

### Requirement: Loop de auto-correção guiado pela skill
Quando um dos scripts de verificação falhar, a skill `java-quality-gate` SHALL instruir o agente a corrigir a implementação e/ou os testes unitários (JUnit 5 + Mockito) e executar o script novamente, repetindo o ciclo até que ambos os scripts retornem sucesso.

#### Scenario: Falha seguida de correção
- **WHEN** o script de coverage ou mutação falha reportando gaps específicos
- **THEN** a skill instrui o agente a escrever/ajustar testes visando exatamente os gaps reportados e rodar o script novamente

### Requirement: Trava anti-loop-infinito sem estado em disco
O script SHALL calcular, a cada execução, uma assinatura estável (hash) do conjunto de gaps atuais e aceitar como argumentos de linha de comando opcionais `--previous-signature <hash>` e `--attempt <N>` (a memória entre execuções vive na conversa do agente, nunca em arquivo). Se a assinatura calculada for igual à `--previous-signature` recebida e `--attempt` já tiver atingido 5, o script SHALL retornar `STATUS: STUCK` em vez de `STATUS: FAIL`, reportando a assinatura, a lista de gaps e o número de tentativas — e a skill SHALL instruir o agente a parar e reportar ao usuário em vez de tentar novamente. A skill SHALL instruir o agente a sempre repassar a assinatura e o contador retornados por uma execução como entrada da próxima chamada do mesmo script.

#### Scenario: Progresso entre execuções
- **WHEN** a assinatura calculada na execução atual é diferente da `--previous-signature` recebida
- **THEN** o script retorna `STATUS: FAIL` (não `STUCK`), e instrui a skill a orientar o agente a reiniciar a contagem (`--attempt 1`) na próxima chamada

#### Scenario: Cenário travado
- **WHEN** a assinatura calculada é igual à `--previous-signature` recebida e `--attempt` já é 5 ou mais
- **THEN** o script retorna `STATUS: STUCK` com a assinatura, os gaps e o número de tentativas, e a skill instrui o agente a parar o loop e reportar ao usuário em vez de tentar novamente

### Requirement: Exceção auditável para mutantes equivalentes
O sistema SHALL permitir excluir um mutante do cálculo de mutation score apenas via configuração do PITest (`excludedMethods`/`excludedClasses` no `pom.xml`), e apenas para mutantes que já foram reportados pelo mecanismo de trava (cenário travado). Cada exclusão SHALL vir acompanhada de um comentário no `pom.xml` justificando por que o mutante é considerado equivalente ou impossível de matar.

#### Scenario: Exclusão válida
- **WHEN** um mutante foi reportado como cenário travado pelo mecanismo de trava
- **THEN** é permitido adicionar uma entrada de exclusão no pom.xml para esse mutante/método, acompanhada de comentário justificando a decisão

#### Scenario: Exclusão sem trava prévia
- **WHEN** um mutante ainda não foi reportado como cenário travado (ainda dentro do limite de tentativas)
- **THEN** a skill não deve instruir ou aceitar a adição de exclusão para esse mutante — o agente deve continuar tentando matá-lo

### Requirement: Task obrigatória no fluxo SDD para código Java
O `openspec/config.yaml` SHALL referenciar a skill `java-quality-gate` como rule aplicável durante a geração do artefato `tasks.md`. Sempre que uma change do OpenSpec envolver modificações em código Java (`app/backend-api`), o `tasks.md` gerado SHALL incluir uma task explícita de executar a skill `java-quality-gate` antes de a change ser considerada apply-ready.

#### Scenario: Change envolvendo código Java
- **WHEN** o proposal/design de uma change descrevem impacto em `app/backend-api`
- **THEN** o `tasks.md` gerado inclui uma task explícita de rodar a skill `java-quality-gate`

#### Scenario: Change sem impacto em código Java
- **WHEN** uma change não descreve nenhum impacto em código Java
- **THEN** o `tasks.md` gerado não é obrigado a incluir a task de `java-quality-gate`
