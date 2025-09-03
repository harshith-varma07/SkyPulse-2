#!/bin/bash

# Python Analytics Setup Script for AirSight
echo "Setting up Python Analytics for AirSight..."

# Check if Python is installed
if ! command -v python &> /dev/null && ! command -v python3 &> /dev/null; then
    echo "Error: Python is not installed. Please install Python 3.8+ first."
    exit 1
fi

# Use python3 if python is not available
PYTHON_CMD="python"
if ! command -v python &> /dev/null; then
    PYTHON_CMD="python3"
fi

echo "Using Python command: $PYTHON_CMD"

# Create virtual environment
echo "Creating virtual environment..."
$PYTHON_CMD -m venv venv

# Activate virtual environment
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    # Windows
    source venv/Scripts/activate
else
    # Linux/Mac
    source venv/bin/activate
fi

echo "Virtual environment activated."

# Upgrade pip
echo "Upgrading pip..."
pip install --upgrade pip

# Install requirements
echo "Installing Python dependencies..."
pip install -r requirements.txt

echo "Python analytics setup completed successfully!"
echo ""
echo "To activate the virtual environment manually:"
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    echo "  source python-analytics/venv/Scripts/activate"
else
    echo "  source python-analytics/venv/bin/activate"
fi
echo ""
echo "To test the analytics service:"
echo "  cd python-analytics"
echo "  python analytics_service.py"
