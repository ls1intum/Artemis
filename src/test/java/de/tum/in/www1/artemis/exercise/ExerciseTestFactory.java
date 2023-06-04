package de.tum.in.www1.artemis.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

/**
 * Factory to create exercises for testing.
 */
public class ExerciseTestFactory {

    /**
     * Populates an exercise with default values for a course.
     *
     * @param exercise          the exercise to populate
     * @param releaseDate       the release date
     * @param dueDate           the due date
     * @param assessmentDueDate the assessment due date
     * @param course            the course the exercise belongs to
     * @return the populated exercise
     */
    public static Exercise populateExercise(Exercise exercise, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Problem Statement");
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        exercise.setReleaseDate(releaseDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.getCategories().add("Category");
        exercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
        return exercise;
    }

    /**
     * Populates an exercise with default values for an exam.
     *
     * @param exercise      the exercise to populate
     * @param exerciseGroup the exercise group the exercise belongs to
     * @return the populated exercise
     */
    public static Exercise populateExerciseForExam(Exercise exercise, ExerciseGroup exerciseGroup) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Exam Problem Statement");
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        // these values are set to null explicitly
        exercise.setReleaseDate(null);
        exercise.setDueDate(null);
        exercise.setAssessmentDueDate(null);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.getCategories().add("Category");
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        if (!(exercise instanceof QuizExercise)) {
            exercise.setGradingInstructions("Grading instructions");
            exercise.setGradingCriteria(List.of(new GradingCriterion()));
        }
        return exercise;
    }
}
