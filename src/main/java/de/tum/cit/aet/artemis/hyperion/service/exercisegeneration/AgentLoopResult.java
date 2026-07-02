package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

/**
 * Outcome of an agent loop run: how it ended, how many model turns it took, and the agent's final message (which may be empty).
 */
public record AgentLoopResult(Status status, int turns, String finalMessage) {

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
