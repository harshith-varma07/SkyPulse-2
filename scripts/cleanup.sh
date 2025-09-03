#!/bin/bash

# AirSight Cleanup Script
# Remove generated and temporary files

echo "🧹 Cleaning up AirSight development files..."

# Remove generated environment files
if [ -f "docker/.env" ]; then
    echo "Removing docker/.env"
    rm docker/.env
fi

if [ -f "docker/.env.gcp" ]; then
    echo "Removing docker/.env.gcp"
    rm docker/.env.gcp
fi

# Remove Terraform state files (be careful with this!)
if [ -d "terraform/.terraform" ]; then
    echo "Removing Terraform cache"
    rm -rf terraform/.terraform
fi

if [ -f "terraform/terraform.tfvars" ]; then
    echo "Removing terraform.tfvars (backup as .tfvars.bak)"
    mv terraform/terraform.tfvars terraform/terraform.tfvars.bak
fi

# Remove build artifacts
if [ -d "target" ]; then
    echo "Removing Maven target directory"
    rm -rf target
fi

# Remove logs
if [ -d "logs" ]; then
    echo "Removing logs directory"
    rm -rf logs
fi

# Remove temporary files
find . -name "*.tmp" -delete 2>/dev/null || true
find . -name "*.temp" -delete 2>/dev/null || true
find . -name ".DS_Store" -delete 2>/dev/null || true

echo "✅ Cleanup completed!"
echo ""
echo "To regenerate configurations, run:"
echo "  ./scripts/generate-env.sh"