# Multi-stage Docker build for Leave Application System Backend
# Stage 1: Build JAR using Maven and Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and pom.xml first for layer caching
COPY pom.xml .
COPY src ./src

# Install maven and package application (skipping live integration tests in container build)
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Stage 2: Minimal Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create unprivileged application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create persistent data directory for H2
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser

# Copy built artifact from builder stage
COPY --from=builder /workspace/target/leave-backend-1.0.0.jar app.jar

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENV SERVER_PORT=8081
ENV ZEEBE_GATEWAY_ADDRESS=zeebe:26500

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
