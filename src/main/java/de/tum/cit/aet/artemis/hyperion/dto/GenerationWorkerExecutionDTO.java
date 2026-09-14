package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Execution ownership for one worker slot; no model content. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationWorkerExecutionDTO(int slot, UUID executionId, String jobId, long exerciseId) {
}
