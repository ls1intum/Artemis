package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewVersionChangeService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;

/**
 * A running generation owns the exercise it is rewriting. Toggling second correction underneath it would race repository and metadata writes it is mid-way through, so the toggle
 * must be refused before anything is mutated — not merely reported afterwards.
 */
class ExerciseVersionMutationGuardTest {

    @Test
    void toggleSecondCorrection_whenGenerationOwnsProgrammingExercise_rejectsBeforeMutation() {
        long exerciseId = 42L;
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        when(exerciseRepository.findByIdElseThrow(exerciseId)).thenReturn(exercise);
        ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);
        when(mutationGuard.claimExternalMutation(OptionalLong.of(exerciseId)))
                .thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));

        ExerciseVersionService versionService = versionServiceWith(exerciseRepository, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> versionService.toggleSecondCorrection(exerciseId, new User()));

        verify(exerciseRepository, never()).toggleSecondCorrection(any());
    }

    /** Only the repository and the guard carry behaviour here; the remaining collaborators exist solely to satisfy the constructor. */
    private static ExerciseVersionService versionServiceWith(ExerciseRepository exerciseRepository, ProgrammingExerciseMutationGuardService mutationGuard) {
        return new ExerciseVersionService(mock(ExerciseVersionRepository.class), exerciseRepository, mutationGuard, mock(GitService.class),
                mock(ProgrammingExerciseRepository.class), mock(QuizExerciseRepository.class), Optional.empty(), Optional.empty(), Optional.empty(), mock(UserRepository.class),
                mock(ExerciseEditorSyncService.class), mock(ChannelRepository.class), mock(ExerciseReviewVersionChangeService.class), mock(ApplicationEventPublisher.class),
                new ObjectMapper(), mock(Executor.class));
    }
}
