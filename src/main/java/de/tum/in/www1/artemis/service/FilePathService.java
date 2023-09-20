package de.tum.in.www1.artemis.service;

import java.net.URI;
import java.nio.file.Path;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.exception.FilePathParsingException;

@Service
public class FilePathService {

    // Note: We use this static field as a kind of constant. In Spring, we cannot inject a value into a constant field, so we have to use this work-around.
    // This is also documented here: https://www.baeldung.com/spring-inject-static-field
    // We can not use a normal service here, as some classes (in the domain package) require this service (or depend on another service that depend on this service), were we cannot
    // use auto-injection
    // TODO: Rework this behaviour be removing the dependencies to services (like FileService) from the domain package
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

    /**
     * Convert the given public file url to its corresponding local path
     *
     * @param publicPath the public file url to convert
     * @throws FilePathParsingException if the path is unknown
     * @return the actual path to that file in the local filesystem
     */
    public Path actualPathForPublicPathOrThrow(URI publicPath) {
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
    public Path actualPathForPublicPath(URI publicPath) {
        // first extract the filename from the url
        String uriPath = publicPath.getPath();
        Path path = Path.of(uriPath);
        String filename = path.getFileName().toString();

        // check for known path to convert
        if (uriPath.startsWith("/api/files/temp")) {
            return FilePathService.getTempFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/drag-and-drop/backgrounds")) {
            return FilePathService.getDragAndDropBackgroundFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/drag-and-drop/drag-items")) {
            return FilePathService.getDragItemFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/course/icons")) {
            return FilePathService.getCourseIconFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/exam-user/signatures")) {
            return FilePathService.getExamUserSignatureFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/exam-user")) {
            return FilePathService.getStudentImageFilePath().resolve(filename);
        }
        if (uriPath.startsWith("/api/files/attachments/lecture")) {
            String lectureId = path.getName(4).toString();
            return FilePathService.getLectureAttachmentFilePath().resolve(Path.of(lectureId, filename));
        }
        if (uriPath.startsWith("/api/files/attachments/attachment-unit")) {
            return actualPathForPublicAttachmentUnitFilePath(publicPath, filename);
        }
        if (uriPath.startsWith("/api/files/file-upload-exercises")) {
            return actualPathForPublicFileUploadExercisesFilePath(publicPath, filename);
        }

        return null;
    }

    private Path actualPathForPublicAttachmentUnitFilePath(URI publicPath, String filename) {
        Path path = Path.of(publicPath.getPath());
        if (!publicPath.toString().contains("/slide")) {
            String attachmentUnitId = path.getName(4).toString();
            return FilePathService.getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, filename));
        }
        try {
            String attachmentUnitId = path.getName(4).toString();
            String slideId = path.getName(6).toString();
            // check if the ids are valid long values
            Long.parseLong(attachmentUnitId);
            Long.parseLong(slideId);
            return FilePathService.getAttachmentUnitFilePath().resolve(Path.of(attachmentUnitId, "slide", slideId, filename));
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Public path does not contain correct attachmentUnitId or slideId: " + publicPath, e);
        }
    }

    private Path actualPathForPublicFileUploadExercisesFilePath(URI publicPath, String filename) {
        Path path = Path.of(publicPath.getPath());
        try {
            String expectedExerciseId = path.getName(3).toString();
            String expectedSubmissionId = path.getName(5).toString();
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
    public URI publicPathForActualPathOrThrow(Path actualPathString, @Nullable Long entityId) {
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
    public URI publicPathForActualPath(Path path, @Nullable Long entityId) {
        // first extract filename
        String filename = path.getFileName().toString();

        // generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();
        // check for known path to convert
        if (path.startsWith(FilePathService.getTempFilePath())) {
            return URI.create(FileService.DEFAULT_FILE_SUBPATH + filename);
        }
        if (path.startsWith(FilePathService.getDragAndDropBackgroundFilePath())) {
            return URI.create("/api/files/drag-and-drop/backgrounds/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getDragItemFilePath())) {
            return URI.create("/api/files/drag-and-drop/drag-items/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getCourseIconFilePath())) {
            return URI.create("/api/files/course/icons/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getExamUserSignatureFilePath())) {
            return URI.create("/api/files/exam-user/signatures/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getStudentImageFilePath())) {
            return URI.create("/api/files/exam-user/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getLectureAttachmentFilePath())) {
            return URI.create("/api/files/attachments/lecture/" + id + "/" + filename);
        }
        if (path.startsWith(FilePathService.getAttachmentUnitFilePath())) {
            return publicPathForActualAttachmentUnitFilePath(path, filename, id);
        }
        if (path.startsWith(FilePathService.getFileUploadExercisesFilePath())) {
            return publicPathForActualFileUploadExercisesFilePath(path, filename, id);
        }

        return null;
    }

    private URI publicPathForActualAttachmentUnitFilePath(Path path, String filename, String id) {
        if (!path.toString().contains("/slide")) {
            return URI.create("/api/files/attachments/attachment-unit/" + id + "/" + filename);
        }
        try {
            // The last name is the file name, the one before that is the slide number and the one before that is the attachmentUnitId, in which we are interested
            // (e.g. uploads/attachments/attachment-unit/941/slide/1/State_pattern_941_Slide_1.png)
            final String expectedAttachmentUnitId = path.getName(path.getNameCount() - 4).toString();
            final long attachmentUnitId = Long.parseLong(expectedAttachmentUnitId);
            return URI.create("/api/files/attachments/attachment-unit/" + attachmentUnitId + "/slide/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. AttachmentUnit ID should be present here: " + path, e);
        }
    }

    private URI publicPathForActualFileUploadExercisesFilePath(Path path, String filename, String id) {
        try {
            // The last name is the file name, the one before that is the submissionId and the one before that is the exerciseId, in which we are interested
            final var expectedExerciseId = path.getName(path.getNameCount() - 3).toString();
            final long exerciseId = Long.parseLong(expectedExerciseId);
            return URI.create("/api/files/file-upload-exercises/" + exerciseId + "/submissions/" + id + "/" + filename);
        }
        catch (IllegalArgumentException e) {
            throw new FilePathParsingException("Unexpected String in upload file path. Exercise ID should be present here: " + path, e);
        }
    }
}
