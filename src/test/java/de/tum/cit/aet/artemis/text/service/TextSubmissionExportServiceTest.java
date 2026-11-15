package de.tum.cit.aet.artemis.text.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class TextSubmissionExportServiceTest {

    @TempDir
    private Path tempDir;

    private TextSubmissionExportService service;

    @BeforeEach
    void setUp() {
        service = new TextSubmissionExportService(mock(ExerciseRepository.class), mock(ZipFileService.class), mock(FileService.class));
    }

    @Test
    void saveSubmissionToFile_nullText_createsEmptyFile() throws IOException {
        File file = tempDir.resolve("empty.txt").toFile();
        TextSubmission submission = new TextSubmission();
        submission.setText(null);

        service.saveSubmissionToFile(new TextExercise(), submission, file);

        assertThat(file).exists().isEmpty();
    }

    @Test
    void saveSubmissionToFile_nullTextAndExistingFile_keepsFileUntouched() throws IOException {
        Path path = tempDir.resolve("existing.txt");
        FileUtils.writeStringToFile(path.toFile(), "keep", StandardCharsets.UTF_8);
        TextSubmission submission = new TextSubmission();
        submission.setText(null);

        service.saveSubmissionToFile(new TextExercise(), submission, path.toFile());

        assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo("keep");
    }

    @Test
    void saveSubmissionToFile_nullTextAndMissingParentDirectory_throwsIOException() {
        File file = tempDir.resolve("missing-dir").resolve("empty.txt").toFile();
        TextSubmission submission = new TextSubmission();
        submission.setText(null);

        assertThatThrownBy(() -> service.saveSubmissionToFile(new TextExercise(), submission, file)).isInstanceOf(IOException.class);
    }

    @Test
    void saveSubmissionToFile_withText_writesText() throws IOException {
        File file = tempDir.resolve("text.txt").toFile();
        TextSubmission submission = new TextSubmission();
        submission.setText("hello");

        service.saveSubmissionToFile(new TextExercise(), submission, file);

        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }
}
