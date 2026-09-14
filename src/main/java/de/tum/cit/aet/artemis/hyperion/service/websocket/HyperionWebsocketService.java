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
     * <p>
     * The message is handed off and this method returns right away; a delivery failure is only logged. It must not wait for
     * the send to finish: {@link WebsocketMessagingService} runs every send on the shared {@code taskExecutor}, and that is
     * also the pool the {@code @Async} code generation jobs calling this method run on. A job thread waiting for a send that
     * is queued behind the running jobs only gets it once another pool thread frees up, and once every pool thread waits
     * like that the pool is deadlocked until the server restarts. {@code IrisWebsocketService#send} hands off the same way.
     * <p>
     * Each message is its own task, so this method makes no promise about the order in which the messages of one job
     * reach the client. Both Hyperion clients finish on the terminal event and refresh their state from the server then,
     * so an event that overtakes a neighbour costs at most a stale label.
     *
     * @param userLogin   the receiver's login
     * @param topicSuffix suffix appended to "/topic/hyperion/"
     * @param payload     the payload to send
     */
    public void send(String userLogin, String topicSuffix, Object payload) {
        String topic = TOPIC_PREFIX + topicSuffix;
        websocketMessagingService.sendMessageToUser(userLogin, topic, payload).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.error("Error sending Hyperion message to {} on topic {}: {}", userLogin, topic, payload, throwable);
            }
            else {
                log.debug("Sent Hyperion message to {} on topic {}: {}", userLogin, topic, payload);
            }
        });
    }
}
