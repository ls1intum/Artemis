package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

/**
 * Service for generating and parsing file system paths and public URIs for different file types in Artemis.
 * <p>
 * This service provides static methods to convert between internal file system paths and public URIs,
 * as well as to generate base paths for various file storage locations (e.g., attachments, profile pictures, uploads).
 * The mapping is based on the {@link FilePathType} and the entity IDs associated with the files.
 * </p>
 */
@Profile(PROFILE_CORE)
@Service
public class FilePathService {

    // Note: We use this static field as a kind of constant. In Spring, we cannot inject a value into a constant field, so we have to use this work-around.
    // This is also documented here: https://www.baeldung.com/spring-inject-static-field
    // We can not use a normal service here, as some classes (in the domain package) require this service (or depend on another service that depend on this service), were we cannot
    // use auto-injection
    // TODO: Rework this behavior be removing the dependencies to services (like FileService) from the domain package
    private static String fileUploadPath;

    /**
     * Sets the base file upload path from the application properties.
     * This is used as the root for all file storage locations.
     *
     * @param fileUploadPath the base path for file uploads
     */
    @Value("${artemis.file-upload-path}")
    public void setFileUploadPathStatic(@NotNull String fileUploadPath) {
        FilePathService.fileUploadPath = fileUploadPath;
    }

    /**
     * @return the path to the temporary files directory
     */
    @NotNull
    public static Path getTempFilePath() {
        return Path.of(fileUploadPath, "images", "temp");
    }

    /**
     * @return the path to the drag and drop backgrounds directory
     */
    @NotNull
    public static Path getDragAndDropBackgroundFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "backgrounds");
    }

    /**
     * @return the path to the drag item images directory
     */
    @NotNull
    public static Path getDragItemFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "drag-items");
    }

    /**
     * @return the path to the course icons directory
     */
    @NotNull
    public static Path getCourseIconFilePath() {
        return Path.of(fileUploadPath, "images", "course", "icons");
    }

    /**
     * @return the path to the profile pictures directory
     */
    @NotNull
    public static Path getProfilePictureFilePath() {
        return Path.of(fileUploadPath, "images", "user", "profile-pictures");
    }

    /**
     * @return the path to the exam user signatures directory
     */
    @NotNull
    public static Path getExamUserSignatureFilePath() {
        return Path.of(fileUploadPath, "images", "exam-user", "signatures");
    }

    /**
     * @return the path to the student images directory
     */
    @NotNull
    public static Path getStudentImageFilePath() {
        return Path.of(fileUploadPath, "images", "exam-user");
    }

    /**
     * @return the path to the lecture attachments directory
     */
    @NotNull
    public static Path getLectureAttachmentFileSystemPath() {
        return Path.of(fileUploadPath, "attachments", "lecture");
    }

    /**
     * @return the path to the attachment unit files directory
     */
    @NotNull
    public static Path getAttachmentUnitFileSystemPath() {
        return Path.of(fileUploadPath, "attachments", "attachment-unit");
    }

    /**
     * @return the path to the file upload exercises directory
     */
    @NotNull
    public static Path getFileUploadExercisesFilePath() {
        return Path.of(fileUploadPath, "file-upload-exercises");
    }

    /**
     * @return the path to the markdown files directory
     */
    @NotNull
    public static Path getMarkdownFilePath() {
        return Path.of(fileUploadPath, "markdown");
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
            case MARKDOWN -> getMarkdownFilePath().resolve(filename);
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
            case ATTACHMENT_UNIT -> getAttachmentUnitFileSystemPath(path, filename);
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
     * Generates the path for an attachment unit file based on the provided path and filename.
     *
     * @param path     the path to the attachment unit
     * @param filename the name of the file
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @return the path to the attachment unit file
     */
    @NotNull
    private static Path getAttachmentUnitFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String attachmentUnitId = path.getName(2).toString();
            Long.parseLong(attachmentUnitId);
            return getAttachmentUnitFileSystemPath().resolve(Path.of(attachmentUnitId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentUnitId: " + path, e);
        }
    }

    /**
     * Generates the path for an attachment unit file based on the provided path and filename.
     *
     * @param path     the path to the attachment unit as external URI
     * @param filename the name of the file
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @return the path to the attachment unit file
     */
    @NotNull
    private static Path getStudentVersionSlidesFileSystemPath(@NotNull Path path, @NotNull String filename) {
        try {
            String attachmentUnitId = path.getName(2).toString();
            Long.parseLong(attachmentUnitId);
            return getAttachmentUnitFileSystemPath().resolve(Path.of(attachmentUnitId, "student", filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentUnitId: " + path, e);
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
            String attachmentUnitId = path.getName(2).toString();
            String slideId = path.getName(4).toString();
            Long.parseLong(attachmentUnitId);
            Long.parseLong(slideId);
            return getAttachmentUnitFileSystemPath().resolve(Path.of(attachmentUnitId, "slide", slideId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("External URI does not contain correct attachmentUnitId or slideId: " + path, e);
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
            Long exerciseId = Long.parseLong(expectedExerciseId);
            Long submissionId = Long.parseLong(expectedSubmissionId);
            return FileUploadSubmission.buildFilePath(exerciseId, submissionId).resolve(filename);
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
     *     Path fileSystemPath = Path.of("uploads", "attachments", "lecture", "4", "slides.pdf");
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
            case TEMPORARY -> URI.create(FileService.DEFAULT_FILE_SUBPATH + filename);
            case MARKDOWN -> URI.create("markdown/" + filename);
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
     *     Path fileSystemPath = Path.of("uploads", "attachments", "attachment-unit", "1", "slide", "3", "slide_17.png");
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
            final String expectedAttachmentUnitId = path.getName(path.getNameCount() - 4).toString();
            final long attachmentUnitId = Long.parseLong(expectedAttachmentUnitId);
            return URI.create("attachments/attachment-unit/" + attachmentUnitId + "/slide/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. AttachmentUnit ID should be present here: " + path, e);
        }
    }

    /**
     * Generates the external URI for a file upload exercise submission based on the provided path, filename, and ID.
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads", "file-upload-exercises", "1", "submissions", "2", "submission.pdf");
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
}
