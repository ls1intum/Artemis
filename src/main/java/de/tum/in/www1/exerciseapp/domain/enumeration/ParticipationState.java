package de.tum.in.www1.exerciseapp.domain.enumeration;

/**
 * The ParticipationState enumeration.
 */
public enum ParticipationState {
    UNINITIALIZED(0), REPO_COPIED(1), REPO_CONFIGURED(2), BUILD_PLAN_COPIED(3), BUILD_PLAN_CONFIGURED(4), INITIALIZED(5);

    private Integer stateNumber;

    ParticipationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(ParticipationState state) {
        return this.stateNumber >= state.stateNumber;
    }
}
