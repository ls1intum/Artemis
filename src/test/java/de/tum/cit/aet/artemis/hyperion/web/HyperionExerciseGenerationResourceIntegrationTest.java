package de.tum.cit.aet.artemis.hyperion.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Real-HTTP authorization matrix for {@link HyperionExerciseGenerationResource}: {@link HyperionExerciseGenerationResourceTest} only reflects on the {@code @EnforceAtLeast...}
 * annotations, so the security filters, route mapping and enforcement aspects never run there; they do here.
 * <p>
 * No collaborator is mocked, so every "role passed" row is engineered to hit a real, side-effect-free branch rather than start an agentic job: the fixture exercise uses
 * {@link ProjectType#MAVEN_BLACKBOX}, which the real {@code LanguageGenerationProfile} rejects with a 400, and status/cancel/revert resolve to their "nothing retained yet"
 * branch. A 400/204/404 row is therefore the expected response for an authorized caller, distinct from the 401/403 rows — which is what proves the boundary.
 */
class HyperionExerciseGenerationResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hypgenresource";

    /** Instructor outside the course's prefix-restricted groups; exercises the cross-course branch of {@code @EnforceAtLeastEditorInExercise}. */
    private static final String OTHER_PREFIX = "hypgenresourceother";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    private long exerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        userUtilService.addStudentToCourse(TEST_PREFIX + "student1", course);
        userUtilService.addTeachingAssistantToCourse(TEST_PREFIX + "tutor1", course);
        userUtilService.addEditorToCourse(TEST_PREFIX + "editor1", course);
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor1", course);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        // MAVEN_BLACKBOX (unverified DejaGnu grading) is a real project type LanguageGenerationProfile rejects, so an authorized caller deterministically hits generateExercise's
        // "unsupportedGenerationLanguage" 400 branch instead of reaching the sandbox/orchestration/LLM collaborators.
        programmingExercise.setProjectType(ProjectType.MAVEN_BLACKBOX);
        exerciseId = programmingExerciseRepository.save(programmingExercise).getId();
    }

    private String generateExerciseRequestBody() throws Exception {
        return objectMapper.writeValueAsString(new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null));
    }

    @Test
    @WithAnonymousUser
    void generateExercise_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generateExercise_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void generateExercise_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateExercise_editor_passesAuthorizationAndReachesRealValidation() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generateExercise_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void generateExercise_admin_passesAuthorizationAndReachesRealValidation() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise", exerciseId).contentType(MediaType.APPLICATION_JSON)
                .content(generateExerciseRequestBody())).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void getExerciseGenerationStatus_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExerciseGenerationStatus_student_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExerciseGenerationStatus_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getExerciseGenerationStatus_editor_passesAuthorizationAndReturnsNoContent() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExerciseGenerationStatus_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getExerciseGenerationStatus_admin_passesAuthorizationAndReturnsNoContent() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status", exerciseId)).andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void cancelExerciseGeneration_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void cancelExerciseGeneration_student_returnsForbidden() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cancelExerciseGeneration_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void cancelExerciseGeneration_editor_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cancelExerciseGeneration_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelExerciseGeneration_admin_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}", exerciseId, "job-1")).andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void revertExerciseGeneration_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void revertExerciseGeneration_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void revertExerciseGeneration_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void revertExerciseGeneration_editor_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void revertExerciseGeneration_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void revertExerciseGeneration_admin_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generate-exercise/revert", exerciseId)).andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void getSupportedGenerationLanguages_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSupportedGenerationLanguages_student_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSupportedGenerationLanguages_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getSupportedGenerationLanguages_editor_returnsOk() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSupportedGenerationLanguages_instructorOfUnrelatedCourse_returnsOk() throws Exception {
        // This endpoint is not exercise-scoped: the global EDITOR role suffices regardless of course membership, so a non-member instructor is allowed.
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getSupportedGenerationLanguages_admin_returnsOk() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isOk());
    }
}
