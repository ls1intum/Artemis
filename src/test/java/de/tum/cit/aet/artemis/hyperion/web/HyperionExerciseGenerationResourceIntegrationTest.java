package de.tum.cit.aet.artemis.hyperion.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Spring MVC authorization matrix for {@link HyperionExerciseGenerationResource}: {@link HyperionExerciseGenerationResourceTest} only reflects on the {@code @EnforceAtLeast...}
 * annotations, so the security filters, route mapping and enforcement aspects never run there; they do here.
 * <p>
 * Runs in the common LocalCI + LocalVC context, where generation is enabled and only the external worker transport is mocked. Every "role passed" row hits a real, side-effect-free
 * validation branch: the fixture exercise uses
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
    private JsonMapper objectMapper;

    @Autowired
    private de.tum.cit.aet.artemis.exam.util.ExamUtilService examUtilService;

    @Autowired
    private de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository examRepository;

    @Autowired
    private de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository studentExamRepository;

    @Autowired
    private de.tum.cit.aet.artemis.exam.service.StudentExamAssignmentService assignmentService;

    @Autowired
    private de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi mutationApi;

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

    private static final String VARIANT_REQUEST = """
            {"domainText":"A library","placement":{"type":"STANDALONE"}}
            """;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void programmingVariant_cannotUseLegacyQuizPipeline() throws Exception {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExerciseRepository.save(exercise);
        request.performMvcRequest(post("/api/hyperion/exercises/" + exerciseId + "/generate-variant").contentType(MediaType.APPLICATION_JSON).content(VARIANT_REQUEST))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value("unsupportedType"));
    }

    @Test
    @WithAnonymousUser
    void programmingVariant_anonymous_isUnauthorized() throws Exception {
        request.performMvcRequest(
                post("/api/hyperion/programming-exercises/" + exerciseId + "/generation/variants").contentType(MediaType.APPLICATION_JSON).content(VARIANT_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void programmingVariant_student_isForbidden() throws Exception {
        request.performMvcRequest(
                post("/api/hyperion/programming-exercises/" + exerciseId + "/generation/variants").contentType(MediaType.APPLICATION_JSON).content(VARIANT_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void programmingVariant_foreignInstructor_isForbidden() throws Exception {
        request.performMvcRequest(
                post("/api/hyperion/programming-exercises/" + exerciseId + "/generation/variants").contentType(MediaType.APPLICATION_JSON).content(VARIANT_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void programmingVariant_editor_rejectsUnsupportedConfigurationBeforeProvisioning() throws Exception {
        request.performMvcRequest(
                post("/api/hyperion/programming-exercises/" + exerciseId + "/generation/variants").contentType(MediaType.APPLICATION_JSON).content(VARIANT_REQUEST))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value("unsupportedGenerationLanguage"));
    }

    @Test
    void mutationGuardAcceptsDatelessAndFutureDraftsButRejectsReleasedExercises() {
        var exercise = programmingExerciseRepository.findById(exerciseId).orElseThrow();
        exercise.setReleaseDate(null);
        exercise.setStartDate(null);
        programmingExerciseRepository.saveAndFlush(exercise);
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isTrue();
        exercise.setReleaseDate(java.time.ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isTrue();
        exercise.setReleaseDate(java.time.ZonedDateTime.now().minusDays(1));
        exercise.setStartDate(java.time.ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.saveAndFlush(exercise);
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isFalse();
    }

    @Test
    void examAssignmentClosesAuthoringBeforeParticipationsExist() {
        var exam = prepareExamExercise();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isTrue();
        var assigned = assignmentService.assignStudent(exam.getId(), student.getId());
        org.assertj.core.api.Assertions.assertThat(assigned.getExercises()).extracting(de.tum.cit.aet.artemis.exercise.domain.Exercise::getId).contains(exerciseId);
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isFalse();
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.findWithAllParticipationsById(exerciseId).orElseThrow().getStudentParticipations()).isEmpty();
    }

    @Test
    void activeMutationRejectsExamAssignmentWithoutPersistingAStudentExam() {
        var exam = prepareExamExercise();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        String token = mutationApi.claimExternalMutationSlot(exerciseId);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assignmentService.assignStudent(exam.getId(), student.getId()))
                    .isInstanceOf(de.tum.cit.aet.artemis.core.exception.ConflictException.class);
            org.assertj.core.api.Assertions.assertThat(studentExamRepository.findUserIdsWithStudentExamsForExam(exam.getId())).isEmpty();
        }
        finally {
            mutationApi.clearExternalMutationSlot(exerciseId, token);
        }
        org.assertj.core.api.Assertions.assertThat(assignmentService.assignStudent(exam.getId(), student.getId())).isNotNull();
    }

    @Test
    void testRunAssignmentAlsoClosesAuthoring() {
        var exam = prepareExamExercise();
        var testRun = new de.tum.cit.aet.artemis.exam.domain.StudentExam();
        testRun.setExam(exam);
        testRun.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        testRun.setTestRun(true);
        testRun.setSubmitted(false);
        testRun.setWorkingTime(3600);
        testRun.setExercises(List.of(programmingExerciseRepository.findById(exerciseId).orElseThrow()));
        assignmentService.assignTestRun(testRun);
        org.assertj.core.api.Assertions.assertThat(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId)).isFalse();
    }

    private de.tum.cit.aet.artemis.exam.domain.Exam prepareExamExercise() {
        var exercise = programmingExerciseRepository.findById(exerciseId).orElseThrow();
        var exam = examUtilService.addExamWithExerciseGroup(exercise.getCourseViaExerciseGroupOrCourseMember(), true);
        exam.setStartDate(java.time.ZonedDateTime.now().plusDays(1));
        exam.setEndDate(java.time.ZonedDateTime.now().plusDays(1).plusHours(2));
        exam.setNumberOfExercisesInExam(1);
        exam = examRepository.saveAndFlush(exam);
        exercise.setCourse(null);
        exercise.setExerciseGroup(exam.getExerciseGroups().getFirst());
        programmingExerciseRepository.saveAndFlush(exercise);
        return exam;
    }

    @Test
    @WithAnonymousUser
    void capabilitiesRequireAuthentication() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generation/capabilities", exerciseId)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void capabilitiesRejectStudents() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generation/capabilities", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void capabilitiesRejectForeignInstructors() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generation/capabilities", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void capabilitiesTellEditorsWhenTheConfigurationIsUnsupported() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/{exerciseId}/generation/capabilities", exerciseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(false)).andExpect(jsonPath("$.canCreateVariant").value(false))
                .andExpect(jsonPath("$.restriction").value("unsupportedGenerationLanguage"));
    }

    @Test
    @WithAnonymousUser
    void generationWorkers_anonymous_isUnauthorized() throws Exception {
        request.performMvcRequest(get("/api/aiworker/admin/workers")).andExpect(status().isUnauthorized());
        Mockito.verify(aiWorkers, Mockito.never()).workerStatuses();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generationWorkers_instructor_isForbidden() throws Exception {
        request.performMvcRequest(get("/api/aiworker/admin/workers")).andExpect(status().isForbidden());
        Mockito.verify(aiWorkers, Mockito.never()).workerStatuses();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void generationWorkers_admin_returnsDiagnosticSnapshot() throws Exception {
        Mockito.when(aiWorkers.workerStatuses()).thenReturn(List.of(new WorkerStatusDTO("worker-1", WorkerState.OFFLINE, null, null, null, null, false)));
        request.performMvcRequest(get("/api/aiworker/admin/workers")).andExpect(status().isOk()).andExpect(jsonPath("$[0].workerId").value("worker-1"))
                .andExpect(jsonPath("$[0].state").value("OFFLINE")).andExpect(jsonPath("$[0].lastHeartbeat").doesNotExist());
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
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void revertExerciseGeneration_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void revertExerciseGeneration_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void revertExerciseGeneration_editor_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void revertExerciseGeneration_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void revertExerciseGeneration_admin_passesAuthorizationAndReturnsNotFound() throws Exception {
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/generation/runs/adapt-job/revert", exerciseId)).andExpect(status().isNotFound());
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
