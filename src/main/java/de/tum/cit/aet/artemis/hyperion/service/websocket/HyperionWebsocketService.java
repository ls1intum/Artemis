package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static de.tum.cit.aet.artemis.hyperion.web.HyperionWebsocketTopics.EXERCISE_GENERATION_STATE;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;

@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class HyperionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(HyperionWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    public HyperionWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Notifies editors when a generation acquires or releases an exercise's mutation slot.
     *
     * @param event the new exercise state
     */
    @EventListener
    public void sendExerciseState(ExerciseGenerationStateChangedEvent event) {
        var state = event.state();
        websocketMessagingService.sendMessage(EXERCISE_GENERATION_STATE.at(state.exerciseId()), state);
    }

    /**
     * Sends a websocket message to a specific user under the Hyperion namespace.
     *
     * @param userLogin   the receiver's login
     * @param destination a destination of one of the {@link de.tum.cit.aet.artemis.hyperion.web.HyperionWebsocketTopics}
     * @param payload     the payload to send
     */
    public void send(String userLogin, WebsocketUserDestination destination, Object payload) {
        try {
            websocketMessagingService.sendMessageToUser(userLogin, destination, payload).get();
            log.debug("Sent Hyperion message to {} on topic {}: {}", userLogin, destination, payload);
        }
        catch (InterruptedException | ExecutionException e) {
            // The interrupt status is deliberately not restored, which is what java:S2142 would ask for. A code
            // generation job sends many messages through this method on one thread and finishes with a terminal done
            // or error event. CompletableFuture.get() throws as soon as the flag is set, so restoring it would fail
            // every later send of that job, including the terminal one, and the client's job view would stay "in
            // progress" until the page is reloaded.
            log.error("Error sending Hyperion message to {} on topic {}: {}", userLogin, destination, payload, e);
        }
    }
}
