package de.tum.cit.aet.artemis.aiworker.dto;

import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Opaque correlation and exact worker-incarnation authority for one execution. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExecutionIdentityDTO(String jobId, String resourceId, UUID executionId, String workerId, UUID workerIncarnation, int slot) {

    private static final Pattern WORKER_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public ExecutionIdentityDTO {
        if (jobId == null || jobId.isBlank() || jobId.length() > 128 || resourceId == null || resourceId.isBlank() || resourceId.length() > 128 || executionId == null
                || workerIncarnation == null || workerId == null || !WORKER_ID_PATTERN.matcher(workerId).matches() || slot < 0 || slot >= 16) {
            throw new IllegalArgumentException("Invalid execution identity");
        }
    }
}
