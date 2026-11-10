# ==============================================================================
# Multi-stage Dockerfile for Spring Boot Backend (Java 25 LTS)
# ==============================================================================

# Stage 1: Build Application with Maven Wrapper
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace/app

# Copy Maven wrapper & pom.xml for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copy source code and build production jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal JRE Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Run as non-root service user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/app/target/*.jar app.jar

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
