package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured result returned by a delegated Atlas orchestration worker. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerResultDTO(boolean success, @NonNull String message, List<AppliedActionDTO> appliedActions) {

    public WorkerResultDTO {
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        appliedActions = appliedActions == null ? List.of() : List.copyOf(appliedActions);
    }
}
