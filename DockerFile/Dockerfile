# Multi-stage build for AirSight Spring Boot application
# Optimized for Google Cloud deployment

# Build stage
FROM maven:3.8.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
COPY frontend ./frontend
RUN mvn clean package -DskipTests -Pprod

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install necessary packages
RUN apk add --no-cache \
    tzdata \
    curl \
    && cp /usr/share/zoneinfo/UTC /etc/localtime \
    && echo "UTC" > /etc/timezone \
    && apk del tzdata

# Create non-root user for security
RUN addgroup -g 1001 -S airsight && \
    adduser -S -D -H -u 1001 -h /app -s /sbin/nologin -G airsight -g airsight airsight

WORKDIR /app

# Copy the jar from builder stage
COPY --from=builder /app/target/air-quality-monitoring-*.jar app.jar

# Copy frontend files
COPY --from=builder /app/frontend ./static

# Change ownership
RUN chown -R airsight:airsight /app

# Switch to non-root user
USER airsight

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication"

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
