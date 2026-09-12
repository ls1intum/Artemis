package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Exercise group as returned in the error body of a rejected exam import (see
 * {@link de.tum.cit.aet.artemis.exam.exception.ExamConfigurationException}).
 * <p>
 * The import dialog writes this list back onto the exam it is editing and re-renders the selection table from it, so
 * the record carries exactly the fields that table reads and echoes back on the retry: the group title and mandatory
 * flag, and per exercise the source id, the type discriminator, the title and short name (both blanked by the server
 * wherever the user must pick a new one) and the points. Everything else the exercise entity used to serialize (the
 * whole exercise graph including its nested exam, course and participations) is not read by the dialog.
 *
 * @param title       the title of the exercise group
 * @param isMandatory whether the exercise group must be included when generating student exams
 * @param exercises   the exercises of the group, with the rejected titles / short names blanked
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamImportErrorExerciseGroupDTO(@Nullable String title, @Nullable Boolean isMandatory, List<ExerciseForExamImportErrorDTO> exercises) {

    /**
     * Exercise of a rejected exam import, as embedded in {@link ExamImportErrorExerciseGroupDTO}.
     *
     * @param id          the id of the source exercise the import copies from
     * @param type        the exercise type discriminator (serialized as the lowercase value, e.g. "programming")
     * @param title       the exercise title, blank if the server rejected it and the user must choose a new one
     * @param shortName   the exercise short name, blank if the server rejected it and the user must choose a new one
     * @param maxPoints   the maximum points achievable for the exercise
     * @param bonusPoints the bonus points achievable for the exercise
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseForExamImportErrorDTO(@Nullable Long id, ExerciseType type, @Nullable String title, @Nullable String shortName, @Nullable Double maxPoints,
            @Nullable Double bonusPoints) {

        /**
         * Builds the error-body projection of an exercise.
         *
         * @param exercise the exercise of the rejected import
         * @return the projection
         */
        public static ExerciseForExamImportErrorDTO of(Exercise exercise) {
            return new ExerciseForExamImportErrorDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(),
                    exercise.getBonusPoints());
        }
    }

    /**
     * Builds the error-body projection of the exercise groups of a rejected import.
     *
     * @param exerciseGroups the exercise groups of the rejected import
     * @return the projections, in the iteration order of the given groups and their exercises
     */
    public static List<ExamImportErrorExerciseGroupDTO> ofAll(List<ExerciseGroup> exerciseGroups) {
        return exerciseGroups.stream().map(ExamImportErrorExerciseGroupDTO::of).toList();
    }

    private static ExamImportErrorExerciseGroupDTO of(ExerciseGroup exerciseGroup) {
        List<ExerciseForExamImportErrorDTO> exercises = exerciseGroup.getExercises().stream().map(ExerciseForExamImportErrorDTO::of).toList();
        return new ExamImportErrorExerciseGroupDTO(exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), exercises);
    }
}
