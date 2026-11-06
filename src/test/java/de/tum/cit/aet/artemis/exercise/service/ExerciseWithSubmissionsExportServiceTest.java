package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.service.TextExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

class ExerciseWithSubmissionsExportServiceTest {

    @TempDir
    private Path exportDir;

    private ExerciseWithSubmissionsExportService service;

    private TextExercise exercise;

    @BeforeEach
    void setUp() throws IOException {
        FileService fileService = mock(FileService.class);
        when(fileService.createTemporaryDirectory(any(), anyString(), anyLong())).thenReturn(exportDir);
        service = new TextExerciseWithSubmissionsExportService(mock(TextSubmissionExportService.class), JsonObjectMapper.get(), mock(TeamAssignmentConfigRepository.class),
                mock(PlagiarismDetectionConfigRepository.class), fileService, mock(ZipFileService.class));
        Course course = new Course();
        course.setShortName("export");
        exercise = new TextExercise();
        exercise.setId(1L);
        exercise.setTitle("Code examples");
        exercise.setCourse(course);
    }

    @ParameterizedTest
    @ValueSource(strings = { "```text\n/api/core/files/markdown/example.png\n```", "~~~text\n![example](/api/core/files/markdown/example.png)\n~~~",
            "    ![example](/api/core/files/markdown/example.png)", "`/api/core/files/markdown/example.png`", "`` ![example](/api/core/files/markdown/example.png) `nested` ``",
            "<!-- <img src=/api/core/files/markdown/example.png> -->", "<pre><img src=/api/core/files/markdown/example.png></pre>",
            "<code><a href=/api/core/files/markdown/example.png>example</a></code>", "A literal path: /api/core/files/markdown/example.png",
            "!\\[example](/api/core/files/markdown/example.png)", "<a href=\"https://example.org/?redirect=/api/core/files/markdown/example.png\">external</a>" })
    void exportsLiteralPathsUnchangedWithoutCopyingFiles(String statement) throws Exception {
        exercise.setProblemStatement(statement);

        try (MockedStatic<FilePathConverter> paths = Mockito.mockStatic(FilePathConverter.class)) {
            paths.when(FilePathConverter::getTempFilePath).thenReturn(exportDir);
            paths.when(FilePathConverter::getMarkdownFilePath).thenReturn(exportDir);
            service.exportExerciseForDownload(exercise);
        }

        assertThat(Files.readString(exportDir.resolve("Problem-Statement-Code_examples.md"))).isEqualTo(statement);
        assertThat(exportDir.resolve("files")).doesNotExist();
    }

    @Test
    void exportsActualReferencesAlongsideCodeWithoutChangingTheStatement() throws Exception {
        Path uploads = Files.createDirectory(exportDir.resolve("uploads"));
        FileUtils.writeByteArrayToFile(uploads.resolve("image.png").toFile(), new byte[] { 1, 2, 3 });
        FileUtils.writeByteArrayToFile(uploads.resolve("attachment.pdf").toFile(), new byte[] { 4, 5, 6 });
        String statement = """
                ```text
                /api/core/files/markdown/missing.png
                ![example](/api/core/files/markdown/missing.png)
                ```
                `![example](/api/core/files/markdown/missing.png)`
                ![image](/api/core/files/markdown/image.png) <img src=/api/core/files/markdown/image.png>
                [attachment][file] <a href='/api/core/files/markdown/attachment.pdf'>download</a>

                [file]: /api/core/files/markdown/attachment.pdf
                ![outside](/api/core/files/markdown/../outside.png)
                """;
        exercise.setProblemStatement(statement);

        try (MockedStatic<FilePathConverter> paths = Mockito.mockStatic(FilePathConverter.class)) {
            paths.when(FilePathConverter::getMarkdownFilePath).thenReturn(uploads);
            paths.when(FilePathConverter::getTempFilePath).thenReturn(exportDir);
            service.exportExerciseForDownload(exercise);
        }

        assertThat(Files.readString(exportDir.resolve("Problem-Statement-Code_examples.md"))).isEqualTo(statement);
        try (var files = Files.list(exportDir.resolve("files"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactlyInAnyOrderElementsOf(List.of("image.png", "attachment.pdf"));
        }
        assertThat(Files.readAllBytes(exportDir.resolve("files/image.png"))).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(exportDir.resolve("files/attachment.pdf"))).containsExactly(4, 5, 6);
    }

    @ParameterizedTest
    @ValueSource(strings = { "![image](/api/core/files/markdown/missing.png)", "[file](/api/core/files/markdown/missing.png)", "<img src=/api/core/files/markdown/missing.png>",
            "<a href='/api/core/files/markdown/missing.png'>file</a>" })
    void missingActualReferencesStillFailTheDownload(String statement) {
        exercise.setProblemStatement(statement);
        try (MockedStatic<FilePathConverter> paths = Mockito.mockStatic(FilePathConverter.class)) {
            paths.when(FilePathConverter::getMarkdownFilePath).thenReturn(exportDir);
            paths.when(FilePathConverter::getTempFilePath).thenReturn(exportDir);

            assertThatThrownBy(() -> service.exportExerciseForDownload(exercise)).isInstanceOf(IOException.class).hasMessageContaining("Failed to copy embedded files");
        }
    }
}
