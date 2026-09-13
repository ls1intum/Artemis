package de.tum.cit.aet.artemis.text.service;

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
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Tests the text exercise details file a course or exam archive contains.
 */
class TextExerciseWithSubmissionsExportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textexportservice";

    @Autowired
    private TextExerciseWithSubmissionsExportService textExerciseWithSubmissionsExportService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @TempDir
    private Path exportDir;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportTextExerciseWithSubmissions_writesTheExerciseDetailsWithoutTheEntityGraph() throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(TEST_PREFIX);
        TextExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exercise.setTeamAssignmentConfig(teamAssignmentConfig());
        exercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        exercise = exerciseRepository.save(exercise);
        assertThat(exercise.getTeamAssignmentConfig().getId()).as("the fixture stores both configurations").isNotNull();
        assertThat(exercise.getPlagiarismDetectionConfig().getId()).isNotNull();
        List<String> exportErrors = new ArrayList<>();

        textExerciseWithSubmissionsExportService.exportTextExerciseWithSubmissions(exercise, SubmissionExportOptionsDTO.exportAll(), exportDir, exportErrors,
                new ArrayList<ArchivalReportEntry>());

        assertThat(exportErrors).as("the details are written without a JSON error").noneMatch(error -> error.contains("exercise details"));
        String details = Files.readString(exportDir.resolve("Exercise-Details-" + exercise.getTitle() + ".json"));
        var written = JsonObjectMapper.get().readValue(details, TextExerciseResponseDTO.class);
        assertThat(written.title()).isEqualTo(exercise.getTitle());
        assertThat(written.problemStatement()).isEqualTo(exercise.getProblemStatement());
        assertThat(written.exampleSolution()).isEqualTo(exercise.getExampleSolution());
        assertThat(details).as("no student data and no entity back references are written").doesNotContain("studentParticipations").doesNotContain("\"exercises\"");
        // the file is read elsewhere, where the ids of this instance's configuration rows mean nothing
        assertThat(written.teamAssignmentConfig().id()).as("the exported team assignment configuration carries no id").isNull();
        assertThat(written.plagiarismDetectionConfig().id()).as("the exported plagiarism configuration carries no id").isNull();
        assertThat(written.teamAssignmentConfig().maxTeamSize()).as("the settings themselves survive").isEqualTo(10);
    }

    private static TeamAssignmentConfig teamAssignmentConfig() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(1);
        config.setMaxTeamSize(10);
        return config;
    }
}
