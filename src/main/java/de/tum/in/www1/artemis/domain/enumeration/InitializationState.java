package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The InitializationState enumeration.
 * UNINITIALIZED:
 * INITIALIZED: The participation is set up for submissions from the student
 * FINISHED: Text- / Modelling: At least one submission is done. Quiz: No further submissions should be possible
 * ARCHIVED: The participation is closed and cannot be edited / updated. The participation can only be loaded for review.
 * IMPORTANT: ARCHIVED participations need to be loaded via specified Repository-Calls, as they are excluded from the generic ones.
 * DB-Constraint: studentId / teamId + exerciseId + InitializationState have to be unique (except for ARCHIVED)
 */
public enum InitializationState {

    UNINITIALIZED(0), REPO_COPIED(1), REPO_CONFIGURED(2), INACTIVE(3), BUILD_PLAN_COPIED(4), BUILD_PLAN_CONFIGURED(5), INITIALIZED(6), FINISHED(7), ARCHIVED(8);

    private final Integer stateNumber;

    InitializationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(InitializationState state) {
        return this.stateNumber >= state.stateNumber;
    }
}
