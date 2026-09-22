package de.tum.cit.aet.artemis.aiworker.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.config.AiWorkerEnabled;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

/** Publishes current administrative snapshots only while administrators are watching. */
@Lazy
@Service
@Profile("core & scheduling")
@Conditional(AiWorkerEnabled.class)
public class WorkerMonitoringService {

    public static final String TOPIC = "/topic/admin/ai-workers";

    private final WorkerRegistryService source;

    private final WebsocketMessagingService messaging;

    private final SimpUserRegistry subscribers;

    public WorkerMonitoringService(WorkerRegistryService source, WebsocketMessagingService messaging, SimpUserRegistry subscribers) {
        this.source = source;
        this.messaging = messaging;
        this.subscribers = subscribers;
    }

    /** Refreshes watched snapshots, including expired worker presence and completed runs. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 5000)
    public void publish() {
        if (!subscribers.findSubscriptions(subscription -> TOPIC.equals(subscription.getDestination())).isEmpty()) {
            messaging.sendMessage(TOPIC, source.workerStatuses());
        }
    }
}
