# AirSight Deployment Guide for Google Cloud Platform

## Overview
This document provides comprehensive instructions for deploying the AirSight application to Google Cloud Platform using various deployment methods.

## Prerequisites
1. Google Cloud Platform account with billing enabled
2. Google Cloud SDK installed locally
3. Docker installed locally
4. Maven installed locally
5. Git repository access

## Quick Start Commands

### 1. Setup Google Cloud Project
```bash
# Set your project ID
export PROJECT_ID="your-airsight-project-id"

# Create a new project (if needed)
gcloud projects create $PROJECT_ID --name="AirSight"

# Set the project
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable cloudbuild.googleapis.com
gcloud services enable run.googleapis.com
gcloud services enable sql-component.googleapis.com
gcloud services enable sqladmin.googleapis.com
```

### 2. Setup Cloud SQL Database
```bash
# Create Cloud SQL instance
gcloud sql instances create airsight-db \
    --database-version=MYSQL_8_0 \
    --tier=db-f1-micro \
    --region=us-central1

# Create database
gcloud sql databases create airqualitydb --instance=airsight-db

# Create database user
gcloud sql users create airsight_user \
    --instance=airsight-db \
    --password=your_secure_password_here
```

### 3. Deploy using Cloud Build (Recommended)
```bash
# Clone the repository
git clone https://github.com/harshith-varma07/AirSight.git
cd AirSight

# Update substitutions in cloudbuild.yaml with your values
# Then submit the build
gcloud builds submit --config=cloudbuild.yaml \
    --substitutions=_PROJECT_ID=$PROJECT_ID,_DATABASE_URL="jdbc:mysql://INSTANCE_IP:3306/airqualitydb"
```

### 4. Deploy to Cloud Run (Alternative)
```bash
# Build and deploy directly
gcloud run deploy airsight-app \
    --source . \
    --region us-central1 \
    --allow-unauthenticated \
    --port 8080 \
    --memory 1Gi \
    --set-env-vars SPRING_PROFILES_ACTIVE=prod
```

### 5. Deploy to App Engine (Alternative)
```bash
# Update app.yaml with your project-specific values
# Then deploy
gcloud app deploy app.yaml
```

### 6. Deploy to GKE (Advanced)
```bash
# Create GKE cluster
gcloud container clusters create airsight-cluster \
    --zone us-central1-a \
    --num-nodes 2

# Get credentials
gcloud container clusters get-credentials airsight-cluster --zone us-central1-a

# Deploy to Kubernetes
kubectl apply -f k8s-deployment.yaml
```

## Configuration Steps

### Environment Variables
Set the following environment variables or update them in your deployment YAML:

- `SPRING_DATASOURCE_URL`: Your Cloud SQL connection string
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `OPENAQ_API_KEY`: OpenAQ API key for air quality data
- `TWILIO_ACCOUNT_SID`: Twilio SID for SMS notifications
- `TWILIO_AUTH_TOKEN`: Twilio token
- `TWILIO_PHONE_NUMBER`: Your Twilio phone number

### Secret Management
For production deployments, use Google Secret Manager:

```bash
# Store secrets
echo -n "your_database_password" | gcloud secrets create db-password --data-file=-
echo -n "your_api_key" | gcloud secrets create openaq-api-key --data-file=-

# Grant access to Cloud Run
gcloud secrets add-iam-policy-binding db-password \
    --member="serviceAccount:PROJECT_NUMBER-compute@developer.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
```

## Monitoring and Logging

### Enable monitoring
```bash
# Install monitoring agent (for GKE)
kubectl apply -f https://raw.githubusercontent.com/GoogleCloudPlatform/container-engine-accelerators/master/daemonset.yaml
```

### View logs
```bash
# Cloud Run logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=airsight-app"

# App Engine logs
gcloud logging read "resource.type=gae_app"
```

## Scaling Configuration

### Cloud Run Auto-scaling
- Configured in cloudbuild.yaml
- Min instances: 1
- Max instances: 10
- Concurrency: 100 requests per instance

### GKE Horizontal Pod Autoscaler
```bash
kubectl autoscale deployment airsight-app --cpu-percent=50 --min=2 --max=10
```

## Cost Optimization Tips

1. **Use appropriate instance sizes**: Start with smaller instances and scale up as needed
2. **Set up budget alerts**: Monitor spending through GCP console
3. **Use preemptible instances**: For non-critical workloads in GKE
4. **Configure auto-scaling**: Avoid over-provisioning resources

## Security Considerations

1. **Use IAM roles**: Grant minimal required permissions
2. **Enable VPC**: For network isolation
3. **Use HTTPS**: Enable SSL/TLS for all endpoints
4. **Rotate secrets**: Regular rotation of API keys and passwords
5. **Enable audit logging**: Track access and changes

## Troubleshooting

### Common Issues:
1. **Database connection**: Ensure Cloud SQL instance is accessible
2. **Memory issues**: Increase memory allocation if needed
3. **Build failures**: Check Java version compatibility
4. **Network issues**: Verify VPC and firewall rules

### Useful Commands:
```bash
# Check Cloud Run service status
gcloud run services list

# View Cloud Build history
gcloud builds list

# Check GKE cluster status
kubectl get pods

# View application logs
gcloud logging read "resource.type=cloud_run_revision"
```

## Support

For issues with deployment:
1. Check the application logs
2. Verify all environment variables are set correctly
3. Ensure all required APIs are enabled
4. Check IAM permissions

## Files Created

1. **cloudbuild.yaml** - Main build and deployment configuration
2. **Dockerfile** - Container image definition
3. **app.yaml** - App Engine configuration
4. **k8s-deployment.yaml** - Kubernetes deployment manifests
5. **skaffold.yaml** - Development workflow automation
6. **docker-compose.yaml** - Local development environment
7. **.gcloudignore** - Files to exclude from deployment

Choose the deployment method that best fits your needs and infrastructure requirements.
