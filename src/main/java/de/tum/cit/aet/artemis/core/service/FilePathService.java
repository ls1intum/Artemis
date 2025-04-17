package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;

import jakarta.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

    public static Path getAttachmentVideoUnitFilePath() {
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
     * @throws FilePathParsingException if the path is unknown
     * @return the actual path to that file in the local filesystem
     */
    public static Path actualPathForPublicPathOrThrow(URI publicPath) {
        Path actualPath = actualPathForPublicPath(publicPath);
        if (actualPath == null) {
            // path is unknown => cannot convert
            throw new FilePathParsingException("Unknown Filepath: " + publicPath);
        }

        return actualPath;
    }

    /**
     * Convert the given public file url to its corresponding local path
     *
     * @param publicPath the public file url to convert
     * @return the actual path to that file in the local filesystem
     */
    public static Path actualPathForPublicPath(URI publicPath) {
        // first extract the filename from the url
        String uriPath = publicPath.getPath();
        Path path = Path.of(uriPath);
        String filename = path.getFileName().toString();

        // check for known path to convert
        if (uriPath.startsWith("temp")) {
            return getTempFilePath().resolve(filename);
        }
        if (uriPath.startsWith("drag-and-drop/backgrounds")) {
            return getDragAndDropBackgroundFilePath().resolve(filename);
        }
        if (uriPath.startsWith("drag-and-drop/drag-items")) {
            return getDragItemFilePath().resolve(filename);
        }
        if (uriPath.startsWith("course/icons")) {
            return getCourseIconFilePath().resolve(filename);
        }
        if (uriPath.startsWith("user/profile-pictures")) {
            return getProfilePictureFilePath().resolve(filename);
        }
        if (uriPath.startsWith("exam-user/signatures")) {
            return getExamUserSignatureFilePath().resolve(filename);
        }
        if (uriPath.startsWith("exam-user")) {
            return getStudentImageFilePath().resolve(filename);
        }
        if (uriPath.startsWith("attachments/lecture")) {
            String lectureId = path.getName(2).toString();
            return getLectureAttachmentFilePath().resolve(Path.of(lectureId, filename));
        }
        if (uriPath.startsWith("attachments/attachment-unit")) {
            return actualPathForPublicAttachmentVideoUnitFilePath(publicPath, filename);
        }
        if (uriPath.startsWith("file-upload-exercises")) {
            return actualPathForPublicFileUploadExercisesFilePath(publicPath, filename);
        }

        return null;
    }

    private static Path actualPathForPublicAttachmentVideoUnitFilePath(URI publicPath, String filename) {
        Path path = Path.of(publicPath.getPath());
        if (publicPath.toString().contains("student")) {
            String attachmentUnitId = path.getName(2).toString();
            return getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, "student", filename));
        }
        if (!publicPath.toString().contains("slide")) {
            String attachmentVideoUnitId = path.getName(2).toString();
            return getAttachmentVideoUnitFilePath().resolve(Path.of(attachmentVideoUnitId, filename));
        }
        try {
            String attachmentVideoUnitId = path.getName(2).toString();
            String slideId = path.getName(4).toString();
            // check if the ids are valid long values
            Long.parseLong(attachmentVideoUnitId);
            Long.parseLong(slideId);
            return getAttachmentVideoUnitFilePath().resolve(Path.of(attachmentVideoUnitId, "slide", slideId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Public path does not contain correct attachmentVideoUnitId or slideId: " + publicPath, e);
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
    public static URI publicPathForActualPathOrThrow(Path actualPathString, @Nullable Long entityId) {
        URI publicPath = publicPathForActualPath(actualPathString, entityId);
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
    public static URI publicPathForActualPath(Path path, @Nullable Long entityId) {
        // first extract filename
        String filename = path.getFileName().toString();

        // generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();
        // check for known path to convert
        if (path.startsWith(getTempFilePath())) {
            return URI.create(FileService.DEFAULT_FILE_SUBPATH + filename);
        }
        if (path.startsWith(getDragAndDropBackgroundFilePath())) {
            return URI.create("drag-and-drop/backgrounds/" + id + "/" + filename);
        }
        if (path.startsWith(getDragItemFilePath())) {
            return URI.create("drag-and-drop/drag-items/" + id + "/" + filename);
        }
        if (path.startsWith(getCourseIconFilePath())) {
            return URI.create("course/icons/" + id + "/" + filename);
        }
        if (path.startsWith(getProfilePictureFilePath())) {
            return URI.create("user/profile-pictures/" + id + "/" + filename);
        }
        if (path.startsWith(getExamUserSignatureFilePath())) {
            return URI.create("exam-user/signatures/" + id + "/" + filename);
        }
        if (path.startsWith(getStudentImageFilePath())) {
            return URI.create("exam-user/" + id + "/" + filename);
        }
        if (path.startsWith(getLectureAttachmentFilePath())) {
            return URI.create("attachments/lecture/" + id + "/" + filename);
        }
        if (path.startsWith(getAttachmentVideoUnitFilePath())) {
            return publicPathForActualAttachmentVideoUnitFilePath(path, filename, id);
        }
        if (path.startsWith(getFileUploadExercisesFilePath())) {
            return publicPathForActualFileUploadExercisesFilePath(path, filename, id);
        }

        return null;
    }

    private static URI publicPathForActualAttachmentVideoUnitFilePath(Path path, String filename, String id) {
        if (path.toString().contains("student")) {
            return URI.create("attachments/attachment-unit/" + id + "/student/" + filename);
        }
        if (!path.toString().contains("slide")) {
            return URI.create("attachments/attachment-unit/" + id + "/" + filename);
        }
        try {
            // The last name is the file name, the one before that is the slide number and the one before that is the attachmentVideoUnitId, in which we are interested
            // (e.g. uploads/attachments/attachment-unit/941/slide/1/State_pattern_941_Slide_1.png)
            final String expectedAttachmentVideoUnitId = path.getName(path.getNameCount() - 4).toString();
            final long attachmentVideoUnitId = Long.parseLong(expectedAttachmentVideoUnitId);
            return URI.create("attachments/attachment-unit/" + attachmentVideoUnitId + "/slide/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. AttachmentVideoUnit ID should be present here: " + path, e);
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
