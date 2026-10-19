package de.tum.cit.aet.artemis.aiworker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;

/** Administrator-only worker capacity snapshot, without prompts, credentials, or generated artifacts. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerStatusDTO(String workerId, WorkerState state, @Nullable Instant lastHeartbeat, @Nullable String imageDigest, @Nullable UUID incarnation,
        @Nullable UUID activeExecution, boolean leaseHeld, int capacity, int availableSlots, List<WorkerExecutionDTO> executions, @Nullable WorkloadCapabilityDTO capability) {

    public WorkerStatusDTO(String workerId, WorkerState state, @Nullable Instant lastHeartbeat, @Nullable String imageDigest, @Nullable UUID incarnation,
            @Nullable UUID activeExecution, boolean leaseHeld) {
        this(workerId, state, lastHeartbeat, imageDigest, incarnation, activeExecution, leaseHeld, 0, 0, List.of(), null);
    }
}
