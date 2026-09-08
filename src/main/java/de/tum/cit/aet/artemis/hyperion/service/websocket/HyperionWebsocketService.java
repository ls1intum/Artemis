package de.tum.cit.aet.artemis.hyperion.service.websocket;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationCancellationEvent;

@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class HyperionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(HyperionWebsocketService.class);

    private static final String TOPIC_PREFIX = "/topic/hyperion/";

    private static final long DELIVERY_TIMEOUT_SECONDS = 10;

    private final WebsocketMessagingService websocketMessagingService;

    public HyperionWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    @EventListener
    public void sendCancellation(GenerationCancellationEvent cancellation) {
        send(cancellation.userLogin(), "exercise-generation/jobs/" + cancellation.jobId(), cancellation.event());
    }

    /**
     * Broadcasts shared generation lock state to authorized exercise editors.
     *
     * @param event the exercise-scoped generation state change
     */
    @EventListener
    public void broadcastExerciseState(ExerciseGenerationStateChangedEvent event) {
        String topic = TOPIC_PREFIX + "exercise-generation/exercises/" + event.state().exerciseId() + "/state";
        try {
            websocketMessagingService.sendMessage(topic, event.state()).orTimeout(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
                if (error != null) {
                    log.warn("Could not deliver Hyperion exercise state on topic {}", topic, error);
                }
            });
        }
        catch (RuntimeException e) {
            log.warn("Could not send Hyperion exercise state on topic {}", topic, e);
        }
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
        try {
            websocketMessagingService.sendMessageToUser(userLogin, topic, payload).orTimeout(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
                if (error == null) {
                    log.debug("Sent Hyperion {} message to {} on topic {}", payload.getClass().getSimpleName(), userLogin, topic);
                }
                else {
                    log.warn("Could not deliver Hyperion {} message to {} on topic {}", payload.getClass().getSimpleName(), userLogin, topic, error);
                }
            });
        }
        catch (RuntimeException e) {
            log.warn("Could not send Hyperion {} message to {} on topic {}", payload.getClass().getSimpleName(), userLogin, topic, e);
        }
    }
}
