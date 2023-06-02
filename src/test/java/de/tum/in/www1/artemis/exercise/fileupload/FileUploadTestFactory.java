package de.tum.in.www1.artemis.exercise.fileupload;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseTestFactory;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

public class FileUploadTestFactory {

    /**
     * Creates a dummy attachment for testing.
     *
     * @param date The optional upload and release date to set on the attachment
     * @return Attachment that was created
     */
    public static Attachment generateAttachment(ZonedDateTime date) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        if (date != null) {
            attachment.setReleaseDate(date);
            attachment.setUploadDate(date);
        }
        attachment.setName("TestAttachment");
        attachment.setVersion(1);
        return attachment;
    }

    /**
     * Creates a dummy attachment for testing with a placeholder image file on disk.
     *
     * @param startDate The release date to set on the attachment
     * @return Attachment that was created with its link set to a testing file on disk
     */
    public static Attachment generateAttachmentWithFile(ZonedDateTime startDate) {
        Attachment attachment = generateAttachment(startDate);
        String testFileName = "test_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), new File(FilePathService.getTempFilePath(), testFileName));
        }
        catch (IOException ex) {
            fail("Failed while copying test attachment files", ex);
        }
        // Path.toString() uses platform dependant path separators. Since we want to use this as a URL later, we need to replace \ with /.
        attachment.setLink(Path.of(FileService.DEFAULT_FILE_SUBPATH, testFileName).toString().replace('\\', '/'));
        return attachment;
    }

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
