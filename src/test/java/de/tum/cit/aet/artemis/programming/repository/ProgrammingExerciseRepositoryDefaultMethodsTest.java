package de.tum.cit.aet.artemis.programming.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the logic that lives in the default methods of the programming exercise repository.
 * <p>
 * Two things here are worth pinning without a database. The validation is what an instructor runs into when they create
 * an exercise, and it guards the version control system rather than only the form: a title or short name that slips
 * through produces a project the VCS cannot name, and a short name that is too long produces repository URLs that no
 * longer fit the column they are stored in. The lookup from a participation picks a different query per participation
 * type on purpose, so a wrong branch would silently run the slow query or return the exercise of nobody.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseRepositoryDefaultMethodsTest {

    /**
     * The repository itself is under test, so the default methods run for real and only the queries are stubbed.
     */
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private ProgrammingExerciseTestRepository repository;

    private Course course;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setShortName("course1");
        exercise = new ProgrammingExercise();
        exercise.setId(7L);
        exercise.setTitle("Sorting Algorithms");
        exercise.setShortName("sorting");
        lenient().when(repository.countByTitleAndCourse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        lenient().when(repository.countByTitleAndExerciseGroupExamCourse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        lenient().when(repository.countByShortNameAndCourse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        lenient().when(repository.countByShortNameAndExerciseGroupExamCourse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
    }

    // --- validating what an instructor entered ---------------------------------------------------------------------

    @Test
    void anExerciseWithAUsableTitleAndShortNameIsAccepted() {
        assertThatCode(() -> repository.validateCourseSettings(exercise, course)).doesNotThrowAnyException();
    }

    @Test
    void anExerciseWithoutATitleIsRejected() {
        exercise.setTitle(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateTitle(exercise, course));
    }

    @Test
    void anExerciseWithATitleOfTwoCharactersIsRejected() {
        exercise.setTitle("AB");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateTitle(exercise, course));
    }

    @Test
    void aTitleWithCharactersTheVersionControlSystemCannotNameIsRejected() {
        exercise.setTitle("Sorting/Algorithms");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateTitle(exercise, course));
    }

    @Test
    void aTitleAlreadyUsedByAnotherExerciseOfTheCourseIsRejected() {
        // The project in the version control system is named after the title, so two exercises cannot share one.
        when(repository.countByTitleAndCourse("Sorting Algorithms", course)).thenReturn(1L);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateTitle(exercise, course));
    }

    @Test
    void aTitleAlreadyUsedByAnExamExerciseOfTheCourseIsRejected() {
        // An exam exercise lives in the same version control project space, so its title collides just as much.
        when(repository.countByTitleAndExerciseGroupExamCourse("Sorting Algorithms", course)).thenReturn(1L);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateTitle(exercise, course));
    }

    @Test
    void anExerciseWithoutAShortNameIsRejected() {
        exercise.setShortName(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course));
    }

    @Test
    void anExerciseInACourseWithoutAShortNameIsRejected() {
        course.setShortName(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course));
    }

    @Test
    void aShortNameThatDoesNotStartWithALetterIsRejected() {
        // The short name becomes part of the project key, which has to be a usable identifier.
        exercise.setShortName("1sorting");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course));
    }

    @Test
    void aShortNameThatIsTooLongIsRejected() {
        // The student repository URLs are built from it, and beyond this length they no longer fit the column they are stored in.
        exercise.setShortName("s".repeat(60));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course))
                .withMessageContaining("must not exceed");
    }

    @Test
    void aShortNameAlreadyUsedInTheCourseIsRejected() {
        when(repository.countByShortNameAndCourse("sorting", course)).thenReturn(1L);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course));
    }

    @Test
    void aShortNameAlreadyUsedByAnExamExerciseOfTheCourseIsRejected() {
        when(repository.countByShortNameAndExerciseGroupExamCourse("sorting", course)).thenReturn(1L);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> repository.validateCourseAndExerciseShortName(exercise, course));
    }

    // --- finding the exercise of a participation -------------------------------------------------------------------

    @Test
    void aParticipationThatAlreadyCarriesItsExerciseIsNotLookedUpAgain() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        participation.setProgrammingExercise(exercise);

        assertThat(repository.getProgrammingExerciseFromParticipation(participation)).isSameAs(exercise);

        verify(repository, org.mockito.Mockito.never()).findByStudentParticipationId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aTemplateParticipationIsLookedUpByItsOwnQuery() {
        // Each participation type has its own query on purpose; the generic one would be far slower.
        var participation = new TemplateProgrammingExerciseParticipation();
        participation.setId(20L);
        when(repository.findByTemplateParticipationId(20L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseFromParticipation(participation)).isSameAs(exercise);
        assertThat(participation.getProgrammingExercise()).as("the exercise is attached so the next call needs no query").isSameAs(exercise);
    }

    @Test
    void aSolutionParticipationIsLookedUpByItsOwnQuery() {
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setId(30L);
        when(repository.findBySolutionParticipationId(30L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseFromParticipation(participation)).isSameAs(exercise);
    }

    @Test
    void aStudentParticipationIsLookedUpByItsOwnQuery() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        when(repository.findByStudentParticipationId(10L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseFromParticipation(participation)).isSameAs(exercise);
    }

    @Test
    void aParticipationWhoseExerciseIsGoneYieldsNothing() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        when(repository.findByStudentParticipationId(10L)).thenReturn(Optional.empty());

        assertThat(repository.getProgrammingExerciseFromParticipation(participation)).isNull();
    }

    @Test
    void aParticipationWhoseExerciseIsGoneIsReportedWhenTheCallerCannotHandleNull() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        when(repository.findByStudentParticipationId(10L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> repository.getProgrammingExerciseFromParticipationElseThrow(participation));
    }

    @Test
    void theBuildConfigVariantUsesTheQueriesThatLoadIt() {
        // The build config is needed to run a build, and loading it separately would cost a query per participation.
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        when(repository.findByStudentParticipationIdWithBuildConfig(10L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseWithBuildConfigFromParticipation(participation)).isSameAs(exercise);
    }

    @Test
    void theBuildConfigVariantOfATemplateParticipationUsesItsOwnQuery() {
        var participation = new TemplateProgrammingExerciseParticipation();
        participation.setId(20L);
        when(repository.findByTemplateParticipationIdWithBuildConfig(20L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseWithBuildConfigFromParticipation(participation)).isSameAs(exercise);
    }

    @Test
    void theBuildConfigVariantOfASolutionParticipationUsesItsOwnQuery() {
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setId(30L);
        when(repository.findBySolutionParticipationIdWithBuildConfig(30L)).thenReturn(Optional.of(exercise));

        assertThat(repository.getProgrammingExerciseWithBuildConfigFromParticipation(participation)).isSameAs(exercise);
    }

    // --- looking an exercise up by its project key -----------------------------------------------------------------

    @Test
    void aProjectKeyThatNoExerciseUsesIsReported() {
        // The project key comes from a git request, so it can name a project that no longer exists.
        when(repository.findAllByProjectKey("ABC")).thenReturn(List.of());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> repository.findOneByProjectKeyOrThrow("ABC", false, false));
    }

    @Test
    void aProjectKeyUsedByTwoExercisesIsReportedRatherThanPickingOne() {
        // Picking one would attribute a push to whichever exercise happened to come first.
        when(repository.findAllByProjectKey("ABC")).thenReturn(List.of(exercise, new ProgrammingExercise()));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> repository.findOneByProjectKeyOrThrow("ABC", false, false));
    }

    @Test
    void lookingUpByProjectKeyLoadsOnlyWhatTheCallerAskedFor() {
        // Each of these loads a different set of associations; loading them all would make every git request pay for it.
        when(repository.findAllByProjectKey("ABC")).thenReturn(List.of(exercise));
        when(repository.findWithSubmissionPolicyByProjectKey("ABC")).thenReturn(List.of(exercise));
        when(repository.findWithBuildConfigByProjectKey("ABC")).thenReturn(List.of(exercise));
        when(repository.findWithSubmissionPolicyAndBuildConfigByProjectKey("ABC")).thenReturn(List.of(exercise));

        assertThat(repository.findOneByProjectKeyOrThrow("ABC", false, false)).isSameAs(exercise);
        assertThat(repository.findOneByProjectKeyOrThrow("ABC", true, false)).isSameAs(exercise);
        assertThat(repository.findOneByProjectKeyOrThrow("ABC", false, true)).isSameAs(exercise);
        assertThat(repository.findOneByProjectKeyOrThrow("ABC", true, true)).isSameAs(exercise);

        verify(repository).findAllByProjectKey("ABC");
        verify(repository).findWithSubmissionPolicyByProjectKey("ABC");
        verify(repository).findWithBuildConfigByProjectKey("ABC");
        verify(repository).findWithSubmissionPolicyAndBuildConfigByProjectKey("ABC");
    }

    @Test
    void theTwoArgumentLookupByProjectKeyDoesNotLoadTheBuildConfig() {
        when(repository.findWithSubmissionPolicyByProjectKey("ABC")).thenReturn(List.of(exercise));

        assertThat(repository.findOneByProjectKeyOrThrow("ABC", true)).isSameAs(exercise);
    }

}
