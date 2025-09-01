package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCourseScoreDTO(long id, Class<? extends Exercise> type, @NotNull IncludedInOverallScore includedInOverallScore, @NotNull AssessmentType assessmentType,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, double maxPoints,
        @Nullable Double bonusPoints, long courseId) {

    public ExerciseType exerciseType() {
        return ExerciseType.getExerciseTypeFromClass(type);
    }

    /**
     * Creates an ExerciseCourseScoreDTO from an Exercise entity.
     *
     * @param exercise the exercise entity
     * @return the corresponding ExerciseCourseScoreDTO
     */
    public static ExerciseCourseScoreDTO from(Exercise exercise) {
        ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate = null;
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            buildAndTestStudentSubmissionsAfterDueDate = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate();
        }
        return new ExerciseCourseScoreDTO(exercise.getId(), exercise.getClass(), exercise.getIncludedInOverallScore(), exercise.getAssessmentType(), exercise.getDueDate(),
                exercise.getAssessmentDueDate(), buildAndTestStudentSubmissionsAfterDueDate, exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getCourseViaExerciseGroupOrCourseMember().getId());
    }
}
