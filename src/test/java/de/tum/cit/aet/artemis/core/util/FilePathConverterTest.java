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
    void testFileSystemPathForPublicUriForAllFilePathTypes() {
        // TEMPORARY
        Path actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("temp/file.tmp"), FilePathType.TEMPORARY);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("temp").resolve("file.tmp"));

        // DRAG_AND_DROP_BACKGROUND
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("drag-and-drop/backgrounds/bg.png"), FilePathType.DRAG_AND_DROP_BACKGROUND);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("drag-and-drop").resolve("backgrounds").resolve("bg.png"));

        // DRAG_ITEM
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("drag-and-drop/drag-items/item.png"), FilePathType.DRAG_ITEM);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("drag-and-drop").resolve("drag-items").resolve("item.png"));

        // COURSE_ICON
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("course/icons/icon.png"), FilePathType.COURSE_ICON);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("course").resolve("icons").resolve("icon.png"));

        // PROFILE_PICTURE
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("user/profile-pictures/avatar.jpg"), FilePathType.PROFILE_PICTURE);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("user").resolve("profile-pictures").resolve("avatar.jpg"));

        // EXAM_USER_SIGNATURE
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("exam-user/signatures/sign.png"), FilePathType.EXAM_USER_SIGNATURE);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("exam-user").resolve("signatures").resolve("sign.png"));

        // EXAM_ATTENDANCE_CHECK_STUDENT_IMAGE
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("exam-user/42/photo.jpg"), FilePathType.EXAM_USER_IMAGE);
        assertThat(actualPath).isEqualTo(rootPath.resolve("images").resolve("exam-user").resolve("42").resolve("photo.jpg"));

        // LECTURE_ATTACHMENT
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/lecture/4/slides.pdf"), FilePathType.LECTURE_ATTACHMENT);
        assertThat(actualPath).isEqualTo(rootPath.resolve("attachments").resolve("lecture").resolve("4").resolve("slides.pdf"));

        // SLIDE
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/attachment-unit/4/slide/1/slide1.pdf"), FilePathType.SLIDE);
        assertThat(actualPath).isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("slide").resolve("1").resolve("slide1.pdf"));

        // STUDENT_VERSION_SLIDES
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/attachment-unit/4/student/notes.pdf"), FilePathType.STUDENT_VERSION_SLIDES);
        assertThat(actualPath).isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("student").resolve("notes.pdf"));

        // ATTACHMENT_UNIT
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/attachment-unit/4/file.pdf"), FilePathType.ATTACHMENT_UNIT);
        assertThat(actualPath).isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("file.pdf"));

        // FILE_UPLOAD_SUBMISSION
        // This requires a valid structure: file-upload-exercises/{exerciseId}/submissions/{submissionId}/file
        actualPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("file-upload-exercises/7/submissions/9/solution.txt"), FilePathType.FILE_UPLOAD_SUBMISSION);
        assertThat(actualPath).isEqualTo(FilePathConverter.buildFileUploadSubmissionPath(7L, 9L).resolve("solution.txt"));
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

        // DRAG_ITEM
        path = FilePathConverter.getDragItemFilePath().resolve("item.png");
        uri = FilePathConverter.externalUriForFileSystemPath(path, FilePathType.DRAG_ITEM, 5L);
        assertThat(uri).isEqualTo(URI.create("drag-and-drop/drag-items/5/item.png"));

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
    void testFileSystemPathForExternalUriShouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri(URI.create("file-upload-exercises/file.pdf"), FilePathType.FILE_UPLOAD_SUBMISSION))
                .withMessageStartingWith("External URI does not contain correct exerciseId or submissionId:");
    }

    @Test
    void testExternalUriForFileSystemPathShouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            Path actualFileUploadPath = FilePathConverter.getFileUploadExercisesFilePath();
            FilePathConverter.externalUriForFileSystemPath(actualFileUploadPath, FilePathType.FILE_UPLOAD_SUBMISSION, 1L);

        }).withMessageStartingWith("Unexpected String in upload file path. Exercise ID should be present here:");
    }

    @Test
    void testFileSystemPathForExternalUriInvalidLectureAttachment() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/lecture/slides.pdf"), FilePathType.LECTURE_ATTACHMENT))
                .withMessageContaining("lectureId");
    }

    @Test
    void testFileSystemPathForExternalUriInvalidAttachmentVideoUnit() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/attachment-unit/file.pdf"), FilePathType.ATTACHMENT_UNIT))
                .withMessageContaining("attachmentVideoUnitId");
    }

    @Test
    void testFileSystemPathForExternalUriInvalidSlide() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/attachment-unit/4/slide/slide.jpg"), FilePathType.SLIDE))
                .withMessageContaining("attachmentVideoUnitId or slideId");
    }

    @Test
    void testFileSystemPathForPublicUriInvalidFileUploadSubmission() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri(URI.create("file-upload-exercises/file.pdf"), FilePathType.FILE_UPLOAD_SUBMISSION))
                .withMessageContaining("exerciseId or submissionId");
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
    void testGetStudentVersionSlidesFileSystemPathShouldThrowException() {
        // Path too short, missing attachmentVideoUnitId
        Path invalidPath = Path.of("attachments", "attachment-unit", "student", "notes.pdf");
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathConverter.fileSystemPathForExternalUri((invalidPath.toUri()), FilePathType.STUDENT_VERSION_SLIDES))
                .withMessageContaining("attachmentVideoUnitId");
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
