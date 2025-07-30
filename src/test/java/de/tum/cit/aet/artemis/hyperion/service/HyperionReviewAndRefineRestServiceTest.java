package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.hasJsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key" })

class HyperionReviewAndRefineRestServiceTest extends AbstractHyperionRestTest {

    private static final String TEST_PREFIX = "hyperionservice";

    @Autowired
    private HyperionReviewAndRefineRestService hyperionReviewAndRefineRestService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        hyperionRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    // ===== Consistency Check Tests =====

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckConsistency_Success() throws Exception {
        // Given
        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), "No issues found", hasJsonPath("$.problem_statement"),
                hasJsonPath("$.programming_language"), hasJsonPath("$.template_repository.files"), hasJsonPath("$.solution_repository.files"),
                hasJsonPath("$.test_repository.files"));

        // When
        ConsistencyCheckResponseDTO result = hyperionReviewAndRefineRestService.checkConsistency(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), programmingExercise);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.hasIssues()).isFalse();
        assertThat(result.issues()).isEmpty();
        assertThat(result.summary()).isEqualTo("No issues found");
        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckConsistency_WithIssues() throws NetworkingException {
        hyperionRequestMockProvider.mockConsistencyCheckWithIssues(programmingExercise.getId());

        ConsistencyCheckResponseDTO result = hyperionReviewAndRefineRestService.checkConsistency(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), programmingExercise);

        assertThat(result).isNotNull();
        assertThat(result.hasIssues()).isTrue();
        assertThat(result.issues()).hasSize(2);
        assertThat(result.issues().get(0).category()).isEqualTo("test-coverage");
        assertThat(result.issues().get(0).severity()).isEqualTo("HIGH");
        assertThat(result.issues().get(1).category()).isEqualTo("problem-statement");
        assertThat(result.issues().get(1).severity()).isEqualTo("MEDIUM");
        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckConsistency_NetworkError() throws Exception {
        hyperionRequestMockProvider.mockConsistencyCheckNetworkError();

        assertThatExceptionOfType(NetworkingException.class)
                .isThrownBy(() -> hyperionReviewAndRefineRestService.checkConsistency(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), programmingExercise));

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckConsistency_Timeout() throws Exception {
        hyperionRequestMockProvider.mockConsistencyCheckTimeout(programmingExercise.getId());

        assertThatExceptionOfType(NetworkingException.class)
                .isThrownBy(() -> hyperionReviewAndRefineRestService.checkConsistency(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), programmingExercise));

        hyperionRequestMockProvider.verify();
    }

    // ===== Problem Statement Rewrite Tests =====

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_Success() throws Exception {
        String originalText = "Original problem statement";
        String improvedText = "Improved problem statement with better clarity";

        hyperionRequestMockProvider.mockProblemStatementRewriteSuccess(course.getId(), improvedText, hasJsonPath("$.text"));

        ProblemStatementRewriteResponseDTO result = hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course,
                originalText);

        assertThat(result).isNotNull();
        assertThat(result.rewrittenText()).isEqualTo(improvedText);
        assertThat(result.improved()).isTrue();
        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_NoImprovement() throws Exception {
        String originalText = "Already perfect problem statement";

        hyperionRequestMockProvider.mockProblemStatementRewriteNoImprovement(course.getId(), originalText);

        ProblemStatementRewriteResponseDTO result = hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course,
                originalText);

        assertThat(result).isNotNull();
        assertThat(result.rewrittenText()).isEqualTo(originalText);
        assertThat(result.improved()).isFalse();
        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_NetworkError() throws Exception {
        String originalText = "Problem statement";
        hyperionRequestMockProvider.mockRewriteProblemStatementNetworkError();

        assertThatExceptionOfType(NetworkingException.class)
                .isThrownBy(() -> hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, originalText));

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_Timeout() throws Exception {
        String originalText = "Problem statement";
        hyperionRequestMockProvider.mockRewriteProblemStatementTimeout(course.getId());

        assertThatExceptionOfType(NetworkingException.class)
                .isThrownBy(() -> hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, originalText));

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_EmptyText() throws Exception {
        String emptyText = "";

        // The service should validate and throw an exception for empty text
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, emptyText))
                .withMessage("Problem statement text must not be null or empty");
    }

    // ===== Edge Case Tests =====

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckConsistency_LargeExercise() throws Exception {
        // Create a large exercise with extensive problem statement
        String largeProblemStatement = "This is a very large problem statement with extensive requirements. ".repeat(100);
        programmingExercise.setProblemStatement(largeProblemStatement);

        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), "No issues found");

        ConsistencyCheckResponseDTO result = hyperionReviewAndRefineRestService.checkConsistency(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), programmingExercise);

        assertThat(result).isNotNull();
        assertThat(result.hasIssues()).isFalse();
        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_LargeText() throws NetworkingException {
        String largeText = "Complex problem statement with many details. ".repeat(50);

        hyperionRequestMockProvider.mockProblemStatementRewriteSuccess(course.getId(), largeText, largeText + " Enhanced with additional clarity.");

        ProblemStatementRewriteResponseDTO result = hyperionReviewAndRefineRestService.rewriteProblemStatement(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course,
                largeText);

        assertThat(result).isNotNull();
        assertThat(result.improved()).isTrue();
        assertThat(result.rewrittenText()).contains("Enhanced with additional clarity");
        hyperionRequestMockProvider.verify();
    }
}
