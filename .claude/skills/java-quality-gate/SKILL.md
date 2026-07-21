---
name: java-quality-gate
description: 'Enforce a deterministic 100% JUnit 5 line coverage (JaCoCo) and 100% PITest mutation score gate on Java code. 
Use this whenever you finish implementing or modifying Java code in app/backend-api, whenever an OpenSpec task list includes a "run java-quality-gate" step, or whenever the user asks to write unit tests, check coverage, run mutation testing, or verify test quality for this Java codebase. Do not rely on your own judgment about whether tests are "good enough" — always run the scripts and follow their PASS/FAIL/STUCK verdict.'
---

# Java Quality Gate

Testes bons não são um julgamento subjetivo — são um número. Esta skill existe pra tirar
esse julgamento de você e colocar em dois scripts determinísticos: um mede line coverage
(JaCoCo), outro mede mutation score (PITest). Nenhum dos dois escreve nada em disco;
eles leem o relatório do build daquela rodada e devolvem um veredito em texto.

## Fluxo

1. Implemente ou ajuste os testes unitários (JUnit 5 + Mockito) para o código Java em questão.
2. Rode `scripts/run-coverage.sh` até `STATUS: PASS`.
3. Só depois, rode `scripts/run-mutation.sh` até `STATUS: PASS`.
4. Se qualquer um dos dois voltar `STATUS: STUCK`, pare e reporte ao usuário — não tente de novo.

Coverage primeiro, mutação depois: mutantes só fazem sentido sobre código que já tem
100% de linha exercitada — testar mutação de código não coberto é ruído.

## Rodando os scripts

```bash
.claude/skills/java-quality-gate/scripts/run-coverage.sh
.claude/skills/java-quality-gate/scripts/run-mutation.sh
```

Cada script imprime um bloco assim:

```
STATUS: FAIL
LINE_COVERAGE: 87.50%
SIGNATURE: 3f9a1c2b7e0d5a41
ATTEMPT: 1
Gaps de cobertura encontrados:
  - tech.buildrun.notebooklm.source.SourceService.java:42: Adicione ou ajuste um teste unitario JUnit 5 + Mockito que exercite esta linha.
Corrija os gaps acima e rode este script de novo passando --previous-signature 3f9a1c2b7e0d5a41 --attempt 1.
```

`STATUS` é sempre `PASS`, `FAIL` ou `STUCK` — nunca interprete o coverage/mutation
score você mesmo, use exatamente o que o script decidiu.

### O protocolo de memória (sem arquivo em disco)

Os scripts não persistem nada entre execuções. A memória de "quantas vezes seguidas
essa mesma falha apareceu" é sua, dentro desta conversa: quando um script terminar com
`FAIL`, ele imprime `SIGNATURE` e `ATTEMPT` — guarde os dois e repasse na próxima
chamada:

```bash
scripts/run-coverage.sh --previous-signature 3f9a1c2b7e0d5a41 --attempt 1
```

Se os gaps mudarem (você corrigiu algo, mesmo que trocou por outro), o script detecta
sozinho que a assinatura mudou e reseta a contagem — você não precisa calcular nada,
só repassar o que ele te devolveu da vez anterior. Na primeira chamada, não passe
nenhum dos dois argumentos.

### Quando o script travar (`STATUS: STUCK`)

Depois de 5 tentativas consecutivas com exatamente o mesmo conjunto de gaps, o script
para de pedir mais tentativas e devolve `STATUS: STUCK`. Isso significa: pare de tentar
corrigir sozinho e reporte ao usuário o cenário exato (o bloco de saída já traz tudo
que ele precisa pra decidir). Não invente uma correção diferente nem force o gate a
passar sem entender por quê está travado.

Para mutantes especificamente: só depois de um `STUCK` em `run-mutation.sh` é
aceitável considerar aquele mutante equivalente/impossível de matar e excluí-lo via
`excludedMethods`/`excludedClasses` no `pom.xml` — e sempre com um comentário ao lado
explicando o motivo. Excluir um mutante que ainda não passou pelo `STUCK` é trapacear
o gate, não resolvê-lo.

## Se os plugins Maven ainda não estiverem configurados

Os scripts esperam `target/site/jacoco/jacoco.xml` e
`target/pit-reports/**/mutations.xml` existirem depois do build — ou seja, dependem de
`jacoco-maven-plugin` e `pitest-maven` estarem no `pom.xml` de `app/backend-api`. Se um
script falhar reclamando que o relatório não existe, leia `references/pom-plugins.md`
e adicione a configuração de lá antes de tentar de novo.
