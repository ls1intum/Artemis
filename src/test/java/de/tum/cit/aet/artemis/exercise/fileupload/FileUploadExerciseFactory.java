package de.tum.cit.aet.artemis.exercise.fileupload;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating FileUploadExercises and related objects.
 */
public class FileUploadExerciseFactory {

    /**
     * Generates a FileUploadExercise for a course.
     *
     * @param releaseDate       The release date of the exercise
     * @param dueDate           The due date of the exercise
     * @param assessmentDueDate The assessment due date of the exercise
     * @param filePattern       The pattern for the allowed file types
     * @param course            The course of the exercise
     * @return The generated FileUploadExercise
     */
    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        var fileUploadExercise = (FileUploadExercise) ExerciseFactory.populateExercise(new FileUploadExercise(), releaseDate, dueDate, assessmentDueDate, course);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise.setExampleSolution("This is my example solution");
        return fileUploadExercise;
    }

    /**
     * Generates a FileUploadExercise for an exam.
     *
     * @param filePattern   The pattern for the allowed file types
     * @param exerciseGroup The exercise group of the exercise
     * @return The generated FileUploadExercise
     */
    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) ExerciseFactory.populateExerciseForExam(fileUploadExercise, exerciseGroup);
    }

    /**
     * Generates a FileUploadExercise for an exam.
     *
     * @param filePattern   The pattern for the allowed file types
     * @param exerciseGroup The exercise group of the exercise
     * @param title         The title of the exercise
     * @return The generated FileUploadExercise
     */
    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup, String title) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) ExerciseFactory.populateExerciseForExam(fileUploadExercise, exerciseGroup, title);
    }
}
