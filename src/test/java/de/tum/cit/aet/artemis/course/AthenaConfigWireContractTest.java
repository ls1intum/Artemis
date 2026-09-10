package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins the endpoints that have to report the two Athena course switches.
 * <p>
 * {@code Course.athenaConfig} is lazy and {@code open-in-view} is disabled, so a response built from a course that
 * nobody resolved the configuration for reports both switches as false - no exception, no log line, just the AI
 * feedback features quietly missing from the client. Each endpoint here is one the webapp reads them from, and each
 * one resolves the configuration explicitly through {@code CourseAthenaConfigRepository}.
 */
class AthenaConfigWireContractTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenawire";

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(true);
        athenaConfig.setFormativeFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        course = courseRepository.save(course);

        textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(3),
                ZonedDateTime.now().plusDays(5));
    }

    private void assertBothFlagsPresent(JsonNode course) {
        assertThat(course).as("the response has to carry the course").isNotNull();
        assertThat(course.get("athenaGradingFeedbackEnabled").asBoolean())
                .as("athenaGradingFeedbackEnabled must reach the client; the assessment views gate feedback suggestions on it").isTrue();
        assertThat(course.get("athenaFormativeFeedbackEnabled").asBoolean()).as("athenaFormativeFeedbackEnabled must reach the client; the request-feedback button gates on it")
                .isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void exerciseDetailsCarryTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void assessmentDashboardExerciseCarriesTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/exercise/exercises/" + textExercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void courseManagementDetailCarriesTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/course/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId(), HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response);
    }
}
