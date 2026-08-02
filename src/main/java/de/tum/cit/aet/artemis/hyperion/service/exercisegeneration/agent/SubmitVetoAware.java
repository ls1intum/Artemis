package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/** Allows a tool to reject the loop-ending effect of its latest {@code submit} call. */
public interface SubmitVetoAware {

    /**
     * Returns and clears the latest submit veto.
     *
     * @return whether submission was vetoed
     */
    boolean consumeSubmitVeto();
}
