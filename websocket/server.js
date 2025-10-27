const WebSocket = require('ws');
const http = require('http');
const url = require('url');

// ================= Wire protocol =================

/**
 * Candidate представляет WebRTC ICE candidate.
 */
class Candidate {
    constructor(sdpMid, sdpMLineIndex, candidate) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.candidate = candidate;
    }
}

/**
 * Envelope - это обертка для всех сообщений WebSocket.
 */
class Envelope {
    constructor(type, room, peerId, from, sdp, candidate, payload) {
        this.type = type;                // offer | answer | ice | peer-joined | peer-left | error
        this.room = room;               // Сервер подставляет ID комнаты
        this.peerId = peerId;           // Для 'peer-joined', 'peer-left'
        this.from = from;               // Сервер подставляет ID отправителя
        this.sdp = sdp;                 // Для 'offer' и 'answer'
        this.candidate = candidate;     // Для 'ice'
        this.payload = payload;         // Для 'error'
    }
}

// ================= Hub / Rooms =================

/**
 * Client представляет одно WebSocket соединение.
 */
class Client {
    constructor(id, room, ws) {
        this.id = id;
        this.room = room;
        this.ws = ws;
        this.isAlive = true;
        this.ws.on('pong', () => {
            this.isAlive = true;
        });
    }

    send(data) {
        if (this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(data);
        }
    }

    close(code, reason) {
        this.ws.close(code, reason);
    }
}

/**
 * Room управляет набором клиентов в одной комнате.
 */
class Room {
    constructor(id) {
        this.id = id;
        this.clients = new Map();
        console.log(`Room '${id}' created`);
    }

    add(client) {
        this.clients.set(client.id, client);
        console.log(`Client ${client.id} added to room ${this.id}`);
    }

    remove(id) {
        if (this.clients.has(id)) {
            this.clients.delete(id);
            console.log(`Client ${id} removed from room ${this.id}`);
            return true;
        }
        return false;
    }
    /**
     * broadcast отправляет сообщение всем клиентам в комнате, кроме отправителя.
     */
    broadcast(from, payload) {
        for (const [id, client] of this.clients) {
            if (id === from) continue;
            if (client.ws.readyState === WebSocket.OPEN) {
                try {
                    client.send(payload);
                } catch (error) {
                    console.log(`Slow consumer detected for client ${client.id}. Closing connection.`);
                    client.close(1008, 'slow consumer');
                }
            }
        }
    }
    isEmpty() {
        return this.clients.size === 0;
    }
    getClientCount() {
        return this.clients.size;
    }
    getOtherClients(excludeId) {
        const others = [];
        for (const [id, client] of this.clients) {
            if (id !== excludeId) {
                others.push(id);
            }
        }
        return others;
    }
}
/**
 * Hub управляет всеми комнатами.
 */
class Hub {
    constructor() {
        this.rooms = new Map();
    }
    getOrCreate(roomId) {
        if (!this.rooms.has(roomId)) {
            this.rooms.set(roomId, new Room(roomId));
        }
        return this.rooms.get(roomId);
    }
    removeClient(roomId, clientId) {
        const room = this.rooms.get(roomId);
        if (room) {
            const removed = room.remove(clientId);
            if (removed && room.isEmpty()) {
                this.rooms.delete(roomId);
                console.log(`Room '${roomId}' deleted as it is empty`);
            }
            return removed;
        }
        return false;
    }

    getRoom(roomId) {
        return this.rooms.get(roomId);
    }
}
// ================= Server setup =================
const hub = new Hub();
const server = http.createServer();
const wss = new WebSocket.Server({ 
    server,
    verifyClient: (info, callback) => {
        callback(true);
    }
});
const PORT = process.env.ADDR || 8777;
const PING_INTERVAL = 30000; // 30 секунд
const MAX_MESSAGE_SIZE = 1 * 1024 * 1024; // 1 MiB
setInterval(() => {
    wss.clients.forEach((ws) => {
        const client = ws.clientRef;
        if (client && !client.isAlive) {
            console.log(`Client ${client.id} is not responding, terminating connection`);
            return ws.terminate();
        }
        if (client) {
            client.isAlive = false;
            ws.ping();
        }
    });
}, PING_INTERVAL);
wss.on('connection', (ws, req) => {
    const parsedUrl = url.parse(req.url, true);
    const roomId = parsedUrl.query.room;
    const peerId = parsedUrl.query.peer;
    console.log('=== NEW CONNECTION ===');
    console.log('Full URL:', req.url);
    console.log('Parsed query:', parsedUrl.query);
    console.log('Room ID:', roomId);
    console.log('Peer ID:', peerId);

    if (!roomId || !peerId) {
        console.log('Query parameters room and peer are required');
        ws.close(1008, 'Query parameters room and peer are required');
        return;
    }
    console.log(`Client '${peerId}' connecting to room '${roomId}'`);
    const room = hub.getOrCreate(roomId);
    const client = new Client(peerId, room, ws);
    ws.clientRef = client;
    room.add(client);
    const existingPeers = room.getOtherClients(peerId);
    console.log(`Notifying new client about existing peers: ${existingPeers.join(', ')}`);
    
    existingPeers.forEach(peer => {
        const envelope = new Envelope('peer-joined', roomId, peer);
        const message = JSON.stringify(envelope);
        console.log(`Sending peer-joined to new client: ${message}`);
        client.send(message);
    });
    const notifyPayload = new Envelope('peer-joined', roomId, peerId);
    const notifyMessage = JSON.stringify(notifyPayload);
    console.log(`Notifying room about new client: ${notifyMessage}`);
    room.broadcast(peerId, notifyMessage);
    ws.on('message', (data) => {
        if (data.length > MAX_MESSAGE_SIZE) {
            console.log(`Message too large from ${peerId}`);
            ws.close(1009, 'message too large');
            return;
        }
        try {
            const envelope = JSON.parse(data);
            envelope.from = peerId;
            envelope.room = roomId;
            console.log(`Received '${envelope.type}' from '${peerId}', broadcasting to room '${roomId}'`);
            const payload = JSON.stringify(envelope);
            switch (envelope.type) {
                case 'offer':
                case 'answer':
                case 'ice':
                    console.log(`Broadcasting ${envelope.type} to room`);
                    room.broadcast(peerId, payload);
                    break;
                default:
                    console.log(`Unknown message type '${envelope.type}' from client '${peerId}'`);
            }
        } catch (error) {
            console.log(`Invalid JSON from ${peerId}:`, error.message);
            console.log('Raw data:', data.toString());
        }
    });
    ws.on('close', (code, reason) => {
        console.log(`Client '${peerId}' disconnected from room '${roomId}': ${code} ${reason}`);
        hub.removeClient(roomId, peerId);        
        const leavePayload = new Envelope('peer-left', roomId, peerId);
        room.broadcast(peerId, JSON.stringify(leavePayload));
        console.log(`Cleanup for client '${peerId}' finished`);
    });
    ws.on('error', (error) => {
        console.log(`WebSocket error for client '${peerId}':`, error.message);
    });
});
server.on('request', (req, res) => {
    console.log('HTTP Request:', req.method, req.url);
});
server.listen(PORT, '0.0.0.0', () => {
    console.log(`WebRTC signaling server running on port ${PORT}`);
    console.log(`WebSocket endpoint: ws://localhost:${PORT}/ws`);
    console.log('Waiting for connections...');
});
const gracefulShutdown = () => {
    console.log('Shutting down server...');
    wss.clients.forEach((client) => {
        client.close(1000, 'server shutdown');
    });
    setTimeout(() => {
        server.close(() => {
            console.log('Server gracefully stopped');
            process.exit(0);
        });
    }, 5000);
};
process.on('SIGINT', gracefulShutdown);
process.on('SIGTERM', gracefulShutdown);