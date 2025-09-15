# Multi-stage Dockerfile for JSimpleRag

# Stage 1: Build stage
FROM maven:3.9-openjdk-17-slim AS builder

WORKDIR /build

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM openjdk:17-jdk-slim

WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 jsimplerag && \
    adduser --system --uid 1001 --gid 1001 jsimplerag

# Copy built application
COPY --from=builder /build/target/simplerag-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R jsimplerag:jsimplerag /app

# Install PostgreSQL client for health checks
RUN apt-get update && \
    apt-get install -y postgresql-client && \
    rm -rf /var/lib/apt/lists/*

# Switch to non-root user
USER jsimplerag

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx1g -XX:+UseContainerSupport"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]