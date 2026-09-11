package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Administrator-only live generation summary; no prompts, repository contents or credentials. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveGenerationDTO(String jobId, long exerciseId, String userLogin, Instant startedAt, boolean cancellable, boolean cancellationRequested) {
}
