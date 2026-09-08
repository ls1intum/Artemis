package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Identifies one assignment; a restarted worker or another run cannot reuse its authority. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExecutionIdentity(String jobId, long exerciseId, UUID executionId, String workerId, UUID workerIncarnation) {

    public ExecutionIdentity {
        if (jobId == null || jobId.isBlank() || jobId.length() > 128 || exerciseId <= 0) {
            throw new IllegalArgumentException("A job and exercise identity are required");
        }
        if (workerId == null || !workerId.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Worker identity must be a bounded destination-safe name");
        }
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(workerIncarnation);
    }
}
