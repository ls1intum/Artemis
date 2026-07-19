package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;
import de.tum.cit.aet.artemis.programming.service.SubmissionPolicyService;

class SubmissionPolicyResourceMutationGuardTest {

    private static final long EXERCISE_ID = 42L;

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void mutation_whenGenerationOwnsSlot_rejectsBeforeSideEffects(Mutation mutation) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        if (mutation != Mutation.ADD) {
            LockRepositoryPolicy existingPolicy = new LockRepositoryPolicy();
            existingPolicy.setActive(false);
            exercise.setSubmissionPolicy(existingPolicy);
        }
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdWithSubmissionPolicyElseThrow(EXERCISE_ID)).thenReturn(exercise);
        SubmissionPolicyService submissionPolicyService = mock(SubmissionPolicyService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        SubmissionPolicyResource resource = new SubmissionPolicyResource(repository, mock(AuthorizationCheckService.class), submissionPolicyService,
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ParticipationAuthorizationCheckService.class), versionService, mock(UserRepository.class),
                mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> mutation.invoke(resource));

        verifyNoInteractions(versionService);
        switch (mutation) {
            case ADD -> verify(submissionPolicyService, never()).addSubmissionPolicyToProgrammingExercise(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            case REMOVE -> verify(submissionPolicyService, never()).removeSubmissionPolicyFromProgrammingExercise(org.mockito.ArgumentMatchers.any());
            case TOGGLE -> {
                verify(submissionPolicyService, never()).enableSubmissionPolicy(org.mockito.ArgumentMatchers.any());
                verify(submissionPolicyService, never()).disableSubmissionPolicy(org.mockito.ArgumentMatchers.any());
            }
            case UPDATE -> verify(submissionPolicyService, never()).updateSubmissionPolicy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void addReleasesTheLeaseAfterPolicyServiceFailsSoASubsequentClaimSucceeds() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdWithSubmissionPolicyElseThrow(EXERCISE_ID)).thenReturn(exercise);
        SubmissionPolicyService submissionPolicyService = mock(SubmissionPolicyService.class);
        LockRepositoryPolicy policy = new LockRepositoryPolicy();
        IllegalStateException failure = new IllegalStateException("policy add failed");
        when(submissionPolicyService.addSubmissionPolicyToProgrammingExercise(policy, exercise)).thenThrow(failure);
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenAnswer(invocation -> {
            assertThat(leaseHeld.compareAndSet(false, true)).isTrue();
            return new ProgrammingExerciseMutationGuard.MutationLease(() -> leaseHeld.set(false));
        });
        SubmissionPolicyResource resource = new SubmissionPolicyResource(repository, mock(AuthorizationCheckService.class), submissionPolicyService,
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ParticipationAuthorizationCheckService.class), mock(ExerciseVersionService.class),
                mock(UserRepository.class), mutationGuard);

        assertThatThrownBy(() -> resource.addSubmissionPolicyToProgrammingExercise(EXERCISE_ID, policy)).isSameAs(failure);

        assertThat(leaseHeld).isFalse();
        assertThatCode(() -> mutationGuard.claimExternalMutation(EXERCISE_ID).close()).doesNotThrowAnyException();
    }

    private enum Mutation {

        ADD, REMOVE, TOGGLE, UPDATE;

        void invoke(SubmissionPolicyResource resource) throws Exception {
            LockRepositoryPolicy policy = new LockRepositoryPolicy();
            switch (this) {
                case ADD -> resource.addSubmissionPolicyToProgrammingExercise(EXERCISE_ID, policy);
                case REMOVE -> resource.removeSubmissionPolicyFromProgrammingExercise(EXERCISE_ID);
                case TOGGLE -> resource.toggleSubmissionPolicy(EXERCISE_ID, true);
                case UPDATE -> resource.updateSubmissionPolicy(EXERCISE_ID, policy);
            }
        }
    }
}
