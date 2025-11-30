package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Tests for PyrisDTOService with uncommitted changes support
 */
class PyrisDTOServiceUncommittedChangesTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisdtoserviceuncommittedchanges";

    @Autowired
    private PyrisDTOService pyrisDTOService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise exercise;

    private ProgrammingSubmission submission;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);

        // Create a programming submission
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        submission = ParticipationFactory.generateProgrammingSubmission(true);
        participationUtilService.addSubmission(participation, submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_withUncommittedFiles_mergesCorrectly() {
        // Arrange - Uncommitted files that should be included
        var uncommittedFiles = Map.of("src/Main.java", "public class Main { // NEW uncommitted code }", "src/Utils.java", "public class Utils { // new file }");

        // Act
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission, uncommittedFiles);

        // Assert - Repository should contain uncommitted files
        assertThat(result.repository()).containsAllEntriesOf(uncommittedFiles);
        assertThat(result.repository().get("src/Main.java")).isEqualTo("public class Main { // NEW uncommitted code }");
        assertThat(result.repository().get("src/Utils.java")).isEqualTo("public class Utils { // new file }");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_withEmptyUncommittedFiles_usesOnlyCommitted() {
        // Arrange
        var uncommittedFiles = Map.<String, String>of(); // Empty map

        // Act
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission, uncommittedFiles);

        // Assert - Should contain repository (committed files or empty)
        assertThat(result.repository()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_backwardCompatibility() {
        // Act - Call original method without uncommitted files
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission);

        // Assert - Should work normally
        assertThat(result.repository()).isNotNull();
        assertThat(result.id()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_onlyUncommittedFiles() {
        // Arrange - Only uncommitted files
        var uncommittedFiles = Map.of("src/Main.java", "public class Main { // all new }", "src/Utils.java", "public class Utils { }");

        // Act
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission, uncommittedFiles);

        // Assert - Should contain at least the uncommitted files
        assertThat(result.repository()).containsAllEntriesOf(uncommittedFiles);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_uncommittedFileOverridesPriority() {
        // Arrange - Multiple uncommitted files
        var uncommittedFiles = Map.of("src/Main.java", "UNCOMMITTED_MAIN", "src/Utils.java", "UNCOMMITTED_UTILS", "src/Helper.java", "UNCOMMITTED_HELPER");

        // Act
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission, uncommittedFiles);

        // Assert - All uncommitted files should be present in the result
        assertThat(result.repository().get("src/Main.java")).isEqualTo("UNCOMMITTED_MAIN");
        assertThat(result.repository().get("src/Utils.java")).isEqualTo("UNCOMMITTED_UTILS");
        assertThat(result.repository().get("src/Helper.java")).isEqualTo("UNCOMMITTED_HELPER");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToPyrisSubmissionDTO_multipleUncommittedFiles() {
        // Arrange - Multiple files with different paths
        var uncommittedFiles = Map.of("src/de/tum/Main.java", "package de.tum; public class Main { }", "src/de/tum/model/User.java",
                "package de.tum.model; public class User { }", "README.md", "# Updated README");

        // Act
        var result = pyrisDTOService.toPyrisSubmissionDTO(submission, uncommittedFiles);

        // Assert - All uncommitted files should be in repository
        assertThat(result.repository()).containsAllEntriesOf(uncommittedFiles);
        assertThat(result.repository()).containsKeys("src/de/tum/Main.java", "src/de/tum/model/User.java", "README.md");
    }
}
