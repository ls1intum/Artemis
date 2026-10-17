package de.tum.cit.aet.artemis.aiworker.api;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.aiworker.config.AiWorkerEnabled;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerClientService;
import de.tum.cit.aet.artemis.aiworker.service.WorkerRegistryService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;

/** Public coordinator boundary. Workloads never access distributed worker state or provider transport directly. */
@Lazy
@Controller
@Conditional(AiWorkerEnabled.class)
public class AiWorkerApi implements AbstractApi {

    private final WorkerRegistryService registry;

    private final WorkerClientService client;

    public AiWorkerApi(WorkerRegistryService registry, WorkerClientService client) {
        this.registry = registry;
        this.client = client;
    }

    public boolean available(WorkloadCapabilityDTO capability) {
        return registry.hasAvailableSlot(capability);
    }

    public ExecutionClaimDTO claim(String jobId, String resourceId, WorkloadCapabilityDTO capability) {
        return registry.claim(jobId, resourceId, capability);
    }

    public boolean renew(ExecutionClaimDTO claim) {
        return registry.renew(claim);
    }

    public void release(ExecutionClaimDTO claim) {
        registry.release(claim);
    }

    public void recordCompletion(ExecutionClaimDTO claim, WorkerEventDTO event) {
        registry.recordCompletion(claim, event);
    }

    public List<WorkerStatusDTO> statuses() {
        return registry.workerStatuses();
    }

    public void send(WorkerCommandDTO command) {
        client.send(command);
    }

    public void receive(ExecutionClaimDTO claim, Consumer<WorkerEventDTO> apply) {
        client.receive(claim, apply);
    }
}
