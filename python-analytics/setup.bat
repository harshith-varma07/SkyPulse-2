@echo off
REM Python Analytics Setup Script for AirSight (Windows)
echo Setting up Python Analytics for AirSight...

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo Error: Python is not installed. Please install Python 3.8+ first.
    echo Download from: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo Python is installed.

REM Create virtual environment
echo Creating virtual environment...
python -m venv venv

REM Activate virtual environment
echo Activating virtual environment...
call venv\Scripts\activate.bat

REM Upgrade pip
echo Upgrading pip...
python -m pip install --upgrade pip

REM Install requirements
echo Installing Python dependencies...
pip install -r requirements.txt

echo Python analytics setup completed successfully!
echo.
echo To activate the virtual environment manually:
echo   python-analytics\venv\Scripts\activate.bat
echo.
echo To test the analytics service:
echo   cd python-analytics
echo   python analytics_service.py
pause
