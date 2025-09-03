@echo off
REM AirSight Deployment Script for Windows
REM This script automates the deployment process for AirSight application

echo.
echo 🚀 Starting AirSight Deployment Process...
echo.

set PROJECT_NAME=airsight
set DOCKER_COMPOSE_FILE=docker/docker-compose.yml
set ENV_FILE=docker/.env

REM Check if Docker is installed and running
echo [INFO] Checking Docker installation...

docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed. Please install Docker Desktop first.
    pause
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose is not available. Please ensure Docker Desktop is properly installed.
    pause
    exit /b 1
)

echo [INFO] Docker and Docker Compose are available ✓

REM Setup environment file
echo [INFO] Setting up environment configuration...

if not exist "%ENV_FILE%" (
    if exist "docker/.env.example" (
        copy "docker\.env.example" "%ENV_FILE%"
        echo [WARN] Created .env file from example. Please configure your settings in %ENV_FILE%
        echo [WARN] Press any key to continue after configuring the .env file...
        pause
    ) else (
        echo [ERROR] No environment file found. Please create %ENV_FILE%
        pause
        exit /b 1
    )
) else (
    echo [INFO] Environment file exists ✓
)

REM Clean and compile
echo [INFO] Building AirSight application...

where mvn >nul 2>&1
if errorlevel 1 (
    echo [WARN] Maven not found. Using Docker build only.
) else (
    echo [INFO] Cleaning and compiling with Maven...
    call mvn clean compile
)

REM Build Docker images
echo [INFO] Building Docker images...
docker-compose -f "%DOCKER_COMPOSE_FILE%" build --no-cache

if errorlevel 1 (
    echo [ERROR] Docker build failed.
    pause
    exit /b 1
)

echo [INFO] Application build completed ✓

REM Deploy the application
echo [INFO] Deploying AirSight application...

REM Stop existing containers if any
echo [INFO] Stopping existing containers...
docker-compose -f "%DOCKER_COMPOSE_FILE%" down --remove-orphans

REM Start the application stack
echo [INFO] Starting application stack...
docker-compose -f "%DOCKER_COMPOSE_FILE%" up -d

if errorlevel 1 (
    echo [ERROR] Deployment failed.
    pause
    exit /b 1
)

echo [INFO] Deployment completed ✓

REM Wait for services to start
echo [INFO] Waiting for services to start...
timeout /t 30 /nobreak >nul

REM Check application health
echo [INFO] Checking application health...

set /a max_attempts=12
set /a attempt=1

:health_check_loop
echo [INFO] Checking application health (attempt %attempt%/%max_attempts%)...

curl -f http://localhost:8080/api/health >nul 2>&1
if errorlevel 1 (
    if %attempt% geq %max_attempts% (
        echo [ERROR] Application health check failed after %max_attempts% attempts
        echo [INFO] Checking container logs...
        docker-compose -f "%DOCKER_COMPOSE_FILE%" logs --tail=50
        pause
        exit /b 1
    )
    timeout /t 10 /nobreak >nul
    set /a attempt+=1
    goto health_check_loop
)

echo [INFO] Application is healthy ✓

REM Display deployment information
echo.
echo 🎉 AirSight deployment completed successfully!
echo.
echo 📊 Application URLs:
echo    Frontend: http://localhost:8080
echo    API: http://localhost:8080/api
echo    Health Check: http://localhost:8080/api/health
echo.
echo 🗄️ Database Access:
echo    MySQL: localhost:3307
echo    Database: airqualitydb
echo.
echo 📝 Useful Commands:
echo    View logs: docker-compose -f %DOCKER_COMPOSE_FILE% logs -f
echo    Stop services: docker-compose -f %DOCKER_COMPOSE_FILE% down
echo    Restart services: docker-compose -f %DOCKER_COMPOSE_FILE% restart
echo.
echo Press any key to exit...
pause
