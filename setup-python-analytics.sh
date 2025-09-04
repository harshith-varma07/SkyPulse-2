#!/bin/bash
# SkyPulse Python Analytics Setup Script for Linux/macOS
# This script sets up the Python environment for analytics

echo
echo "========================================"
echo "SkyPulse Python Analytics Setup"
echo "========================================"
echo

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    if ! command -v python &> /dev/null; then
        echo "[ERROR] Python is not installed or not in PATH"
        echo "Please install Python 3.7+ using your package manager"
        echo "Ubuntu/Debian: sudo apt install python3 python3-pip"
        echo "CentOS/RHEL: sudo yum install python3 python3-pip"
        echo "macOS: brew install python3"
        exit 1
    else
        PYTHON_CMD="python"
    fi
else
    PYTHON_CMD="python3"
fi

echo "[INFO] Python detected:"
$PYTHON_CMD --version

# Navigate to python-analytics directory
cd "$(dirname "$0")/python-analytics"
if [ $? -ne 0 ]; then
    echo "[ERROR] Could not find python-analytics directory"
    exit 1
fi

echo
echo "[INFO] Setting up Python analytics environment..."

# Try to run the service manager setup
$PYTHON_CMD service_manager.py setup
if [ $? -eq 0 ]; then
    echo
    echo "[SUCCESS] Python analytics environment setup completed!"
    echo "You can now use the analytics features in SkyPulse."
else
    echo
    echo "[ERROR] Setup failed. Trying alternative installation..."
    
    # Fallback: install packages directly
    echo "[INFO] Installing required packages..."
    $PYTHON_CMD -m pip install --upgrade pip
    $PYTHON_CMD -m pip install pandas numpy matplotlib seaborn reportlab PyMySQL SQLAlchemy
    
    if [ $? -eq 0 ]; then
        echo "[SUCCESS] Packages installed successfully!"
        echo "Creating setup completion marker..."
        touch .setup_complete
    else
        echo "[ERROR] Failed to install packages. Please check your internet connection."
        echo "You may need to run this with sudo or install pip first."
    fi
fi

echo
echo "[INFO] Testing analytics system..."
$PYTHON_CMD service_manager.py health
if [ $? -eq 0 ]; then
    echo "[SUCCESS] Analytics system is ready!"
else
    echo "[WARNING] Some issues detected. Analytics may not work properly."
    echo "Please check the error messages above."
fi

echo
echo "========================================"
echo "Setup complete!"
echo "========================================"
