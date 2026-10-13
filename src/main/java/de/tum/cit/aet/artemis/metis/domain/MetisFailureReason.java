package de.tum.cit.aet.artemis.metis.domain;

import java.util.Set;

/**
 * Why a {@link MetisCheck} is {@link MetisCheckStatus#SKIPPED} or {@link MetisCheckStatus#FAILED}, or why a student is shown a check that is not available.
 * <p>
 * The stored reasons are written to {@code metis_check.failure_reason} as the constant's {@link #name()}: the server stores codes, never prose, and the client
 * translates them. The last three constants are never stored; the student view presents them instead of a stored status.
 */
public enum MetisFailureReason {

    /** SKIPPED: the student's AI decision does not allow the configured endpoint (no decision, no AI, or local AI only while the endpoint is a cloud service). */
    NO_AI_CONSENT,

    /** SKIPPED: the participation has no submission with a commit before its effective due date. */
    NO_SUBMISSION,

    /** SKIPPED: the filtered diff of the student's repository against the setup commit is empty. */
    NO_OWN_CODE,

    /**
     * SKIPPED: at generation time the student's window had ended, was empty (an extension past the window end) or left less time than one question needs. No
     * repository was read and no LLM was called. A change of a window of the exercise and "generate now" requeue such checks.
     */
    WINDOW_CLOSED,

    /** FAILED: the repository is missing or unreadable, or the submitted commit is not in it. */
    REPOSITORY_UNAVAILABLE,

    /** FAILED: the LLM returned unusable output twice in a row. */
    INVALID_LLM_RESPONSE,

    /** FAILED: the LLM endpoint rejected the request (HTTP 400, 413 or 422), so the same request can never succeed. */
    LLM_REJECTED,

    /** FAILED: the LLM endpoint was unavailable for every generation attempt the configuration allows. */
    LLM_UNAVAILABLE,

    /** FAILED: an unexpected exception ended the generation; the server log holds its stack trace. */
    INTERNAL_ERROR,

    /** Presented only: the exercise has no enabled check. */
    NOT_ENABLED,

    /** Presented only: the participation never gets a check, because the user lacks the student role in the course or also holds a staff role there. */
    NOT_ELIGIBLE,

    /** Presented only: a stored {@link #NO_AI_CONSENT} of a student who allowed local AI only, while the configured endpoint is a cloud service. */
    CLOUD_AI_REQUIRED;

    /**
     * The stored reasons that "generate now" puts back into the queue: every FAILED reason, and the SKIPPED reasons that can change after the due date, missing
     * consent and a closed window. {@link #NO_SUBMISSION} and {@link #NO_OWN_CODE} stay final for a due date, because neither the submissions before it nor the
     * diff can change; an extension re-evaluates them through the reset of checks whose due date moved into the future.
     */
    public static final Set<String> REQUEUED_BY_GENERATE_NOW = Set.of(NO_AI_CONSENT.name(), WINDOW_CLOSED.name(), REPOSITORY_UNAVAILABLE.name(), INVALID_LLM_RESPONSE.name(),
            LLM_REJECTED.name(), LLM_UNAVAILABLE.name(), INTERNAL_ERROR.name());
}
