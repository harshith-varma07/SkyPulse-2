#!/bin/bash

# AirSight Deployment Script
# This script automates the deployment process for AirSight application

set -e

echo "🚀 Starting AirSight Deployment Process..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="airsight"
DOCKER_COMPOSE_FILE="docker/docker-compose.yml"
ENV_FILE="docker/.env"

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is installed and running
check_docker() {
    print_status "Checking Docker installation..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker daemon."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    print_status "Docker and Docker Compose are available ✓"
}

# Setup environment file
setup_environment() {
    print_status "Setting up environment configuration..."
    
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "docker/.env.example" ]; then
            cp docker/.env.example "$ENV_FILE"
            print_warning "Created .env file from example. Please configure your settings in $ENV_FILE"
        else
            print_error "No environment file found. Please create $ENV_FILE"
            exit 1
        fi
    else
        print_status "Environment file exists ✓"
    fi
}

# Build the application
build_application() {
    print_status "Building AirSight application..."
    
    # Clean and compile
    if command -v mvn &> /dev/null; then
        print_status "Cleaning and compiling with Maven..."
        mvn clean compile
    else
        print_warning "Maven not found. Using Docker build only."
    fi
    
    # Build Docker images
    print_status "Building Docker images..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" build --no-cache
    
    print_status "Application build completed ✓"
}

# Deploy the application
deploy_application() {
    print_status "Deploying AirSight application..."
    
    # Stop existing containers if any
    print_status "Stopping existing containers..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" down --remove-orphans
    
    # Start the application stack
    print_status "Starting application stack..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" up -d
    
    print_status "Deployment completed ✓"
}

# Check application health
check_health() {
    print_status "Checking application health..."
    
    # Wait for services to start
    sleep 30
    
    # Check MySQL health
    if docker-compose -f "$DOCKER_COMPOSE_FILE" exec mysql mysqladmin ping -h localhost --silent; then
        print_status "MySQL is healthy ✓"
    else
        print_warning "MySQL health check failed"
    fi
    
    # Check Redis health (if enabled)
    if docker-compose -f "$DOCKER_COMPOSE_FILE" ps | grep redis &> /dev/null; then
        if docker-compose -f "$DOCKER_COMPOSE_FILE" exec redis redis-cli ping | grep PONG &> /dev/null; then
            print_status "Redis is healthy ✓"
        else
            print_warning "Redis health check failed"
        fi
    fi
    
    # Check application health
    max_attempts=12
    attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        print_status "Checking application health (attempt $attempt/$max_attempts)..."
        
        if curl -f http://localhost:8080/api/health &> /dev/null; then
            print_status "Application is healthy ✓"
            break
        else
            if [ $attempt -eq $max_attempts ]; then
                print_error "Application health check failed after $max_attempts attempts"
                return 1
            fi
            sleep 10
            ((attempt++))
        fi
    done
}

# Display deployment information
show_deployment_info() {
    print_status "🎉 AirSight deployment completed successfully!"
    echo ""
    echo "📊 Application URLs:"
    echo "   Frontend: http://localhost:8080"
    echo "   API: http://localhost:8080/api"
    echo "   Health Check: http://localhost:8080/api/health"
    echo ""
    echo "🗄️ Database Access:"
    echo "   MySQL: localhost:3307"
    echo "   Database: airqualitydb"
    echo ""
    echo "📝 Useful Commands:"
    echo "   View logs: docker-compose -f $DOCKER_COMPOSE_FILE logs -f"
    echo "   Stop services: docker-compose -f $DOCKER_COMPOSE_FILE down"
    echo "   Restart services: docker-compose -f $DOCKER_COMPOSE_FILE restart"
    echo "   Access MySQL: docker-compose -f $DOCKER_COMPOSE_FILE exec mysql mysql -u airsight_user -p airqualitydb"
    echo ""
}

# Cleanup on failure
cleanup_on_failure() {
    print_error "Deployment failed. Cleaning up..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" down --remove-orphans
    exit 1
}

# Main deployment process
main() {
    trap cleanup_on_failure ERR
    
    echo "🌟 AirSight - Real-time Air Quality Monitoring System"
    echo "=================================================="
    echo ""
    
    check_docker
    setup_environment
    build_application
    deploy_application
    
    if check_health; then
        show_deployment_info
    else
        print_error "Health check failed. Please check the logs."
        docker-compose -f "$DOCKER_COMPOSE_FILE" logs
        exit 1
    fi
}

# Run main function
main "$@"
