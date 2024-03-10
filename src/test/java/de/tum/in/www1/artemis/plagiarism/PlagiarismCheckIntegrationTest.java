package de.tum.in.www1.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.util.TestResourceUtils;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;

class PlagiarismCheckIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plagiarismcheck";

    private static final String TEXT_SUBMISSION = "Lorem ipsum dolor sit amet, consectetur adipiscing elit";

    @Autowired
    private PlagiarismUtilService plagiarismUtilService;

    private final int submissionsAmount = 5;

    private static String modelingSubmission;

    @BeforeAll
    static void initTestCase() throws IOException {
        modelingSubmission = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCheckPlagiarismResultForTextExercise() throws Exception {
        // given
        var exerciseId = plagiarismUtilService.createTextExerciseAndSimilarSubmissions(TEST_PREFIX, TEXT_SUBMISSION, submissionsAmount);

        // when
        var result = createPlagiarismResult("/api/text-exercises/" + exerciseId + "/check-plagiarism");

        // then
        verifyPlagiarismResult(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCheckPlagiarismResultForTeamTextExercise() throws Exception {
        // given
        var exerciseId = plagiarismUtilService.createTeamTextExerciseAndSimilarSubmissions(TEST_PREFIX, TEXT_SUBMISSION, submissionsAmount);

        // when
        var result = createPlagiarismResult("/api/text-exercises/" + exerciseId + "/check-plagiarism");

        // then
        verifyPlagiarismResult(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismResultForModelingExercise() throws Exception {
        // given
        var exerciseId = plagiarismUtilService.createModelingExerciseAndSimilarSubmissionsToTheCourse(TEST_PREFIX, modelingSubmission, submissionsAmount);

        // when
        var result = createPlagiarismResult("/api/modeling-exercises/" + exerciseId + "/check-plagiarism");

        // then
        verifyPlagiarismResult(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismResultForTeamModelingExercise() throws Exception {
        // given
        var exerciseId = plagiarismUtilService.createTeamModelingExerciseAndSimilarSubmissionsToTheCourse(TEST_PREFIX, modelingSubmission, submissionsAmount);

        // when
        var result = createPlagiarismResult("/api/modeling-exercises/" + exerciseId + "/check-plagiarism");

        // then
        verifyPlagiarismResult(result);
    }

    /***
     * Create the plagiarism result response based on the provided path.
     *
     * @param path The provided path to the rest endpoint
     * @return plagiarism result DTO
     */
    private PlagiarismResultDTO createPlagiarismResult(String path) throws Exception {
        var plagiarismOptions = plagiarismUtilService.getDefaultPlagiarismOptions();
        return request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismOptions);
    }

    private static void verifyPlagiarismResult(PlagiarismResultDTO<?> result) {
        // verify comparisons
        for (var comparison : result.plagiarismResult().getComparisons()) {
            var submissionA = comparison.getSubmissionA();
            var submissionB = comparison.getSubmissionB();

            assertThat(submissionA).as("should have a submission A").isNotNull();
            assertThat(submissionB).as("should have a submission B").isNotNull();

            assertThat(submissionA.getPlagiarismComparison()).as("should have a bidirectional connection").isEqualTo(comparison);
            assertThat(submissionB.getPlagiarismComparison()).as("should have a bidirectional connection").isEqualTo(comparison);
        }

        // verify plagiarism result stats
        var stats = result.plagiarismResultStats();
        assertThat(stats.numberOfDetectedSubmissions()).isEqualTo(5);
        assertThat(stats.averageSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(stats.maximalSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
    }
}
