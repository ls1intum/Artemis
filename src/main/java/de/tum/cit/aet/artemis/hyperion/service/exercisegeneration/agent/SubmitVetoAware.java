package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/** The contract the agent loop needs from a sandbox-backed tools object: whether it may end on submit, and whether its sandbox is still alive. */
public interface SubmitVetoAware {

    /**
     * Returns and clears the latest submit veto.
     *
     * @return whether submission was vetoed
     */
    boolean consumeSubmitVeto();

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
