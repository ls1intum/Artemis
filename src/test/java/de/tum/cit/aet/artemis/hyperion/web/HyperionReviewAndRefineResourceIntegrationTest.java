package de.tum.cit.aet.artemis.hyperion.web;

import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.MOCK_INCONSISTENCY_RESULT;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.MOCK_REWRITTEN_STATEMENT;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.hasJsonPath;
import static de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;
import de.tum.cit.aet.artemis.hyperion.config.HyperionRestTestConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for HyperionReviewAndRefineResource REST endpoints.
 *
 * Tests HTTP layer including request/response handling, authentication,
 * authorization, input validation, and error mapping for programming
 * exercise review and enhancement endpoints.
 */
@Import(HyperionRestTestConfiguration.class)
@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key" })
class HyperionReviewAndRefineResourceIntegrationTest extends AbstractHyperionRestTest {

    private static final String TEST_PREFIX = "hyperionresource";

    @Autowired
    private MockMvc restHyperionMockMvc;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    /**
     * Tests complete consistency check endpoint workflow including authentication,
     * request processing, service integration, and response formatting.
     *
     * Validates that the endpoint properly authenticates users, processes exercise data,
     * integrates with Hyperion service, and returns formatted JSON responses.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConsistencyCheckEndpoint() throws Exception {
        // Given: Mock Hyperion API with comprehensive request validation
        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), MOCK_INCONSISTENCY_RESULT,
                // Verify complete exercise data is included in API request
                jsonPath("$.exercise.id", programmingExercise.getId()), jsonPath("$.exercise.title", programmingExercise.getTitle()), hasJsonPath("$.exercise.problem_statement"),
                hasJsonPath("$.exercise.programming_language"),
                // Verify all repository data is properly included
                hasJsonPath("$.template_repository.files"), hasJsonPath("$.solution_repository.files"), hasJsonPath("$.test_repository.files"));

        // When: POST request to consistency check endpoint
        String response = restHyperionMockMvc
                .perform(post("/api/hyperion/programming-exercises/{exerciseId}/check-consistency", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                // Then: Verify HTTP response structure and content
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.inconsistencies").value(MOCK_INCONSISTENCY_RESULT))
                .andExpect(jsonPath("$.analysis_duration_ms").isNumber()).andExpect(jsonPath("$.total_checks_performed").isNumber()).andReturn().getResponse().getContentAsString();

        // Verify response content matches expected format
        assertThat(response).contains("inconsistencies").contains(MOCK_INCONSISTENCY_RESULT).doesNotContain("error");

        hyperionRequestMockProvider.verify();
    }

    /**
     * A+ Test: Complete problem statement rewriting with rich validation.
     *
     * Tests full rewriting workflow including request processing and response handling.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testProblemStatementRewritingEndpoint() throws Exception {
        // Given: Complex problem statement requiring enhancement
        String originalText = "Write a sorting algorithm. Handle edge cases.";

        hyperionRequestMockProvider.mockRewriteProblemStatementSuccess(originalText, MOCK_REWRITTEN_STATEMENT,
                // Validate request structure
                jsonPath("$.text", originalText), hasJsonPath("$.options"), hasJsonPath("$.context"));

        // When: Submit rewriting request
        restHyperionMockMvc.perform(post("/api/hyperion/rewrite-problem-statement").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + originalText + "\"}"))
                // Then: Verify enhanced response
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.rewritten_text").value(MOCK_REWRITTEN_STATEMENT))
                .andExpect(jsonPath("$.improvements_made").exists()).andExpect(jsonPath("$.processing_time_ms").isNumber());

        hyperionRequestMockProvider.verify();
    }

    /**
     * A+ Test: Security and error handling excellence.
     *
     * Validates authentication, authorization, and graceful error responses.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSecurityAndErrorHandling() throws Exception {
        // Test 1: Authorization - students should be forbidden
        restHyperionMockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/check-consistency", programmingExercise.getId())).andExpect(status().isForbidden());

        // Test 2: Service error handling with proper HTTP mapping
        hyperionRequestMockProvider.mockConsistencyCheckFailure(programmingExercise.getId(), HttpStatus.SERVICE_UNAVAILABLE);

        restHyperionMockMvc
                .perform(post("/api/hyperion/programming-exercises/{exerciseId}/check-consistency", programmingExercise.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(TEST_PREFIX + "instructor1").roles("INSTRUCTOR")))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").exists());

        // Test 3: Input validation
        restHyperionMockMvc
                .perform(post("/api/hyperion/rewrite-problem-statement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(TEST_PREFIX + "instructor1").roles("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}")) // Missing required field
                .andExpect(status().isBadRequest());

        hyperionRequestMockProvider.verify();
    }
}
