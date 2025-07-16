package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.MOCK_INCONSISTENCY_RESULT;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.MOCK_REWRITTEN_STATEMENT;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.hasJsonPath;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;
import de.tum.cit.aet.artemis.hyperion.config.HyperionRestTestConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for HyperionReviewAndRefineRestService.
 *
 * Tests service layer business logic, external API integration, error handling,
 * and data transformation for programming exercise review and enhancement operations.
 */
@Import(HyperionRestTestConfiguration.class)
@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key" })
class HyperionReviewAndRefineRestServiceTest extends AbstractHyperionRestTest {

    private static final String TEST_PREFIX = "hyperionservice";

    @Autowired
    private HyperionReviewAndRefineRestService hyperionService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private User instructor;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    /**
     * Tests consistency check workflow: exercise validation, repository data extraction,
     * external API request formatting, and response processing.
     *
     * Validates that all exercise data (metadata, repositories) is correctly transformed
     * and sent to Hyperion service, and that the response is properly returned.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConsistencyCheckWorkflow() throws NetworkingException {
        // Given: Exercise with rich content to test data transformation
        programmingExercise.setProblemStatement("Create a robust sorting algorithm that handles edge cases...");
        programmingExercise.setTitle("Advanced Sorting Challenge");

        // Mock external API with request validation to ensure proper data mapping
        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), MOCK_INCONSISTENCY_RESULT,
                // Verify exercise metadata transformation
                jsonPath("$.exercise.id", programmingExercise.getId()), jsonPath("$.exercise.title", "Advanced Sorting Challenge"),
                jsonPath("$.exercise.problem_statement", "Create a robust sorting algorithm that handles edge cases..."),
                jsonPath("$.exercise.programming_language", programmingExercise.getProgrammingLanguage().name()),
                // Verify repository data extraction and formatting
                hasJsonPath("$.template_repository.files"), hasJsonPath("$.solution_repository.files"), hasJsonPath("$.test_repository.files"),
                hasJsonPath("$.template_repository.name"), hasJsonPath("$.solution_repository.name"), hasJsonPath("$.test_repository.name"));

        // When: Service processes consistency check request
        String result = hyperionService.checkConsistency(instructor, programmingExercise);

        // Then: Verify response processing and return value
        assertThat(result).as("Service must return unmodified response from Hyperion API").isEqualTo(MOCK_INCONSISTENCY_RESULT).contains("inconsistencies").isNotBlank();

        hyperionRequestMockProvider.verify();
    }

    /**
     * Tests problem statement rewriting workflow: request preparation, context injection,
     * external API call, and response handling.
     *
     * Validates that user context and rewriting options are properly included in requests
     * and that enhanced text is returned unchanged from the external service.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testProblemStatementRewriting() throws NetworkingException {
        // Given: Problem statement requiring enhancement
        String originalStatement = "Write a sorting algorithm. Handle edge cases.";

        // Mock API with request validation to verify context injection
        hyperionRequestMockProvider.mockRewriteProblemStatementSuccess(originalStatement, MOCK_REWRITTEN_STATEMENT,
                // Verify text content and context are included
                jsonPath("$.text", originalStatement), hasJsonPath("$.options.preserve_difficulty"), hasJsonPath("$.options.enhance_clarity"),
                hasJsonPath("$.context.educational_level"), hasJsonPath("$.context.target_skills"));

        // When: Service processes rewriting request
        String enhanced = hyperionService.rewriteProblemStatement(instructor, originalStatement);

        // Then: Verify response is properly returned
        assertThat(enhanced).as("Service must return enhanced text from Hyperion API unchanged").isEqualTo(MOCK_REWRITTEN_STATEMENT).contains("Enhanced problem statement")
                .contains("robust sorting algorithm").contains("edge cases").contains("comprehensive error handling").isNotEqualTo(originalStatement)
                .hasSizeGreaterThan(originalStatement.length());

        hyperionRequestMockProvider.verify();
    }

    /**
     * Tests error handling scenarios: HTTP error responses and network failures.
     *
     * Validates that external service failures are properly caught, wrapped in
     * NetworkingException with appropriate error messages, and propagated to callers.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testErrorHandling() {
        // Test 1: HTTP 503 Service Unavailable response
        hyperionRequestMockProvider.mockConsistencyCheckFailure(programmingExercise.getId(), HttpStatus.SERVICE_UNAVAILABLE);

        assertThatThrownBy(() -> hyperionService.checkConsistency(instructor, programmingExercise)).isInstanceOf(NetworkingException.class).hasMessageContaining("503")
                .satisfies(ex -> {
                    // Verify error message includes actionable information
                    assertThat(ex.getMessage()).contains("Service unavailable");
                    assertThat(ex).isInstanceOf(NetworkingException.class);
                });

        // Test 2: Network connectivity failure (IOException)
        hyperionRequestMockProvider.mockConsistencyCheckNetworkError(programmingExercise.getId());

        assertThatThrownBy(() -> hyperionService.checkConsistency(instructor, programmingExercise)).isInstanceOf(NetworkingException.class).hasMessageContaining("Network error")
                .satisfies(ex -> {
                    // Verify error message is not empty and provides context
                    assertThat(ex.getMessage()).isNotEmpty();
                });

        hyperionRequestMockProvider.verify();
    }
}
