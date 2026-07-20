package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * Implemented by a tools object that can veto the loop-ending effect of a {@code submit} tool call. The {@code submit} tool itself always runs and returns its (possibly
 * rejecting) message to the model as an ordinary tool result; this interface is the separate, out-of-band signal that tells {@link AgentLoopRunner} whether that particular
 * {@code submit} call must keep the loop going instead of ending the session.
 * <p>
 * Used by the staged generation workflow: {@link SandboxAgentTools#submit} re-runs the current stage's mechanical check before accepting a submission, and vetoes the loop-ending
 * effect when that check fails so the agent can fix the reported issues and resubmit. A legacy (unstaged) session never sets the veto, so {@code submit} still ends the loop on
 * the first call exactly as before this seam existed.
 */
public interface SubmitVetoAware {

    /**
     * Reports and clears whether the most recent {@code submit} call was rejected. "Consume" because the flag is one-shot: reading it clears it, so a later genuinely-accepted
     * {@code submit} is never blocked by a stale veto from an earlier, already-fixed rejection.
     *
     * @return {@code true} if the most recent {@code submit} tool call was rejected and the agent loop must continue instead of ending
     */
    boolean consumeSubmitVeto();
}
