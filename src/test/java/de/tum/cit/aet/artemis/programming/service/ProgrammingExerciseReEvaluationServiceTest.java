package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService.MutationLease;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseReEvaluationServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final String ORIGINAL_STATEMENT = "the statement before the update";

    private static final String MAPPED_STATEMENT = "the statement that the update brings";

    private ProgrammingExerciseUpdateMappingService mappingService;

    private ProgrammingExerciseCreationUpdateService creationUpdateService;

    private ExerciseService exerciseService;

    private ParticipationTestRepository participationRepository;

    private SubmissionPolicyRepository submissionPolicyRepository;

    private SlideApi slideApi;

    private ProgrammingExerciseReEvaluationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        mappingService = mock(ProgrammingExerciseUpdateMappingService.class);
        creationUpdateService = mock(ProgrammingExerciseCreationUpdateService.class);
        exerciseService = mock(ExerciseService.class);
        participationRepository = mock(ParticipationTestRepository.class);
        submissionPolicyRepository = mock(SubmissionPolicyRepository.class);
        slideApi = mock(SlideApi.class);
        var buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        var mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);

        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setProblemStatement(ORIGINAL_STATEMENT);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenReturn(new MutationLease(() -> {
        }, false));
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(EXERCISE_ID)).thenReturn(new ProgrammingExerciseBuildConfig());
        // applying the update changes the problem statement, so that the statement of the request can be told from the one before it
        doAnswer(invocation -> {
            exercise.setProblemStatement(MAPPED_STATEMENT);
            return exercise;
        }).when(mappingService).apply(any(), any(), any(), any());

        service = new ProgrammingExerciseReEvaluationService(mock(ProgrammingExerciseTestRepository.class), buildConfigRepository, submissionPolicyRepository, mappingService,
                mutationGuard, creationUpdateService, exerciseService, Optional.empty(), Optional.of(slideApi), participationRepository, mock(ExerciseVersionService.class));
    }

    @Test
    void theProblemStatementOfTheRequestIsRefusedBeforeAnythingIsReEvaluated() {
        // only the statement that the update brings is refused, so validating the statement from before the update would let the request through
        doThrow(new BadRequestAlertException("too long", "ProgrammingExercise", "problemStatementTooLong")).when(creationUpdateService)
                .validateProblemStatementLength(MAPPED_STATEMENT);

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, mock(UpdateProgrammingExerciseDTO.class), false, Set.of(), new User()))
                .isInstanceOf(BadRequestAlertException.class);

        verify(exerciseService, never()).reEvaluateExercise(any(), anyBoolean());
        verify(creationUpdateService, never()).updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aChangeOfTheAuxiliaryRepositoriesIsRefusedBeforeAnythingIsApplied() {
        var dto = mock(UpdateProgrammingExerciseDTO.class);
        doThrow(new BadRequestAlertException("changed", "ProgrammingExercise", "auxiliaryRepositoriesChanged")).when(mappingService).validateAuxiliaryRepositoriesUnchanged(dto,
                exercise);

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, dto, false, Set.of(), new User())).isInstanceOf(BadRequestAlertException.class);

        // the stored list is what the request is compared with, and applying the request replaces it
        verify(mappingService, never()).apply(any(), any(), any(), any());
        verify(exerciseService, never()).reEvaluateExercise(any(), anyBoolean());
        verify(creationUpdateService, never()).updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theChannelNameOfTheRequestIsRefusedBeforeAnythingIsReEvaluated() {
        // the update refuses the name only when it saves the channel, which is after the results have been written
        doThrow(new BadRequestAlertException("bad name", "channel", "namePatternInvalid")).when(creationUpdateService).validateChannelNameOfUpdate(exercise);

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
        order.verify(mappingService).validateAuxiliaryRepositoriesUnchanged(dto, exercise);
        order.verify(mappingService).apply(any(), any(), any(), any());
        order.verify(mappingService).validateUpdatedExercise(any(), any(), any(), any(), anyBoolean());
        order.verify(creationUpdateService).validateProblemStatementLength(MAPPED_STATEMENT);
        order.verify(creationUpdateService).validateChannelNameOfUpdate(exercise);
        order.verify(exerciseService).reEvaluateExercise(any(), anyBoolean());
    }

    @Test
    void theEffectsOfTheUpdateRunBeforeTheStoredPolicyIsRead() {
        when(creationUpdateService.updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(exercise);
        when(submissionPolicyRepository.findByProgrammingExerciseId(anyLong())).thenThrow(new IllegalStateException("the database is away"));

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, mock(UpdateProgrammingExerciseDTO.class), false, Set.of(), new User()))
                .isInstanceOf(IllegalStateException.class);

        // a retry would take the saved due date for the original one, so these have to have happened
        InOrder order = inOrder(participationRepository, slideApi, submissionPolicyRepository);
        order.verify(participationRepository).removeIndividualDueDatesIfBeforeDueDate(eq(exercise), any());
        order.verify(slideApi).handleDueDateChange(nullable(ZonedDateTime.class), eq(exercise));
        order.verify(submissionPolicyRepository).findByProgrammingExerciseId(EXERCISE_ID);
    }
}
