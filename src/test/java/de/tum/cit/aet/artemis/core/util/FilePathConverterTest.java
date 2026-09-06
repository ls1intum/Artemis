package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;

class FilePathConverterTest {

    private static final Logger log = LoggerFactory.getLogger(FilePathConverterTest.class);

    private static Path rootPath;

    @BeforeAll
    static void setup() {
        // Read the file upload path from the test configuration file to avoid hardcoding
        rootPath = readFileUploadPathFromConfig();
        log.info("Using file upload root path for tests: {}", rootPath);
        FilePathConverter.setFileUploadPath(rootPath);
    }

    @SuppressWarnings("unchecked")
    private static Path readFileUploadPathFromConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = FilePathConverterTest.class.getClassLoader().getResourceAsStream("config/application-artemis.yml")) {
            Map<String, Object> config = yaml.load(inputStream);
            Map<String, Object> artemis = (Map<String, Object>) config.get("artemis");
            String fileUploadPath = (String) artemis.get("file-upload-path");
            return Path.of(fileUploadPath);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read file-upload-path from application-local.yml", e);
        }
    }

    @Test
    void testExternalUriForFileSystemPathForAllFilePathTypes() {
        // TEMPORARY
        Path path = FilePathConverter.getTempFilePath().resolve("file.tmp");
        URI uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.TEMPORARY, 1L);
        assertThat(uri).isEqualTo(URI.create("temp/file.tmp"));

        // DRAG_AND_DROP_BACKGROUND
        path = FilePathConverter.getDragAndDropBackgroundFilePath().resolve("bg.png");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.DRAG_AND_DROP_BACKGROUND, 42L);
        assertThat(uri).isEqualTo(URI.create("drag-and-drop/backgrounds/42/bg.png"));

        // DRAG_ITEM is question-scoped and therefore has its own method, see testExternalUriForDragItemFileSystemPath
        Path dragItemPath = FilePathConverter.getDragItemFilePath().resolve("item.png");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> FilePathConverter.externalUriForFileSystemPath(dragItemPath, FilePathType.DRAG_ITEM, 5L))
                .withMessageContaining("externalUriForDragItemFileSystemPath");

        // COURSE_ICON
        path = FilePathConverter.getCourseIconFilePath().resolve("icon.png");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.COURSE_ICON, 3L);
        assertThat(uri).isEqualTo(URI.create("course/icons/3/icon.png"));

        // PROFILE_PICTURE
        path = FilePathConverter.getProfilePictureFilePath().resolve("avatar.jpg");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.PROFILE_PICTURE, 7L);
        assertThat(uri).isEqualTo(URI.create("user/profile-pictures/7/avatar.jpg"));

        // EXAM_USER_SIGNATURE
        path = FilePathConverter.getExamUserSignatureFilePath().resolve("sign.png");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.EXAM_USER_SIGNATURE, 8L);
        assertThat(uri).isEqualTo(URI.create("exam-user/signatures/8/sign.png"));

        // EXAM_ATTENDANCE_CHECK_STUDENT_IMAGE
        path = FilePathConverter.getStudentImageFilePath().resolve("photo.jpg");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.EXAM_USER_IMAGE, 9L);
        assertThat(uri).isEqualTo(URI.create("exam-user/9/photo.jpg"));

        // LECTURE_ATTACHMENT
        path = FilePathConverter.getLectureAttachmentFileSystemPath().resolve(Path.of("4", "slides.pdf"));
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.LECTURE_ATTACHMENT, 4L);
        assertThat(uri).isEqualTo(URI.create("attachments/lecture/4/slides.pdf"));

        // SLIDE
        path = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(Path.of("4", "slide", "1", "slide1.pdf"));
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.SLIDE, 1L);
        assertThat(uri).isEqualTo(URI.create("attachments/attachment-unit/4/slide/1/slide1.pdf"));

        // STUDENT_VERSION_SLIDES
        path = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(Path.of("4", "student", "notes.pdf"));
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.STUDENT_VERSION_SLIDES, 4L);
        assertThat(uri).isEqualTo(URI.create("attachments/attachment-unit/4/student/notes.pdf"));

        // ATTACHMENT_UNIT
        path = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(Path.of("4", "file.pdf"));
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.ATTACHMENT_UNIT, 4L);
        assertThat(uri).isEqualTo(URI.create("attachments/attachment-unit/4/file.pdf"));

        // FILE_UPLOAD_SUBMISSION
        path = FilePathConverter.buildFileUploadSubmissionPath(7L, 9L).resolve("solution.txt");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.FILE_UPLOAD_SUBMISSION, 9L);
        assertThat(uri).isEqualTo(URI.create("file-upload-exercises/7/submissions/9/solution.txt"));
    }

    @Test
    void testExternalUriForDragItemFileSystemPath() {
        Path path = FilePathConverter.getDragItemFilePath().resolve("item.png");

        assertThat(FilePathConverter.externalUriForDragItemFileSystemPath(path, 7L, 2L)).isEqualTo(URI.create("drag-and-drop/questions/7/drag-items/2/item.png"));
        // A question that has not been inserted yet has no id; DragAndDropQuestion.afterCreate() replaces the placeholder once it has one.
        assertThat(FilePathConverter.externalUriForDragItemFileSystemPath(path, null, 2L))
                .isEqualTo(URI.create("drag-and-drop/questions/" + Constants.FILEPATH_ID_PLACEHOLDER + "/drag-items/2/item.png"));
    }

    @Test
    void testExternalUriForFileSystemPathShouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            Path actualFileUploadPath = FilePathConverter.getFileUploadExercisesFilePath();
            FilePathConverter.externalUriForFileSystemPath(actualFileUploadPath, FilePathType.FILE_UPLOAD_SUBMISSION, 1L);

        }).withMessageStartingWith("Unexpected String in upload file path. Exercise ID should be present here:");
    }

    @Test
    void testExternalUriForFileSystemPathInvalidFileUploadSubmission() {
        Path path = FilePathConverter.getFileUploadExercisesFilePath();
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> FilePathConverter.externalUriForFileSystemPath(path, FilePathType.FILE_UPLOAD_SUBMISSION, 1L))
                .withMessageContaining("Exercise ID should be present here");
    }

    @Test
    void testExternalUriForSlideFileSystemPathShouldThrowException() {
        // Path too short, missing attachmentVideoUnitId
        Path invalidPath = rootPath.resolve("attachments").resolve("attachment-unit").resolve("slide").resolve("1").resolve("slide1.pdf");
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            // id is arbitrary here, since the path is invalid
            FilePathConverter.externalUriForFileSystemPath(invalidPath, FilePathType.SLIDE, 1L);
        }).withMessageContaining("AttachmentVideoUnit ID should be present here");
    }

    @Test
    void testGetMarkdownFilePath() {
        assertThat(FilePathConverter.getMarkdownFilePath()).isEqualTo(rootPath.resolve("markdown"));
    }

    @Test
    void testGetMarkdownFilePathForConversation() {
        long courseId = 42L;
        long conversationId = 99L;
        assertThat(FilePathConverter.getMarkdownFilePathForConversation(courseId, conversationId))
                .isEqualTo(rootPath.resolve("markdown").resolve("communication").resolve("42").resolve("99"));
    }
}
