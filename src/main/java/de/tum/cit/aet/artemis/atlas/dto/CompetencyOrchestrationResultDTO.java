package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyOrchestrationResultDTO(Status status, String message, List<AppliedActionDTO> appliedActions) {

    public enum Status {
        SUCCESS, PREVIEW, FAILED, IN_PROGRESS
    }

    public static CompetencyOrchestrationResultDTO success(String message, List<AppliedActionDTO> appliedActions) {
        return new CompetencyOrchestrationResultDTO(Status.SUCCESS, message, appliedActions == null ? List.of() : List.copyOf(appliedActions));
    }

    public static CompetencyOrchestrationResultDTO preview(String message) {
        return new CompetencyOrchestrationResultDTO(Status.PREVIEW, message, List.of());
    }

    public static CompetencyOrchestrationResultDTO failed(String message) {
        return new CompetencyOrchestrationResultDTO(Status.FAILED, message, List.of());
    }

    public static CompetencyOrchestrationResultDTO inProgress(String message) {
        return new CompetencyOrchestrationResultDTO(Status.IN_PROGRESS, message, List.of());
    }
}
