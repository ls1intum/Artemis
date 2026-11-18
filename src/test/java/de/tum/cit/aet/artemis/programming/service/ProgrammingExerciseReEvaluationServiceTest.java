package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService.MutationLease;

class ProgrammingExerciseReEvaluationServiceTest {

    private static final long EXERCISE_ID = 7L;

    private ProgrammingExerciseUpdateMappingService mappingService;

    private ProgrammingExerciseCreationUpdateService creationUpdateService;

    private ExerciseService exerciseService;

    private ProgrammingExerciseReEvaluationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        mappingService = mock(ProgrammingExerciseUpdateMappingService.class);
        creationUpdateService = mock(ProgrammingExerciseCreationUpdateService.class);
        exerciseService = mock(ExerciseService.class);
        var buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        var mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);

        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setProblemStatement("a problem statement");
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenReturn(new MutationLease(() -> {
        }, false));
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(EXERCISE_ID)).thenReturn(new ProgrammingExerciseBuildConfig());

        service = new ProgrammingExerciseReEvaluationService(mock(ProgrammingExerciseRepository.class), buildConfigRepository, mock(SubmissionPolicyRepository.class),
                mappingService, mutationGuard, creationUpdateService, exerciseService, Optional.empty(), Optional.empty(), mock(ParticipationRepository.class),
                mock(ExerciseVersionService.class));
    }

    @Test
    void aProblemStatementThatIsRefusedIsRefusedBeforeAnythingIsReEvaluated() {
        doThrow(new BadRequestAlertException("too long", "ProgrammingExercise", "problemStatementTooLong")).when(creationUpdateService).validateProblemStatementLength(any());

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, mock(UpdateProgrammingExerciseDTO.class), false, Set.of(), new User()))
                .isInstanceOf(BadRequestAlertException.class);

        verify(exerciseService, never()).reEvaluateExercise(any(), anyBoolean());
        verify(creationUpdateService, never()).updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theUpdateIsValidatedBeforeTheResultsAreReEvaluated() {
        var dto = mock(UpdateProgrammingExerciseDTO.class);
        doThrow(new BadRequestAlertException("stop here", "ProgrammingExercise", "stop")).when(exerciseService).reEvaluateExercise(any(), anyBoolean());

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, dto, false, Set.of(), new User())).isInstanceOf(BadRequestAlertException.class);

        InOrder order = inOrder(mappingService, creationUpdateService, exerciseService);
        order.verify(mappingService).validateUpdatedExercise(any(), any(), any(), any(), anyBoolean());
        order.verify(creationUpdateService).validateProblemStatementLength("a problem statement");
        order.verify(exerciseService).reEvaluateExercise(any(), anyBoolean());
    }
}
