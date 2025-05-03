package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;

import jakarta.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

@Profile(PROFILE_CORE)
@Service
public class FilePathService {

    // Note: We use this static field as a kind of constant. In Spring, we cannot inject a value into a constant field, so we have to use this work-around.
    // This is also documented here: https://www.baeldung.com/spring-inject-static-field
    // We can not use a normal service here, as some classes (in the domain package) require this service (or depend on another service that depend on this service), were we cannot
    // use auto-injection
    // TODO: Rework this behavior be removing the dependencies to services (like FileService) from the domain package
    private static String fileUploadPath;

    @Value("${artemis.file-upload-path}")
    public void setFileUploadPathStatic(String fileUploadPath) {
        FilePathService.fileUploadPath = fileUploadPath;
    }

    public static Path getTempFilePath() {
        return Path.of(fileUploadPath, "images", "temp");
    }

    public static Path getDragAndDropBackgroundFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "backgrounds");
    }

    public static Path getDragItemFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "drag-items");
    }

    public static Path getCourseIconFilePath() {
        return Path.of(fileUploadPath, "images", "course", "icons");
    }

    public static Path getProfilePictureFilePath() {
        return Path.of(fileUploadPath, "images", "user", "profile-pictures");
    }

    public static Path getExamUserSignatureFilePath() {
        return Path.of(fileUploadPath, "images", "exam-user", "signatures");
    }

    public static Path getStudentImageFilePath() {
        return Path.of(fileUploadPath, "images", "exam-user");
    }

    public static Path getLectureAttachmentFilePath() {
        return Path.of(fileUploadPath, "attachments", "lecture");
    }

    public static Path getAttachmentUnitFilePath() {
        return Path.of(fileUploadPath, "attachments", "attachment-unit");
    }

    public static Path getFileUploadExercisesFilePath() {
        return Path.of(fileUploadPath, "file-upload-exercises");
    }

    public static Path getMarkdownFilePath() {
        return Path.of(fileUploadPath, "markdown");
    }

    public static Path getMarkdownFilePathForConversation(long courseId, long conversationId) {
        return getMarkdownFilePath().resolve("communication").resolve(String.valueOf(courseId)).resolve(String.valueOf(conversationId));
    }

    /**
     * Convert the given public file url to its corresponding local path
     *
     * @param publicPath the public file url to convert
     * @return the actual path to that file in the local filesystem
     */
    public static Path actualPathForPublicPath(URI publicPath, FilePathType filePathType) {
        // Extract the filename from the url
        String uriPath = publicPath.getPath();
        Path path = Path.of(uriPath);
        String filename = path.getFileName().toString();

        return switch (filePathType) {
            case TEMPORARY -> getTempFilePath().resolve(filename);
            case DRAG_AND_DROP_BACKGROUND -> getDragAndDropBackgroundFilePath().resolve(filename);
            case DRAG_ITEM -> getDragItemFilePath().resolve(filename);
            case COURSE_ICON -> getCourseIconFilePath().resolve(filename);
            case PROFILE_PICTURE -> getProfilePictureFilePath().resolve(filename);
            case EXAM_USER_SIGNATURE -> getExamUserSignatureFilePath().resolve(filename);
            case STUDENT_IMAGE -> getStudentImageFilePath().resolve(filename);
            case LECTURE_ATTACHMENT -> getLectureAttachmentFilePath(path, filename);
            case SLIDE -> getSlideFilePath(publicPath, path, filename);
            case STUDENT_VERSION_SLIDES -> getStudentVersionSlidesPath(path, filename);
            case ATTACHMENT_UNIT -> getAttachmentUnitFilePath(path, filename);
            case FILE_UPLOAD_SUBMISSION -> actualPathForPublicFileUploadExercisesFilePath(publicPath, filename);
        };
    }

    private static Path getLectureAttachmentFilePath(Path path, String filename) {
        String lectureId = path.getName(2).toString();
        return getLectureAttachmentFilePath().resolve(Path.of(lectureId, filename));
    }

    private static Path getAttachmentUnitFilePath(Path path, String filename) {
        String attachmentUnitId = path.getName(2).toString();
        return getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, filename));
    }

    private static Path getStudentVersionSlidesPath(Path path, String filename) {
        String attachmentUnitId = path.getName(2).toString();
        return getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, "student", filename));
    }

    private static Path getSlideFilePath(URI publicPath, Path path, String filename) {
        try {
            String attachmentUnitId = path.getName(2).toString();
            String slideId = path.getName(4).toString();
            // check if the ids are valid long values
            Long.parseLong(attachmentUnitId);
            Long.parseLong(slideId);
            return getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, "slide", slideId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Public path does not contain correct attachmentUnitId or slideId: " + publicPath, e);
        }
    }

    private static Path actualPathForPublicFileUploadExercisesFilePath(URI publicPath, String filename) {
        Path path = Path.of(publicPath.getPath());
        try {
            String expectedExerciseId = path.getName(1).toString();
            String expectedSubmissionId = path.getName(3).toString();
            Long exerciseId = Long.parseLong(expectedExerciseId);
            Long submissionId = Long.parseLong(expectedSubmissionId);
            return FileUploadSubmission.buildFilePath(exerciseId, submissionId).resolve(filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Public path does not contain correct exerciseId or submissionId: " + publicPath, e);
        }
    }

    /**
     * Generate the public path for the file at the given path
     *
     * @param actualPathString the path to the file in the local filesystem
     * @param entityId         the id of the entity associated with the file
     * @throws FilePathParsingException if the path is unknown
     * @return the public file url that can be used by users to access the file from outside
     */
    public static URI publicPathForActualPathOrThrow(Path actualPathString, FilePathType filePathType, @Nullable Long entityId) {
        URI publicPath = publicPathForActualPath(actualPathString, filePathType, entityId);
        if (publicPath == null) {
            // path is unknown => cannot convert
            throw new FilePathParsingException("Unknown Filepath: " + actualPathString);
        }

        return publicPath;
    }

    /**
     * Generate the public path for the file at the given path
     *
     * @param path     the path to the file in the local filesystem
     * @param entityId the id of the entity associated with the file
     * @return the public file url that can be used by users to access the file from outside
     */
    public static URI publicPathForActualPath(Path path, FilePathType filePathType, @Nullable Long entityId) {
        // Extract filename
        String filename = path.getFileName().toString();

        // Generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();

        return switch (filePathType) {
            case TEMPORARY -> URI.create(FileService.DEFAULT_FILE_SUBPATH + filename);
            case DRAG_AND_DROP_BACKGROUND -> URI.create("drag-and-drop/backgrounds/" + id + "/" + filename);
            case DRAG_ITEM -> URI.create("drag-and-drop/drag-items/" + id + "/" + filename);
            case COURSE_ICON -> URI.create("course/icons/" + id + "/" + filename);
            case PROFILE_PICTURE -> URI.create("user/profile-pictures/" + id + "/" + filename);
            case EXAM_USER_SIGNATURE -> URI.create("exam-user/signatures/" + id + "/" + filename);
            case STUDENT_IMAGE -> URI.create("exam-user/" + id + "/" + filename);
            case LECTURE_ATTACHMENT -> URI.create("attachments/lecture/" + id + "/" + filename);
            case SLIDE -> publicPathForActualSlideFilePath(path, filename, id);
            case FILE_UPLOAD_SUBMISSION -> publicPathForActualFileUploadExercisesFilePath(path, filename, id);
            case STUDENT_VERSION_SLIDES -> URI.create("attachments/attachment-unit/" + id + "/student/" + filename);
            case ATTACHMENT_UNIT -> URI.create("attachments/attachment-unit/" + id + "/" + filename);
        };
    }

    private static URI publicPathForActualSlideFilePath(Path path, String filename, String id) {
        try {
            // The last name is the file name, the one before that is the slide number and the one before that is the attachmentUnitId, in which we are interested
            // (e.g. uploads/attachments/attachment-unit/941/slide/1/State_pattern_941_Slide_1.png)
            final String expectedAttachmentUnitId = path.getName(path.getNameCount() - 4).toString();
            final long attachmentUnitId = Long.parseLong(expectedAttachmentUnitId);
            return URI.create("attachments/attachment-unit/" + attachmentUnitId + "/slide/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. AttachmentUnit ID should be present here: " + path, e);
        }
    }

    private static URI publicPathForActualFileUploadExercisesFilePath(Path path, String filename, String id) {
        try {
            // The last name is the file name, the one before that is the submissionId and the one before that is the exerciseId, in which we are interested
            final var expectedExerciseId = path.getName(path.getNameCount() - 3).toString();
            final long exerciseId = Long.parseLong(expectedExerciseId);
            return URI.create("file-upload-exercises/" + exerciseId + "/submissions/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. Exercise ID should be present here: " + path, e);
        }
    }
}
