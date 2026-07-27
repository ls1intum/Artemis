package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * Implemented by a tools object that wants to know which agent turn is currently executing, so it can tag out-of-band events (e.g. streamed file changes) with the turn number.
 * The {@link AgentLoopRunner} calls {@link #onTurn(int)} at the start of each turn; a plain tools object that does not implement this interface is simply not notified.
 */
public interface TurnAware {

    /**
     * @param turn the 1-based turn number about to execute
     */
    void onTurn(int turn);
}
