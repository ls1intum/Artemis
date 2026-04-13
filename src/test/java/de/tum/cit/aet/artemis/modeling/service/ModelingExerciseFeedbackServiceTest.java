package de.tum.cit.aet.artemis.modeling.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

/**
 * Unit tests for the soft-skip logic in {@link ModelingExerciseFeedbackService#generateAutomaticFeedbackForTestExamAsync}
 * (Athena absent, empty submission, already-rated submission, happy path).
 */
@ExtendWith(MockitoExtension.class)
class ModelingExerciseFeedbackServiceTest {

    private static final long PARTICIPATION_ID = 42L;

    // A tiny but non-empty Apollon v4 model, enough for ModelingSubmission.isEmpty() to return false.
    private static final String NON_EMPTY_MODEL = "{\"version\":\"4.0.0\",\"type\":\"ClassDiagram\",\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}";

    @Mock
    private AthenaFeedbackApi athenaFeedbackApi;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private ResultService resultService;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ResultWebsocketService resultWebsocketService;

    @Mock
    private ParticipationService participationService;

    private ModelingExercise modelingExercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        modelingExercise = new ModelingExercise();
        modelingExercise.setId(1L);
        modelingExercise.setMaxPoints(10.0);

        participation = new StudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setExercise(modelingExercise);
    }

    private ModelingExerciseFeedbackService newService(Optional<AthenaFeedbackApi> api) {
        return new ModelingExerciseFeedbackService(api, submissionService, resultService, resultRepository, resultWebsocketService, participationService);
    }

    @Test
    void shouldSkipWhenAthenaApiIsNotPresent() {
        ModelingExerciseFeedbackService service = newService(Optional.empty());

        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);

        verifyNoInteractions(participationService, resultWebsocketService, resultRepository, submissionService);
    }

    @Test
    void shouldSkipWhenNoLatestSubmissionExists() throws Exception {
        StudentParticipation participationWithoutSubmissions = new StudentParticipation();
        participationWithoutSubmissions.setId(PARTICIPATION_ID);
        participationWithoutSubmissions.setExercise(modelingExercise);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithoutSubmissions);

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);

        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false));
    }

    @Test
    void shouldSkipWhenLatestSubmissionIsEmpty() throws Exception {
        ModelingSubmission emptySubmission = new ModelingSubmission();
        emptySubmission.setModel(null);
        emptySubmission.setExplanationText(null);
        emptySubmission.setSubmitted(true);
        emptySubmission.setSubmissionDate(ZonedDateTime.now());

        StudentParticipation participationWithEmpty = new StudentParticipation();
        participationWithEmpty.setId(PARTICIPATION_ID);
        participationWithEmpty.setExercise(modelingExercise);
        participationWithEmpty.addSubmission(emptySubmission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithEmpty);

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);

        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false));
    }

    @Test
    void shouldSkipWhenLatestSubmissionAlreadyHasAthenaResult() throws Exception {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(NON_EMPTY_MODEL);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());

        Result existingAthenaResult = new Result();
        existingAthenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        submission.addResult(existingAthenaResult);

        StudentParticipation participationWithAthenaResult = new StudentParticipation();
        participationWithAthenaResult.setId(PARTICIPATION_ID);
        participationWithAthenaResult.setExercise(modelingExercise);
        participationWithAthenaResult.addSubmission(submission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithAthenaResult);

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);

        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false));
    }

    @Test
    void shouldTriggerAsyncAthenaCallWhenSubmissionIsNonEmptyAndHasNoAthenaResult() throws Exception {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(NON_EMPTY_MODEL);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());

        StudentParticipation validParticipation = new StudentParticipation();
        validParticipation.setId(PARTICIPATION_ID);
        validParticipation.setExercise(modelingExercise);
        validParticipation.addSubmission(submission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(validParticipation);
        when(athenaFeedbackApi.getModelingFeedbackSuggestions(eq(modelingExercise), any(ModelingSubmission.class), eq(false))).thenReturn(List.of());
        // The downstream resultRepository.save happens inside the async task after the Athena call. We do not
        // stub it here: the async task may or may not reach the save before Mockito cleans up the mock, and an
        // unstubbed save() simply returns null (any resulting NPE is swallowed by the try/catch in the service).
        // We only need to verify that we actually entered the async path, which the two awaits below assert.

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);

        // Generation is asynchronous via CompletableFuture.runAsync(); wait for the broadcast that opens
        // generateAutomaticNonGradedFeedback and the actual Athena call to confirm we entered the success path.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(resultWebsocketService).broadcastNewResult(eq(validParticipation), any(Result.class)));
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(athenaFeedbackApi).getModelingFeedbackSuggestions(eq(modelingExercise), any(ModelingSubmission.class), eq(false)));
    }
}
