package de.tum.cit.aet.artemis.exercise.service;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@ExtendWith(MockitoExtension.class)
class SubmissionAssessmentEligibilityServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @InjectMocks
    private SubmissionAssessmentEligibilityService service;

    private final User tutor = new User();

    private final TextExercise exercise = new TextExercise();

    private final StudentParticipation participation = new StudentParticipation();

    private final TextSubmission submission = new TextSubmission();

    @BeforeEach
    void setup() {
        tutor.setId(1L);
        when(userRepository.getUserWithAuthorities()).thenReturn(tutor);
        exercise.setDueDate(now().minusHours(1));
        participation.setExercise(exercise);
        submission.setParticipation(participation);
        submission.setSubmitted(true);
    }

    @Test
    void reusesTheSuppliedUserAcrossCorrectionRounds() {
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
        assertThat(service.canStartAssessment(submission, exercise, 0, tutor)).isTrue();
        assertThat(service.canStartAssessment(submission, exercise, 1, tutor)).isFalse();
        verify(userRepository, times(1)).getUserWithAuthorities();
    }

    @Test
    void instructorsCanAssessSubmittedWorkBeforeItsIndividualDeadline() {
        when(authCheckService.isAtLeastInstructorForExercise(exercise, tutor)).thenReturn(true);
        participation.setIndividualDueDate(now().plusDays(1));
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
        submission.setSubmitted(false);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
    }

    @Test
    void requiresSubmittedWorkAndTheEffectiveDeadline() {
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
        submission.setSubmitted(false);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
        submission.setSubmitted(null);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
        submission.setSubmitted(true);
        participation.setIndividualDueDate(now().plusHours(1));
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
        participation.setIndividualDueDate(now().minusHours(1));
        exercise.setDueDate(now().plusHours(1));
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
        participation.setIndividualDueDate(null);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
    }

    @Test
    void requiresACompletedPreviousCorrectionByAnotherAssessor() {
        assertThat(service.canStartAssessment(submission, exercise, 1)).isFalse();
        var result = new Result().assessmentType(AssessmentType.MANUAL).completionDate(now());
        result.setAssessor(tutor);
        result.setCorrectionRound(0);
        submission.setResults(Set.of(result));
        assertThat(service.canStartAssessment(submission, exercise, 1)).isFalse();
        User otherTutor = new User();
        otherTutor.setId(2L);
        result.setAssessor(otherTutor);
        assertThat(service.canStartAssessment(submission, exercise, 1)).isTrue();
        result.setCompletionDate(null);
        assertThat(service.canStartAssessment(submission, exercise, 1)).isFalse();
    }

    @Test
    void restrictsTeamsToTheirTutorButAllowsInstructors() {
        exercise.setMode(ExerciseMode.TEAM);
        participation.setParticipant(new Team());
        assertThat(service.canStartAssessment(submission, exercise, 0)).isFalse();
        participation.getTeam().orElseThrow().setOwner(tutor);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
        participation.getTeam().orElseThrow().setOwner(null);
        when(authCheckService.isAtLeastInstructorForExercise(exercise, tutor)).thenReturn(true);
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
    }

    @Test
    void permitsInstructorTestRunsBeforeTheDeadlineWithoutASubmission() {
        participation.setTestRun(true);
        submission.setSubmitted(false);
        exercise.setDueDate(now().plusHours(1));
        assertThat(service.canStartAssessment(submission, exercise, 0)).isTrue();
    }

    @Test
    void waitsForFinalProgrammingTestsUnlessTestRun() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setDueDate(now().minusHours(2));
        participation.setExercise(programmingExercise);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(now().plusHours(1));
        assertThat(service.canStartAssessment(submission, programmingExercise, 0)).isFalse();
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> service.checkCanStartAssessmentElseThrow(submission, 0));
        participation.setTestRun(true);
        assertThat(service.canStartAssessment(submission, programmingExercise, 0)).isTrue();
        service.checkCanStartAssessmentElseThrow(submission, 0);
        participation.setTestRun(false);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(now().minusHours(1));
        assertThat(service.canStartAssessment(submission, programmingExercise, 0)).isTrue();
        service.checkCanStartAssessmentElseThrow(submission, 0);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        assertThat(service.canStartAssessment(submission, programmingExercise, 0)).isTrue();
    }

    @Test
    void rejectsIneligibleNewLocks() {
        submission.setSubmitted(false);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> service.checkCanStartAssessmentElseThrow(submission, 0));
    }
}
