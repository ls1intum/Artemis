package de.tum.cit.aet.artemis.hyperion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestConfiguration;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestReviewAndRefineService;
import de.tum.cit.aet.artemis.hyperion.web.HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.web.HyperionReviewAndRefineResource.SuggestImprovementsRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import io.grpc.Status;

@Import(HyperionTestConfiguration.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class HyperionReviewAndRefineServiceIntegrationTest extends AbstractHyperionTest {

    private static final String TEST_PREFIX = "hyperion-review-and-refine";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private HyperionTestReviewAndRefineService testReviewAndRefineService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        // Reset test service to default behavior
        testReviewAndRefineService.reset();

        // Create users with different roles
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Create course and programming exercise
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    // ========== Consistency Check Tests ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_asInstructor_success() throws Exception {
        // When & Then
        String result = request.postWithResponseBodyString("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", "", HttpStatus.OK);

        assertThat(result).isEqualTo("No inconsistencies found");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCheckExerciseConsistency_asEditor_success() throws Exception {
        // When & Then
        String result = request.postWithResponseBodyString("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", "", HttpStatus.OK);

        assertThat(result).isEqualTo("No inconsistencies found");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCheckExerciseConsistency_asTutor_forbidden() throws Exception {
        // When & Then
        request.post("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_exerciseNotFound() throws Exception {
        // When & Then - Access forbidden because the exercise doesn't exist or user has no access
        request.post("/api/hyperion/review-and-refine/exercises/999999/check-consistency", "", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckExerciseConsistency_serviceUnavailable() throws Exception {
        // Given - configure service to be unavailable
        testReviewAndRefineService.setCheckBehavior((request, responseObserver) -> {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Hyperion service is temporarily unavailable").asRuntimeException());
            return null;
        });

        // When & Then - Service unavailable results in 503 status
        request.post("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", "", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ========== Rewrite Problem Statement Endpoint Tests ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_asInstructor_success() throws Exception {
        // Given
        String originalText = "Create a sorting algorithm.";
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO(originalText);

        // When
        String result = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestDTO, HttpStatus.OK);

        // Then
        assertThat(result).isEqualTo("Enhanced: " + originalText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRewriteProblemStatement_asEditor_success() throws Exception {
        // Given
        String originalText = "Write a Java program to calculate factorial.";
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO(originalText);

        // When
        String result = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestDTO, HttpStatus.OK);

        // Then
        assertThat(result).isEqualTo("Enhanced: " + originalText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRewriteProblemStatement_asTutor_forbidden() throws Exception {
        // Given
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO("Some text");

        // When & Then
        request.post("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestDTO, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_courseNotFound() throws Exception {
        // Given
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO("Some text");

        // When & Then - Access forbidden because the course doesn't exist or user has no access
        request.post("/api/hyperion/review-and-refine/courses/999999/rewrite-problem-statement", requestDTO, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_emptyText() throws Exception {
        // Given
        String emptyText = "";
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO(emptyText);

        // When
        String result = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestDTO, HttpStatus.OK);

        // Then
        assertThat(result).isEqualTo("Enhanced: " + emptyText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRewriteProblemStatement_serviceUnavailable() throws Exception {
        // Given
        String text = "Some problem statement";
        RewriteProblemStatementRequestDTO requestDTO = new RewriteProblemStatementRequestDTO(text);

        // Configure service to be unavailable
        testReviewAndRefineService.setRewriteBehavior(request -> {
            throw Status.UNAVAILABLE.withDescription("Hyperion service is temporarily unavailable").asRuntimeException();
        });

        // When & Then - Service unavailable results in 503 status
        request.post("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestDTO, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ========== Suggest Improvements Endpoint Tests ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_asInstructor_success() throws Exception {
        // Given
        String problemStatement = "Create a Java program that sorts an array of integers using bubble sort algorithm.";
        var requestDTO = new SuggestImprovementsRequestDTO(problemStatement);

        // When - Start the suggestion process (WebSocket streaming)
        request.postWithoutLocation("/api/hyperion/review-and-refine/courses/" + course.getId() + "/suggest-improvements", requestDTO, HttpStatus.OK, null);

        // Then - Verify the endpoint succeeds
        // Note: WebSocket functionality is tested in HyperionSuggestionsWebsocketTest
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSuggestImprovements_asEditor_success() throws Exception {
        // Given
        String problemStatement = "Implement a calculator in Java.";
        var requestDTO = new SuggestImprovementsRequestDTO(problemStatement);

        // When - Start the suggestion process
        request.postWithoutLocation("/api/hyperion/review-and-refine/courses/" + course.getId() + "/suggest-improvements", requestDTO, HttpStatus.OK, null);

        // Then - Verify the endpoint succeeds
        // Note: WebSocket functionality is tested in HyperionSuggestionsWebsocketTest
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_serviceUnavailable() throws Exception {
        // Given
        String problemStatement = "Test problem statement";
        var requestDTO = new SuggestImprovementsRequestDTO(problemStatement);

        // Configure service to be unavailable during streaming
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Hyperion service is temporarily unavailable").asRuntimeException());
            return null;
        });

        // When & Then - Service unavailable results in 503 status
        request.post("/api/hyperion/review-and-refine/courses/" + course.getId() + "/suggest-improvements", requestDTO, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_courseNotFound() throws Exception {
        // Given
        String problemStatement = "Test problem statement";
        var requestDTO = new SuggestImprovementsRequestDTO(problemStatement);

        // When & Then - Access forbidden because the course doesn't exist or user has no access
        request.post("/api/hyperion/review-and-refine/courses/999999/suggest-improvements", requestDTO, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSuggestImprovements_asStudent_forbidden() throws Exception {
        // Given
        String problemStatement = "Test problem statement";
        var requestDTO = new SuggestImprovementsRequestDTO(problemStatement);

        // When & Then - Access forbidden for students
        request.post("/api/hyperion/review-and-refine/courses/" + course.getId() + "/suggest-improvements", requestDTO, HttpStatus.FORBIDDEN);
    }
}
