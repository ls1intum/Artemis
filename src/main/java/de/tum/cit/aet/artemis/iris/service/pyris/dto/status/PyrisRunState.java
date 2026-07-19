package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

/**
 * Current lifecycle state of a Pyris pipeline run.
 */
public enum PyrisRunState {

    RUNNING, FINISHED, FAILED;

    /**
     * @return whether this state ends the pipeline run
     */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
