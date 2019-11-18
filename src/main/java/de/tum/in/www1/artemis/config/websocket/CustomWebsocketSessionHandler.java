package de.tum.in.www1.artemis.config.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class CustomWebsocketSessionHandler {

    private final Logger log = LoggerFactory.getLogger(CustomWebsocketSessionHandler.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, CustomWebsocketSessionHolder> sessionMap = new ConcurrentHashMap<>();

    private static final int PERIOD = 20 * 1000;

    public CustomWebsocketSessionHandler() {
        scheduler.scheduleAtFixedRate(() -> {
            log.info("There are " + sessionMap.size() + " websocket sessions open!");
        }, PERIOD, PERIOD, TimeUnit.MILLISECONDS);
    }

    public void register(WebSocketSession session) {
        if (session != null) {
            int count = sessionMap.size();
            if (session.getPrincipal() != null) {
                log.debug("New websocket session #" + (count + 1) + ": " + session.getId() + " was established for user " + session.getPrincipal().getName());
            }
            sessionMap.put(session.getId(), new CustomWebsocketSessionHolder(session));
        }
    }

    public void deregister(WebSocketSession session) {
        if (session != null) {
            if (session.getPrincipal() != null) {
                log.debug("Websocket session " + session.getId() + " was closed for user " + session.getPrincipal().getName());
            }
            sessionMap.remove(session.getId());
        }
    }
}
