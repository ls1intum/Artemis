package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Terminal status supplied by a stateless Atlas orchestration worker. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerCompletionDTO(boolean success, String message) {

    public WorkerCompletionDTO {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
