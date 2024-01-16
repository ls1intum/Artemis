package de.tum.in.www1.artemis.exercise.mathexercise;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.math.MathExercise;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating MathExercises and related objects.
 */
public class MathExerciseFactory {

    /**
     * Generates a MathExercise for a Course.
     *
     * @param releaseDate       The release date of the MathExercise
     * @param dueDate           The due date of the MathExercise
     * @param assessmentDueDate The assessment due date of the MathExercise
     * @param course            The Course to which the MathExercise belongs
     * @return The generated MathExercise
     */
    public static MathExercise generateMathExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var mathExercise = (MathExercise) ExerciseFactory.populateExercise(new MathExercise(), releaseDate, dueDate, assessmentDueDate, course);
        return mathExercise;
    }

    /**
     * Generates a MathExercise for an Exam.
     *
     * @param exerciseGroup The ExerciseGroup to which the MathExercise belongs
     * @return The generated MathExercise
     */
    public static MathExercise generateMathExerciseForExam(ExerciseGroup exerciseGroup) {
        var mathExercise = (MathExercise) ExerciseFactory.populateExerciseForExam(new MathExercise(), exerciseGroup);
        return mathExercise;
    }

    /**
     * Generates a MathExercise for an Exam.
     *
     * @param exerciseGroup The ExerciseGroup to which the MathExercise belongs
     * @param title         The title of the MathExercise
     * @return The generated MathExercise
     */
    public static MathExercise generateMathExerciseForExam(ExerciseGroup exerciseGroup, String title) {
        var mathExercise = (MathExercise) ExerciseFactory.populateExerciseForExam(new MathExercise(), exerciseGroup, title);
        return mathExercise;
    }

}
