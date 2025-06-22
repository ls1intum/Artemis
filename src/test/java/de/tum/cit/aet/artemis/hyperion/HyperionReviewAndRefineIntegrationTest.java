package de.tum.cit.aet.artemis.hyperion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestConfiguration;
import de.tum.cit.aet.artemis.hyperion.web.HyperionReviewAndRefineResource;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Comprehensive integration tests for Hyperion ReviewAndRefine functionality.
 * Tests both consistency checking and problem statement rewriting capabilities.
 * Uses in-process gRPC for reliable, fast testing without network dependencies.
 */
@SpringBootTest
@Profile(PROFILE_HYPERION)
@Import(HyperionTestConfiguration.class)
class HyperionReviewAndRefineIntegrationTest extends AbstractHyperionIntegrationTest {

    private static final String TEST_PREFIX = "hyperiontest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    // ========================================
    // CONSISTENCY CHECK TESTS
    // ========================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void checkConsistency_asInstructor_shouldSucceed() throws Exception {
        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", null, HttpStatus.OK,
                null, null, null);

        assertThat(response).isEqualTo("No inconsistencies found");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void checkConsistency_asEditor_shouldSucceed() throws Exception {
        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", null, HttpStatus.OK,
                null, null, null);

        assertThat(response).isEqualTo("No inconsistencies found");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void checkConsistency_asTutor_shouldBeForbidden() throws Exception {
        request.postWithoutResponseBody("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkConsistency_asStudent_shouldBeForbidden() throws Exception {
        request.postWithoutResponseBody("/api/hyperion/review-and-refine/exercises/" + programmingExercise.getId() + "/check-consistency", null, HttpStatus.FORBIDDEN);
    }

    // ========================================
    // PROBLEM STATEMENT REWRITING TESTS
    // ========================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rewriteProblemStatement_asInstructor_shouldSucceed() throws Exception {
        var textToRewrite = "Write a simple Java program that calculates the sum of two numbers.";
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO(textToRewrite);

        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestBody, HttpStatus.OK,
                null, null, null);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response).contains("Improved:");
        assertThat(response).contains(textToRewrite);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void rewriteProblemStatement_asTutor_shouldSucceed() throws Exception {
        var textToRewrite = "Create a function that returns the factorial of a number.";
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO(textToRewrite);

        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestBody, HttpStatus.OK,
                null, null, null);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response).contains("Improved:");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rewriteProblemStatement_asStudent_shouldBeForbidden() throws Exception {
        var textToRewrite = "Write a program to sort an array.";
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO(textToRewrite);

        request.postWithoutResponseBody("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestBody, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rewriteProblemStatement_withEmptyText_shouldHandleGracefully() throws Exception {
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO("");

        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + course.getId() + "/rewrite-problem-statement", requestBody, HttpStatus.OK,
                null, null, null);

        assertThat(response).isNotNull();
        assertThat(response).contains("Improved:");
    }
}
