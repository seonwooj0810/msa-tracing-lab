# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY bff ./bff
COPY member-service ./member-service
COPY order-service ./order-service
COPY product-service ./product-service
ARG MODULE
RUN --mount=type=cache,target=/root/.gradle ./gradlew :${MODULE}:bootJar --no-daemon
RUN cp ${MODULE}/build/libs/app.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/app.jar ./app.jar
USER 10001:10001
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=15 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
