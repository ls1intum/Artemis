package de.tum.cit.aet.artemis.atlas.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CompetencyOrchestrationResourceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "atlasorchres";

    /** Instructor not enrolled in the test course; exercises the wrong-course branch of {@code @EnforceAtLeastInstructorInExercise}. */
    private static final String OTHER_PREFIX = "atlasorchresother";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private AtlasOrchestratorProperties orchestratorProperties;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        // Only TEST_PREFIX instructor is enrolled; OTHER_PREFIX instructor has no UCR entry and will be denied.
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        featureToggleService.enableFeature(Feature.AtlasAgent);
    }

    @AfterEach
    void tearDown() {
        featureToggleService.disableFeature(Feature.AtlasAgent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void runForExercise_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void runForExercise_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void runForExercise_editor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForExercise_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForExercise_nonProgrammingWrongCourseInstructor_returnsForbidden() throws Exception {
        // The endpoint is now generic over all exercise types, so the @EnforceAtLeastInstructorInExercise
        // gate must bind to the owning course of a non-programming exercise too — an instructor of another
        // course cannot trigger orchestration on this course's text exercise.
        TextExercise textExercise = textExerciseUtilService.createSampleTextExercise(course);
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", textExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void runForExercise_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForExercise_atlasAgentFeatureDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.AtlasAgent);
        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForExamProgrammingExercise_returnsUnprocessableEntity() throws Exception {
        // Exam programming exercises are explicitly out of scope: the orchestrator's course
        // resolution would walk to the underlying course and silently mutate course-wide
        // competencies, which is never what the instructor wants.
        ProgrammingExercise examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);

        request.performMvcRequest(post("/api/atlas/orchestrator/exercises/{exerciseId}/run", examExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureReason").value("UNSUPPORTED_EXERCISE"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getDefaults_instructor_returnsDefaultsFromProperties() throws Exception {
        // @EnforceAtLeastInstructor is a global role gate (not course-scoped), so the configured
        // server-side defaults are returned verbatim. Asserting against the autowired properties
        // keeps the test robust if the configured values change.
        request.performMvcRequest(get("/api/atlas/orchestrator/defaults")).andExpect(status().isOk())
                .andExpect(jsonPath("$.debounceWindowSeconds").value(orchestratorProperties.debounceWindowSeconds()))
                .andExpect(jsonPath("$.maxDailyOrchestrations").value(orchestratorProperties.maxDailyOrchestrations()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDefaults_student_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/atlas/orchestrator/defaults")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getDefaults_atlasAgentFeatureDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.AtlasAgent);
        request.performMvcRequest(get("/api/atlas/orchestrator/defaults")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void getDefaults_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(get("/api/atlas/orchestrator/defaults")).andExpect(status().isUnauthorized());
    }
}
