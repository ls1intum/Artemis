package de.tum.in.www1.exerciseapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CustomWebsocketSessionHandler {
    private final Logger log = LoggerFactory.getLogger(CustomWebsocketSessionHandler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public CustomWebsocketSessionHandler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                log.info("Try to cleanup old websocket sessions (Not implemented yet)");
                sessionMap.keySet().forEach(sessionId -> {
                    try {
                        WebSocketSession session = sessionMap.get(sessionId);
                        //TODO: only close sessions that have not been active for more than 60s
                        if (session.isOpen() && false) {
                            session.close();
                            sessionMap.remove(sessionId);
                        }
                        else {
                            sessionMap.remove(sessionId);
                        }
                    } catch (IOException e) {
                        log.error("Error while closing websocket session: {}", e.getMessage());
                    }
                });
                log.info("There are still " + sessionMap.size() + " sessions open!");
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void register(WebSocketSession session) {
        int count = sessionMap.size();
        log.info("New websocket session #" + (count+1) + ": " + session.getId() + " was established for user " + session.getPrincipal().getName());
        sessionMap.put(session.getId(), session);
    }

    public void deregister(WebSocketSession session) {
        log.info("Websocket session " + session.getId() + " was closed for user " + session.getPrincipal().getName());
        sessionMap.remove(session.getId());
    }
}
