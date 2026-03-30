# ============================================================
# Multi-stage Dockerfile for Collaborative Document Editor
# Stage 1: Build with Maven
# Stage 2: Run with lightweight JRE
# ============================================================

# --- Build Stage ---
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Build the application (skip tests for faster builds; run tests in CI)
COPY src ./src
RUN mvn clean package -DskipTests -B && \
    mv target/collaborative-editor-1.0.0.jar app.jar

# --- Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/app.jar .

# Curl for healthcheck
RUN apk add --no-cache curl

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar app.jar"]
