# Configuração dos plugins Maven (JaCoCo + PITest)

Leia este arquivo só quando `app/backend-api/pom.xml` ainda não tiver os
plugins abaixo — os scripts da skill dependem deles pra gerar
`target/site/jacoco/jacoco.xml` e `target/pit-reports/**/mutations.xml`.

## JaCoCo — 100% line coverage

Adicionar dentro de `<build><plugins>`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>1.00</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <haltOnFailure>true</haltOnFailure>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## PITest — 100% mutation score

Adicionar dentro de `<build><plugins>`:

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.17.0</version>
    <configuration>
        <targetClasses>
            <param>tech.buildrun.notebooklm.*</param>
        </targetClasses>
        <targetTests>
            <param>tech.buildrun.notebooklm.*</param>
        </targetTests>
        <mutationThreshold>100</mutationThreshold>
        <!-- Só adicione entradas aqui depois que o mutante foi reportado
             como STUCK pelo run-mutation.sh, sempre com comentário
             justificando por que é equivalente/impossível de matar. -->
        <excludedMethods>
            <!-- <param>Classe#metodo: motivo</param> -->
        </excludedMethods>
        <excludedClasses>
            <!-- <param>tech.buildrun.notebooklm.Foo: motivo</param> -->
        </excludedClasses>
    </configuration>
</plugin>
```

Ajuste `groupId`/versões conforme o que o `mvn help:evaluate` ou o BOM do
projeto resolver como versão estável no momento da implementação — os
números acima são um ponto de partida, não um pin obrigatório.

## Dependências de teste

`spring-boot-starter-data-jpa-test` e `spring-boot-starter-webmvc-test`
já trazem JUnit 5 transitivamente. Se Mockito não vier junto, adicionar:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```
