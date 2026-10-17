package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Unit tests for the fail-closed course resolution of the {@link RepositoryAccessService}.
 * An exercise of a masked exam graph has no resolvable course, and every access check has to refuse instead of dereferencing null.
 */
class RepositoryAccessServiceTest {

    private AuthorizationCheckService authorizationCheckService;

    private RepositoryAccessService repositoryAccessService;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        authorizationCheckService = mock(AuthorizationCheckService.class);
        repositoryAccessService = new RepositoryAccessService(Optional.empty(), authorizationCheckService, new ExerciseDateService(null, Optional.empty(), Optional.empty()),
                mock(ParticipationAuthorizationCheckService.class));
    }

    /**
     * @return an exam exercise whose exam was masked out, so that its course cannot be resolved
     */
    private static ProgrammingExercise exerciseWithoutResolvableCourse() {
        var exercise = new ProgrammingExercise();
        exercise.setExerciseGroup(new ExerciseGroup());
        return exercise;
    }

    private static ProgrammingExercise exerciseWithCourse(Course course) {
        var exercise = new ProgrammingExercise();
        exercise.setCourse(course);
        exercise.setId(1L);
        return exercise;
    }

    @Test
    void checkAccessRepositoryElseThrow_withoutResolvableCourse_failsClosed() {
        var participation = new ProgrammingExerciseStudentParticipation();
        var exercise = exerciseWithoutResolvableCourse();

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, RepositoryActionType.READ))
                .withMessageContaining("could not be resolved");
        verifyNoInteractions(authorizationCheckService);
    }

    @Test
    void checkAccessTestOrAuxRepositoryElseThrow_withoutResolvableCourse_failsClosedForEditorAndTeachingAssistant() {
        var exercise = exerciseWithoutResolvableCourse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(true, exercise, user, "tests"));
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, exercise, user, "tests"));
        verifyNoInteractions(authorizationCheckService);
    }

    @Test
    void checkAccessTestOrAuxRepositoryElseThrow_withCourse_delegatesToTheRoleChecks() {
        var course = new Course();
        var exercise = exerciseWithCourse(course);
        when(authorizationCheckService.isAtLeastEditorInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)).thenReturn(false);

        assertThatCode(() -> repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(true, exercise, user, "tests")).doesNotThrowAnyException();
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, exercise, user, "tests"))
                .withMessageContaining("tests");
    }

    @Test
    void checkHasAccessToPlagiarismSubmission_withoutResolvableCourse_failsClosed() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exerciseWithoutResolvableCourse());

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkHasAccessToPlagiarismSubmission(participation, user, RepositoryActionType.READ));
    }

    @Test
    void checkHasAccessToPlagiarismSubmission_forWriteAction_isRefusedWithoutCourseLookup() {
        ProgrammingExerciseParticipation participation = new ProgrammingExerciseStudentParticipation();

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkHasAccessToPlagiarismSubmission(participation, user, RepositoryActionType.WRITE));
        verifyNoInteractions(authorizationCheckService);
    }

    @Test
    void checkHasAccessToOfflineIDEElseThrow_withoutResolvableCourse_failsClosedWhenTheOfflineIdeIsDisabled() {
        var exercise = exerciseWithoutResolvableCourse();
        exercise.setAllowOfflineIde(false);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> repositoryAccessService.checkHasAccessToOfflineIDEElseThrow(exercise, user))
                .withMessageContaining("could not be resolved");
    }

    @Test
    void checkHasAccessToOfflineIDEElseThrow_withEnabledOfflineIde_doesNotNeedTheCourse() {
        var exercise = exerciseWithoutResolvableCourse();
        exercise.setAllowOfflineIde(true);

        assertThatCode(() -> repositoryAccessService.checkHasAccessToOfflineIDEElseThrow(exercise, user)).doesNotThrowAnyException();
        verifyNoInteractions(authorizationCheckService);
    }

    @Test
    void checkHasAccessToOfflineIDEElseThrow_forStudentOnlyWithDisabledOfflineIde_isForbidden() {
        var course = new Course();
        var exercise = exerciseWithCourse(course);
        exercise.setAllowOfflineIde(false);
        when(authorizationCheckService.isOnlyStudentInCourse(course, user)).thenReturn(true);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> repositoryAccessService.checkHasAccessToOfflineIDEElseThrow(exercise, user));
    }

    @Test
    void checkHasAccessToForcePush_withoutResolvableCourse_failsClosedForAllowedRepository() {
        var exercise = exerciseWithoutResolvableCourse();

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkHasAccessToForcePush(exercise, user, RepositoryType.TEMPLATE.toString()));
    }

    @Test
    void checkHasAccessToForcePush_forAStudentRepositoryName_isDeniedWithoutCourseLookup() {
        var exercise = exerciseWithoutResolvableCourse();

        assertThat(repositoryAccessService.checkHasAccessToForcePush(exercise, user, "some-student-login")).isFalse();
        verifyNoInteractions(authorizationCheckService);
    }

    @Test
    void checkHasAccessToForcePush_forEditorOfATemplateRepository_isAllowed() {
        var course = new Course();
        var exercise = exerciseWithCourse(course);
        when(authorizationCheckService.isAtLeastEditorInCourse(course, user)).thenReturn(true);

        assertThat(repositoryAccessService.checkHasAccessToForcePush(exercise, user, RepositoryType.TEMPLATE.toString())).isTrue();
    }

    @Test
    void checkHasAccessToPlagiarismSubmission_withoutExercise_failsClosed() {
        var participation = new ProgrammingExerciseStudentParticipation();

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkHasAccessToPlagiarismSubmission(participation, user, RepositoryActionType.READ));
        verifyNoInteractions(authorizationCheckService);
    }

    @ParameterizedTest
    @CsvSource({ "AUTOMATIC,-1,true", "AUTOMATIC,1,true", "SEMI_AUTOMATIC,-1,false", "SEMI_AUTOMATIC,1,true", "MANUAL,-1,false", "MANUAL,1,true", "SEMI_AUTOMATIC,0,true",
            "AUTOMATIC,0,true" })
    void tutorSupportAccessIsReadOnlyAndRecheckedForEveryGitOperation(AssessmentType assessmentType, int dueDateOffset, boolean readable) {
        var course = new Course();
        course.setId(1L);
        var exercise = exerciseWithCourse(course);
        exercise.setAssessmentType(assessmentType);
        exercise.setDueDate(dueDateOffset == 0 ? null : ZonedDateTime.now().plusDays(dueDateOffset));
        var participation = new ProgrammingExerciseStudentParticipation();
        var projection = GitRepositoryAccessDTO.of(exercise);
        when(authorizationCheckService.isTeachingAssistantInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isTeachingAssistantInCourse(course.getId(), user)).thenReturn(true);

        for (var action : RepositoryActionType.values()) {
            if (readable && action == RepositoryActionType.READ) {
                assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, action)).doesNotThrowAnyException();
                assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, projection, action)).doesNotThrowAnyException();
            }
            else {
                assertThatExceptionOfType(AccessForbiddenException.class)
                        .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, action));
                assertThatExceptionOfType(AccessForbiddenException.class)
                        .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, projection, action));
            }
        }
        // Exercise repositories remain available for preparing assessments, even when student repositories close.
        var template = new TemplateProgrammingExerciseParticipation();
        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(template, user, exercise, RepositoryActionType.READ)).doesNotThrowAnyException();
        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(template, user, projection, RepositoryActionType.READ)).doesNotThrowAnyException();
    }

    @Test
    void anonymousAssessmentKeepsSnapshotAccessButNotLiveRepositoryAccess() {
        var course = new Course();
        var exercise = exerciseWithCourse(course);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        var participation = new ProgrammingExerciseStudentParticipation();
        when(authorizationCheckService.isTeachingAssistantInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)).thenReturn(true);

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, RepositoryActionType.READ));
        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryForAssessmentElseThrow(participation, user, exercise)).doesNotThrowAnyException();

        when(authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)).thenReturn(false);
        when(authorizationCheckService.isTeachingAssistantInCourse(course, user)).thenReturn(false);
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryForAssessmentElseThrow(participation, user, exercise));
    }

    @Test
    void instructorsAndParticipationOwnersKeepRepositoryAccessAfterTheDeadline() {
        var course = new Course();
        course.setId(1L);
        var exercise = exerciseWithCourse(course);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        var participation = new ProgrammingExerciseStudentParticipation();
        var projection = GitRepositoryAccessDTO.of(exercise);
        when(authorizationCheckService.isAtLeastEditorInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isAtLeastEditorInCourse(course.getId(), user)).thenReturn(true);
        for (var action : RepositoryActionType.values()) {
            assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, action)).doesNotThrowAnyException();
            assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, projection, action)).doesNotThrowAnyException();
        }
        when(authorizationCheckService.isAtLeastEditorInCourse(course, user)).thenReturn(false);
        when(authorizationCheckService.isAtLeastEditorInCourse(course.getId(), user)).thenReturn(false);
        when(authorizationCheckService.isTeachingAssistantInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isTeachingAssistantInCourse(course.getId(), user)).thenReturn(true);
        when(authorizationCheckService.isAtLeastStudentInCourse(course, user)).thenReturn(true);
        when(authorizationCheckService.isAtLeastStudentInCourse(course.getId(), user)).thenReturn(true);
        when(authorizationCheckService.isOwnerOfParticipation(participation, user)).thenReturn(true);
        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, RepositoryActionType.READ)).doesNotThrowAnyException();
        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, projection, RepositoryActionType.READ)).doesNotThrowAnyException();
    }

}
