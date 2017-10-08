package de.tum.in.www1.exerciseapp.domain.enumeration;

/**
 * The ParticipationState enumeration.
 */
public enum ParticipationState {
    UNINITIALIZED(0), REPO_COPIED(1), REPO_CONFIGURED(2), INACTIVE(3), BUILD_PLAN_COPIED(4), BUILD_PLAN_CONFIGURED(5), INITIALIZED(6);

    private Integer stateNumber;

    ParticipationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(ParticipationState state) {
        return this.stateNumber >= state.stateNumber;
    }
}
