package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/** Outcome of one agent-loop session. {@code turns} is session-local budget state, not the run-level telemetry count. */
public record AgentLoopResult(Status status, int turns, String finalMessage) {

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
