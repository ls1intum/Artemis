package de.tum.in.www1.artemis.exercise;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

/**
 * Factory for creating Exercises and related objects.
 */
public class ExerciseFactory {

    /**
     * Populates an exercise with passed dates and sets its course. Other attributes are initialized with default values.
     *
     * @param exercise          The exercise to be populated.
     * @param releaseDate       The release date of the exercise.
     * @param dueDate           The due date of the exercise.
     * @param assessmentDueDate The assessment due date of the exercise.
     * @param course            The course to which the exercise should be added.
     * @return The populated course exercise.
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
     * Populates an exam exercise with default values and adds it to the exercise group.
     *
     * @param exercise      The exercise to be populated.
     * @param exerciseGroup The exam exercise group to which the exercise should be added.
     * @return The populated exam exercise.
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
            exercise.setGradingCriteria(Set.of(new GradingCriterion()));
        }
        return exercise;
    }

    /**
     * Populates an exam exercise with default values, the passed title, and adds it to the exercise group.
     *
     * @param exercise      The exercise to be populated.
     * @param exerciseGroup The exam exercise group to which the exercise should be added.
     * @param title         The title used for the exercise.
     * @return The populated exam exercise.
     */
    public static Exercise populateExerciseForExam(Exercise exercise, ExerciseGroup exerciseGroup, String title) {
        var populatedExercise = populateExerciseForExam(exercise, exerciseGroup);
        populatedExercise.setTitle(title + UUID.randomUUID().toString().substring(0, 3));
        return populatedExercise;
    }

    /**
     * Generates a grading criterion with the passed title.
     *
     * @param title The title that should be set.
     * @return The newly created grading criterion.
     */
    public static GradingCriterion generateGradingCriterion(String title) {
        var criterion = new GradingCriterion();
        criterion.setTitle(title);
        return criterion;
    }

    /**
     * Generates grading instructions using a grading criterion.
     *
     * @param criterion                The grading criterion of the instructions.
     * @param numberOfTestInstructions The number of instructions that should be created.
     * @param usageCount               The usage count of each instruction.
     * @return Set of generated grading instructions.
     */
    public static Set<GradingInstruction> generateGradingInstructions(GradingCriterion criterion, int numberOfTestInstructions, int usageCount) {
        var instructions = new HashSet<GradingInstruction>();
        while (numberOfTestInstructions > 0) {
            var exampleInstruction1 = new GradingInstruction();
            exampleInstruction1.setGradingCriterion(criterion);
            exampleInstruction1.setCredits(1);
            exampleInstruction1.setGradingScale("good test");
            exampleInstruction1.setInstructionDescription("created first instruction with empty criteria for testing");
            exampleInstruction1.setFeedback("test feedback");
            exampleInstruction1.setUsageCount(usageCount);
            instructions.add(exampleInstruction1);
            numberOfTestInstructions--;
        }
        return instructions;
    }
}
