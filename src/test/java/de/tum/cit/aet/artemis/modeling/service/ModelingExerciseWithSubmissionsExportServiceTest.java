package de.tum.cit.aet.artemis.modeling.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the modeling exercise details file a course or exam archive contains.
 */
class ModelingExerciseWithSubmissionsExportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "modelingexportservice";

    @Autowired
    private ModelingExerciseWithSubmissionsExportService modelingExerciseWithSubmissionsExportService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @TempDir
    private Path exportDir;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportModelingExerciseWithSubmissions_writesTheExerciseDetailsWithoutTheEntityGraph() throws Exception {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise(TEST_PREFIX);
        ModelingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        List<String> exportErrors = new ArrayList<>();

        modelingExerciseWithSubmissionsExportService.exportModelingExerciseWithSubmissions(exercise, SubmissionExportOptionsDTO.exportAll(), exportDir, exportErrors,
                new ArrayList<ArchivalReportEntry>());

        assertThat(exportErrors).as("the details are written without a JSON error").noneMatch(error -> error.contains("exercise details"));
        String details = Files.readString(exportDir.resolve("Exercise-Details-" + exercise.getTitle() + ".json"));
        var written = JsonObjectMapper.get().readValue(details, ModelingExerciseResponseDTO.class);
        assertThat(written.title()).isEqualTo(exercise.getTitle());
        assertThat(written.problemStatement()).isEqualTo(exercise.getProblemStatement());
        assertThat(written.diagramType()).isEqualTo(exercise.getDiagramType());
        assertThat(written.exampleSolutionModel()).isEqualTo(exercise.getExampleSolutionModel());
        assertThat(details).as("no student data and no entity back references are written").doesNotContain("studentParticipations").doesNotContain("\"exercises\"");
    }
}
