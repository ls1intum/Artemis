package de.tum.cit.aet.artemis.exercise.web;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;

class ExerciseResourceMutationGuardTest {

    @Test
    void toggleSecondCorrection_whenGenerationOwnsProgrammingExercise_rejectsBeforeMutation() {
        long exerciseId = 42L;
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        when(exerciseRepository.findByIdElseThrow(exerciseId)).thenReturn(exercise);
        ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ExerciseResource resource = new ExerciseResource(mock(ExerciseService.class), mock(ExerciseDeletionService.class), mock(ParticipationService.class),
                mock(UserRepository.class), Optional.empty(), mock(AuthorizationCheckService.class), mock(TutorParticipationService.class),
                mock(ProgrammingExerciseRepository.class), mock(GradingCriterionRepository.class), exerciseRepository, mock(QuizBatchService.class),
                mock(ParticipationRepository.class), mock(ExerciseVersionService.class), Optional.empty(), Optional.empty(), mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.toggleSecondCorrectionEnabled(exerciseId));

        verify(exerciseRepository, never()).toggleSecondCorrection(exercise);
    }
}
