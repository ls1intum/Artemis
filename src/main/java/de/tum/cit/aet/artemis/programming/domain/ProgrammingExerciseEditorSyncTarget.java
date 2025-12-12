package de.tum.cit.aet.artemis.programming.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum ProgrammingExerciseEditorSyncTarget {
    PROBLEM_STATEMENT, TEMPLATE_REPOSITORY, SOLUTION_REPOSITORY, TESTS_REPOSITORY, AUXILIARY_REPOSITORY
}
