package de.tum.cit.aet.artemis.text.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
    private SubmissionService submissionService;

    @Mock
    private ResultService resultService;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ResultWebsocketService resultWebsocketService;

    @Mock
    private ParticipationService participationService;

    @Mock
    private TextBlockService textBlockService;

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
    }

    private TextExerciseFeedbackService newService(Optional<AthenaFeedbackApi> api) {
        return new TextExerciseFeedbackService(api, submissionService, resultService, resultRepository, resultWebsocketService, participationService, textBlockService);
    }

    @Test
    void shouldSkipWhenAthenaApiIsNotPresent() {
        TextExerciseFeedbackService service = newService(Optional.empty());

        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);

        // Nothing should be looked up if Athena is not configured at all.
        verifyNoInteractions(participationService, resultWebsocketService, resultRepository, submissionService);
    }

    @Test
    void shouldSkipWhenNoLatestSubmissionExists() throws Exception {
        StudentParticipation participationWithoutSubmissions = new StudentParticipation();
        participationWithoutSubmissions.setId(PARTICIPATION_ID);
        participationWithoutSubmissions.setExercise(textExercise);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithoutSubmissions);

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);

        // No submission → no websocket broadcast, no athena call, no result saved.
        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false));
    }

    @Test
    void shouldSkipWhenLatestSubmissionIsEmpty() throws Exception {
        TextSubmission emptySubmission = new TextSubmission();
        emptySubmission.setText("");
        emptySubmission.setSubmitted(true);
        emptySubmission.setSubmissionDate(java.time.ZonedDateTime.now());

        StudentParticipation participationWithEmpty = new StudentParticipation();
        participationWithEmpty.setId(PARTICIPATION_ID);
        participationWithEmpty.setExercise(textExercise);
        participationWithEmpty.addSubmission(emptySubmission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithEmpty);

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);

        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false));
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

        StudentParticipation participationWithAthenaResult = new StudentParticipation();
        participationWithAthenaResult.setId(PARTICIPATION_ID);
        participationWithAthenaResult.setExercise(textExercise);
        participationWithAthenaResult.addSubmission(submission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(participationWithAthenaResult);

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);

        // Already rated by Athena → do not re-trigger to avoid duplicate results and extra Athena traffic.
        verifyNoInteractions(resultWebsocketService, resultRepository);
        verify(athenaFeedbackApi, never()).getTextFeedbackSuggestions(any(), any(), eq(false));
    }

    @Test
    void shouldTriggerAsyncAthenaCallWhenSubmissionIsNonEmptyAndHasNoAthenaResult() throws Exception {
        TextSubmission submission = new TextSubmission();
        submission.setText("Student wrote a meaningful answer here");
        submission.setSubmitted(true);
        submission.setSubmissionDate(java.time.ZonedDateTime.now());

        StudentParticipation validParticipation = new StudentParticipation();
        validParticipation.setId(PARTICIPATION_ID);
        validParticipation.setExercise(textExercise);
        validParticipation.addSubmission(submission);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID)).thenReturn(validParticipation);
        // Stub the Athena call so the async path can complete without network I/O. An empty list is enough to
        // exercise the success branch end-to-end without relying on feedback content.
        when(athenaFeedbackApi.getTextFeedbackSuggestions(eq(textExercise), any(TextSubmission.class), eq(false))).thenReturn(List.of());
        // The downstream resultRepository.save happens inside the async task after the Athena call. We do not
        // stub it here: the async task may or may not reach the save before Mockito cleans up the mock, and an
        // unstubbed save() simply returns null (any resulting NPE is swallowed by the try/catch in the service).
        // We only need to verify that we actually entered the async path, which the two awaits below assert.

        TextExerciseFeedbackService service = newService(Optional.of(athenaFeedbackApi));
        service.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);

        // The generation runs asynchronously via CompletableFuture.runAsync(); wait for the initial broadcast which
        // happens at the very start of generateAutomaticNonGradedFeedback.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(resultWebsocketService).broadcastNewResult(eq(validParticipation), any(Result.class)));
        // And the Athena call must actually reach the stub.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(athenaFeedbackApi).getTextFeedbackSuggestions(eq(textExercise), any(TextSubmission.class), eq(false)));
    }
}
