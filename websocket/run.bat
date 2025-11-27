@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo INSTANT WebRTC Servers Startup - No Docker!
echo.
set "ORIGINAL_DIR=%CD%"
:EXIT
    cd /d "%ORIGINAL_DIR%"
    echo All servers stopped. Current directory restored to:
    echo %CD%
    pause
    exit /b %errorlevel%
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: npm is not installed or not in PATH
    goto :EXIT
)
where go >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Go is not installed or not in PATH
    goto :EXIT
)
if not exist "media-server\" (
    echo Error: media-server directory not found
    goto :EXIT
)
echo Starting Signaling Server (Node.js)...
start "Signaling Server" npm start
if %errorlevel% neq 0 (
    echo Failed to start Signaling Server
    goto :EXIT
)
echo Waiting for signaling server to start...
timeout /t 3 /nobreak >nul
echo Starting Media Server (Go)...
cd media-server
go run main.go
goto :EXIT

@REM docker version
goto label1
@echo off
echo Starting WebRTC to RTSP servers with local Docker builds...
docker-compose -f docker-compose-local.yml up --build
if "%1"=="" goto error

if "%1"=="start" (
    echo Starting WebRTC to RTSP servers...
    docker-compose up -d
    goto end
)

if "%1"=="stop" (
    echo Stopping WebRTC to RTSP servers...
    docker-compose down
    goto end
)

if "%1"=="restart" (
    echo Restarting WebRTC to RTSP servers...
    docker-compose restart
    goto end
)

if "%1"=="status" (
    docker-compose ps
    goto end
)

if "%1"=="logs" (
    docker-compose logs -f
    goto end
)

:error
echo Usage: %0 {start^|stop^|restart^|status^|logs}
exit /b 1

:end
:label1