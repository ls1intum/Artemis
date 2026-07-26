package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * A DTO representing a consistency error.
 *
 * @param programmingExercise the exercise the error was found on
 * @param type                what is inconsistent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyErrorDTO(ProgrammingExerciseSummaryDTO programmingExercise, ErrorType type) {

    /**
     * The exercise reference carried by a consistency error. The consistency-check table reads only the id and the
     * title off {@code error.programmingExercise}, so the whole exercise never has to cross the wire.
     *
     * @param id    the exercise id
     * @param title the exercise title
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingExerciseSummaryDTO(Long id, String title) {

        /**
         * Projects a programming exercise onto the fields the consistency-check table reads.
         *
         * @param exercise the exercise to project (may be {@code null})
         * @return the projection, or {@code null} if the input was {@code null}
         */
        public static ProgrammingExerciseSummaryDTO of(ProgrammingExercise exercise) {
            if (exercise == null) {
                return null;
            }
            return new ProgrammingExerciseSummaryDTO(exercise.getId(), exercise.getTitle());
        }
    }

    public enum ErrorType {
        VCS_PROJECT_MISSING, TEMPLATE_REPO_MISSING, SOLUTION_REPO_MISSING, AUXILIARY_REPO_MISSING, TEST_REPO_MISSING, TEMPLATE_BUILD_PLAN_MISSING, SOLUTION_BUILD_PLAN_MISSING
    }
}
