package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyOrchestrationResultDTO(Status status, String summary, @Nullable FailureReason failureReason) {

    public CompetencyOrchestrationResultDTO {
        if (status == Status.FAILED && failureReason == null) {
            throw new IllegalArgumentException("failureReason must be set when status is FAILED");
        }
    }

    public enum Status {
        SUCCESS, FAILED, IN_PROGRESS
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
         * exam exercises). Advising on competencies for an exam exercise would silently affect
         * the underlying course's competencies, which is never what the instructor wants —
         * surfaced as 422.
         */
        UNSUPPORTED_EXERCISE
    }

    public static CompetencyOrchestrationResultDTO success(String summary) {
        return new CompetencyOrchestrationResultDTO(Status.SUCCESS, summary, null);
    }

    public static CompetencyOrchestrationResultDTO failed(String summary, FailureReason failureReason) {
        return new CompetencyOrchestrationResultDTO(Status.FAILED, summary, failureReason);
    }

    public static CompetencyOrchestrationResultDTO inProgress(String summary) {
        return new CompetencyOrchestrationResultDTO(Status.IN_PROGRESS, summary, null);
    }
}
