package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of one agent-loop session. {@code turns} is session-local budget state, not the run-level telemetry count.
 *
 * @param status       how the session ended
 * @param turns        the model turns the session used
 * @param finalMessage the model's last non-blank text
 * @param stopCause    why an {@link Status#ERROR} session stopped, bounded and safe to show the instructor; {@code null} otherwise or when unknown
 */
public record AgentLoopResult(Status status, int turns, String finalMessage, @Nullable String stopCause) {

    private static final int MAX_STOP_CAUSE_CHARS = 2_000;

    public AgentLoopResult {
        if (stopCause != null && stopCause.length() > MAX_STOP_CAUSE_CHARS) {
            stopCause = stopCause.substring(0, MAX_STOP_CAUSE_CHARS - 1) + "…";
        }
    }

    public AgentLoopResult(Status status, int turns, String finalMessage) {
        this(status, turns, finalMessage, null);
    }

    public static AgentLoopResult outsideSession(Status status, String finalMessage) {
        return new AgentLoopResult(status, 0, finalMessage);
    }

    public enum Status {
        /** The agent stopped on its own (no further tool calls) within the budget. */
        COMPLETED,
        /** The iteration budget was reached before the agent stopped. */
        BUDGET_EXHAUSTED,
        /** The run was cancelled. */
        CANCELLED,
        /** The run ended with an unrecoverable error. */
        ERROR
    }
}
