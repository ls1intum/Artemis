package de.tum.cit.aet.artemis.aiworker.service.messaging;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;

/** Publishes persistent critical events; failed publication leaves the terminal event pending on the supervisor. */
@FunctionalInterface
public interface WorkerEventPublisher {

    void publish(WorkerEventDTO event);
}
