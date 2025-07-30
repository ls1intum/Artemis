package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for HyperionReviewAndRefineResource REST endpoints.
 * Tests the complete HTTP request/response cycle including authentication, validation,
 * service integration, error handling, and response formatting.
 */
@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_hyperion_integration_test" })
class HyperionReviewAndRefineResourceIntegrationTest extends AbstractHyperionRestTest {

    private static final String TEST_PREFIX = "hyperionresourceintegration";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private HyperionRequestMockProvider hyperionRequestMockProvider;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTest() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    // ==================== Consistency Check Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_Success() throws Exception {
        // Mock successful consistency check
        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), "No issues found");

        // Perform request
        ConsistencyCheckResponseDTO response = request.postWithResponseBody("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null,
                ConsistencyCheckResponseDTO.class, HttpStatus.OK);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.hasIssues()).isFalse();
        assertThat(response.summary()).contains("No issues found");

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_ServiceUnavailable() throws Exception {
        // Mock service unavailable
        hyperionRequestMockProvider.mockConsistencyCheckFailure(HttpStatus.SERVICE_UNAVAILABLE);

        // Perform request and expect 503
        request.post("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null, HttpStatus.SERVICE_UNAVAILABLE);

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_ExerciseNotFound() throws Exception {
        // Request consistency check for non-existent exercise
        request.post("/api/hyperion/programming/exercises/99999/consistency-check", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckExerciseConsistency_Forbidden() throws Exception {
        // Students should not be able to access consistency check
        request.post("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCheckExerciseConsistency_Forbidden_Tutor() throws Exception {
        // Tutors should not be able to access consistency check (instructor-only)
        request.post("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null, HttpStatus.FORBIDDEN);
    }

    // ==================== Problem Statement Rewrite Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_Success() throws Exception {
        String originalText = "Write a sorting method.";
        String improvedText = "Write a robust sorting method that efficiently handles various edge cases including empty arrays, single elements, and duplicate values.";

        // Mock successful rewrite
        hyperionRequestMockProvider.mockProblemStatementRewriteSuccess(course.getId(), improvedText);

        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO(originalText);

        // Perform request
        ProblemStatementRewriteResponseDTO response = request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO,
                ProblemStatementRewriteResponseDTO.class, HttpStatus.OK);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.rewrittenText()).isEqualTo(improvedText);
        assertThat(response.improved()).isTrue();

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_EmptyText() throws Exception {
        // Create request DTO with empty text
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("");

        // Perform request and expect 400 Bad Request
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_ServiceUnavailable() throws Exception {
        // Mock service unavailable
        hyperionRequestMockProvider.mockRewriteProblemStatementFailure(HttpStatus.SERVICE_UNAVAILABLE);

        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("Test text");

        // Perform request and expect 503
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class,
                HttpStatus.SERVICE_UNAVAILABLE);

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRewriteProblemStatement_Forbidden() throws Exception {
        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("Test text");

        // Students should not be able to access problem statement rewrite
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRewriteProblemStatement_Forbidden_Tutor() throws Exception {
        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("Test text");

        // Tutors should not be able to access problem statement rewrite (instructor-only)
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class, HttpStatus.FORBIDDEN);
    }

    // ==================== Additional Edge Case Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_InvalidExerciseId() throws Exception {
        // Test with negative exercise ID
        request.post("/api/hyperion/programming/exercises/-1/consistency-check", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_InvalidCourseId() throws Exception {
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("Test text");

        // Test with negative course ID
        request.postWithResponseBody("/api/hyperion/programming/courses/-1/problem-statement-rewrite", requestDTO, String.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_NullText() throws Exception {
        // Create request DTO with null text
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO(null);

        // Perform request and expect 400 Bad Request
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_WhitespaceText() throws Exception {
        // Create request DTO with whitespace-only text
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("   \t\n   ");

        // Perform request and expect 400 Bad Request
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_WithIssues() throws Exception {
        // Mock consistency check with issues found
        hyperionRequestMockProvider.mockConsistencyCheckWithIssues(programmingExercise.getId());

        // Perform request
        ConsistencyCheckResponseDTO response = request.postWithResponseBody("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null,
                ConsistencyCheckResponseDTO.class, HttpStatus.OK);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.hasIssues()).isTrue();
        assertThat(response.issues()).isNotEmpty();
        assertThat(response.summary()).contains("Found");

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_NetworkTimeout() throws Exception {
        // Mock network timeout
        hyperionRequestMockProvider.mockConsistencyCheckTimeout(programmingExercise.getId());

        // Perform request and expect 503
        request.post("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null, HttpStatus.SERVICE_UNAVAILABLE);

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_NetworkTimeout() throws Exception {
        // Mock network timeout
        hyperionRequestMockProvider.mockRewriteProblemStatementTimeout(course.getId());

        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO("Test text");

        // Perform request and expect 503
        request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO, String.class,
                HttpStatus.SERVICE_UNAVAILABLE);

        hyperionRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_NoImprovement() throws Exception {
        String originalText = "Write a sorting method.";

        // Mock rewrite that doesn't improve the text
        hyperionRequestMockProvider.mockProblemStatementRewriteNoImprovement(programmingExercise.getId(), originalText);

        // Create request DTO
        ProblemStatementRewriteRequestDTO requestDTO = new ProblemStatementRewriteRequestDTO(originalText);

        // Perform request
        ProblemStatementRewriteResponseDTO response = request.postWithResponseBody("/api/hyperion/programming/courses/" + course.getId() + "/problem-statement-rewrite", requestDTO,
                ProblemStatementRewriteResponseDTO.class, HttpStatus.OK);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.rewrittenText()).isEqualTo(originalText);
        assertThat(response.improved()).isFalse();

        hyperionRequestMockProvider.verify();
    }

    // ==================== Performance and Load Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConsistencyCheck_LargeExercise() throws Exception {
        // Set up exercise with complex problem statement
        programmingExercise.setProblemStatement("Very long problem statement with detailed requirements...");

        // Mock successful consistency check for large exercise
        hyperionRequestMockProvider.mockConsistencyCheckSuccess(programmingExercise.getId(), "Analysis completed");

        // Perform request and measure response time
        long startTime = System.currentTimeMillis();
        ConsistencyCheckResponseDTO response = request.postWithResponseBody("/api/hyperion/programming/exercises/" + programmingExercise.getId() + "/consistency-check", null,
                ConsistencyCheckResponseDTO.class, HttpStatus.OK);
        long duration = System.currentTimeMillis() - startTime;

        // Verify response
        assertThat(response).isNotNull();
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds

        hyperionRequestMockProvider.verify();
    }

    // ==================== Authorization Edge Cases ====================
}
