package de.tum.in.www1.artemis.exercise.fileupload;

import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseTestFactory;

/**
 * Factory to create file upload exercises and submissions for testing.
 */
public class FileUploadTestFactory {

    /**
     * Creates a dummy file upload exercise for a course.
     *
     * @param releaseDate       The release date of the exercise
     * @param dueDate           The due date of the exercise
     * @param assessmentDueDate The assessment due date of the exercise
     * @param filePattern       The file pattern of the exercise
     * @param course            The course the exercise belongs to
     * @return FileUploadExercise that was created
     */
    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        var fileUploadExercise = (FileUploadExercise) ExerciseTestFactory.populateExercise(new FileUploadExercise(), releaseDate, dueDate, assessmentDueDate, course);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise.setExampleSolution("This is my example solution");
        return fileUploadExercise;
    }

    /**
     * Creates a dummy file upload exercise for an exam.
     *
     * @param filePattern   The file pattern of the exercise
     * @param exerciseGroup The exercise group the exercise belongs to
     * @return FileUploadExercise that was created
     */
    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) ExerciseTestFactory.populateExerciseForExam(fileUploadExercise, exerciseGroup);
    }

    /**
     * Creates a dummy file upload submission for testing.
     *
     * @param submitted Whether the submission should contain a submission date
     * @return FileUploadSubmission that was created
     */
    public static FileUploadSubmission generateFileUploadSubmission(boolean submitted) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(submitted);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    /**
     * Creates a dummy file upload submission for testing with a file.
     *
     * @param submitted Whether the submission should contain a submission date
     * @param filePath  The path to the file that was uploaded
     * @return FileUploadSubmission that was created
     */
    public static FileUploadSubmission generateFileUploadSubmissionWithFile(boolean submitted, String filePath) {
        FileUploadSubmission fileUploadSubmission = generateFileUploadSubmission(submitted);
        fileUploadSubmission.setFilePath(filePath);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    /**
     * Creates a dummy file upload submission submitted late.
     *
     * @return FileUploadSubmission that was created
     */
    public static FileUploadSubmission generateLateFileUploadSubmission() {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(now().plusDays(1));
        return fileUploadSubmission;
    }
}
