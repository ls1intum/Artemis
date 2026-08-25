package de.tum.cit.aet.artemis.hyperion.service.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class HyperionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(HyperionWebsocketService.class);

    private static final String TOPIC_PREFIX = "/topic/hyperion/";

    private final WebsocketMessagingService websocketMessagingService;

    public HyperionWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a websocket message to a specific user under the Hyperion namespace.
     *
     * @param userLogin   the receiver's login
     * @param topicSuffix suffix appended to "/topic/hyperion/"
     * @param payload     the payload to send
     */
    public void send(String userLogin, String topicSuffix, Object payload) {
        String topic = TOPIC_PREFIX + topicSuffix;
        websocketMessagingService.sendMessageToUser(userLogin, topic, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error sending Hyperion message to {} on topic {}: {}", userLogin, topic, payload, ex);
                    }
                    else {
                        log.debug("Sent Hyperion message to {} on topic {}: {}", userLogin, topic, payload);
                    }
                });
    }
}
