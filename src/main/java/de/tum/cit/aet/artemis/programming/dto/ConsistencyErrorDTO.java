package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * A DTO representing a consistency error
 */
// TODO: use a ProgrammingExerciseDTO instead of the whole ProgrammingExercise entity
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyErrorDTO(ProgrammingExercise programmingExercise, ErrorType type) {

    public enum ErrorType {
        VCS_PROJECT_MISSING, TEMPLATE_REPO_MISSING, SOLUTION_REPO_MISSING, AUXILIARY_REPO_MISSING, TEST_REPO_MISSING, TEMPLATE_BUILD_PLAN_MISSING, SOLUTION_BUILD_PLAN_MISSING;
    }
}
