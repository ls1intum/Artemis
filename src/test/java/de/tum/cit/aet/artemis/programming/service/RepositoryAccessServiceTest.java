package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
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
        repositoryAccessService = new RepositoryAccessService(Optional.empty(), authorizationCheckService, mock(ExerciseDateService.class),
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
}
