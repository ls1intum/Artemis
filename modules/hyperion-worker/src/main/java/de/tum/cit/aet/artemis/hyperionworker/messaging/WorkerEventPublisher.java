package de.tum.cit.aet.artemis.hyperionworker.messaging;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;

/** Publishes persistent critical events; failed publication leaves the terminal event pending on the supervisor. */
@FunctionalInterface
public interface WorkerEventPublisher {

    void publish(WorkerEvent event);
}
