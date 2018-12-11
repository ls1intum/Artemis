package de.tum.in.www1.artemis.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CustomWebsocketSessionHandler {
    private final Logger log = LoggerFactory.getLogger(CustomWebsocketSessionHandler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, CustomWebsocketSessionHolder> sessionMap = new ConcurrentHashMap<>();
    private static final int SESSION_TIMEOUT = 60 * 1000;


    public CustomWebsocketSessionHandler() {
        scheduler.scheduleAtFixedRate(() -> {
            //DEACTIVATED AT THE MOMENT because we don't receive the Heart Beats and it is not necessary because the websockets work reliably
//                long currentTime = System.currentTimeMillis();
////                log.info("Try to close old websocket sessions");
//                sessionMap.keySet().forEach(sessionId -> {
////                    try {
//                        CustomWebsocketSessionHolder sessionHolder = sessionMap.get(sessionId);
//                        long timeSinceLastMessage = currentTime - sessionHolder.getLastMessageTime();
//                        log.debug("Session " + sessionId + ": time since last message: " + timeSinceLastMessage);
//                        WebSocketSession session = sessionHolder.getSession();
//                        if (session.isOpen()) {
//                            if (timeSinceLastMessage > SESSION_TIMEOUT) {
////                                log.info("Try to close websocket session " + sessionId + " for user " + session.getPrincipal().getName());
//
////                                session.close();
////                                sessionMap.remove(sessionId);
//                            }
//                        }
//                        else {
//                            sessionMap.remove(sessionId);
//                        }
////                    } catch (IOException e) {
////                        log.error("Error while closing websocket session: {}", e.getMessage());
////                    }
//                });
            log.info("There are " + sessionMap.size() + " websocket sessions open!");
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void register(WebSocketSession session) {
        int count = sessionMap.size();
        log.debug("New websocket session #" + (count+1) + ": " + session.getId() + " was established for user " + session.getPrincipal().getName());
        sessionMap.put(session.getId(), new CustomWebsocketSessionHolder(session));
    }

    public void deregister(WebSocketSession session) {
        log.debug("Websocket session " + session.getId() + " was closed for user " + session.getPrincipal().getName());
        sessionMap.remove(session.getId());
    }

//    public void handleOutboundMessage(String sessionId, Message<?> message) {
//        if (sessionId != null) {
//            CustomWebsocketSessionHolder sessionHolder = sessionMap.get(sessionId);
//            if (sessionHolder != null) {
//                WebSocketSession session = sessionHolder.getSession();
//                if (session != null) {
//                    String user = "unknown";
//                    if (session.getPrincipal() != null) {
//                        user = session.getPrincipal().getName();
//                    }
//                    log.debug("Websocket outbound message was handled for session " + sessionId + " for user " + user);
//                    sessionHolder.setLastMessageTime(System.currentTimeMillis());
//                }
//            }
//        }
//    }
//
//    public void handleInboundMessage(WebSocketSession session, WebSocketMessage<?> message) {
//        CustomWebsocketSessionHolder sessionHolder = sessionMap.get(session.getId());
//        if (sessionHolder != null) {
//            String user = "unknown";
//            if (session.getPrincipal() != null) {
//                user = session.getPrincipal().getName();
//            }
//            log.debug("Websocket inbound message was handled for session " + session.getId() + " for user " + user);
//            sessionHolder.setLastMessageTime(System.currentTimeMillis());
//        }
//    }
}
