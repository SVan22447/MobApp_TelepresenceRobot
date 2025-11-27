echo "INSTANT WebRTC Servers Startup - No Docker!"
echo
cleanup() {
    echo
    echo "Завершение работы серверов..."
    kill $NPM_PID 2>/dev/null
    kill $MEDIA_SERVER_PID 2>/dev/null
    echo "Все серверы остановлены."
}
trap cleanup EXIT INT TERM
check_port() {
    local port=$1
    local service=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        echo "ОШИБКА: Порт $port уже занят. $service не может быть запущен."
        return 1
    fi
    return 0
}
wait_for_server() {
    local port=$1
    local service=$2
    local max_attempts=30
    local attempt=1
    echo "Ожидание запуска $service..."
    while [ $attempt -le $max_attempts ]; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
            echo "$service запущен и слушает порт $port"
            return 0
        fi
        echo "Попытка $attempt/$max_attempts..."
        sleep 2
        ((attempt++))
    done
    echo "ПРЕДУПРЕЖДЕНИЕ: $service не запустился в течение 60 секунд"
    return 1
}
echo "Проверка доступности портов..."
if ! check_port 8777 "Signaling Server"; then
    exit 1
fi

if ! check_port 8554 "Media Server"; then
    exit 1
fi

echo "Запуск Signaling Server (Node.js)..."
npm start &
NPM_PID=$!
echo "Signaling Server запущен с PID: $NPM_PID"

if ! wait_for_server 8777 "Signaling Server"; then
    echo "Не удалось запустить Signaling Server"
    exit 1
fi

echo
echo "Запуск Media Server (Go)..."
cd media-server || {
    echo "ОШИБКА: Директория media-server не найдена!"
    exit 1
}
go run main.go &
MEDIA_SERVER_PID=$!
echo "Media Server запущен с PID: $MEDIA_SERVER_PID"

if ! wait_for_server 8554 "Media Server"; then
    echo "Не удалось запустить Media Server"
    exit 1
fi
echo
echo "=== Все серверы успешно запущены ==="
echo "• Signaling Server: http://localhost:8777"
echo "• Media Server: http://localhost:8080"
echo
echo "Для остановки нажмите Ctrl+C"
echo
wait $MEDIA_SERVER_PID