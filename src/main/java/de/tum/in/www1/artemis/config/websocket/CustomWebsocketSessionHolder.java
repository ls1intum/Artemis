package de.tum.in.www1.artemis.config.websocket;

import org.springframework.web.socket.WebSocketSession;

public class CustomWebsocketSessionHolder {

    private WebSocketSession session;
    private final long createTime;
    private long lastMessageTime;

    public CustomWebsocketSessionHolder(WebSocketSession session) {
        this.session = session;
        this.createTime = System.currentTimeMillis();
        this.lastMessageTime = this.createTime;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public void setSession(WebSocketSession session) {
        this.session = session;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
}
