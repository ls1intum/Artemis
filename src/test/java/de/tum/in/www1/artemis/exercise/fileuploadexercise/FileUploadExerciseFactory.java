package de.tum.in.www1.artemis.exercise.fileuploadexercise;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating FileUploadExercises and related objects.
 */
public class FileUploadExerciseFactory {

    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        var fileUploadExercise = (FileUploadExercise) ExerciseFactory.populateExercise(new FileUploadExercise(), releaseDate, dueDate, assessmentDueDate, course);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise.setExampleSolution("This is my example solution");
        return fileUploadExercise;
    }

    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) ExerciseFactory.populateExerciseForExam(fileUploadExercise, exerciseGroup);
    }
}
