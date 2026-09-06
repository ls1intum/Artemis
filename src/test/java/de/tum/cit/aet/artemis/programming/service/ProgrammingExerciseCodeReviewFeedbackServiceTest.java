package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;

/**
 * Unit tests for the non-graded feedback a student can request while working on a programming exercise.
 * <p>
 * The request has two very different outcomes depending on whether Athena is configured: with it the feedback is
 * generated automatically, and without it a tutor has to be told and the student's repository is locked by an individual
 * due date. Both write a result the student sees immediately, so the interesting part is what that result says - an
 * empty placeholder while the generation runs, the suggestions once they arrive, and an unsuccessful result rather than
 * nothing at all when the generation fails.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseCodeReviewFeedbackServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final long PARTICIPATION_ID = 10L;

    @Mock
    private GroupNotificationService groupNotificationService;

    @Mock
    private AthenaFeedbackApi athenaFeedbackApi;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ResultService resultService;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private ProgrammingMessagingService programmingMessagingService;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private ProgrammingSubmission submission;

    private User requestingUser;

    @BeforeEach
    void setUp() throws Exception {
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setProgrammingExercise(exercise);
        submission = new ProgrammingSubmission();
        submission.setId(50L);
        submission.setParticipation(participation);
        participation.setSubmissions(new java.util.HashSet<>(Set.of(submission)));
        requestingUser = new User();
        requestingUser.setId(1L);
        requestingUser.setLogin("ge12abc");
    }

    private ProgrammingExerciseCodeReviewFeedbackService serviceWithAthena(Optional<AthenaFeedbackApi> api) {
        return new ProgrammingExerciseCodeReviewFeedbackService(groupNotificationService, api, submissionService, userRepository, resultService,
                programmingExerciseStudentParticipationRepository, resultRepository, programmingExerciseParticipationService, programmingMessagingService);
    }

    @Test
    void withoutAthena_aFeedbackRequestGoesToTheTutorsAndLocksTheRepository() throws Exception {
        // Nothing can generate the feedback, so a human has to, and the student must not keep changing the code meanwhile.
        var service = serviceWithAthena(Optional.empty());
        when(programmingExerciseStudentParticipationRepository.save(participation)).thenReturn(participation);

        var updated = service.handleNonGradedFeedbackRequest(EXERCISE_ID, participation, exercise);

        verify(groupNotificationService).notifyTutorGroupAboutNewFeedbackRequest(exercise);
        // The individual due date is the flag that a request is pending; without it the request is invisible to everything else.
        assertThat(updated.getIndividualDueDate()).isNotNull();
    }

    @Test
    void withoutAthena_theResultsOfEarlierAttemptsStopCounting() throws Exception {
        // The student asked for feedback on the current state, so the score of a previous attempt must not stay rated.
        var service = serviceWithAthena(Optional.empty());
        var earlierResult = new Result();
        earlierResult.setId(90L);
        earlierResult.setRated(true);
        submission.setResults(Set.of(earlierResult));
        when(programmingExerciseStudentParticipationRepository.save(participation)).thenReturn(participation);

        service.handleNonGradedFeedbackRequest(EXERCISE_ID, participation, exercise);

        assertThat(earlierResult.isRated()).isFalse();
        verify(resultRepository).saveAll(any());
    }

    @Test
    void withAthena_theRateLimitIsCheckedBeforeAnythingIsGenerated() throws Exception {
        // Generation costs money per request, so the limit has to stop the request rather than the result being discarded later.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        org.mockito.Mockito.doThrow(new BadRequestAlertException("too many requests", "participation", "rateLimitExceeded")).when(athenaFeedbackApi)
                .checkRateLimitOrThrow(participation);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.handleNonGradedFeedbackRequest(EXERCISE_ID, participation, exercise));

        verify(groupNotificationService, never()).notifyTutorGroupAboutNewFeedbackRequest(any());
        // The limit has to stop the request before any generation is started, not only before the tutors are told.
        verify(submissionService, never()).saveNewEmptyResult(any());
        verify(programmingExerciseStudentParticipationRepository, never()).save(any());
    }

    /**
     * Everything the automatic generation needs before it reaches Athena.
     */
    private Result withAnEmptyResultForTheLatestSubmission() throws Exception {
        when(programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);
        var automaticResult = new Result();
        automaticResult.setId(91L);
        when(submissionService.saveNewEmptyResult(submission)).thenReturn(automaticResult);
        lenient().when(resultRepository.save(automaticResult)).thenReturn(automaticResult);
        return automaticResult;
    }

    @Test
    void generatingFeedback_firstPublishesAPlaceholderResultSoTheStudentSeesTheRequestIsInWork() throws Exception {
        // Generation takes minutes; without this the student sees nothing at all and assumes the request was lost.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        var automaticResult = withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser))).thenReturn(List.of());
        var firstPublished = new java.util.concurrent.atomic.AtomicReference<ZonedDateTime>();
        org.mockito.Mockito.doAnswer(invocation -> {
            firstPublished.compareAndSet(null, ((Result) invocation.getArgument(0)).getCompletionDate());
            return null;
        }).when(programmingMessagingService).notifyUserAboutNewResult(any(), any());
        ZonedDateTime publishedAt = ZonedDateTime.now();

        service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser);

        ZonedDateTime completionDateWhenFirstPublished = firstPublished.get();
        assertThat(automaticResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        assertThat(automaticResult.isRated()).isTrue();
        // A result without a completion date is not shown at all, so the placeholder is dated into the future while the work
        // runs; the date is captured at the moment it is published because the finished result overwrites it.
        assertThat(completionDateWhenFirstPublished).isNotNull().isAfter(publishedAt);
        verify(programmingMessagingService, org.mockito.Mockito.atLeastOnce()).notifyUserAboutNewResult(automaticResult, participation);
    }

    @Test
    void generatingFeedback_forAParticipationWithoutASubmission_isRefused() throws Exception {
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        participation.setSubmissions(Set.of());
        when(programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser));
    }

    private static ProgrammingFeedbackDTO suggestion(String filePath, String description, Integer lineStart, Integer lineEnd, double credits) {
        return new ProgrammingFeedbackDTO(1L, EXERCISE_ID, 50L, "title", description, credits, null, filePath, lineStart, lineEnd);
    }

    private List<Feedback> generatedFeedbacks(ProgrammingExerciseCodeReviewFeedbackService service) throws Exception {
        service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser);
        ArgumentCaptor<List<Feedback>> feedbacks = ArgumentCaptor.captor();
        verify(resultService).storeFeedbackInResult(any(), feedbacks.capture(), anyBoolean());
        return feedbacks.getValue();
    }

    @Test
    void generatingFeedback_namesTheFileAndTheLineRangeASuggestionAppliesTo() throws Exception {
        // The student has to be able to find the place the suggestion is about; the reference is what the editor anchors on.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser)))
                .thenReturn(List.of(suggestion("src/Main.java", "extract this", 10, 20, 0.0)));

        var feedbacks = generatedFeedbacks(service);

        assertThat(feedbacks).hasSize(1);
        assertThat(feedbacks.getFirst().getText()).contains("src/Main.java").contains("lines 10-20");
        assertThat(feedbacks.getFirst().getReference()).isEqualTo("file:src/Main.java_line:10-20");
        assertThat(feedbacks.getFirst().getDetailText()).isEqualTo("extract this");
    }

    @Test
    void generatingFeedback_forASuggestionAboutASingleLine_namesThatLine() throws Exception {
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser)))
                .thenReturn(List.of(suggestion("src/Main.java", "rename this", 10, null, 0.0)));

        var feedbacks = generatedFeedbacks(service);

        assertThat(feedbacks.getFirst().getText()).contains("at line 10");
        assertThat(feedbacks.getFirst().getReference()).isEqualTo("file:src/Main.java_line:10");
    }

    @Test
    void generatingFeedback_forASuggestionAboutAWholeFile_namesOnlyTheFile() throws Exception {
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser)))
                .thenReturn(List.of(suggestion("src/Main.java", "split this class", null, null, 0.0)));

        var feedbacks = generatedFeedbacks(service);

        assertThat(feedbacks.getFirst().getText()).contains("src/Main.java").doesNotContain("line");
        assertThat(feedbacks.getFirst().getReference()).as("a suggestion about a whole file anchors on nothing in particular").isNull();
    }

    @Test
    void generatingFeedback_dropsSuggestionsThatNameNoFileOrCarryNoText() throws Exception {
        // Such a suggestion would render as an empty feedback card the student cannot act on.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser))).thenReturn(List
                .of(suggestion(null, "no file", 1, null, 0.0), suggestion("src/Main.java", null, 1, null, 0.0), suggestion("src/Main.java", "the only usable one", 1, null, 0.0)));

        var feedbacks = generatedFeedbacks(service);

        assertThat(feedbacks).hasSize(1);
        assertThat(feedbacks.getFirst().getDetailText()).isEqualTo("the only usable one");
    }

    @Test
    void generatingFeedback_showsTheSuggestionsThatCostTheMostPointsLast() throws Exception {
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser))).thenReturn(
                List.of(suggestion("src/C.java", "third", 1, null, 3.0), suggestion("src/A.java", "first", 1, null, -2.0), suggestion("src/B.java", "second", 1, null, 1.0)));

        var feedbacks = generatedFeedbacks(service);

        assertThat(feedbacks).extracting(Feedback::getDetailText).containsExactly("first", "second", "third");
    }

    @Test
    void generatingFeedback_marksTheResultSuccessfulOnceTheSuggestionsAreStored() throws Exception {
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        var automaticResult = withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser)))
                .thenReturn(List.of(suggestion("src/Main.java", "extract this", 1, null, 0.0)));

        service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser);

        assertThat(automaticResult.isSuccessful()).isTrue();
        assertThat(automaticResult.getCompletionDate()).isCloseTo(ZonedDateTime.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    }

    @Test
    void generatingFeedback_whenAthenaFails_leavesAnUnsuccessfulResultRatherThanNothing() throws Exception {
        // The placeholder result is already visible; leaving it as it is would show the student a request that never ends.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        var automaticResult = withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser)))
                .thenThrow(new IllegalStateException("Athena is unreachable"));

        service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser);

        assertThat(automaticResult.isSuccessful()).isFalse();
        assertThat(automaticResult.getCompletionDate()).isCloseTo(ZonedDateTime.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        verify(resultRepository, org.mockito.Mockito.atLeastOnce()).save(automaticResult);
        verify(programmingMessagingService, org.mockito.Mockito.atLeast(2)).notifyUserAboutNewResult(automaticResult, participation);
    }

    @Test
    void generatingFeedback_carriesTheScoreOfTheLastBuildIntoThePlaceholder() throws Exception {
        // The placeholder replaces the visible result, so showing zero would look to the student like they lost their points.
        var service = serviceWithAthena(Optional.of(athenaFeedbackApi));
        var previousResult = new Result();
        previousResult.setId(80L);
        previousResult.setScore(75.0);
        submission.setResults(Set.of(previousResult));
        var automaticResult = withAnEmptyResultForTheLatestSubmission();
        when(athenaFeedbackApi.getProgrammingFeedbackSuggestions(eq(exercise), eq(submission), anyBoolean(), eq(requestingUser))).thenReturn(List.of());

        service.generateAutomaticNonGradedFeedback(participation, exercise, requestingUser);

        assertThat(automaticResult.getScore()).isEqualTo(75.0);
    }
}
