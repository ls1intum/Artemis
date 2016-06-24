package de.tum.in.www1.exerciseapp.domain.enumeration;

/**
 * The ParticipationState enumeration.
 */
public enum ParticipationState {
    UNINITIALIZED(0), REPO_FORKED(1), REPO_PERMISSIONS_SET(2), PLAN_CLONED(3), PLAN_REPO_UPDATED(4), PLAN_ENABLED(5), INITIALIZED(6);



    private Integer stateNumber;

    ParticipationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(ParticipationState state) {
        return this.stateNumber >= state.stateNumber;
    }
}
