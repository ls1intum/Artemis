package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Explicit terminal status supplied by the main Atlas orchestrator. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrchestrationCompletionDTO(boolean verified, @NonNull String message) {

    public OrchestrationCompletionDTO {
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
