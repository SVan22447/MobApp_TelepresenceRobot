case "$1" in
    start)
        echo "Starting WebRTC to RTSP servers..."
        docker-compose up -d
        ;;
    stop)
        echo "Stopping WebRTC to RTSP servers..."
        docker-compose down
        ;;
    restart)
        echo "Restarting WebRTC to RTSP servers..."
        docker-compose restart
        ;;
    status)
        docker-compose ps
        ;;
    logs)
        docker-compose logs -f
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac