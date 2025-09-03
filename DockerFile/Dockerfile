# Multi-stage build for SkyPulse Air Quality Monitoring
# Optimized for production deployment with Java 17

# Build stage
FROM maven:3.8.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
COPY frontend ./frontend
RUN mvn clean package -DskipTests -Pprod

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install necessary packages and Python for analytics fallback
RUN apk add --no-cache \
    tzdata \
    curl \
    && cp /usr/share/zoneinfo/UTC /etc/localtime \
    && echo "UTC" > /etc/timezone \
    && apk del tzdata

# Create non-root user for security
RUN addgroup -g 1001 -S skypulse && \
    adduser -S -D -H -u 1001 -h /app -s /sbin/nologin -G skypulse -g skypulse skypulse

WORKDIR /app

# Copy the jar from builder stage
COPY --from=builder /app/target/air-quality-monitoring-*.jar app.jar

# Copy frontend files
COPY --from=builder /app/frontend ./static

# Create directories for data and logs
RUN mkdir -p ./data ./logs && chown -R skypulse:skypulse /app

# Switch to non-root user
USER skypulse

# Health check using our custom endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/system/alive || exit 1

# Expose port
EXPOSE 8080

# Environment variables with production defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200"
ENV DATABASE_URL="jdbc:h2:file:./data/airquality;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE"
ENV LOG_LEVEL="INFO"

# Start the application with optimized JVM settings
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
