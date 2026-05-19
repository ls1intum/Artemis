package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyOrchestrationResultDTO(Status status, String summary, List<AppliedActionDTO> appliedActions, @Nullable FailureReason failureReason) {

    public CompetencyOrchestrationResultDTO {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        appliedActions = appliedActions == null ? List.of() : List.copyOf(appliedActions);
        if (status == Status.SUCCESS && summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank when status is SUCCESS");
        }
        if (status == Status.FAILED && failureReason == null) {
            throw new IllegalArgumentException("failureReason must be set when status is FAILED");
        }
        if (status != Status.FAILED && status != Status.PARTIAL && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null unless status is FAILED or PARTIAL");
        }
    }

    public enum Status {
        SUCCESS, PARTIAL, FAILED, IN_PROGRESS
    }

    /**
     * Distinguishes the ways a run can fail without forcing the controller to parse a message
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

    public static CompetencyOrchestrationResultDTO success(String summary, List<AppliedActionDTO> appliedActions) {
        return new CompetencyOrchestrationResultDTO(Status.SUCCESS, summary, appliedActions, null);
    }

    /**
     * The LLM run failed but had already committed at least one mutation; the caller needs the
     * audit trail so the partial change can be reviewed/reverted.
     */
    public static CompetencyOrchestrationResultDTO partial(String summary, List<AppliedActionDTO> appliedActions, FailureReason failureReason) {
        return new CompetencyOrchestrationResultDTO(Status.PARTIAL, summary, appliedActions, failureReason);
    }

    public static CompetencyOrchestrationResultDTO failed(String summary, FailureReason failureReason) {
        return new CompetencyOrchestrationResultDTO(Status.FAILED, summary, List.of(), failureReason);
    }

    public static CompetencyOrchestrationResultDTO inProgress(String summary) {
        return new CompetencyOrchestrationResultDTO(Status.IN_PROGRESS, summary, List.of(), null);
    }
}
