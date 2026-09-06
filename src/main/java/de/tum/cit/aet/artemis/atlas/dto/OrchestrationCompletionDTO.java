package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Explicit terminal status supplied by the main Atlas orchestrator. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrchestrationCompletionDTO(boolean verified, String message) {

    public OrchestrationCompletionDTO {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
