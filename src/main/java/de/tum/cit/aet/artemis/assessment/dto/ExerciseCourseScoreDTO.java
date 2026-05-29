package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCourseScoreDTO(long id, ExerciseType type, @NotNull IncludedInOverallScore includedInOverallScore, @NotNull AssessmentType assessmentType,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, double maxPoints,
        @Nullable Double bonusPoints, long courseId) {

    /**
     * JPQL constructor that accepts the raw entity class produced by Hibernate's {@code TYPE(...)} function
     * and maps it to the {@link ExerciseType} discriminator used by the canonical record component.
     */
    public ExerciseCourseScoreDTO(long id, Class<? extends Exercise> type, @NotNull IncludedInOverallScore includedInOverallScore, @NotNull AssessmentType assessmentType,
            @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, double maxPoints,
            @Nullable Double bonusPoints, long courseId) {
        this(id, ExerciseType.getExerciseTypeFromClass(type), includedInOverallScore, assessmentType, dueDate, assessmentDueDate, buildAndTestStudentSubmissionsAfterDueDate,
                maxPoints, bonusPoints, courseId);
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
        return new ExerciseCourseScoreDTO(exercise.getId(), ExerciseType.getExerciseTypeFromClass(exercise.getClass()), exercise.getIncludedInOverallScore(),
                exercise.getAssessmentType(), exercise.getDueDate(), exercise.getAssessmentDueDate(), buildAndTestStudentSubmissionsAfterDueDate, exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getCourseViaExerciseGroupOrCourseMember().getId());
    }
}
