package de.tum.cit.aet.artemis.exercise.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes the state of a participation.
 */
public enum InitializationState {

    /**
     * Student started the exercise, but repository/build plan are not yet set up.
     */
    UNINITIALIZED(0),
    /**
     * Template content was copied to the student repository.
     */
    REPO_COPIED(1),
    /**
     * Student repository is fully set up.
     */
    REPO_CONFIGURED(2),
    /**
     * Build plan in external CI system was cleaned up due to inactivity.
     */
    INACTIVE(3),
    /**
     * Template build plan was copied.
     */
    BUILD_PLAN_COPIED(4),
    /**
     * Build plan for the student participation is fully configured.
     */
    BUILD_PLAN_CONFIGURED(5),
    /**
     * Student can work on the repository and submit, i.e. push code.
     */
    INITIALIZED(6),
    /**
     * Student has submitted successfully to the exercise.
     */
    FINISHED(7);

    private final Integer stateNumber;

    InitializationState(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public boolean hasCompletedState(InitializationState state) {
        return this.stateNumber >= state.stateNumber;
    }

    /**
     * All states for which {@link #hasCompletedState} returns true for the given state. Lets a query filter on
     * completion without spelling the states out, so it cannot drift apart from the check above when a state is added.
     *
     * @param state the state that must have been completed
     * @return the states that have completed it, including the state itself
     */
    public static Set<InitializationState> statesThatCompleted(InitializationState state) {
        return Arrays.stream(values()).filter(candidate -> candidate.hasCompletedState(state)).collect(Collectors.toUnmodifiableSet());
    }
}
