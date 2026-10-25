package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;

class ParticipationLockPolicyTest {

    private final ExerciseDateService dates = mock(ExerciseDateService.class);

    private final SubmissionPolicyRepository policies = mock(SubmissionPolicyRepository.class);

    private final SubmissionTestRepository submissions = mock(SubmissionTestRepository.class);

    @Test
    void repeatedTestExamChecksOnlyParticipationIdForEntityAndProjection() {
        var api = mock(StudentExamApi.class);
        var service = service(Optional.of(api));
        var participation = mock(ProgrammingExerciseStudentParticipation.class);
        when(participation.getId()).thenReturn(12L);
        when(api.isSubmitted(12L)).thenReturn(Optional.of(false));
        var exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(34L);
        when(exercise.isExamExercise()).thenReturn(true);
        when(exercise.isTestExamExercise()).thenReturn(true);
        assertThat(service.isLocked(participation, exercise)).isFalse();
        var projection = new GitRepositoryAccessDTO(34L, 56L, ExerciseMode.INDIVIDUAL, true, null, null, null, null, 78L, null, true);
        assertThat(service.isLocked(participation, projection)).isFalse();
        verify(api, times(2)).isSubmitted(12L);
        verify(exercise, never()).getExam();
        verify(policies, never()).findByProgrammingExerciseId(34L);
    }

    @Test
    void absentExamApiIsReportedBeforeInspectingExamFlags() {
        var service = service(Optional.empty());
        var exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(34L);
        when(exercise.isExamExercise()).thenReturn(true);
        assertThatThrownBy(() -> service.isLocked(mock(ProgrammingExerciseStudentParticipation.class), exercise)).isInstanceOf(ExamApiNotPresentException.class);
        verify(exercise, never()).isTestExamExercise();
    }

    @Test
    void activeLockPolicyUsesSubmissionCountAfterDateChecks() {
        var service = service(Optional.empty());
        var participation = mock(ProgrammingExerciseStudentParticipation.class);
        when(participation.getId()).thenReturn(12L);
        var policy = mock(LockRepositoryPolicy.class);
        when(policy.isActive()).thenReturn(true);
        when(policy.getSubmissionLimit()).thenReturn(3);
        when(policies.findByProgrammingExerciseId(34L)).thenReturn(policy);
        when(submissions.countByParticipationId(12L)).thenReturn(3L);
        var exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(34L);
        assertThat(service.isLocked(participation, exercise)).isTrue();
        var projection = new GitRepositoryAccessDTO(34L, 56L, ExerciseMode.INDIVIDUAL, true, null, null, null, null, null, null, null);
        assertThat(service.isLocked(participation, projection)).isTrue();
    }

    private ParticipationAuthorizationCheckService service(Optional<StudentExamApi> api) {
        return new ParticipationAuthorizationCheckService(null, null, null, null, dates, policies, submissions, api, null);
    }
}
