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