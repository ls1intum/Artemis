package de.tum.cit.aet.artemis.modeling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AthenaFeedbackRequestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

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
    private AthenaFeedbackRequestRepository athenaFeedbackRequestRepository;

    @Mock
    private ResultWebsocketService resultWebsocketService;

    @Mock
    private ParticipationService participationService;

    @Mock
    private UserTestRepository userRepository;

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
        org.mockito.Mockito.lenient().when(athenaFeedbackRequestRepository.reserveAthenaFeedbackRequest(any())).thenAnswer(invocation -> {
            Result pending = new Result();
            pending.setId(11L);
            pending.setSubmission(invocation.getArgument(0));
            pending.setCompletionDate(java.time.ZonedDateTime.now().plusMinutes(5));
            return pending;
        });
        org.mockito.Mockito.lenient().when(athenaFeedbackRequestRepository.completeAthenaFeedbackRequest(any(), org.mockito.ArgumentMatchers.anyDouble(), any(), any()))
                .thenReturn(true);

    }

    private ModelingExerciseFeedbackService newService(Optional<AthenaFeedbackApi> api) {
        return new ModelingExerciseFeedbackService(api, athenaFeedbackRequestRepository, resultWebsocketService, participationService, userRepository);
    }

    @Test
    void shouldSkipWhenAthenaApiIsNotPresent() {
        ModelingExerciseFeedbackService service = newService(Optional.empty());

        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, new ModelingSubmission());

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
    }

    @Test
    void shouldSkipWhenSubmissionIsNotAModelingSubmission() throws Exception {
        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, new TextSubmission());

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false), any());
    }

    @Test
    void shouldSkipWhenLatestSubmissionIsEmpty() throws Exception {
        ModelingSubmission emptySubmission = new ModelingSubmission();
        emptySubmission.setModel(null);
        emptySubmission.setExplanationText(null);
        emptySubmission.setSubmitted(true);
        emptySubmission.setSubmissionDate(ZonedDateTime.now());

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, emptySubmission);

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false), any());
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

        when(athenaFeedbackApi.submissionHasAthenaResult(submission)).thenReturn(true);

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, submission);

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getModelingFeedbackSuggestions(any(), any(), eq(false), any());
    }

    @Test
    void shouldTriggerAsyncAthenaCallWhenSubmissionIsNonEmptyAndHasNoAthenaResult() throws Exception {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(NON_EMPTY_MODEL);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());

        when(userRepository.getUser()).thenReturn(new User());
        when(athenaFeedbackApi.getModelingFeedbackSuggestions(eq(modelingExercise), any(ModelingSubmission.class), eq(false), any())).thenReturn(List.of());

        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, submission);

        // Generation is asynchronous via CompletableFuture.runAsync(); wait for the broadcast that opens
        // generateAutomaticNonGradedFeedback and the actual Athena call to confirm we entered the success path.
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(resultWebsocketService, org.mockito.Mockito.atLeastOnce()).broadcastNewResult(eq(participation), any(Result.class)));
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(athenaFeedbackApi).getModelingFeedbackSuggestions(eq(modelingExercise), any(ModelingSubmission.class), eq(false), any()));
    }

    @Test
    void stillGeneratesFeedbackWhenThePendingNotificationFails() throws Exception {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel("{\"elements\":[{}]}");
        when(userRepository.getUser()).thenReturn(new User());
        when(athenaFeedbackApi.getModelingFeedbackSuggestions(eq(modelingExercise), any(ModelingSubmission.class), eq(false), any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("Websocket unavailable")).when(resultWebsocketService).broadcastNewResult(eq(participation), any(Result.class));

        newService(Optional.of(athenaFeedbackApi)).generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, submission);

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(athenaFeedbackRequestRepository).completeAthenaFeedbackRequest(any(), org.mockito.ArgumentMatchers.anyDouble(), any(), any()));
        verify(athenaFeedbackRequestRepository, never()).failAthenaFeedbackRequest(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exposesPersistedPendingResultBeforeAthenaCompletes(boolean testExam) throws Exception {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setId(7L);
        submission.setModel(NON_EMPTY_MODEL);
        submission.setSubmitted(true);
        submission.setSubmissionDate(java.time.ZonedDateTime.now());
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        CountDownLatch athenaStarted = new CountDownLatch(1);
        CountDownLatch releaseAthena = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Long> pendingId = new AtomicReference<>();
        AtomicReference<java.time.ZonedDateTime> pendingDeadline = new AtomicReference<>();
        when(userRepository.getUser()).thenReturn(new User());
        if (!testExam) {
            when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        }
        when(athenaFeedbackRequestRepository.reserveAthenaFeedbackRequest(any())).thenAnswer(invocation -> {
            Result result = new Result();
            result.setSubmission(invocation.getArgument(0));
            result.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            result.setCompletionDate(java.time.ZonedDateTime.now().plusMinutes(5));
            result.setId(11L);
            return result;
        });
        when(athenaFeedbackApi.getModelingFeedbackSuggestions(eq(modelingExercise), eq(submission), eq(false), any())).thenAnswer(invocation -> {
            athenaStarted.countDown();
            if (!releaseAthena.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the Athena response");
            }
            return List.of();
        });
        doAnswer(invocation -> {
            Result result = invocation.getArgument(1);
            if (result.isSuccessful() == null) {
                pendingId.set(result.getId());
                pendingDeadline.set(result.getCompletionDate());
            }
            else {
                completed.countDown();
            }
            return null;
        }).when(resultWebsocketService).broadcastNewResult(eq(participation), any(Result.class));
        ModelingExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        try {
            if (testExam) {
                service.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise, submission);
            }
            else {
                service.handleNonGradedFeedbackRequest(participation, modelingExercise);
            }
            assertThat(athenaStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(pendingId.get()).as("Reloads need a persisted pending result id").isEqualTo(11L);
            assertThat(pendingDeadline.get()).as("Pending feedback has a retry deadline").isAfter(java.time.ZonedDateTime.now());
        }
        finally {
            releaseAthena.countDown();
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

}
