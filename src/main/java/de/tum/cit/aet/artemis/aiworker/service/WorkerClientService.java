package de.tum.cit.aet.artemis.aiworker.service;

import java.util.function.Consumer;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.config.AiWorkerEnabled;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;

/** Sends commands and applies events for one exact execution. */
@Lazy
@Service
@Conditional(AiWorkerEnabled.class)
public class WorkerClientService {

    private final WorkerTransport transport;

    public WorkerClientService(WorkerTransport transport) {
        this.transport = transport;
    }

    public void send(WorkerCommandDTO command) {
        transport.send(command);
    }

    /**
     * Applies and acknowledges one event only after the callback succeeds.
     *
     * @param claim the exact execution claim
     * @param apply accepts the verified event
     */
    public void receive(ExecutionClaimDTO claim, Consumer<WorkerEventDTO> apply) {
        boolean received = transport.receive(claim.identity(), event -> {
            if (!claim.imageDigest().equals(event.imageDigest()) || !claim.capability().equals(event.capability())) {
                throw new IllegalArgumentException("Worker result does not match the claimed execution and image");
            }
            apply.accept(event);
        });
        if (!received) {
            try {
                Thread.sleep(1_000);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
