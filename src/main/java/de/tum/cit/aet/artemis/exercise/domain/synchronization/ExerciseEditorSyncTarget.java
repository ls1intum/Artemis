package de.tum.cit.aet.artemis.exercise.domain.synchronization;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT, TEMPLATE_REPOSITORY, SOLUTION_REPOSITORY, TESTS_REPOSITORY, AUXILIARY_REPOSITORY, EXERCISE_METADATA
}
