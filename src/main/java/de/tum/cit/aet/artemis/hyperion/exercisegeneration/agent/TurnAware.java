package de.tum.cit.aet.artemis.hyperion.exercisegeneration.agent;

/**
 * Implemented by a tools object that wants to know which agent turn is currently executing, so it can tag out-of-band events (e.g. streamed file snapshots) with the turn number.
 * The {@link AgentLoopRunner} calls {@link #onTurn(int)} at the start of each turn; a plain tools object that does not implement this interface is simply not notified.
 */
public interface TurnAware {

    /**
     * Notifies the tools that a new agent turn is about to execute.
     *
     * @param turn the 1-based turn number
     */
    void onTurn(int turn);
}
