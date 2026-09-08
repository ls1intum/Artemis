package de.tum.cit.aet.artemis.hyperion.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.localci.service.TestBuildAgentConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.WeaviateTestConfiguration;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTestBase;

/**
 * Spring MVC authorization matrix for {@link HyperionExerciseGenerationResource}: {@link HyperionExerciseGenerationResourceTest} only reflects on the {@code @EnforceAtLeast...}
 * annotations, so the security filters, route mapping and enforcement aspects never run there; they do here.
 * <p>
 * Only the external worker transport is mocked. Every "role passed" row hits a real, side-effect-free validation branch: the fixture exercise uses
 * {@link ProjectType#MAVEN_BLACKBOX}, which the real {@code LanguageGenerationProfile} rejects with a 400, and status/cancel/revert resolve to their "nothing retained yet"
 * branch. A 400/204/404 row is therefore the expected response for an authorized caller, distinct from the 401/403 rows — which is what proves the boundary.
 */
// This opt-in context shares the LocalCI fixtures, not the ordinary suite's TCP listeners or named grid instance.
@Tag("BucketLocalCILocalVC")
@ResourceLock("AbstractSpringIntegrationLocalCILocalVCTest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({ "test", "artemis", "buildagent", "core", "scheduling", "localci", "localvc" })
@ContextConfiguration(classes = TestBuildAgentConfiguration.class)
@TestPropertySource(properties = { "artemis.user-management.ldap.enabled=true", "artemis.athena.enabled=true", "artemis.apollon.enabled=false",
        "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.continuous-integration.specify-concurrent-builds=true",
        "artemis.continuous-integration.concurrent-build-size=1", "artemis.continuous-integration.asynchronous=false",
        "artemis.continuous-integration.build.images.java.default=dummy-docker-image",
        "artemis.continuous-integration.build.images.c.default=ls1tum/artemis-c-minimal-docker:1.0.0",
        "artemis.continuous-integration.build.images.c.fact=ls1tum/artemis-fact-minimal-docker:1.1.0", "artemis.continuous-integration.image-cleanup.enabled=true",
        "artemis.continuous-integration.image-cleanup.disk-space-threshold-mb=1000000000", "spring.liquibase.enabled=true", "artemis.iris.enabled=true",
        "artemis.iris.health-ttl=500", "info.contact=test@localhost", "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Hyperion_resource_authorization",
        "artemis.version-control.build-agent-use-ssh=true", "artemis.version-control.ssh-private-key-folder-path=local/hyperion-resource-authorization/ssh-keys",
        "artemis.hyperion.enabled=true", "artemis.deimos.enabled=true", "artemis.atlas.enabled=true", "artemis.atlas.atlasml.enabled=true",
        // Use separate repo paths for LocalCI/LocalVC tests to isolate from other test buckets
        "artemis.repo-clone-path=./local/hyperion-resource-authorization/repos",
        "artemis.version-control.local-vcs-repo-path=./local/hyperion-resource-authorization/local-vcs-repos", "artemis.lti.enabled=true",
        "artemis.hyperion.exercise-generation.enabled=true", "artemis.hyperion.workers.broker-url=tcp://unused:61617?sslEnabled=true&verifyHost=true",
        "artemis.hyperion.workers.user=test-core", "artemis.hyperion.workers.password=test-password", "artemis.hyperion.workers.ids=worker-1" })
class HyperionExerciseGenerationResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTestBase {

    @MockitoBean
    private SshServer sshServer;

    @DynamicPropertySource
    static void weaviateProperties(DynamicPropertyRegistry registry) {
        WeaviateTestConfiguration.registerWeaviateProperties(registry, weaviateContainer, "HyperionResourceAuthorization_");
    }

    @MockitoBean
    private GenerationWorkerRegistryService workerRegistry;

    @MockitoBean
    private GenerationWorkerClientService workerClient;

    private static final String TEST_PREFIX = "hypgenresource";

    /** Instructor outside the course's prefix-restricted groups; exercises the cross-course branch of {@code @EnforceAtLeastEditorInExercise}. */
    private static final String OTHER_PREFIX = "hypgenresourceother";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    private long exerciseId;

    @AfterEach
    void resetMocks() {
        Mockito.reset(sshServer, workerClient, workerRegistry);
    }

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

    @Test
    @WithAnonymousUser
    void recoveryRequiresAuthentication() throws Exception {
        request.performMvcRequest(get("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slot", exerciseId)).andExpect(status().isUnauthorized());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slots/token", exerciseId).param("reason", "incident"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void recoveryRejectsCourseInstructor() throws Exception {
        request.performMvcRequest(get("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slot", exerciseId)).andExpect(status().isForbidden());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slots/token", exerciseId).param("reason", "incident"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void recoveryAdminReachesValidationAndExactTokenLookup() throws Exception {
        request.performMvcRequest(get("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slot", exerciseId)).andExpect(status().isNotFound());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slots/token", exerciseId).param("reason", "incident"))
                .andExpect(status().isNotFound());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/{exerciseId}/hyperion-wedged-slots/token", exerciseId).param("reason", " "))
                .andExpect(status().isBadRequest());
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
