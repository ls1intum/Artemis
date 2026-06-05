package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * HTTP/security-layer integration test for Hyperion agentic whole-exercise generation: drives the real request path — {@code HyperionExerciseGenerationResource} (with its
 * {@code @EnforceAtLeastEditorInExercise} aspect) → {@code ExerciseGenerationJobService} → the status and cancel endpoints — over MockMvc against the full Spring context with
 * Testcontainers Postgres. Covers only the runtime behaviour that needs the context and the DB-backed authorization aspect yet is reachable without the Docker/GPU agent: the
 * role/course authorization boundaries, the per-course cancel scope, the 204/404 status edges, rejection of an unknown exercise id, the run guard that rejects a language the
 * differential oracle cannot verify, and the global-authorized supported-languages endpoint that serves the server's single source of truth to the client — all without a
 * {@code @MockitoSpyBean}, so
 * it
 * forks no Spring context. The terminal-state and single-flight service logic is proven in {@link ExerciseGenerationTaskServiceTest} and {@link ExerciseGenerationJobServiceTest}.
 */
class HyperionExerciseGenerationRuntimeIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hypgenruntime";

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    private long exerciseId;

    private long otherCourseExerciseId;

    private long unsupportedLanguageExerciseId;

    private String basePath() {
        return "/api/hyperion/programming-exercises/" + exerciseId + "/generate-exercise";
    }

    @BeforeEach
    void setUp() {
        // Two editors so the cross-user authorization boundary (editor2 must not be able to touch editor1's job) can be exercised within the SAME course.
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 2, 1);

        Course course = new Course();
        course.setTitle("Hyperion Runtime Course");
        course.setStudentGroupName(TEST_PREFIX + "student");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        course.setEditorGroupName(TEST_PREFIX + "editor");
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        course = courseRepository.save(course);

        for (String role : List.of("student", "tutor", "editor", "instructor")) {
            var user = userUtilService.getUserByLogin(TEST_PREFIX + role + "1");
            user.getGroups().add(switch (role) {
                case "student" -> course.getStudentGroupName();
                case "tutor" -> course.getTeachingAssistantGroupName();
                case "editor" -> course.getEditorGroupName();
                default -> course.getInstructorGroupName();
            });
            userTestRepository.save(user);
        }
        // The second editor is a member of the same course's editor group, so the resource-level @EnforceAtLeastEditorInExercise lets them through — the only thing that should
        // stop
        // them touching editor1's job is per-job ownership, which is precisely what we probe.
        var editor2 = userUtilService.getUserByLogin(TEST_PREFIX + "editor2");
        editor2.getGroups().add(course.getEditorGroupName());
        userTestRepository.save(editor2);

        exerciseId = persistExerciseWithBuildConfig(course, "Hyperion Runtime Exercise", ProgrammingLanguage.JAVA);
        // Same owning course, but a language Hyperion does NOT offer for whole-exercise generation (only a best-effort, non-oracle-verifiable profile) — the run guard must reject
        // it.
        unsupportedLanguageExerciseId = persistExerciseWithBuildConfig(course, "Hyperion OCaml Exercise", ProgrammingLanguage.OCAML);

        // A second course the test users are NOT members of, to prove course-scoped authorization (an editor of course A cannot generate for an exercise in course B).
        Course otherCourse = new Course();
        otherCourse.setTitle("Other Course");
        otherCourse.setStudentGroupName(TEST_PREFIX + "otherstudent");
        otherCourse.setTeachingAssistantGroupName(TEST_PREFIX + "othertutor");
        otherCourse.setEditorGroupName(TEST_PREFIX + "othereditor");
        otherCourse.setInstructorGroupName(TEST_PREFIX + "otherinstructor");
        otherCourse = courseRepository.save(otherCourse);
        otherCourseExerciseId = persistExerciseWithBuildConfig(otherCourse, "Other Exercise", ProgrammingLanguage.JAVA);
    }

    private long persistExerciseWithBuildConfig(Course course, String title, ProgrammingLanguage language) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle(title);
        exercise.setCourse(course);
        exercise.setProgrammingLanguage(language);
        // The resource rejects an exercise without a build config (BadRequestAlertException); persist one first, then attach it so the cascade does not hit a transient instance.
        ProgrammingExerciseBuildConfig buildConfig = buildConfigRepository.save(new ProgrammingExerciseBuildConfig());
        exercise.setBuildConfig(buildConfig);
        return programmingExerciseRepository.save(exercise).getId();
    }

    // ---- Authorization: only an editor/instructor of the OWNING course may start a run ----------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void start_forbiddenForTutor() throws Exception {
        request.postWithResponseBody(basePath(), new ExerciseGenerationRequestDTO("x"), ExerciseGenerationJobStartDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void start_forbiddenForStudent() throws Exception {
        request.postWithResponseBody(basePath(), new ExerciseGenerationRequestDTO("x"), ExerciseGenerationJobStartDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void start_forbiddenForEditorOfADifferentCourse() throws Exception {
        // The editor is an editor of the test course, but NOT of the other course — course-scoped authorization must reject the cross-course request.
        request.postWithResponseBody("/api/hyperion/programming-exercises/" + otherCourseExerciseId + "/generate-exercise", new ExerciseGenerationRequestDTO("x"),
                ExerciseGenerationJobStartDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void start_unknownExerciseId_isRejectedCleanlyBeforeAnyJob() throws Exception {
        // Authorize-then-load: the @EnforceAtLeastEditorInExercise aspect runs first and cannot resolve membership for a non-existent exercise, so it forbids (403) before the
        // handler's own findByIdElseThrow (404) is reached. Either way the request is rejected cleanly with a 4xx, never a 500 — and no job slot is ever claimed.
        request.postWithResponseBody("/api/hyperion/programming-exercises/9999999/generate-exercise", new ExerciseGenerationRequestDTO("x"), ExerciseGenerationJobStartDTO.class,
                HttpStatus.FORBIDDEN);
    }

    // ---- Input validation: the free-text prompt flowing into the LLM is bounded -----------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void start_overlongPrompt_isRejectedWithBadRequest() throws Exception {
        // The prompt is @Size(max = 8000); an authorized editor of the owning course is still rejected by bean validation (400) before any job is started, capping the unbounded
        // text that would otherwise flow into the LLM (cost/abuse vector).
        String overlongPrompt = "x".repeat(8001);
        request.postWithResponseBody(basePath(), new ExerciseGenerationRequestDTO(overlongPrompt), ExerciseGenerationJobStartDTO.class, HttpStatus.BAD_REQUEST);
    }

    // ---- Status: 204 when nothing has run for the caller --------------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void status_whenNoRun_isNoContent() throws Exception {
        // 204 returns an empty body; getNullable asserts the 204 status and tolerates the empty body (a typed get would fail to deserialize "").
        ExerciseGenerationStatusDTO status = request.getNullable(basePath() + "/status", HttpStatus.NO_CONTENT, ExerciseGenerationStatusDTO.class);
        assertThat(status).isNull();
    }

    // ---- Cancel: 404 for an unknown job; course-scoped before the per-job owner check is even reached --------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void cancel_unknownJob_isNotFound() throws Exception {
        request.delete(basePath() + "/jobs/" + java.util.UUID.randomUUID(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void cancel_crossCourseJob_isForbiddenByCourseScope() throws Exception {
        // The exercise lives in the other course editor1 is not a member of; @EnforceAtLeastEditorInExercise rejects before requestCancellation is ever reached.
        request.delete("/api/hyperion/programming-exercises/" + otherCourseExerciseId + "/generate-exercise/jobs/" + java.util.UUID.randomUUID(), HttpStatus.FORBIDDEN);
    }

    // ---- Run guard: an authorized editor cannot start a run for a language the differential oracle cannot verify
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void start_unsupportedLanguage_isRejectedWithBadRequest() throws Exception {
        // OCaml is authorized (same course) but not in the generation-supported set; the run guard must reject it with 400 rather than starting a confusing, unverifiable run.
        request.postWithResponseBody("/api/hyperion/programming-exercises/" + unsupportedLanguageExerciseId + "/generate-exercise", new ExerciseGenerationRequestDTO("x"),
                ExerciseGenerationJobStartDTO.class, HttpStatus.BAD_REQUEST);
    }

    // ---- Supported-languages endpoint: serves the server's single source of truth to the client --------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void supportedLanguages_returnsTheOracleVerifiableSet() throws Exception {
        List<ProgrammingLanguage> languages = request.getList("/api/hyperion/programming-exercises/generation/supported-languages", HttpStatus.OK, ProgrammingLanguage.class);
        assertThat(languages).containsExactlyInAnyOrder(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN, ProgrammingLanguage.PYTHON, ProgrammingLanguage.JAVASCRIPT,
                ProgrammingLanguage.TYPESCRIPT, ProgrammingLanguage.GO, ProgrammingLanguage.RUST, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.C_SHARP,
                ProgrammingLanguage.DART, ProgrammingLanguage.RUBY, ProgrammingLanguage.R, ProgrammingLanguage.HASKELL, ProgrammingLanguage.SWIFT);
        // The best-effort / no-profile languages are intentionally excluded from the one-click offer.
        assertThat(languages).doesNotContain(ProgrammingLanguage.C, ProgrammingLanguage.OCAML, ProgrammingLanguage.BASH, ProgrammingLanguage.ASSEMBLER, ProgrammingLanguage.MATLAB,
                ProgrammingLanguage.VHDL, ProgrammingLanguage.EMPTY, ProgrammingLanguage.SQL, ProgrammingLanguage.POWERSHELL, ProgrammingLanguage.ADA, ProgrammingLanguage.PHP);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void supportedLanguages_forbiddenForTutor() throws Exception {
        // The global @EnforceAtLeastEditor floor: a tutor (below editor) cannot read the set.
        request.getList("/api/hyperion/programming-exercises/generation/supported-languages", HttpStatus.FORBIDDEN, ProgrammingLanguage.class);
    }
}
