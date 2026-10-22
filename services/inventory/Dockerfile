# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY contracts/pom.xml contracts/
COPY libs/pom.xml libs/
COPY libs/observability-starter/pom.xml libs/observability-starter/
COPY libs/security-starter/pom.xml libs/security-starter/
COPY libs/outbox-starter/pom.xml libs/outbox-starter/
COPY libs/business-calendar/pom.xml libs/business-calendar/
COPY libs/test-support/pom.xml libs/test-support/
COPY services/pom.xml services/
COPY services/gateway/pom.xml services/gateway/
COPY services/inventory/pom.xml services/inventory/
COPY tools/pom.xml tools/
COPY tools/external-stubs/pom.xml tools/external-stubs/
COPY infra/pom.xml infra/
COPY load-tests/pom.xml load-tests/
COPY spikes/s1-operaton/pom.xml spikes/s1-operaton/
COPY spikes/s2-debezium-outbox/pom.xml spikes/s2-debezium-outbox/

RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B -pl services/inventory -am || true

COPY libs/observability-starter/src libs/observability-starter/src
COPY libs/security-starter/src libs/security-starter/src
COPY libs/outbox-starter/src libs/outbox-starter/src
COPY services/inventory/src services/inventory/src
RUN ./mvnw clean package -pl services/inventory -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/services/inventory/target/smart-rent-hub-inventory-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
