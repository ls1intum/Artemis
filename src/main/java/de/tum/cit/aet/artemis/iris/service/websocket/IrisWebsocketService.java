package de.tum.cit.aet.artemis.iris.service.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * A service to send a message over the websocket to a specific user
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(IrisWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param userLogin   the login of the user
     * @param destination a destination of one of the {@link de.tum.cit.aet.artemis.iris.web.IrisWebsocketTopics}
     * @param payload     the DTO to send, which will be serialized to JSON
     */
    public void send(String userLogin, WebsocketUserDestination destination, Object payload) {
        websocketMessagingService.sendMessageToUser(userLogin, destination, payload).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.warn("Error while sending message to Iris user {} on topic {}: {}", userLogin, destination, payload, throwable);
            }
            else {
                log.debug("Sent message to Iris user {} on topic {}: {}", userLogin, destination, payload);
            }
        });
    }

}
