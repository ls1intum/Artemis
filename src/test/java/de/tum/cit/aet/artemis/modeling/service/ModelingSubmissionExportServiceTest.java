package de.tum.cit.aet.artemis.modeling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

class ModelingSubmissionExportServiceTest {

    @TempDir
    Path tempDir;

    private ModelingSubmissionExportService service;

    @BeforeEach
    void setUp() {
        service = new ModelingSubmissionExportService(mock(ExerciseRepository.class), mock(ZipFileService.class), mock(FileService.class));
    }

    @Test
    void saveSubmissionToFile_withoutModelCreatesEmptyFile() throws IOException {
        var file = tempDir.resolve("empty.json").toFile();
        var submission = new ModelingSubmission();
        submission.setModel(null);

        service.saveSubmissionToFile(new ModelingExercise(), submission, file);

        assertThat(file).exists().isEmpty();
    }

    @Test
    void saveSubmissionToFile_withoutModelKeepsExistingFile() throws IOException {
        var file = tempDir.resolve("existing.json").toFile();
        FileUtils.writeStringToFile(file, "old", StandardCharsets.UTF_8);
        var submission = new ModelingSubmission();
        submission.setModel(null);

        service.saveSubmissionToFile(new ModelingExercise(), submission, file);

        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8)).isEqualTo("old");
    }

    @Test
    void saveSubmissionToFile_withModelWritesModel() throws IOException {
        var file = tempDir.resolve("model.json").toFile();
        var submission = new ModelingSubmission();
        submission.setModel("{\"version\":\"3.0.0\"}");

        service.saveSubmissionToFile(new ModelingExercise(), submission, file);

        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8)).isEqualTo("{\"version\":\"3.0.0\"}");
    }
}
