package de.tum.cit.aet.artemis.aiworker.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Execution ownership for one worker slot; no model content. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerExecutionDTO(int slot, UUID executionId, String jobId, String resourceId) {
}
