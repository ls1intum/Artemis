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
 * Real-HTTP authorization matrix for {@link HyperionExerciseGenerationResource}.
 * <p>
 * {@link HyperionExerciseGenerationResourceTest} constructs the resource directly with mocks; its least-privilege test only reflects on the {@code @EnforceAtLeast...} annotations,
 * so Spring Security filters, route mapping, and the {@code @EnforceAtLeastEditorInExercise}/{@code @EnforceAtLeastEditor} AOP aspects never actually run. This class drives the
 * same endpoints through the real filter chain and MockMvc so a regression in the security configuration, route mapping, or the aspect itself would be caught here.
 * <p>
 * No collaborator is mocked (the repo's {@code SpringContextConfigurationArchitectureTest} forbids per-class {@code @MockitoBean}/{@code @MockitoSpyBean} outside the shared base
 * classes, since that would fork a fresh Spring context per test class). Instead, every "role passed" assertion below is engineered to hit a real, side-effect-free branch of the
 * genuine service instead of ever starting an agentic job: the fixture exercise uses {@link ProjectType#MAVEN_BLACKBOX}, which the real {@code LanguageGenerationProfile} rejects
 * before the resource ever reaches the sandbox/orchestration/LLM collaborators, and the status/cancel/revert endpoints naturally resolve to their real "nothing retained yet"
 * branch for an exercise that never had a run. This class therefore asserts status codes at the role boundary only (not business behaviour, which is already covered by
 * {@link HyperionExerciseGenerationResourceTest} and the mocked-LLM end-to-end tests) - but a 400/204/404 in the "success" rows below is the expected real response for an
 * authorized caller, distinctly different from the 401/403 rows, which is what proves the boundary itself.
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
        // OTHER_PREFIX must be added first: addUsers wipes the groups of every existing user, so whichever batch is added last keeps its groups. The TEST_PREFIX users need to
        // retain their group memberships to pass the @EnforceAtLeastEditorInExercise DB check.
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Restrict the course's groups to TEST_PREFIX so the OTHER_PREFIX instructor is not a member of this course and the cross-course isolation branch can be exercised.
        course.setStudentGroupName(TEST_PREFIX + "tumuser");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        course.setEditorGroupName(TEST_PREFIX + "editor");
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        courseRepository.save(course);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        // MAVEN_BLACKBOX (unverified DejaGnu grading) is a real project type the differential verifier does not support (see LanguageGenerationProfile), so an authorized caller
        // deterministically hits generateExercise's real "unsupportedGenerationLanguage" 400 branch instead of ever reaching the sandbox/orchestration/LLM collaborators.
        programmingExercise.setProjectType(ProjectType.MAVEN_BLACKBOX);
        exerciseId = programmingExerciseRepository.save(programmingExercise).getId();
    }

    private String generateExerciseRequestBody() throws Exception {
        return objectMapper.writeValueAsString(new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null));
    }

    // ---- POST programming-exercises/{exerciseId}/generate-exercise ----

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

    // ---- GET programming-exercises/{exerciseId}/generate-exercise/status ----

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
        // No run was ever started for this exercise, so the real service naturally resolves to "nothing retained" (204) once authorization passes.
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

    // ---- DELETE programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId} ----

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
        // No job named "job-1" is active for this exercise, so the real service naturally reports "no matching job" (404) once authorization passes.
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

    // ---- POST programming-exercises/{exerciseId}/generate-exercise/revert ----

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
        // No baseline was ever recorded for this exercise, so the real service naturally reports "nothing to revert" (404) once authorization passes.
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

    // ---- GET programming-exercises/generation/supported-languages (not exercise-scoped: guarded by the global least-privilege editor role only) ----

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
        // Unlike the exercise-scoped endpoints above, this endpoint is not scoped to any specific exercise/course: any authenticated user holding at least the global EDITOR role
        // is allowed, regardless of course membership - so the OTHER_PREFIX instructor (who is not a member of the fixture course) is still allowed here.
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getSupportedGenerationLanguages_admin_returnsOk() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/generation/supported-languages")).andExpect(status().isOk());
    }
}
