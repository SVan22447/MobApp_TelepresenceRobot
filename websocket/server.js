const express = require('express');
const http = require('http');
const socketIo = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// Разрешаем CORS для Express
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    next();
});

// Простой endpoint для проверки работы сервера
app.get('/health', (req, res) => {
    res.status(200).json({ status: 'OK', message: 'Server is running' });
});

io.on("connection", socket => {
    const room = socket.handshake.query.room;
    const peer = socket.handshake.query.peer;
    
    console.log("User connected:", peer, "to room:", room);

    if (room) {
        socket.join(room);
        
        // Уведомляем других участников о новом пире
        socket.to(room).emit("message", JSON.stringify({
            type: "peer-joined",
            peerId: peer,
            from: peer
        }));

        // Обработка WebRTC signaling сообщений
        socket.on("message", (data) => {
            try {
                const message = JSON.parse(data);
                console.log("Received message type:", message.type, "from:", peer);
                
                if (message.type === "offer") {
                    socket.to(room).emit("message", JSON.stringify({
                        type: "offer",
                        sdp: message.sdp,
                        from: peer
                    }));
                } else if (message.type === "answer") {
                    socket.to(room).emit("message", JSON.stringify({
                        type: "answer", 
                        sdp: message.sdp,
                        from: peer
                    }));
                } else if (message.type === "ice") {
                    socket.to(room).emit("message", JSON.stringify({
                        type: "ice",
                        candidate: message.candidate,
                        from: peer
                    }));
                }
            } catch (error) {
                console.error("Error parsing message:", error);
            }
        });
    } else {
        console.log("User connected without room:", peer);
    }

    socket.on("disconnect", () => {
        console.log("User disconnected:", peer, "from room:", room);
        if (room) {
            socket.to(room).emit("message", JSON.stringify({
                type: "peer-left",
                peerId: peer
            }));
        }
    });

    socket.on("error", (error) => {
        console.error("Socket error:", error);
    });
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, '0.0.0.0', () => {
    console.log('Server running on port ' + PORT);
    console.log('Health check available at: http://localhost:' + PORT + '/health');
});

// Обработка ошибок сервера
server.on('error', (error) => {
    console.error('Server error:', error);
});