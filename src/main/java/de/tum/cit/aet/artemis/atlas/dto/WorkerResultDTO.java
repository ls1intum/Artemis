package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured result returned by a delegated Atlas orchestration worker. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerResultDTO(boolean success, String message, List<AppliedActionDTO> appliedActions) {

    public WorkerResultDTO {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        appliedActions = appliedActions == null ? List.of() : List.copyOf(appliedActions);
    }
}
