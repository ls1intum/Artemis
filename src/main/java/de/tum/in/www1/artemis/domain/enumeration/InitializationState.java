package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The InitializationState enumeration.
 * UNINITIALIZED:
 * INITIALIZED: The participation is set up for submissions from the student
 * FINISHED: Text- / Modelling: At least one submission is done. Quiz: No further submissions should be possible
 */
public enum InitializationState {

    UNINITIALIZED(0), REPO_COPIED(1), REPO_CONFIGURED(2), INACTIVE(3), BUILD_PLAN_COPIED(4), BUILD_PLAN_CONFIGURED(5), INITIALIZED(6), FINISHED(7);

    private final Integer stateNumber;

    InitializationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(InitializationState state) {
        return this.stateNumber >= state.stateNumber;
    }
}
