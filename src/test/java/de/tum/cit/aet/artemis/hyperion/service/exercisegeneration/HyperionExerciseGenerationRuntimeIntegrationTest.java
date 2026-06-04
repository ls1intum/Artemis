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
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * HTTP/security-layer integration test for Hyperion agentic whole-exercise generation. It drives the REAL request path — {@code HyperionExerciseGenerationResource} (with its
 * {@code @EnforceAtLeastEditorInExercise} aspect) → the real {@code ExerciseGenerationJobService} (Hazelcast single-flight slot + replayable, owner-scoped transcript) → the status
 * and cancel endpoints — over MockMvc, exactly as an instructor's browser would, against the full Spring context with Testcontainers Postgres.
 * <p>
 * This test deliberately covers ONLY the runtime behaviour that genuinely needs the Spring context and the DB-backed authorization aspect AND that can be reached without running
 * the real (Docker/GPU) agent: the role/course authorization boundaries (which reject the request before any job is claimed and never reach the orchestrator), the per-course
 * cancel
 * scope, the {@code 204}-when-nothing-ran and {@code 404}-for-an-unknown-job edges, and the clean rejection of an unknown exercise id. It does this <em>without</em> a
 * {@code @MockitoSpyBean}, so it adds no per-test Spring context fork.
 * <p>
 * <b>What is covered elsewhere (and why).</b> The previous single class spied the orchestrator/persistence/recovery to drive controlled outcomes through the full async pipeline.
 * That coverage is preserved without spies by splitting it where it naturally lives:
 * <ul>
 * <li>{@link ExerciseGenerationTaskServiceTest} drives the REAL terminal-state handling end-to-end ({@code taskService.runAsync} with hand-mocked collaborators, no context):
 * accepted
 * &rarr;persist, rejected&rarr;recover ({@code NEEDS_REVIEW}/{@code PARTIAL}/degraded/diverted), cancelled, error, persist-only-on-acceptance, the half-commit degraded mislabel,
 * and
 * the persist-failure&rarr;ERROR path.</li>
 * <li>{@link ExerciseGenerationJobServiceTest} pins the single-flight {@code 409} (as the {@code ConflictException} the resource maps to {@code 409}), the transcript cap +
 * STARTED-head
 * preservation, the owner-scoped {@code getStatus} privacy boundary, the owner-only {@code requestCancellation} (non-owner refused), and the run-once cancel hook — against a real
 * embedded Hazelcast.</li>
 * </ul>
 * <b>Coverage genuinely not reproduced here:</b> the thin mapping of a <em>live</em> slot to its HTTP status (a {@code 409} on a concurrent start, a running {@code 200}/cancel
 * while a
 * run is in flight) and the cooperative mid-run cancel both require either a controllable orchestrator outcome (which only {@code @MockitoSpyBean}/{@code @MockitoBean} could
 * provide
 * here — both forbidden outside base classes and each forks a context) or a real Docker sandbox (unavailable in normal CI). Those service-level results are proven directly in the
 * two
 * unit tests above; only their trivial HTTP-status translation for a live slot is not asserted over MockMvc.
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

        exerciseId = persistExerciseWithBuildConfig(course, "Hyperion Runtime Exercise");

        // A second course the test users are NOT members of, to prove course-scoped authorization (an editor of course A cannot generate for an exercise in course B).
        Course otherCourse = new Course();
        otherCourse.setTitle("Other Course");
        otherCourse.setStudentGroupName(TEST_PREFIX + "otherstudent");
        otherCourse.setTeachingAssistantGroupName(TEST_PREFIX + "othertutor");
        otherCourse.setEditorGroupName(TEST_PREFIX + "othereditor");
        otherCourse.setInstructorGroupName(TEST_PREFIX + "otherinstructor");
        otherCourse = courseRepository.save(otherCourse);
        otherCourseExerciseId = persistExerciseWithBuildConfig(otherCourse, "Other Exercise");
    }

    private long persistExerciseWithBuildConfig(Course course, String title) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle(title);
        exercise.setCourse(course);
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
}
