package de.tum.cit.aet.artemis.deimos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Authorization and validation tests for the two Deimos batch endpoints.
 * <p>
 * Deimos triggers an expensive, instructor-only LLM batch over student code, so the access rules and the window
 * validation are the parts that most need automated cover. Every request here uses a window with no candidate
 * participations, so an accepted run has nothing to analyse and never reaches the LLM. The Deimos executor is
 * synchronous under the test profile, so the batch completes before the response is asserted.
 */
class DeimosResourceIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "deimosres";

    /** Instructor outside the course's prefix-restricted group; exercises the wrong-course branch of the security annotations. */
    private static final String OTHER_PREFIX = "deimosresother";

    private static final String COURSE_ENDPOINT = "/api/deimos/courses/{courseId}/analysis-runs";

    private static final String EXERCISE_ENDPOINT = "/api/deimos/programming-exercises/{exerciseId}/analysis-runs";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        // OTHER_PREFIX must be added first: addUsers wipes the groups of every existing user, so whichever batch is
        // added last keeps its groups. The TEST_PREFIX instructor needs to retain its group membership.
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setStudentGroupName(TEST_PREFIX + "tumuser");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        course.setEditorGroupName(TEST_PREFIX + "editor");
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        courseRepository.save(course);
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        featureToggleService.enableFeature(Feature.Deimos);
    }

    @AfterEach
    void tearDown() {
        featureToggleService.disableFeature(Feature.Deimos);
    }

    /**
     * Builds a request body for a window that contains no submissions, so an accepted run analyses nothing.
     *
     * @param from start of the window
     * @param to   end of the window
     * @return the JSON body
     */
    private static String body(ZonedDateTime from, ZonedDateTime to) {
        return """
                {"from": "%s", "to": "%s"}""".formatted(from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static String emptyWindowBody() {
        ZonedDateTime from = ZonedDateTime.now().minusDays(3);
        return body(from, from.plusDays(1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_instructor_returnsAccepted() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNotEmpty()).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerExerciseBatch_instructor_returnsAccepted() throws Exception {
        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerCourseBatch_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void triggerCourseBatch_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void triggerCourseBatch_editor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void triggerCourseBatch_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerExerciseBatch_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void triggerExerciseBatch_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerExerciseBatch_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void triggerExerciseBatch_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_featureToggleDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.Deimos);

        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerExerciseBatch_featureToggleDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.Deimos);

        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(emptyWindowBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_fromAfterTo_returnsBadRequest() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();

        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(body(now, now.minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_windowTooLarge_returnsBadRequest() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();

        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(body(now.minusDays(32), now)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerExerciseBatch_windowTooLarge_returnsBadRequest() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();

        request.performMvcRequest(post(EXERCISE_ENDPOINT, programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON).content(body(now.minusDays(32), now)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerCourseBatch_exactlyMaximumWindow_returnsAccepted() throws Exception {
        // The server permits exactly 31 days; this pins the boundary the client validation has to match.
        ZonedDateTime to = ZonedDateTime.now().minusDays(60);

        request.performMvcRequest(post(COURSE_ENDPOINT, course.getId()).contentType(MediaType.APPLICATION_JSON).content(body(to.minusDays(31), to)))
                .andExpect(status().isAccepted());
    }
}
