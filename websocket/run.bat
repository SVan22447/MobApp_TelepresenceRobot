@echo off
chcp 65001 >nul

echo INSTANT WebRTC Servers Startup - No Docker!
echo.
set "ORIGINAL_DIR=%CD%"
echo Checking system dependencies...
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: npm is not installed or not in PATH
    goto :EXIT
)
where go >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Go is not installed or not in PATH
    goto :EXIT
)
if not exist "media-server\" (
    echo ERROR: media-server directory not found
    goto :EXIT
)
echo Checking Node.js dependencies...
if not exist "package.json" (
    echo ERROR: package.json not found in current directory
    goto :EXIT
)
if not exist "node_modules\" (
    echo Node modules not found. Installing dependencies...
    npm install
    if %errorlevel% neq 0 (
        echo ERROR: npm install failed
        goto :EXIT
    )
    echo Node.js dependencies installed successfully!
) else (
    echo Node.js dependencies found.
)
echo Checking Go dependencies...
if not exist "media-server\go.mod" (
    echo ERROR: go.mod not found in media-server directory
    goto :EXIT
)
if not exist "media-server\go.sum" (
    echo go.sum not found. Running go mod tidy...
    cd media-server
    go mod tidy
    if %errorlevel% neq 0 (
        echo ERROR: go mod tidy failed
        cd ..
        goto :EXIT
    )
    cd ..
    echo Go dependencies installed successfully!
) else (
    echo Go dependencies found.
)
echo.
echo Starting servers...
echo Starting Signaling Server (Node.js)...
start "WebRTC Signaling Server" cmd /k "npm start"
echo Waiting for signaling server to start (3 seconds)...
timeout /t 3 /nobreak >nul
echo Starting Media Server (Go)...
echo Media Server is running. Press Ctrl+C to stop.
echo.

cd media-server
go run main.go

echo.
echo Media Server stopped.

:EXIT
    cd /d "%ORIGINAL_DIR%"
    echo.
    echo Current directory restored to:
    echo %CD%
    pause
    exit /b 0