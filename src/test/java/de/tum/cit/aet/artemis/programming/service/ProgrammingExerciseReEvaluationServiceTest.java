package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final ZonedDateTime ORIGINAL_DUE_DATE = ZonedDateTime.parse("2030-01-01T12:00:00Z");

    private static final ZonedDateTime MAPPED_DUE_DATE = ZonedDateTime.parse("2030-01-01T13:00:00Z");

    private ProgrammingExerciseUpdateMappingService mappingService;

    private ProgrammingExerciseCreationUpdateService creationUpdateService;

    private ExerciseService exerciseService;

    private ParticipationTestRepository participationRepository;

    private SubmissionPolicyRepository submissionPolicyRepository;

    private SlideApi slideApi;

    private ExerciseVersionService exerciseVersionService;

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
        exerciseVersionService = mock(ExerciseVersionService.class);
        var buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        var mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);

        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setProblemStatement(ORIGINAL_STATEMENT);
        exercise.setDueDate(ORIGINAL_DUE_DATE);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenReturn(new MutationLease(() -> {
        }, false));
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(EXERCISE_ID)).thenReturn(new ProgrammingExerciseBuildConfig());
        // applying the update changes the problem statement and the due date, so that those of the request can be told from the ones before it
        doAnswer(invocation -> {
            exercise.setProblemStatement(MAPPED_STATEMENT);
            exercise.setDueDate(MAPPED_DUE_DATE);
            return exercise;
        }).when(mappingService).apply(any(), any(), any(), any());

        service = new ProgrammingExerciseReEvaluationService(mock(ProgrammingExerciseTestRepository.class), buildConfigRepository, submissionPolicyRepository, mappingService,
                mutationGuard, creationUpdateService, exerciseService, Optional.empty(), Optional.of(slideApi), participationRepository, exerciseVersionService);
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
        // a re-evaluation does not require the stored references of the template and solution, which the ordinary update does
        order.verify(mappingService).validateUpdatedExercise(any(), any(), any(), any(), eq(false));
        order.verify(creationUpdateService).validateProblemStatementLength(MAPPED_STATEMENT);
        order.verify(creationUpdateService).validateChannelNameOfUpdate(exercise);
        order.verify(exerciseService).reEvaluateExercise(any(), anyBoolean());
    }

    @Test
    void theEffectsAndTheVersionOfTheUpdateComeBeforeTheStoredPolicyIsRead() {
        var user = new User();
        when(creationUpdateService.updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(exercise);
        when(submissionPolicyRepository.findByProgrammingExerciseId(anyLong())).thenThrow(new IllegalStateException("the database is away"));

        assertThatThrownBy(() -> service.reEvaluateAndUpdate(EXERCISE_ID, exercise, mock(UpdateProgrammingExerciseDTO.class), false, Set.of(), user))
                .isInstanceOf(IllegalStateException.class);

        // a retry would take the saved due date for the original one and would not repeat these, so they have to have happened, and with the due date from before the update:
        // with the saved one, the cleanup of individual due dates and the slides would see no change
        assertThat(exercise.getDueDate()).isEqualTo(MAPPED_DUE_DATE);
        InOrder order = inOrder(participationRepository, slideApi, exerciseVersionService, submissionPolicyRepository);
        order.verify(participationRepository).removeIndividualDueDatesIfBeforeDueDate(exercise, ORIGINAL_DUE_DATE);
        order.verify(slideApi).handleDueDateChange(ORIGINAL_DUE_DATE, exercise);
        order.verify(exerciseVersionService).createExerciseVersion(exercise, user);
        order.verify(submissionPolicyRepository).findByProgrammingExerciseId(EXERCISE_ID);
    }
}
