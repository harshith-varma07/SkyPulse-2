#!/bin/bash

# AirSight Environment Configuration Generator
# This script automatically generates .env files from application.properties

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 AirSight Environment Configuration Generator${NC}"
echo "=================================================="

# Configuration
PROPERTIES_DIR="src/main/resources"
OUTPUT_DIR="docker"
DEFAULT_ENV_FILE="$OUTPUT_DIR/.env"
GCP_ENV_FILE="$OUTPUT_DIR/.env.gcp"

# Function to print status
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to extract properties from application.properties files
extract_properties() {
    local properties_file="$1"
    local env_file="$2"
    local description="$3"
    
    if [ ! -f "$properties_file" ]; then
        print_warning "Properties file not found: $properties_file"
        return
    fi
    
    print_status "Processing $description..."
    
    # Extract database configuration
    DB_URL=$(grep "^spring.datasource.url" "$properties_file" | cut -d'=' -f2- | sed 's/\${DATABASE_URL://' | sed 's/}//' || echo "")
    DB_USERNAME=$(grep "^spring.datasource.username" "$properties_file" | cut -d'=' -f2- | sed 's/\${DB_USERNAME://' | sed 's/}//' || echo "")
    DB_PASSWORD=$(grep "^spring.datasource.password" "$properties_file" | cut -d'=' -f2- | sed 's/\${DB_PASSWORD://' | sed 's/}//' || echo "")
    
    # Extract server configuration
    SERVER_PORT=$(grep "^server.port" "$properties_file" | cut -d'=' -f2 || echo "8080")
    
    # Extract OpenAQ configuration
    OPENAQ_URL=$(grep "^openaq.api.url" "$properties_file" | cut -d'=' -f2- || echo "")
    OPENAQ_TIMEOUT=$(grep "^openaq.api.timeout" "$properties_file" | cut -d'=' -f2 || echo "10000")
    
    # Extract Twilio configuration
    TWILIO_SID=$(grep "^twilio.account.sid" "$properties_file" | cut -d'=' -f2- | sed 's/\${TWILIO_ACCOUNT_SID://' | sed 's/}//' || echo "")
    TWILIO_TOKEN=$(grep "^twilio.auth.token" "$properties_file" | cut -d'=' -f2- | sed 's/\${TWILIO_AUTH_TOKEN://' | sed 's/}//' || echo "")
    TWILIO_PHONE=$(grep "^twilio.phone.number" "$properties_file" | cut -d'=' -f2- | sed 's/\${TWILIO_PHONE_NUMBER://' | sed 's/}//' || echo "")
    
    # Extract logging configuration
    LOG_LEVEL=$(grep "^logging.level.com.air.airquality" "$properties_file" | cut -d'=' -f2 || echo "INFO")
    SHOW_SQL=$(grep "^spring.jpa.show-sql" "$properties_file" | cut -d'=' -f2 || echo "false")
    
    # Extract performance configuration
    POOL_SIZE=$(grep "^spring.datasource.hikari.maximum-pool-size" "$properties_file" | cut -d'=' -f2 || echo "10")
    BATCH_SIZE=$(grep "^alert.batch.size" "$properties_file" | cut -d'=' -f2 || echo "10")
    
    # Write to environment file
    cat > "$env_file" << EOF
# AirSight Environment Configuration
# Generated automatically from $properties_file
# $(date)

# ==============================================
# DATABASE CONFIGURATION
# ==============================================
DATABASE_URL=${DB_URL:-jdbc:mysql://mysql:3306/airqualitydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
DB_USERNAME=${DB_USERNAME:-airsight_user}
DB_PASSWORD=${DB_PASSWORD:-airsight_secure_password_2024}
DB_ROOT_PASSWORD=airsight_root_secure_password_2024
DB_PORT=3307

# ==============================================
# APPLICATION CONFIGURATION
# ==============================================
APP_PORT=${SERVER_PORT}
LOG_LEVEL=${LOG_LEVEL}
SHOW_SQL=${SHOW_SQL}
ENVIRONMENT=production

# ==============================================
# PERFORMANCE CONFIGURATION
# ==============================================
HIKARI_POOL_SIZE=${POOL_SIZE}
ALERT_BATCH_SIZE=${BATCH_SIZE}

# ==============================================
# EXTERNAL API CONFIGURATION
# ==============================================
OPENAQ_API_URL=${OPENAQ_URL}
OPENAQ_API_KEY=your_openaq_api_key_here
OPENAQ_TIMEOUT=${OPENAQ_TIMEOUT}

# ==============================================
# SMS NOTIFICATION CONFIGURATION (Optional)
# ==============================================
TWILIO_ACCOUNT_SID=${TWILIO_SID:-your_twilio_account_sid}
TWILIO_AUTH_TOKEN=${TWILIO_TOKEN:-your_twilio_auth_token}
TWILIO_PHONE_NUMBER=${TWILIO_PHONE:-+1234567890}

# ==============================================
# REDIS CONFIGURATION
# ==============================================
REDIS_HOST=redis
REDIS_PORT=6379

# ==============================================
# NGINX CONFIGURATION
# ==============================================
NGINX_PORT=80
NGINX_SSL_PORT=443

# ==============================================
# JVM CONFIGURATION
# ==============================================
JAVA_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

# ==============================================
# TIMEZONE CONFIGURATION
# ==============================================
TZ=UTC

# ==============================================
# MONITORING CONFIGURATION
# ==============================================
HEALTH_CHECK_ENABLED=true
METRICS_ENABLED=true

# ==============================================
# BACKUP CONFIGURATION
# ==============================================
BACKUP_ENABLED=true
BACKUP_SCHEDULE=0 2 * * *
BACKUP_RETENTION_DAYS=30
EOF

    print_status "Environment file generated: $env_file"
}

# Function to generate Google Cloud specific environment file
generate_gcp_env() {
    local gcp_env="$1"
    
    print_status "Generating Google Cloud Platform specific configuration..."
    
    cat > "$gcp_env" << 'EOF'
# AirSight Google Cloud Platform Configuration
# Generated automatically for GCP deployment

# ==============================================
# GOOGLE CLOUD CONFIGURATION
# ==============================================
GCP_PROJECT_ID=your-project-id
GCP_REGION=us-central1
GCP_ZONE=us-central1-a

# ==============================================
# CLOUD SQL CONFIGURATION
# ==============================================
# Cloud SQL instance connection name: PROJECT_ID:REGION:INSTANCE_NAME
CLOUD_SQL_CONNECTION_NAME=your-project-id:us-central1:airsight-mysql
DATABASE_URL=jdbc:mysql://127.0.0.1:3306/airqualitydb?cloudSqlInstance=your-project-id:us-central1:airsight-mysql&socketFactory=com.google.cloud.sql.mysql.SocketFactory&useSSL=false
DB_USERNAME=airsight_user
DB_PASSWORD=airsight_secure_password_2024
DB_ROOT_PASSWORD=airsight_root_secure_password_2024

# ==============================================
# CLOUD RUN CONFIGURATION
# ==============================================
CLOUD_RUN_SERVICE_NAME=airsight
CLOUD_RUN_REGION=us-central1
CLOUD_RUN_MAX_INSTANCES=10
CLOUD_RUN_MIN_INSTANCES=1
CLOUD_RUN_CPU=2
CLOUD_RUN_MEMORY=2Gi
CLOUD_RUN_CONCURRENCY=80
CLOUD_RUN_TIMEOUT=300

# ==============================================
# APPLICATION CONFIGURATION
# ==============================================
APP_PORT=8080
LOG_LEVEL=INFO
SHOW_SQL=false
ENVIRONMENT=production

# ==============================================
# EXTERNAL API CONFIGURATION
# ==============================================
OPENAQ_API_URL=https://api.openaq.org/v2/latest
OPENAQ_API_KEY=your_openaq_api_key_here
OPENAQ_TIMEOUT=10000

# ==============================================
# SECRET MANAGER CONFIGURATION
# ==============================================
# Store sensitive data in Google Cloud Secret Manager
TWILIO_ACCOUNT_SID_SECRET=projects/your-project-id/secrets/twilio-account-sid/versions/latest
TWILIO_AUTH_TOKEN_SECRET=projects/your-project-id/secrets/twilio-auth-token/versions/latest
TWILIO_PHONE_NUMBER_SECRET=projects/your-project-id/secrets/twilio-phone-number/versions/latest

# ==============================================
# MEMORYSTORE REDIS CONFIGURATION
# ==============================================
REDIS_HOST=10.0.0.3
REDIS_PORT=6379

# ==============================================
# JVM CONFIGURATION (Cloud Run Optimized)
# ==============================================
JAVA_OPTS=-Xmx1536m -Xms768m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom

# ==============================================
# TIMEZONE CONFIGURATION
# ==============================================
TZ=UTC

# ==============================================
# MONITORING CONFIGURATION
# ==============================================
HEALTH_CHECK_ENABLED=true
METRICS_ENABLED=true
GOOGLE_CLOUD_MONITORING_ENABLED=true

# ==============================================
# LOGGING CONFIGURATION
# ==============================================
GOOGLE_CLOUD_LOGGING_ENABLED=true
LOG_FORMAT=json
EOF

    print_status "Google Cloud environment file generated: $gcp_env"
}

# Main execution
main() {
    print_status "Starting environment configuration generation..."
    
    # Create output directory if it doesn't exist
    mkdir -p "$OUTPUT_DIR"
    
    # Generate standard environment file from production properties
    if [ -f "$PROPERTIES_DIR/application-prod.properties" ]; then
        extract_properties "$PROPERTIES_DIR/application-prod.properties" "$DEFAULT_ENV_FILE" "production properties"
    elif [ -f "$PROPERTIES_DIR/application.properties" ]; then
        extract_properties "$PROPERTIES_DIR/application.properties" "$DEFAULT_ENV_FILE" "default properties"
    else
        print_error "No application properties file found!"
        exit 1
    fi
    
    # Generate Google Cloud specific configuration
    generate_gcp_env "$GCP_ENV_FILE"
    
    echo ""
    print_status "✅ Environment configuration generation completed!"
    echo ""
    echo "📋 Generated files:"
    echo "   • $DEFAULT_ENV_FILE - Standard deployment configuration"
    echo "   • $GCP_ENV_FILE - Google Cloud Platform configuration"
    echo ""
    echo "📝 Next steps:"
    echo "   1. Review and update the generated .env files with your specific values"
    echo "   2. For Google Cloud deployment, copy .env.gcp to .env and update GCP_PROJECT_ID"
    echo "   3. Set up secrets in Google Cloud Secret Manager for sensitive data"
    echo "   4. Run the deployment script for your target platform"
    echo ""
}

# Run main function
main "$@"