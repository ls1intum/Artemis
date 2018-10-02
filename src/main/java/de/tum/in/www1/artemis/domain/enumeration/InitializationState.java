package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The InitializationState enumeration.
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
