@echo off
REM SkyPulse Python Analytics Setup Script for Windows
REM This script sets up the Python environment for analytics

echo.
echo ========================================
echo SkyPulse Python Analytics Setup
echo ========================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH
    echo Please install Python 3.7+ from https://python.org
    echo Make sure to check "Add Python to PATH" during installation
    pause
    exit /b 1
)

echo [INFO] Python detected:
python --version

REM Navigate to python-analytics directory
cd /d "%~dp0python-analytics"
if %errorlevel% neq 0 (
    echo [ERROR] Could not find python-analytics directory
    pause
    exit /b 1
)

echo.
echo [INFO] Setting up Python analytics environment...

REM Try to run the service manager setup
python service_manager.py setup
if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] Python analytics environment setup completed!
    echo You can now use the analytics features in SkyPulse.
) else (
    echo.
    echo [ERROR] Setup failed. Trying alternative installation...
    
    REM Fallback: install packages directly
    echo [INFO] Installing required packages...
    python -m pip install --upgrade pip
    python -m pip install pandas numpy matplotlib seaborn reportlab PyMySQL SQLAlchemy
    
    if %errorlevel% equ 0 (
        echo [SUCCESS] Packages installed successfully!
        echo Creating setup completion marker...
        type nul > .setup_complete
    ) else (
        echo [ERROR] Failed to install packages. Please check your internet connection.
        echo You may need to run this as administrator.
    )
)

echo.
echo [INFO] Testing analytics system...
python service_manager.py health
if %errorlevel% equ 0 (
    echo [SUCCESS] Analytics system is ready!
) else (
    echo [WARNING] Some issues detected. Analytics may not work properly.
    echo Please check the error messages above.
)

echo.
echo ========================================
echo Setup complete! Press any key to exit.
echo ========================================
pause >nul
