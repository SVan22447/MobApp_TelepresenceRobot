@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo INSTANT WebRTC Servers Startup - No Docker!
echo.
set "ORIGINAL_DIR=%CD%"
setlocal
for /f "tokens=2 delims=;" %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
echo %ESC%=== WebRTC Servers Starting ===%ESC%
echo.
echo Checking dependencies...
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo %ESC%Error: npm is not installed or not in PATH%ESC%
    goto :EXIT
)
where go >nul 2>&1
if %errorlevel% neq 0 (
    echo %ESC%Error: Go is not installed or not in PATH%ESC%
    goto :EXIT
)
if not exist "media-server\" (
    echo %ESC%Error: media-server directory not found%ESC%
    goto :EXIT
)
if not exist "package.json" (
    echo %ESC%Warning: package.json not found in current directory%ESC%
)
echo %ESC%Starting Signaling Server (Node.js)...%ESC%
start "WebRTC Signaling Server" cmd /c "npm start && pause"
echo %ESC%Waiting for signaling server to start (3 seconds)...%ESC%
timeout /t 3 /nobreak >nul
echo %ESC%Starting Media Server (Go)...%ESC%
echo %ESC%Media Server is running. Press Ctrl+C to stop.%ESC%
echo.

cd media-server
go run main.go

echo.
echo %ESC%[0;33mMedia Server stopped.%ESC%

:EXIT
    cd /d "%ORIGINAL_DIR%"
    echo.
    echo %ESC%[0;32mCurrent directory restored to:%ESC%
    echo %CD%
    echo.
    echo %ESC%[0;33mNote: Signaling Server may still be running in separate window.%ESC%
    echo %ESC%[0;33mClose that window manually if needed.%ESC%
    pause
    exit /b 0
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