#!/bin/bash

# AirSight Google Cloud Platform Deployment Script
# This script automates the deployment process for Google Cloud Platform

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 AirSight Google Cloud Platform Deployment${NC}"
echo "=================================================="

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

print_header() {
    echo -e "\n${BLUE}$1${NC}"
    echo "$(printf '=%.0s' {1..50})"
}

# Check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if gcloud is installed
    if ! command -v gcloud &> /dev/null; then
        print_error "Google Cloud SDK (gcloud) is not installed"
        print_status "Install it from: https://cloud.google.com/sdk/docs/install"
        exit 1
    fi
    
    # Check if user is authenticated
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q "@"; then
        print_error "Not authenticated with Google Cloud"
        print_status "Run: gcloud auth login"
        exit 1
    fi
    
    # Get current project
    PROJECT_ID=$(gcloud config get-value project 2>/dev/null || echo "")
    if [ -z "$PROJECT_ID" ]; then
        print_error "No Google Cloud project selected"
        print_status "Run: gcloud config set project YOUR_PROJECT_ID"
        exit 1
    fi
    
    print_status "✓ Prerequisites met"
    print_status "✓ Authenticated user: $(gcloud auth list --filter=status:ACTIVE --format="value(account)")"
    print_status "✓ Selected project: $PROJECT_ID"
}

# Generate configuration
generate_config() {
    print_header "Generating Configuration"
    
    print_status "Running environment configuration generator..."
    ./scripts/generate-env.sh
    
    # Copy GCP config as the default
    if [ -f "docker/.env.gcp" ]; then
        cp docker/.env.gcp docker/.env.temp
        sed -i "s/your-project-id/$PROJECT_ID/g" docker/.env.temp
        mv docker/.env.temp docker/.env.gcp
        print_status "✓ Updated GCP configuration with project ID: $PROJECT_ID"
    fi
}

# Enable APIs
enable_apis() {
    print_header "Enabling Required APIs"
    
    apis=(
        "cloudbuild.googleapis.com"
        "run.googleapis.com"
        "sqladmin.googleapis.com"
        "secretmanager.googleapis.com"
        "container.googleapis.com"
    )
    
    for api in "${apis[@]}"; do
        print_status "Enabling $api..."
        gcloud services enable "$api" --quiet
    done
    
    print_status "✓ All required APIs enabled"
}

# Deploy with Terraform
deploy_terraform() {
    print_header "Deploying Infrastructure with Terraform"
    
    if [ ! -f "terraform/terraform.tfvars" ]; then
        print_status "Creating Terraform variables file..."
        cp terraform/terraform.tfvars.example terraform/terraform.tfvars
        sed -i "s/your-project-id/$PROJECT_ID/g" terraform/terraform.tfvars
        print_warning "Please review and update terraform/terraform.tfvars with your preferences"
        read -p "Press Enter to continue after reviewing terraform.tfvars..."
    fi
    
    cd terraform
    
    print_status "Initializing Terraform..."
    terraform init -input=false
    
    print_status "Planning infrastructure..."
    terraform plan -input=false
    
    echo -e "\n${YELLOW}About to create Google Cloud infrastructure.${NC}"
    echo "This will create:"
    echo "  • Cloud SQL MySQL instance"
    echo "  • Memorystore Redis instance" 
    echo "  • Secret Manager secrets"
    echo "  • Service accounts and IAM bindings"
    echo "  • Cloud Run service (placeholder)"
    echo ""
    
    read -p "Continue with deployment? (y/N): " confirm
    if [[ $confirm =~ ^[Yy]$ ]]; then
        print_status "Applying infrastructure..."
        terraform apply -auto-approve
        print_status "✓ Infrastructure deployed successfully"
    else
        print_warning "Infrastructure deployment cancelled"
        cd ..
        return
    fi
    
    cd ..
}

# Deploy application with Cloud Build
deploy_application() {
    print_header "Deploying Application"
    
    print_status "Building and deploying with Cloud Build..."
    gcloud builds submit --config=cloudbuild.yaml --substitutions=_REGION=us-central1
    
    print_status "✓ Application deployed successfully"
}

# Show deployment info
show_info() {
    print_header "Deployment Information"
    
    # Get Cloud Run URL
    CLOUD_RUN_URL=$(gcloud run services describe airsight --region=us-central1 --format="value(status.url)" 2>/dev/null || echo "Not deployed")
    
    # Get database info
    DB_CONNECTION=$(gcloud sql instances describe airsight-mysql --format="value(connectionName)" 2>/dev/null || echo "Not created")
    
    echo -e "${GREEN}🎉 AirSight deployment completed!${NC}"
    echo ""
    echo "📊 Application URLs:"
    echo "   Frontend: $CLOUD_RUN_URL"
    echo "   API: $CLOUD_RUN_URL/api"
    echo "   Health Check: $CLOUD_RUN_URL/api/health"
    echo ""
    echo "🗄️ Database:"
    echo "   Connection: $DB_CONNECTION"
    echo ""
    echo "🔧 Next Steps:"
    echo "   1. Update Secret Manager secrets with your API keys:"
    echo "      gcloud secrets versions add openaq-api-key --data-file=- <<< 'your-api-key'"
    echo "   2. Import database schema:"
    echo "      gsutil cp database_setup.sql gs://your-bucket/"
    echo "      gcloud sql import sql airsight-mysql gs://your-bucket/database_setup.sql --database=airqualitydb"
    echo "   3. Test the deployment:"
    echo "      curl $CLOUD_RUN_URL/api/health"
}

# Deployment type selection
select_deployment_type() {
    echo ""
    echo "Select deployment type:"
    echo "1) Full deployment (Terraform + Application)"
    echo "2) Terraform only (Infrastructure)"
    echo "3) Application only (requires existing infrastructure)"
    echo "4) Quick Cloud Run deployment"
    echo ""
    read -p "Enter choice (1-4): " choice
    
    case $choice in
        1)
            deploy_terraform
            deploy_application
            ;;
        2)
            deploy_terraform
            ;;
        3)
            deploy_application
            ;;
        4)
            print_status "Quick deployment using Cloud Build..."
            gcloud builds submit --config=cloudbuild.yaml
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac
}

# Main execution
main() {
    check_prerequisites
    generate_config
    enable_apis
    select_deployment_type
    show_info
}

# Handle script interruption
trap 'echo -e "\n${RED}Deployment interrupted${NC}"; exit 1' INT

# Run main function
main "$@"