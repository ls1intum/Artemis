package de.tum.cit.aet.artemis.core.util;

import java.net.URI;
import java.nio.file.Path;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;

/**
 * Converter for generating and parsing file system paths and external URIs for different file types in Artemis.
 * <p>
 * This converter provides static methods to convert between internal file system paths and external URIs,
 * as well as to generate base paths for various file storage locations (e.g., attachments, profile pictures, uploads).
 * The mapping is based on the {@link FilePathType} and the entity IDs associated with the files.
 * </p>
 */
public final class FilePathConverter {

    /**
     * The base path for file uploads, set from application properties.
     * This is used as the root for all file storage locations.
     * Must be initialized before any file path operations are performed, typically during application startup (see ArtemisApp.java).
     */
    @NotNull
    private static Path fileUploadPath;

    private FilePathConverter() {
    }

    /**
     * Sets the base file upload path from the application properties.
     * This is used as the root for all file storage locations.
     *
     * @param fileUploadPath the base path for file uploads
     */
    public static void setFileUploadPath(@NotNull Path fileUploadPath) {
        FilePathConverter.fileUploadPath = fileUploadPath;
    }

    /**
     * @return the path to the temporary files directory
     */
    @NotNull
    public static Path getTempFilePath() {
        return fileUploadPath.resolve("images").resolve("temp");
    }

    /**
     * @return the path to the drag and drop backgrounds directory
     */
    @NotNull
    public static Path getDragAndDropBackgroundFilePath() {
        return fileUploadPath.resolve("images").resolve("drag-and-drop").resolve("backgrounds");
    }

    /**
     * @return the path to the drag item images directory
     */
    @NotNull
    public static Path getDragItemFilePath() {
        return fileUploadPath.resolve("images").resolve("drag-and-drop").resolve("drag-items");
    }

    /**
     * @return the path to the course icons directory
     */
    @NotNull
    public static Path getCourseIconFilePath() {
        return fileUploadPath.resolve("images").resolve("course").resolve("icons");
    }

    /**
     * @return the path to the profile pictures directory
     */
    @NotNull
    public static Path getProfilePictureFilePath() {
        return fileUploadPath.resolve("images").resolve("user").resolve("profile-pictures");
    }

    /**
     * @return the path to the exam user signatures directory
     */
    @NotNull
    public static Path getExamUserSignatureFilePath() {
        return fileUploadPath.resolve("images").resolve("exam-user").resolve("signatures");
    }

    /**
     * @return the path to the student images directory
     */
    @NotNull
    public static Path getStudentImageFilePath() {
        return fileUploadPath.resolve("images").resolve("exam-user");
    }

    /**
     * @return the path to the lecture attachments directory
     */
    @NotNull
    public static Path getLectureAttachmentFileSystemPath() {
        return fileUploadPath.resolve("attachments").resolve("lecture");
    }

    /**
     * @return the path to the attachment video unit files directory
     */
    @NotNull
    public static Path getAttachmentVideoUnitFileSystemPath() {
        return fileUploadPath.resolve("attachments").resolve("attachment-unit");
    }

    /**
     * @return the path to the file upload exercises directory
     */
    @NotNull
    public static Path getFileUploadExercisesFilePath() {
        return fileUploadPath.resolve("file-upload-exercises");
    }

    /**
     * @return the path to the markdown files directory
     */
    @NotNull
    public static Path getMarkdownFilePath() {
        return fileUploadPath.resolve("markdown");
    }

    /**
     * @param courseId       the course ID
     * @param conversationId the conversation ID
     * @return the path to the markdown files for the conversation
     */
    @NotNull
    public static Path getMarkdownFilePathForConversation(long courseId, long conversationId) {
        return getMarkdownFilePath().resolve("communication").resolve(String.valueOf(courseId)).resolve(String.valueOf(conversationId));
    }

    /**
     * Converts a public file URI to its corresponding local file system path.
     * <p>
     * Example:
     *
     * <pre>
     *     URI externalUri = URI.create("attachments/lecture/4/slides.pdf");
     *     Path fileSystemPath = FilePathService.fileSystemPathForExternalUri(externalUri, FilePathType.LECTURE_ATTACHMENT);
     *     fileSystemPath: uploads/attachments/lecture/4/slides.pdf
     * </pre>
     *
     * @param externalUri  the external file URI to convert
     * @param filePathType the type of file path
     * @return the path to the file in the local filesystem
     * @throws FilePathParsingException if the URI cannot be parsed correctly
     */
    @NotNull
    public static Path fileSystemPathForExternalUri(@NotNull URI externalUri, @NotNull FilePathType filePathType) {
        String uriPath = externalUri.getPath();
        Path path = Path.of(uriPath);
        String filename = path.getFileName().toString();

        return switch (filePathType) {
            case TEMPORARY -> getTempFilePath().resolve(filename);
            case DRAG_AND_DROP_BACKGROUND -> getDragAndDropBackgroundFilePath().resolve(filename);
            case DRAG_ITEM -> getDragItemFilePath().resolve(filename);
            case COURSE_ICON -> getCourseIconFilePath().resolve(filename);
            case PROFILE_PICTURE -> getProfilePictureFilePath().resolve(filename);
            case EXAM_USER_SIGNATURE -> getExamUserSignatureFilePath().resolve(filename);
            case EXAM_USER_IMAGE -> getStudentImageFileSystemPath(path, filename);
            case LECTURE_ATTACHMENT -> getLectureAttachmentFileSystemPath(path, filename);
            case SLIDE -> getSlideFileSystemPath(path, filename);
            case STUDENT_VERSION_SLIDES -> getStudentVersionSlidesFileSystemPath(path, filename);
            case ATTACHMENT_UNIT -> getAttachmentVideoUnitFileSystemPath(path, filename);
            case FILE_UPLOAD_SUBMISSION -> fileSystemPathForFileUploadSubmissionExternalUri(externalUri, filename);
        };
    }

    /**
     * Generates the path for a lecture attachment file based on the provided path and filename.
     *
     * @param path     the path to the lecture attachment
     * @param filename the name of the file
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @return the path to the lecture attachment file
     */

    @NotNull
    private static Path getLectureAttachmentFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String lectureId = path.getName(2).toString();
            Long.parseLong(lectureId);
            return getLectureAttachmentFileSystemPath().resolve(Path.of(lectureId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct lectureId: " + path, e);
        }
    }

    /**
     * Generates the path for an attachment video unit file based on the provided path and filename.
     *
     * @param path     the path to the attachment video unit
     * @param filename the name of the file
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @return the path to the attachment video unit file
     */
    @NotNull
    private static Path getAttachmentVideoUnitFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String attachmentVideoUnitId = path.getName(2).toString();
            Long.parseLong(attachmentVideoUnitId);
            return getAttachmentVideoUnitFileSystemPath().resolve(Path.of(attachmentVideoUnitId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentVideoUnitId: " + path, e);
        }
    }

    /**
     * Generates the path for an attachment video unit file based on the provided path and filename.
     *
     * @param path     the path to the attachment video unit as external URI
     * @param filename the name of the file
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @return the path to the attachment video unit file
     */
    @NotNull
    private static Path getStudentVersionSlidesFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String attachmentVideoUnitId = path.getName(2).toString();
            Long.parseLong(attachmentVideoUnitId);
            return getAttachmentVideoUnitFileSystemPath().resolve(Path.of(attachmentVideoUnitId, "student", filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentVideoUnitId: " + path, e);
        }
    }

    /**
     * Generates the path for a slide file based on the provided path and filename.
     *
     * @param path     the path to the slide as external URI
     * @param filename the name of the file
     * @return the path to the slide file
     */
    @NotNull
    private static Path getSlideFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String attachmentVideoUnitId = path.getName(2).toString();
            String slideId = path.getName(4).toString();
            Long.parseLong(attachmentVideoUnitId);
            Long.parseLong(slideId);
            return getAttachmentVideoUnitFileSystemPath().resolve(Path.of(attachmentVideoUnitId, "slide", slideId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentVideoUnitId or slideId: " + path, e);
        }
    }

    @NotNull
    private static Path getStudentImageFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String studentId = path.getName(1).toString();
            Long.parseLong(studentId);
            var suffix = Path.of(studentId, filename);
            return getStudentImageFilePath().resolve(suffix);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct studentId: " + path, e);
        }
    }

    /**
     * Generates the file system path for a file upload exercise submission based on the provided external URI and filename.
     *
     * @param externalUri the external URI of the file upload exercise
     * @param filename    the name of the file
     * @return the file system path to the file upload exercise submission
     */
    @NotNull
    private static Path fileSystemPathForFileUploadSubmissionExternalUri(@NotNull URI externalUri, @NotNull String filename) {
        Path path = Path.of(externalUri.getPath());
        try {
            String expectedExerciseId = path.getName(1).toString();
            String expectedSubmissionId = path.getName(3).toString();
            long exerciseId = Long.parseLong(expectedExerciseId);
            long submissionId = Long.parseLong(expectedSubmissionId);
            return buildFileUploadSubmissionPath(exerciseId, submissionId).resolve(filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct exerciseId or submissionId: " + externalUri, e);
        }
    }

    /**
     * Generates the external URI for a file at the given local file system path.
     *
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("attachments").resolve("lecture").resolve("4").resolve("slides.pdf");
     *     URI externalUri = FilePathService.externalUriForFileSystemPath(fileSystemPath, FilePathType.LECTURE_ATTACHMENT, 4L);
     *     externalUri: attachments/lecture/4/slides.pdf
     * </pre>
     * </p>
     *
     * @param path         the path to the file in the local filesystem
     * @param filePathType the type of file path
     * @param entityId     the ID of the entity associated with the file (may be null)
     * @return the external file URI that can be used to access the file externally
     * @throws FilePathParsingException if the path cannot be parsed correctly
     */
    @NotNull
    public static URI externalUriForFileSystemPath(@NotNull Path path, @NotNull FilePathType filePathType, @Nullable Long entityId) {
        String filename = path.getFileName().toString();
        String id = entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();

        return switch (filePathType) {
            case TEMPORARY -> URI.create(FileUtil.DEFAULT_FILE_SUBPATH + filename);
            case DRAG_AND_DROP_BACKGROUND -> URI.create("drag-and-drop/backgrounds/" + id + "/" + filename);
            case DRAG_ITEM -> URI.create("drag-and-drop/drag-items/" + id + "/" + filename);
            case COURSE_ICON -> URI.create("course/icons/" + id + "/" + filename);
            case PROFILE_PICTURE -> URI.create("user/profile-pictures/" + id + "/" + filename);
            case EXAM_USER_SIGNATURE -> URI.create("exam-user/signatures/" + id + "/" + filename);
            case EXAM_USER_IMAGE -> URI.create("exam-user/" + id + "/" + filename);
            case LECTURE_ATTACHMENT -> URI.create("attachments/lecture/" + id + "/" + filename);
            case SLIDE -> externalUriForSlideFileSystemPath(path, filename, id);
            case FILE_UPLOAD_SUBMISSION -> externalUriForFileUploadExercisesFileSystemPath(path, filename, id);
            case STUDENT_VERSION_SLIDES -> URI.create("attachments/attachment-unit/" + id + "/student/" + filename);
            case ATTACHMENT_UNIT -> URI.create("attachments/attachment-unit/" + id + "/" + filename);
        };
    }

    /**
     * Generates the external URI for a slide file based on the provided path, filename, and ID.
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("attachments").resolve("attachment-unit").resolve("1").resolve("slide").resolve("3").resolve("slide_17.png");
     *     URI externalUri = FilePathService.externalUriForFileSystemPath(fileSystemPath, FilePathType.SLIDE, "3");
     *     externalUri: attachments/attachment-unit/1/slide/3/slide_17.png
     * </pre>
     *
     * @param path     the path to the slide in the local filesystem
     * @param filename the name of the file
     * @param id       the ID of the slide
     * @return the external URI for the slide file
     */
    @NotNull
    private static URI externalUriForSlideFileSystemPath(@NotNull Path path, @NotNull String filename, @NotNull String id) {
        try {
            final String expectedAttachmentVideoUnitId = path.getName(path.getNameCount() - 4).toString();
            final long attachmentVideoUnitId = Long.parseLong(expectedAttachmentVideoUnitId);
            return URI.create("attachments/attachment-unit/" + attachmentVideoUnitId + "/slide/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. AttachmentVideoUnit ID should be present here: " + path, e);
        }
    }

    /**
     * Generates the external URI for a file upload exercise submission based on the provided path, filename, and ID.
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("file-upload-exercises").resolve("1").resolve("submissions").resolve("2").resolve("submission.pdf");
     *     URI externalUri = FilePathService.externalUriForFileSystemPath(fileSystemPath, FilePathType.FILE_UPLOAD_SUBMISSION, "2);
     *     externalUri: file-upload-exercises/1/submissions/2/submission.pdf
     * </pre>
     *
     * @param path     the path to the file upload exercise
     * @param filename the name of the file
     * @param id       the ID of the file upload submission
     * @return the external URI for the file upload exercise submission
     */

    @NotNull
    private static URI externalUriForFileUploadExercisesFileSystemPath(@NotNull Path path, @NotNull String filename, @NotNull String id) {
        try {
            final var expectedExerciseId = path.getName(path.getNameCount() - 3).toString();
            final long exerciseId = Long.parseLong(expectedExerciseId);
            return URI.create("file-upload-exercises/" + exerciseId + "/submissions/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. Exercise ID should be present here: " + path, e);
        }
    }

    /**
     * Builds file path for file upload submission.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission
     * @return path where submission for file upload exercise is stored
     */
    @NotNull
    public static Path buildFileUploadSubmissionPath(long exerciseId, long submissionId) {
        return getFileUploadExercisesFilePath().resolve(String.valueOf(exerciseId)).resolve(String.valueOf(submissionId));
    }
}
