package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.exception.FilePathParsingException;

class FilePathServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FilePathService filePathService;

    @Test
    void testActualPathForPublicPath() {
        Path actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/drag-and-drop/backgrounds/background.jpeg"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "backgrounds", "background.jpeg"));

        actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/drag-and-drop/drag-items/image.jpeg"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "drag-items", "image.jpeg"));

        actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/course/icons/icon.png"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "course", "icons", "icon.png"));

        actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/attachments/lecture/4/slides.pdf"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "lecture", "4", "slides.pdf"));

        actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/attachments/attachment-unit/4/download.pdf"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "4", "download.pdf"));

        actualPath = filePathService.actualPathForPublicPath(URI.create("/api/files/attachments/attachment-unit/4/slide/1/1.jpg"));
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "4", "slide", "1", "1.jpg"));
    }

    @Test
    void testActualPathForPublicFileUploadExercisePath_shouldReturnNull() {
        Path path = filePathService.actualPathForPublicPath(URI.create("/api/unknown-path/unknown-file.pdf"));
        assertThat(path).isNull();
    }

    @Test
    void testActualPathForPublicFileUploadExercisePathOrThrow_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class)
                .isThrownBy(() -> filePathService.actualPathForPublicPathOrThrow(URI.create("/api/files/file-upload-exercises/file.pdf")))
                .withMessageStartingWith("Public path does not contain correct exerciseId or submissionId:");

        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> filePathService.actualPathForPublicPathOrThrow(URI.create("/api/unknown-path/unknown-file.pdf")))
                .withMessageStartingWith("Unknown Filepath:");
    }

    @Test
    void testPublicPathForActualTempFilePath() {
        Path actualPath = FilePathService.getTempFilePath().resolve("test");
        URI publicPath = filePathService.publicPathForActualPath(actualPath, 1L);
        assertThat(publicPath).isEqualTo(URI.create(FileService.DEFAULT_FILE_SUBPATH + actualPath.getFileName()));
    }

    @Test
    void testPublicPathForActualPath_shouldReturnNull() {
        URI otherPath = filePathService.publicPathForActualPath(Path.of("unknown-path", "unknown-file.pdf"), 1L);
        assertThat(otherPath).isNull();
    }

    @Test
    void testPublicPathForActualPath_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            Path actualFileUploadPath = FilePathService.getFileUploadExercisesFilePath();
            filePathService.publicPathForActualPathOrThrow(actualFileUploadPath, 1L);

        }).withMessageStartingWith("Unexpected String in upload file path. Exercise ID should be present here:");

        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> filePathService.publicPathForActualPathOrThrow(Path.of("unknown-path", "unknown-file.pdf"), 1L))
                .withMessageStartingWith("Unknown Filepath:");
    }
}
