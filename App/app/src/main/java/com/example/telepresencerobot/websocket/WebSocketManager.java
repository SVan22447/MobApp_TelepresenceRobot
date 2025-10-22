package com.example.telepresencerobot.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;

public class WebSocketManager {
    private WebSocketClient webSocketClient;
    private final WebSocketListener listener;
    public interface WebSocketListener {
        void onConnected();
        void onDisconnected();
        void onMessage(JSONObject message);
        void onError(String error);
    }
    public WebSocketManager(WebSocketListener listener) {
        this.listener = listener;
    }

    public void connect(String serverUrl) {
        try {
            URI uri = new URI(serverUrl);
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    listener.onConnected();
                }
                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject json = new JSONObject(message);
                        listener.onMessage(json);
                    } catch (Exception e) {
                        listener.onError("Failed to parse message: " + e.getMessage());
                    }
                }
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    listener.onDisconnected();
                }
                @Override
                public void onError(Exception ex) {
                    listener.onError(ex.getMessage());
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            listener.onError("Connection error: " + e.getMessage());
        }
    }
    public void sendMessage(JSONObject message) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(message.toString());
        }
    }
    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }
    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }
}
