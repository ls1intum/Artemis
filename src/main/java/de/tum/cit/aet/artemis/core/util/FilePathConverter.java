package de.tum.cit.aet.artemis.core.util;

import java.net.URI;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;

/**
 * The fixed directory each file type is stored under, and the external URI that is written into the database for it.
 * <p>
 * This converter provides static methods to generate base paths for the various file storage locations (e.g. attachments, profile pictures, uploads), plus the external URI a
 * write path stores. The reverse direction does not live here: to locate a stored file, build the {@link FileSystemLocation} record of its type from the owning entity. Nothing
 * reads an entity id out of a path or URI segment.
 * </p>
 *
 * @see FileSystemLocation for the file system location of a stored file
 * @see PublicFileUrl for the REST URL a stored file is served under
 */
public final class FilePathConverter {

    /**
     * External sub-path under which a drag item picture is served. It is question-scoped because a drag item id is only unique within its question, so the owning question id has
     * to be part of the path for the request to be authorizable at all.
     */
    public static final String DRAG_AND_DROP_QUESTION_SUBPATH = "drag-and-drop/questions/";

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
     * Generates the external URI for a file at the given local file system path.
     *
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("attachments").resolve("lecture").resolve("4").resolve("slides.pdf");
     *     URI externalUri = FilePathConverter.externalUriForFileSystemPath(fileSystemPath, FilePathType.LECTURE_ATTACHMENT, 4L);
     *     externalUri: attachments/lecture/4/slides.pdf
     * </pre>
     * </p>
     *
     * @param path         the path to the file in the local filesystem
     * @param filePathType the type of file path
     * @param entityId     the ID of the entity associated with the file (may be null). {@link FilePathType#SLIDE} is the exception: it is called with the slide <b>number</b>,
     *                         not with the slide id, and the value it produces is a storage key rather than a URL, see
     *                         {@link #externalUriForSlideFileSystemPath}
     * @return the external file URI that can be used to access the file externally
     * @throws FilePathParsingException if the path cannot be parsed correctly
     * @throws IllegalArgumentException if called with {@link FilePathType#DRAG_ITEM}, which needs two ids and therefore has its own method
     */
    @NonNull
    public static URI externalUriForFileSystemPath(@NonNull Path path, @NonNull FilePathType filePathType, @Nullable Long entityId) {
        String filename = path.getFileName().toString();
        String id = idOrPlaceholder(entityId);

        return switch (filePathType) {
            case TEMPORARY -> URI.create(FileUtil.DEFAULT_FILE_SUBPATH + filename);
            case DRAG_AND_DROP_BACKGROUND -> URI.create("drag-and-drop/backgrounds/" + id + "/" + filename);
            // A drag item id is only unique within its question, so the owning question id has to be part of the URI as well.
            case DRAG_ITEM -> throw new IllegalArgumentException("A drag item URI is question-scoped, use externalUriForDragItemFileSystemPath instead");
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
     * Generates the external URI for a drag item picture.
     * <p>
     * A drag item is not an entity of its own: it lives inside its question's JSON content and its id is only unique within that question. The served URI therefore carries both
     * ids, which is also what lets {@code FileResource} authorize the request through the owning question.
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("images").resolve("drag-and-drop").resolve("drag-items").resolve("item.png");
     *     URI externalUri = FilePathConverter.externalUriForDragItemFileSystemPath(fileSystemPath, 7L, 2L);
     *     externalUri: drag-and-drop/questions/7/drag-items/2/item.png
     * </pre>
     *
     * @param path       the path to the drag item picture in the local filesystem
     * @param questionId the id of the owning drag-and-drop question, or null while the question has not been persisted yet (then a placeholder is written, which
     *                       {@code DragAndDropQuestion.afterCreate()} replaces once the id exists)
     * @param dragItemId the question-scoped id of the drag item
     * @return the external file URI that can be used to access the picture externally
     */
    @NonNull
    public static URI externalUriForDragItemFileSystemPath(@NonNull Path path, @Nullable Long questionId, @Nullable Long dragItemId) {
        String filename = path.getFileName().toString();
        return URI.create(DRAG_AND_DROP_QUESTION_SUBPATH + idOrPlaceholder(questionId) + "/drag-items/" + idOrPlaceholder(dragItemId) + "/" + filename);
    }

    /**
     * @param entityId the id of the entity, may be null when it has not been assigned yet
     * @return the id as a string, or the placeholder that is replaced once the entity has been persisted
     */
    @NonNull
    private static String idOrPlaceholder(@Nullable Long entityId) {
        return entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();
    }

    /**
     * Generates the value that is stored in {@code slide.slide_image_path}.
     * <p>
     * <b>This is a storage key, not a URL.</b> It looks like one, but nothing serves it: the value has seven segments while the endpoint that comes closest,
     * {@code files/attachments/attachment-video-units/{attachmentVideoUnitId}/slide/{slideNumber}}, has six and stops at the slide number, and a path variable matches a single
     * segment. The client never builds a request from it either; it fetches a slide image by id through {@code files/slides/{slideId}}. What the value records is where the
     * image was written, which is why the segment after {@code slide/} is the slide's <b>number</b> and not its id: {@code SlideSplitterService} writes the image to
     * {@code attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber}/} and calls this with that same number.
     * <p>
     * Example:
     *
     * <pre>
     *     Path fileSystemPath = Path.of("uploads").resolve("attachments").resolve("attachment-unit").resolve("1").resolve("slide").resolve("3").resolve("slide_17.png");
     *     URI storageKey = FilePathConverter.externalUriForFileSystemPath(fileSystemPath, FilePathType.SLIDE, 3L);
     *     storageKey: attachments/attachment-unit/1/slide/3/slide_17.png
     * </pre>
     *
     * @param path     the path to the slide in the local filesystem
     * @param filename the name of the file
     * @param id       the one-based number of the slide within its document, despite the name this parameter shares with the other cases
     * @return the value stored for the slide image
     */
    @NonNull
    private static URI externalUriForSlideFileSystemPath(@NonNull Path path, @NonNull String filename, @NonNull String id) {
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

    @NonNull
    private static URI externalUriForFileUploadExercisesFileSystemPath(@NonNull Path path, @NonNull String filename, @NonNull String id) {
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
    @NonNull
    public static Path buildFileUploadSubmissionPath(long exerciseId, long submissionId) {
        return getFileUploadExercisesFilePath().resolve(String.valueOf(exerciseId)).resolve(String.valueOf(submissionId));
    }
}
