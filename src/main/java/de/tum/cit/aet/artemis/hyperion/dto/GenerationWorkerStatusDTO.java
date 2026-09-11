package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.GenerationWorkerState;

/** Administrator-only worker capacity snapshot, without prompts, credentials, or generated artifacts. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationWorkerStatusDTO(String workerId, GenerationWorkerState state, @Nullable Instant lastHeartbeat, @Nullable String imageDigest, @Nullable UUID incarnation,
        @Nullable UUID activeExecution, boolean leaseHeld, int capacity, int availableSlots, List<GenerationWorkerExecutionDTO> executions) {

    public GenerationWorkerStatusDTO(String workerId, GenerationWorkerState state, @Nullable Instant lastHeartbeat, @Nullable String imageDigest, @Nullable UUID incarnation,
            @Nullable UUID activeExecution, boolean leaseHeld) {
        this(workerId, state, lastHeartbeat, imageDigest, incarnation, activeExecution, leaseHeld, 1, state == GenerationWorkerState.AVAILABLE ? 1 : 0, List.of());
    }
}
