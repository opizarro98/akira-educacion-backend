# syntax=docker/dockerfile:1

# ---- Build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

RUN apk add --no-cache bash

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- Runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S akira && adduser -S akira -G akira

COPY --from=build /workspace/target/*.jar /app/app.jar

USER akira
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
