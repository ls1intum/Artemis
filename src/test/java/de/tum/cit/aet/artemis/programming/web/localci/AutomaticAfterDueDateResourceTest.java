package de.tum.cit.aet.artemis.programming.web.localci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AutomaticAfterDueDateResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "autoafterduedate";

    private static final ZonedDateTime BASE_TIME = ZonedDateTime.parse("2050-01-01T12:00:00Z");

    /**
     * Login of an editor who exists in the DB (has EDITOR authority) but is NOT a member
     * of any course created in this test class. Used to verify exercise/exam-level 403s.
     */
    private static final String OTHER_EDITOR_LOGIN = TEST_PREFIX + "othereditor";

    private static final String BASE_URL = "/api/programming/programming-exercises/timeline/automatic-after-due-date-preview";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ProgrammingExercise courseExercise;

    private ProgrammingExercise examExercise;

    @BeforeEach
    void initTestCase() {
        // student1, tutor1, editor1, instructor1 are all members of the course that owns the exercise
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        // OTHER_EDITOR_LOGIN has EDITOR authority but is in a group that no course uses,
        // so checkHasAtLeastRoleForExerciseElseThrow will deny them for every exercise here.
        userUtilService.addEditor("other-editor-group", OTHER_EDITOR_LOGIN);

        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        courseExercise.setDueDate(BASE_TIME.plusDays(3));

        examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
    }

    @Test
    void previewAutomaticAfterDueDate_unauthenticated_returnsUnauthorized() throws Exception {
        var requestDTO = newExerciseRequest();
        request.postWithoutLocation(BASE_URL, requestDTO, HttpStatus.UNAUTHORIZED, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void previewAutomaticAfterDueDate_asStudent_returnsForbidden() throws Exception {
        var requestDTO = newExerciseRequest();
        request.postWithoutLocation(BASE_URL, requestDTO, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void previewAutomaticAfterDueDate_asTutor_returnsForbidden() throws Exception {
        var requestDTO = newExerciseRequest();
        request.postWithoutLocation(BASE_URL, requestDTO, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_asEditorOfCourse_returnsOk() throws Exception {
        var requestDTO = existingCourseExerciseRequest();
        ZonedDateTime result = request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);
        // Service may return null (no after-due-date phase) or a date — both are valid here;
        // what matters is that the request is not rejected.
        // If it is non-null it must be after the due date.
        if (result != null) {
            assertThat(result.toInstant()).isAfterOrEqualTo(courseExercise.getDueDate().toInstant());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void previewAutomaticAfterDueDate_asInstructorOfCourse_returnsOk() throws Exception {
        var requestDTO = existingCourseExerciseRequest();
        // Must not throw (i.e. response is 200 or the body is null — both are fine)
        request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);
    }

    /**
     * An editor who does NOT belong to the exercise's course must be blocked by
     * the exercise-level authorization check even though they pass the global
     * {@code @EnforceAtLeastEditor} guard.
     */
    @Test
    @WithMockUser(username = OTHER_EDITOR_LOGIN, roles = "EDITOR")
    void previewAutomaticAfterDueDate_asEditorOfOtherCourse_returnsForbidden() throws Exception {
        var requestDTO = existingCourseExerciseRequest();
        request.postWithoutLocation(BASE_URL, requestDTO, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_asEditorForExamExercise_returnsOk() throws Exception {
        Exam exam = examExercise.getExerciseGroup().getExam();
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(null, exam.getId(), null, true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, false, false);
        request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = OTHER_EDITOR_LOGIN, roles = "EDITOR")
    void previewAutomaticAfterDueDate_asEditorNotInExamCourse_returnsForbidden() throws Exception {
        Exam exam = examExercise.getExerciseGroup().getExam();
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(null, exam.getId(), null, true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, false, false);
        request.postWithoutLocation(BASE_URL, requestDTO, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_newCourseExercise_returnsDateOrNull() throws Exception {
        ZonedDateTime dueDate = BASE_TIME.plusDays(5);
        // Use hasAfterDueDateBuildPhase=true so the result is deterministic
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(null, null, dueDate, true, null, null, null, null);
        ZonedDateTime result = request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);
        assertThat(result.toInstant()).isAfterOrEqualTo(dueDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_newCourseExercise_noAfterDueDatePhase_returnsNull() throws Exception {
        ZonedDateTime dueDate = BASE_TIME.plusDays(5);
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(null, null, dueDate, false, null, null, null, null);
        ZonedDateTime result = request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);
        assertThat(result).isNull();
    }

    /**
     * The exercise already has an AFTER_DUE_DATE build phase, so the preview must return a non-null
     * date that is strictly after the (new) due date supplied in the request.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_existingExerciseWithAfterDueDatePhase_returnsNonNullDate() throws Exception {
        // Give the exercise an after-due-date build phase so the service has something to compute.
        attachAfterDueDateBuildPhase(courseExercise);

        ZonedDateTime newDueDate = BASE_TIME.plusDays(7);
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(courseExercise.getId(), null, newDueDate, null, null, null, null, null);
        ZonedDateTime result = request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isAfter(newDueDate.toInstant());
    }

    /**
     * Offset preservation: if the exercise already has {@code buildAndTestDate = dueDate + offset},
     * and the client sends a NEW due date, the returned preview date must equal
     * {@code newDueDate + offset} (within a small tolerance for timing).
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void previewAutomaticAfterDueDate_existingExerciseWithOffset_preservesOffsetForNewDueDate() throws Exception {
        attachAfterDueDateBuildPhase(courseExercise);

        // Establish a known offset: buildAndTestDate = dueDate + 2 hours
        ZonedDateTime originalDueDate = BASE_TIME.plusDays(3);
        ZonedDateTime originalBuildAndTestDate = originalDueDate.plusHours(2);
        courseExercise.setDueDate(originalDueDate);
        courseExercise.setBuildAndTestStudentSubmissionsAfterDueDate(originalBuildAndTestDate);
        programmingExerciseRepository.save(courseExercise);

        // Ask the preview for a new due date that is further in the future
        ZonedDateTime newDueDate = originalDueDate.plusDays(4);
        var requestDTO = new AutomaticAfterDueDatePreviewRequestDTO(courseExercise.getId(), null, newDueDate, null, null, null, null, null);
        ZonedDateTime result = request.postWithResponseBody(BASE_URL, requestDTO, ZonedDateTime.class, HttpStatus.OK);

        // The service should apply the same 2-hour offset to the new due date
        ZonedDateTime expectedDate = newDueDate.plusHours(2);
        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isCloseTo(expectedDate.toInstant(), within(5, ChronoUnit.SECONDS));
    }

    /** A request for a brand-new exercise (no IDs) using explicit phase flag. */
    private static AutomaticAfterDueDatePreviewRequestDTO newExerciseRequest() {
        return new AutomaticAfterDueDatePreviewRequestDTO(null, null, BASE_TIME.plusDays(2), true, null, null, null, null);
    }

    /** A request referencing an existing course exercise. */
    private AutomaticAfterDueDatePreviewRequestDTO existingCourseExerciseRequest() {
        return new AutomaticAfterDueDatePreviewRequestDTO(courseExercise.getId(), null, BASE_TIME.plusDays(2), null, null, null, null, null);
    }

    /**
     * Adds a single {@code AFTER_DUE_DATE} build phase to the exercise's build config and persists it.
     * This ensures {@code getAutomaticBuildAndTestDate} returns a non-null value when called with
     * the exercise's ID.
     */
    private void attachAfterDueDateBuildPhase(ProgrammingExercise exercise) throws JsonProcessingException {
        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, java.util.List.of("build/test-results/*.xml"));
        ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
        if (buildConfig == null) {
            buildConfig = new ProgrammingExerciseBuildConfig();
            buildConfig.setProgrammingExercise(exercise);
        }
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(java.util.List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(buildConfig);
    }
}
