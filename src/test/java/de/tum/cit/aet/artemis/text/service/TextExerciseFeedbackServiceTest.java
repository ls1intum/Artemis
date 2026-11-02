package de.tum.cit.aet.artemis.text.service;

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
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Unit tests for the soft-skip logic in {@link TextExerciseFeedbackService#generateAutomaticFeedbackForTestExamAsync}
 * (Athena absent, empty submission, already-rated submission, happy path).
 */
@ExtendWith(MockitoExtension.class)
class TextExerciseFeedbackServiceTest {

    private static final long PARTICIPATION_ID = 42L;

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

    private TextExercise textExercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        textExercise = new TextExercise();
        textExercise.setId(1L);
        textExercise.setMaxPoints(10.0);

        participation = new StudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setExercise(textExercise);
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

    private TextExerciseFeedbackService newService(Optional<AthenaFeedbackApi> api) {
        return new TextExerciseFeedbackService(api, athenaFeedbackRequestRepository, resultWebsocketService, participationService, userRepository);
    }

    @Test
    void shouldSkipWhenAthenaApiIsNotPresent() {
        TextExerciseFeedbackService service = newService(Optional.empty());

        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, new TextSubmission());

        // Nothing should be looked up if Athena is not configured at all.
        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
    }

    @Test
    void shouldSkipWhenSubmissionIsNotATextSubmission() throws Exception {
        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, new ModelingSubmission());

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false), any());
    }

    @Test
    void shouldSkipWhenLatestSubmissionIsEmpty() throws Exception {
        TextSubmission emptySubmission = new TextSubmission();
        emptySubmission.setText("");
        emptySubmission.setSubmitted(true);
        emptySubmission.setSubmissionDate(java.time.ZonedDateTime.now());

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, emptySubmission);

        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false), any());
    }

    @Test
    void shouldSkipWhenLatestSubmissionAlreadyHasAthenaResult() throws Exception {
        TextSubmission submission = new TextSubmission();
        submission.setText("some student answer");
        submission.setSubmitted(true);
        submission.setSubmissionDate(java.time.ZonedDateTime.now());

        Result existingAthenaResult = new Result();
        existingAthenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        submission.addResult(existingAthenaResult);

        when(athenaFeedbackApi.submissionHasAthenaResult(submission)).thenReturn(true);

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, submission);

        // Already rated by Athena → do not re-trigger to avoid duplicate results and extra Athena traffic.
        verifyNoInteractions(participationService, resultWebsocketService, athenaFeedbackRequestRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false), any());
    }

    @Test
    void shouldTriggerAsyncAthenaCallWhenSubmissionIsNonEmptyAndHasNoAthenaResult() throws Exception {
        TextSubmission submission = new TextSubmission();
        submission.setText("Student wrote a meaningful answer here");
        submission.setSubmitted(true);
        submission.setSubmissionDate(java.time.ZonedDateTime.now());

        when(userRepository.getUser()).thenReturn(new User());
        // Stub the Athena call so the async path can complete without network I/O. An empty list is enough to
        // exercise the success branch end-to-end without relying on feedback content.
        when(athenaFeedbackApi.getTextFeedbackSuggestions(eq(textExercise), any(TextSubmission.class), eq(false), any())).thenReturn(List.of());

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, submission);

        // The generation runs asynchronously via CompletableFuture.runAsync(); wait for the initial broadcast which
        // happens at the very start of generateAutomaticNonGradedFeedback.
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(resultWebsocketService, org.mockito.Mockito.atLeastOnce()).broadcastNewResult(eq(participation), any(Result.class)));
        // And the Athena call must actually reach the stub.
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(athenaFeedbackApi).getTextFeedbackSuggestions(eq(textExercise), any(TextSubmission.class), eq(false), any()));
    }

    @Test
    void stillGeneratesFeedbackWhenThePendingNotificationFails() throws Exception {
        TextSubmission submission = new TextSubmission();
        submission.setText("A meaningful answer");
        when(userRepository.getUser()).thenReturn(new User());
        when(athenaFeedbackApi.getTextFeedbackSuggestions(eq(textExercise), any(TextSubmission.class), eq(false), any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("Websocket unavailable")).when(resultWebsocketService).broadcastNewResult(eq(participation), any(Result.class));

        newService(Optional.of(athenaFeedbackApi)).generateAutomaticFeedbackForTestExamAsync(participation, textExercise, submission);

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(athenaFeedbackRequestRepository).completeAthenaFeedbackRequest(any(), org.mockito.ArgumentMatchers.anyDouble(), any(), any()));
        verify(athenaFeedbackRequestRepository, never()).failAthenaFeedbackRequest(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exposesPersistedPendingResultBeforeAthenaCompletes(boolean testExam) throws Exception {
        TextSubmission submission = new TextSubmission();
        submission.setId(7L);
        submission.setText("A saved student answer");
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
        when(athenaFeedbackApi.getTextFeedbackSuggestions(eq(textExercise), eq(submission), eq(false), any())).thenAnswer(invocation -> {
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
        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        try {
            if (testExam) {
                service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise, submission);
            }
            else {
                service.handleNonGradedFeedbackRequest(participation, textExercise);
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
