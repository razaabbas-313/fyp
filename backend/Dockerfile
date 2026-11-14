# =========================================================
# FotoFetch AI Backend - Production Dockerfile for Render
# Multi-stage build with Eclipse Temurin Java 21
# =========================================================

# Stage 1: Build the Spring Boot application
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Pre-fetch dependencies for Docker caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime JRE container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S fotofetch && adduser -S fotofetch -G fotofetch

# Copy built JAR from builder stage
COPY --from=builder /build/target/fotofetch-backend-1.0.0.jar app.jar
RUN chown -R fotofetch:fotofetch /app

USER fotofetch

# Render sets PORT dynamically (e.g. 10000)
ENV PORT=8080
EXPOSE ${PORT}

# Production JVM memory constraints tailored for Render 512MB container limit
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:+UseSerialGC -Xms96m -Xmx224m -XX:MaxMetaspaceSize=112m -Xss384k -XX:+ExitOnOutOfMemoryError -Dserver.port=${PORT} -jar app.jar"]
