package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyOrchestrationResultDTO(Status status, String message, List<AppliedActionDTO> appliedActions, @Nullable FailureReason failureReason) {

    public enum Status {
        SUCCESS, PARTIAL, FAILED, IN_PROGRESS
    }

    /**
     * Distinguishes the two ways a run can fail without forcing the controller to parse a message
     * string. Drives the HTTP status code returned by {@link de.tum.cit.aet.artemis.atlas.web.CompetencyOrchestrationResource}.
     */
    public enum FailureReason {
        /** No {@code ChatClient} bean is configured (Atlas chat disabled or misconfigured) — surfaced as 503. */
        NO_CHAT_CLIENT,
        /** The LLM call itself threw — surfaced as 502. */
        LLM_ERROR,
        /**
         * The exercise the orchestrator was triggered for is not a course exercise (currently:
         * exam exercises). Mutating competencies for an exam exercise would silently affect the
         * underlying course's competencies, which is never what the instructor wants —
         * surfaced as 422.
         */
        UNSUPPORTED_EXERCISE
    }

    public static CompetencyOrchestrationResultDTO success(String message, List<AppliedActionDTO> appliedActions) {
        return new CompetencyOrchestrationResultDTO(Status.SUCCESS, message, appliedActions == null ? List.of() : List.copyOf(appliedActions), null);
    }

    /**
     * The LLM run failed but had already committed at least one mutation; the caller needs the
     * audit trail so the partial change can be reviewed/reverted.
     */
    public static CompetencyOrchestrationResultDTO partial(String message, List<AppliedActionDTO> appliedActions, FailureReason failureReason) {
        return new CompetencyOrchestrationResultDTO(Status.PARTIAL, message, appliedActions == null ? List.of() : List.copyOf(appliedActions), failureReason);
    }

    public static CompetencyOrchestrationResultDTO failed(String message, FailureReason failureReason) {
        return new CompetencyOrchestrationResultDTO(Status.FAILED, message, List.of(), failureReason);
    }

    public static CompetencyOrchestrationResultDTO inProgress(String message) {
        return new CompetencyOrchestrationResultDTO(Status.IN_PROGRESS, message, List.of(), null);
    }
}
