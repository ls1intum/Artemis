package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_GENERATION_MONITORING_TOPIC;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/** Publishes current administrative snapshots only while administrators are watching. */
@Lazy
@Service
@Profile("core & scheduling")
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationMonitoringWebsocketService {

    private final GenerationMonitoringService source;

    private final WebsocketMessagingService messaging;

    private final SimpUserRegistry subscribers;

    public GenerationMonitoringWebsocketService(GenerationMonitoringService source, WebsocketMessagingService messaging, SimpUserRegistry subscribers) {
        this.source = source;
        this.messaging = messaging;
        this.subscribers = subscribers;
    }

    /** Refreshes watched snapshots, including expired worker presence and completed runs. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 5000)
    public void publish() {
        if (!subscribers.findSubscriptions(subscription -> HYPERION_GENERATION_MONITORING_TOPIC.equals(subscription.getDestination())).isEmpty()) {
            messaging.sendMessage(HYPERION_GENERATION_MONITORING_TOPIC, source.activeGenerations());
        }
    }
}
