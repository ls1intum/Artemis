package de.tum.cit.aet.artemis.core.util;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

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
     * Must be initialized before any file path operations are performed, typically during application startup (see ArtemisApp.java).
     */
    @NonNull
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
    public static Path getFileUploadPath() {
        return fileUploadPath;
    }

    /**
     * @return the path to the temporary files directory
     */
    @NonNull
    public static Path getTempFilePath() {
        return fileUploadPath.resolve("images").resolve("temp");
    }

    /**
     * @return the path to the drag and drop backgrounds directory
     */
    @NonNull
    public static Path getDragAndDropBackgroundFilePath() {
        return fileUploadPath.resolve("images").resolve("drag-and-drop").resolve("backgrounds");
    }

    /**
     * @return the path to the drag item images directory
     */
    @NonNull
    public static Path getDragItemFilePath() {
        return fileUploadPath.resolve("images").resolve("drag-and-drop").resolve("drag-items");
    }

    /**
     * @return the path to the course icons directory
     */
    @NonNull
    public static Path getCourseIconFilePath() {
        return fileUploadPath.resolve("images").resolve("course").resolve("icons");
    }

    /**
     * @return the path to the profile pictures directory
     */
    @NonNull
    public static Path getProfilePictureFilePath() {
        return fileUploadPath.resolve("images").resolve("user").resolve("profile-pictures");
    }

    /**
     * @return the path to the exam user signatures directory
     */
    @NonNull
    public static Path getExamUserSignatureFilePath() {
        return fileUploadPath.resolve("images").resolve("exam-user").resolve("signatures");
    }

    /**
     * @return the path to the student images directory
     */
    @NonNull
    public static Path getStudentImageFilePath() {
        return fileUploadPath.resolve("images").resolve("exam-user");
    }

    /**
     * @return the path to the lecture attachments directory
     */
    @NonNull
    public static Path getLectureAttachmentFileSystemPath() {
        return fileUploadPath.resolve("attachments").resolve("lecture");
    }

    /**
     * @return the path to the attachment video unit files directory
     */
    @NonNull
    public static Path getAttachmentVideoUnitFileSystemPath() {
        return fileUploadPath.resolve("attachments").resolve("attachment-unit");
    }

    /**
     * @return the path to the file upload exercises directory
     */
    @NonNull
    public static Path getFileUploadExercisesFilePath() {
        return fileUploadPath.resolve("file-upload-exercises");
    }

    /**
     * @return the path to the markdown files directory
     */
    @NonNull
    public static Path getMarkdownFilePath() {
        return fileUploadPath.resolve("markdown");
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
