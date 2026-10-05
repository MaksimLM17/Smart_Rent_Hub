# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY contracts/pom.xml contracts/
COPY libs/pom.xml libs/
COPY services/pom.xml services/
COPY tools/pom.xml tools/
COPY tools/external-stubs/pom.xml tools/external-stubs/
COPY infra/pom.xml infra/
COPY load-tests/pom.xml load-tests/
COPY spikes/s1-operaton/pom.xml spikes/s1-operaton/

RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B -pl tools/external-stubs -am || true

COPY tools/external-stubs/src tools/external-stubs/src
RUN ./mvnw clean package -pl tools/external-stubs -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/tools/external-stubs/target/smart-rent-hub-external-stubs-*.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]
