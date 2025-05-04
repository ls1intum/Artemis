package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FilePathServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void testActualPathForPublicPath() {
        Path actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("drag-and-drop/backgrounds/background.jpeg"), FilePathType.DRAG_AND_DROP_BACKGROUND);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "backgrounds", "background.jpeg"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("drag-and-drop/drag-items/image.jpeg"), FilePathType.DRAG_ITEM);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "drag-items", "image.jpeg"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("course/icons/icon.png"), FilePathType.COURSE_ICON);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "course", "icons", "icon.png"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("attachments/lecture/4/slides.pdf"), FilePathType.LECTURE_ATTACHMENT);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "lecture", "4", "slides.pdf"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("attachments/attachment-unit/4/download.pdf"), FilePathType.ATTACHMENT_UNIT);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "4", "download.pdf"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("attachments/attachment-unit/4/slide/1/1.jpg"), FilePathType.SLIDE);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "4", "slide", "1", "1.jpg"));

        actualPath = FilePathService.fileSystemPathForPublicUri(URI.create("attachments/attachment-unit/4/student/download.pdf"), FilePathType.STUDENT_VERSION_SLIDES);
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "4", "student", "download.pdf"));
    }

    @Test
    void testActualPathForPublicFileUploadExercisePathOrThrow_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> FilePathService.fileSystemPathForPublicUri(URI.create("file-upload-exercises/file.pdf"), FilePathType.FILE_UPLOAD_SUBMISSION))
                .withMessageStartingWith("Public path does not contain correct exerciseId or submissionId:");
    }

    @Test
    void testPublicPathForActualTempFilePath() {
        Path actualPath = FilePathService.getTempFilePath().resolve("test");
        URI publicPath = FilePathService.publicUriForFileSystemPath(actualPath, FilePathType.TEMPORARY, 1L);
        assertThat(publicPath).isEqualTo(URI.create(FileService.DEFAULT_FILE_SUBPATH + actualPath.getFileName()));
    }

    @Test
    void testPublicUriForFileSystemPath_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            Path actualFileUploadPath = FilePathService.getFileUploadExercisesFilePath();
            FilePathService.publicUriForFileSystemPath(actualFileUploadPath, FilePathType.FILE_UPLOAD_SUBMISSION, 1L);

        }).withMessageStartingWith("Unexpected String in upload file path. Exercise ID should be present here:");
    }
}
