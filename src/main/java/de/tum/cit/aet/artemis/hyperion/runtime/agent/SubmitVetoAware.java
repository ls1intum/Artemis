package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.Optional;

/** The contract the agent loop needs from a sandbox-backed tools object: how it may complete, and whether its sandbox is still alive. */
public interface SubmitVetoAware {

    /**
     * Returns and clears the latest submit veto.
     *
     * @return whether submission was vetoed
     */
    boolean consumeSubmitVeto();

    /**
     * Explains what a text-only reply must still do before this session can complete.
     *
     * @return feedback when explicit submission is required, otherwise empty
     */
    default Optional<String> pendingCompletionFeedback() {
        return Optional.empty();
    }

    /**
     * Whether the sandbox session backing these tools has been terminated, which the loop treats as a hard stop.
     * <p>
     * Declared on the interface rather than discovered by the loop through a type switch, whose {@code default -> false} arm would report "sandbox alive" for an unseen
     * implementation and keep the loop calling a dead sandbox.
     *
     * @return whether the sandbox session is gone
     */
    boolean isSandboxSessionTerminated();
}
