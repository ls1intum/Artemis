package de.tum.cit.aet.artemis.exercise.domain.synchronization;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Describes which editor scope a synchronization event refers to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT, TEMPLATE_REPOSITORY, SOLUTION_REPOSITORY, TESTS_REPOSITORY, AUXILIARY_REPOSITORY, EXERCISE_METADATA
}
