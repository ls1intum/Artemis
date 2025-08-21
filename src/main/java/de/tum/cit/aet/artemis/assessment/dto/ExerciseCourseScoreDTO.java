package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

public record ExerciseCourseScoreDTO(long id, Class<? extends Exercise> type, IncludedInOverallScore includedInOverallScore, AssessmentType assessmentType, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, double maxPoints, @Nullable Double bonusPoints, long courseId) {

    public ExerciseType exerciseType() {
        return ExerciseType.getExerciseTypeFromClass(type);
    }

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
