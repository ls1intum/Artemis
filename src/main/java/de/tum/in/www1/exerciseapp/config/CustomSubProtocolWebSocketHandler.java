package de.tum.in.www1.exerciseapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

public class CustomSubProtocolWebSocketHandler extends SubProtocolWebSocketHandler {

    private final Logger log = LoggerFactory.getLogger(CustomSubProtocolWebSocketHandler.class);

    @Autowired
    private CustomWebsocketSessionHandler customWebsocketSessionHandler;

    public CustomSubProtocolWebSocketHandler(MessageChannel clientInboundChannel, SubscribableChannel clientOutboundChannel) {
        super(clientInboundChannel, clientOutboundChannel);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        customWebsocketSessionHandler.register(session);
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        customWebsocketSessionHandler.deregister(session);
        super.afterConnectionClosed(session, closeStatus);
    }

    /**
     * Handle an outbound Spring Message to a WebSocket client.
     */
    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        log.info("Websocket outbound message was handled");
        super.handleMessage(message);
    }

    /**
     * Handle an inbound message from a WebSocket client.
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        log.info("Websocket inbound message was handled");
        super.handleMessage(session, message);
    }
}
