package de.tum.cit.aet.artemis.atlas.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class CompetencyOrchestrationResourceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "atlasorchres";

    /** Instructor not enrolled in the test course; exercises the wrong-course branch of {@code @EnforceAtLeastInstructorInExercise}. */
    private static final String OTHER_PREFIX = "atlasorchresother";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Only TEST_PREFIX instructor is enrolled; OTHER_PREFIX instructor has no UCR entry and will be denied.
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.INSTRUCTOR);
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        featureToggleService.enableFeature(Feature.AtlasAgent);
    }

    @AfterEach
    void tearDown() {
        featureToggleService.disableFeature(Feature.AtlasAgent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForProgrammingExercise_featureEnabledChatClientFails_returnsBadGateway() throws Exception {
        // The shared test base autowires a Mockito-mocked ChatClient, so the orchestrator's
        // null-check passes but invoking chatClient.prompt() yields null and the service catches
        // the NPE, returning FAILED with LLM_ERROR (mapped to 502 by the controller).
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureReason").value("LLM_ERROR"))
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void runForProgrammingExercise_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void runForProgrammingExercise_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void runForProgrammingExercise_editor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForProgrammingExercise_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void runForProgrammingExercise_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForProgrammingExercise_atlasAgentFeatureDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.AtlasAgent);
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForExamProgrammingExercise_returnsUnprocessableEntity() throws Exception {
        // Exam programming exercises are explicitly out of scope: the orchestrator's course
        // resolution would walk to the underlying course and silently mutate course-wide
        // competencies, which is never what the instructor wants.
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Course examCourse = examExercise.getExerciseGroup().getExam().getCourse();
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, examCourse, CourseRole.INSTRUCTOR);

        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", examExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureReason").value("UNSUPPORTED_EXERCISE"));
    }
}
