package de.tum.cit.aet.artemis.core.util;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The fixed directory each file type is stored under.
 * <p>
 * This converter provides static methods to generate base paths for the various file storage locations (e.g. attachments, profile pictures, uploads). Neither direction of the
 * translation between a file and its identity lives here any more: to locate a stored file, build the {@link FileSystemLocation} record of its type from the owning entity, and
 * to name the URL it is served under, build the {@link PublicFileUrl} record of its type. Nothing reads an entity id out of a path or URI segment.
 * </p>
 *
 * @see FileSystemLocation for the file system location of a stored file
 * @see PublicFileUrl for the REST URL a stored file is served under
 */
public final class FilePathConverter {

    /**
     * The base path for file uploads, set from application properties.
     * This is used as the root for all file storage locations.
     * <p>
     * It is process-wide and is published by {@code FileUploadPathEnvironmentPostProcessor} before the application context is created, so that no bean, no
     * {@code @PostConstruct} and no {@code ApplicationReadyEvent} listener can observe it unset. Server tests set it themselves, once per JVM, from
     * {@code AbstractArtemisIntegrationTest}.
     */
    @Nullable
    private static Path fileUploadPath;

    private FilePathConverter() {
    }

    /**
     * Sets the base file upload path from the application properties.
     * This is used as the root for all file storage locations.
     *
     * @param fileUploadPath the base path for file uploads
     */
    public static void setFileUploadPath(@NonNull Path fileUploadPath) {
        FilePathConverter.fileUploadPath = fileUploadPath;
    }

    /**
     * The base path every upload location is resolved against.
     * <p>
     * Exposed so a caller that has to repoint the path can put back what it found. The value is process-wide, and the
     * integration test base sets it once per JVM, so anything that overwrites it without restoring leaves every later
     * caller resolving uploads under the wrong root.
     *
     * @return the base path for file uploads, or null if it has not been set yet
     */
    @Nullable
    public static Path getFileUploadPath() {
        return fileUploadPath;
    }

    /**
     * The upload root, or a loud failure if nothing has published it yet.
     * <p>
     * Every accessor below goes through this rather than reading the field, so that a caller which runs too early is told what is wrong instead of getting a
     * {@link NullPointerException} that says nothing. That matters most for a migration entry, which catches its own failures per row and would otherwise count every file as
     * failed and still be recorded as executed.
     *
     * @return the base path every upload location is resolved against
     * @throws IllegalStateException if the upload path has not been set yet
     */
    @NonNull
    private static Path root() {
        if (fileUploadPath == null) {
            throw new IllegalStateException("The file upload path has not been set yet. It is published by FileUploadPathEnvironmentPostProcessor before the application "
                    + "context is created; a caller reaching this earlier, or a test that has not set it, resolves every stored file against nothing.");
        }
        return fileUploadPath;
    }

    /**
     * @return the path to the temporary files directory
     */
    @NonNull
    public static Path getTempFilePath() {
        return root().resolve("images").resolve("temp");
    }

    /**
     * @return the path to the drag and drop backgrounds directory
     */
    @NonNull
    public static Path getDragAndDropBackgroundFilePath() {
        return root().resolve("images").resolve("drag-and-drop").resolve("backgrounds");
    }

    /**
     * @return the path to the drag item images directory
     */
    @NonNull
    public static Path getDragItemFilePath() {
        return root().resolve("images").resolve("drag-and-drop").resolve("drag-items");
    }

    /**
     * @return the path to the course icons directory
     */
    @NonNull
    public static Path getCourseIconFilePath() {
        return root().resolve("images").resolve("course").resolve("icons");
    }

    /**
     * @return the path to the profile pictures directory
     */
    @NonNull
    public static Path getProfilePictureFilePath() {
        return root().resolve("images").resolve("user").resolve("profile-pictures");
    }

    /**
     * @return the path to the exam user signatures directory
     */
    @NonNull
    public static Path getExamUserSignatureFilePath() {
        return root().resolve("images").resolve("exam-user").resolve("signatures");
    }

    /**
     * @return the path to the student images directory
     */
    @NonNull
    public static Path getStudentImageFilePath() {
        return root().resolve("images").resolve("exam-user");
    }

    /**
     * @return the path to the lecture attachments directory
     */
    @NonNull
    public static Path getLectureAttachmentFileSystemPath() {
        return root().resolve("attachments").resolve("lecture");
    }

    /**
     * @return the path to the attachment video unit files directory
     */
    @NonNull
    public static Path getAttachmentVideoUnitFileSystemPath() {
        return root().resolve("attachments").resolve("attachment-unit");
    }

    /**
     * @return the path to the file upload exercises directory
     */
    @NonNull
    public static Path getFileUploadExercisesFilePath() {
        return root().resolve("file-upload-exercises");
    }

    /**
     * @return the path to the markdown files directory
     */
    @NonNull
    public static Path getMarkdownFilePath() {
        return root().resolve("markdown");
    }

    /**
     * @param courseId       the course ID
     * @param conversationId the conversation ID
     * @return the path to the markdown files for the conversation
     */
    @NonNull
    public static Path getMarkdownFilePathForConversation(long courseId, long conversationId) {
        return getMarkdownFilePath().resolve("communication").resolve(String.valueOf(courseId)).resolve(String.valueOf(conversationId));
    }

    /**
     * Builds file path for file upload submission.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission
     * @return path where submission for file upload exercise is stored
     */
    @NonNull
    public static Path buildFileUploadSubmissionPath(long exerciseId, long submissionId) {
        return getFileUploadExercisesFilePath().resolve(String.valueOf(exerciseId)).resolve(String.valueOf(submissionId));
    }
}
