FROM eclipse-temurin:25-jdk AS builder
WORKDIR /builder

# deps em layer própria: só invalida se pom.xml mudar
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# testes rodam no CI (backend.yml), não no build da imagem
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home app
USER app

# ordem: layers que mudam menos primeiro, cache melhor
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "notebooklm-0.0.1-SNAPSHOT.jar"]
