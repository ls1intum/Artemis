package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisAnswerRequestDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseStatusDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentCheckDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisLlmException;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.service.MetisStudentCheckService;
import de.tum.cit.aet.artemis.metis.util.MetisLlmTestResponses;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * The comprehension check of a student through its REST endpoints, end to end against the database: the presented status in every situation, the start, the
 * answers with their live ratings, follow-up questions and completion, the server-side timer and window, the repair of interrupted transitions, what the
 * student may see when, and the races of an answer request with a read or a parallel writer during its rating.
 * <p>
 * The clock is frozen per method; the LLM transport is the shared mock of the test base, whose stubs perform the concurrent action of a race at the moment the
 * real request waits for the model. In this bucket no repository can be read, so every rating prompt says that the code is not available. Every configuration
 * a method switched on is switched off again afterwards, so that no check or answer of this class is ever claimed by a dispatch elsewhere.
 */
class MetisStudentCheckIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisstudentcheck";

    /** The frozen time of every method. Every other time is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2024-05-06T12:00:00Z");

    private static final ZonedDateTime DUE = NOW.minusHours(2);

    private static final ZonedDateTime WINDOW_START = NOW.minusHours(1);

    private static final ZonedDateTime WINDOW_END = NOW.plusHours(1);

    private static final String COMMIT_HASH = "a".repeat(40);

    private static final String NOT_ENABLED = MetisFailureReason.NOT_ENABLED.name();

    /** The time limit of every main question of the fixtures. */
    private static final int MAIN_TIME_LIMIT = 120;

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisExerciseConfigRepository configRepository;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

    @Autowired
    private MetisStudentCheckService studentCheckService;

    @Autowired
    private MetisProperties metisProperties;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    private final List<Long> enabledExerciseIds = new ArrayList<>();

    private Course course;

    private ProgrammingExercise exercise;

    private long exerciseId;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exerciseId = exercise.getId();
        Exercise stored = exerciseRepository.findByIdElseThrow(exerciseId);
        stored.setReleaseDate(DUE.minusDays(7));
        exerciseRepository.save(stored);
        exerciseUtilService.updateExerciseDueDate(exerciseId, DUE);
        // The fixture seeds cloud AI consent only for accounts without any decision, so a decision a method changed would otherwise reach the next one
        for (String student : List.of("student1", "student2", "student3")) {
            userUtilService.setAiSelectionDecision(user(student), AiSelectionDecision.CLOUD_AI);
        }
        freezeClockAt(NOW);
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
        enabledExerciseIds.forEach(id -> metisUtilService.saveConfig(id, false, false, null, null));
        enabledExerciseIds.clear();
    }

    @Nested
    class ExerciseStatus {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReportAnEnabledCheck() throws Exception {
            enable(WINDOW_START, WINDOW_END);

            assertThat(request.get(statusPath(), HttpStatus.OK, MetisExerciseStatusDTO.class)).isEqualTo(new MetisExerciseStatusDTO(true));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReportNoCheckWithoutConfigurationOrWithASwitchedOffOne() throws Exception {
            assertThat(request.get(statusPath(), HttpStatus.OK, MetisExerciseStatusDTO.class)).isEqualTo(new MetisExerciseStatusDTO(false));

            metisUtilService.saveConfig(exerciseId, false, false, WINDOW_START, WINDOW_END);

            assertThat(request.get(statusPath(), HttpStatus.OK, MetisExerciseStatusDTO.class)).isEqualTo(new MetisExerciseStatusDTO(false));
        }
    }

    @Nested
    class PresentedStatus {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentTheCheckAsNotEnabledWithTheCountsOfTheConfiguration() throws Exception {
            var expectedWithoutConfiguration = new MetisStudentCheckDTO(MetisCheckStatus.SKIPPED, null, null, null, 3, 5, null, List.of(), false, NOT_ENABLED);
            assertThat(getOwnCheck()).isEqualTo(expectedWithoutConfiguration);

            update(metisUtilService.saveConfig(exerciseId, false, false, WINDOW_START, WINDOW_END), config -> config.setMainQuestionCount(4));

            var expectedSwitchedOff = new MetisStudentCheckDTO(MetisCheckStatus.SKIPPED, null, null, null, 4, 5, null, List.of(), false, NOT_ENABLED);
            assertThat(getOwnCheck()).isEqualTo(expectedSwitchedOff);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentPendingBeforeTheDueDateWithAndWithoutParticipation() throws Exception {
            ZonedDateTime futureDueDate = NOW.plusDays(1);
            exerciseUtilService.updateExerciseDueDate(exerciseId, futureDueDate);
            enable(null, futureDueDate.plusHours(2));

            MetisStudentCheckDTO withoutParticipation = getOwnCheck();

            assertThat(withoutParticipation.status()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(withoutParticipation.failureReason()).isNull();
            assertThat(withoutParticipation.windowStart().toInstant()).isEqualTo(futureDueDate.toInstant());

            participation("student1");

            assertThat(getOwnCheck().status()).isEqualTo(MetisCheckStatus.PENDING);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentNoSubmissionWithoutParticipationAfterTheDueDate() throws Exception {
            enable(WINDOW_START, WINDOW_END);

            MetisStudentCheckDTO check = getOwnCheck();

            assertThat(check.status()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(check.failureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            assertThat(check.windowStart().toInstant()).isEqualTo(WINDOW_START.toInstant());
            assertThat(check.windowEnd().toInstant()).isEqualTo(WINDOW_END.toInstant());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
        void shouldPresentNoSubmissionToATutorWithoutParticipation() throws Exception {
            enable(WINDOW_START, WINDOW_END);

            MetisStudentCheckDTO check = getOwnCheck();

            assertThat(check.status()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(check.failureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldPresentATutorWithAGradedParticipationAsNotEligibleAlsoWithAStudentRole() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            participation("tutor1");

            assertThat(getOwnCheck().failureReason()).isEqualTo(MetisFailureReason.NOT_ELIGIBLE.name());

            userUtilService.enrollUserInCourse(user("tutor1"), course, CourseRole.STUDENT);

            MetisStudentCheckDTO check = getOwnCheck();
            assertThat(check.status()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(check.failureReason()).isEqualTo(MetisFailureReason.NOT_ELIGIBLE.name());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentAParticipationWithoutRepositoryAfterTheDueDateAsNoSubmission() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            saveParticipation("student1", null, null);

            MetisStudentCheckDTO check = getOwnCheck();

            assertThat(check.status()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(check.failureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentAParticipationWhoseCheckTheSchedulerHasNotCreatedYetAsPending() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            participation("student1");

            MetisStudentCheckDTO check = getOwnCheck();

            assertThat(check.status()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(check.failureReason()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentAReadyCheckBeforeTheWindowWithoutQuestions() throws Exception {
            enable(NOW.plusHours(1), NOW.plusHours(2));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);
            mainQuestion(check, 2);

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.READY);
            assertThat(presented.windowStart().toInstant()).isEqualTo(NOW.plusHours(1).toInstant());
            assertThat(presented.mainQuestionCount()).isEqualTo(2);
            assertThat(presented.likertScalePoints()).isEqualTo(5);
            assertThat(presented.currentQuestion()).isNull();
            assertThat(presented.answeredQuestions()).isEmpty();
            assertThat(presented.ratingsVisible()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentACheckInReviewAsInReviewWithItsOwnCountBeforeTheWindowOpens() throws Exception {
            enable(NOW.plusHours(1), NOW.plusHours(2));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.IN_REVIEW);
            mainQuestion(check, 1);
            // The instructor asks for more questions while the generated ones wait for the review
            update(configRepository.findByIdElseThrow(exerciseId), config -> config.setMainQuestionCount(4));

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(presented.mainQuestionCount()).isEqualTo(1);
            assertThat(presented.currentQuestion()).isNull();
            assertThat(presented.answeredQuestions()).isEmpty();
            assertThat(presented.ratingsVisible()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentACheckInReviewAsReadyOnceTheWindowHasOpenedAndLetTheStudentStartIt() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.IN_REVIEW);
            MetisQuestion first = mainQuestion(check, 1);
            mainQuestion(check, 2);

            assertThat(getOwnCheck().status()).isEqualTo(MetisCheckStatus.READY);

            MetisStudentCheckDTO started = start(HttpStatus.OK);

            assertThat(started.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(started.currentQuestion().id()).isEqualTo(first.getId());
            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentTheStoredReasonOfAFailedCheck() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.FAILED);
            update(check, failed -> failed.setFailureReason(MetisFailureReason.INVALID_LLM_RESPONSE.name()));

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.FAILED);
            assertThat(presented.failureReason()).isEqualTo(MetisFailureReason.INVALID_LLM_RESPONSE.name());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentACheckWhoseDueDateWasExtendedIntoTheFutureAsPendingAndRefuseToStartIt() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1", NOW.plusHours(1)), MetisCheckStatus.READY);
            mainQuestion(check, 1);

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(presented.windowStart().toInstant()).isEqualTo(NOW.plusHours(1).toInstant());
            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.notReady");
            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRequeueACheckSkippedForMissingConsentOnceTheStudentConsents() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = skippedForMissingConsent(participation("student1"));

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(presented.failureReason()).isNull();
            MetisCheck stored = reload(check);
            assertThat(stored.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(stored.getFailureReason()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldKeepACheckSkippedForMissingConsentWhileTheStudentDoesNotConsent() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = skippedForMissingConsent(participation("student1"));
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.status()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(presented.failureReason()).isEqualTo(MetisFailureReason.NO_AI_CONSENT.name());
            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldPresentTheWindowOfTheTutorialGroupWithItsTitle() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            TutorialGroup group = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Metis Monday", "", 10, false, "Garching", "English", user("tutor1"),
                    Set.of(user("student1")));
            metisUtilService.saveGroupWindow(exerciseId, group.getId(), NOW.plusHours(2), NOW.plusHours(4));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);

            MetisStudentCheckDTO presented = getOwnCheck();

            assertThat(presented.windowStart().toInstant()).isEqualTo(NOW.plusHours(2).toInstant());
            assertThat(presented.windowEnd().toInstant()).isEqualTo(NOW.plusHours(4).toInstant());
            assertThat(presented.tutorialGroupTitle()).isEqualTo("Metis Monday");
            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.windowNotOpen");
        }
    }

    @Nested
    class Start {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStartTheCheckAndShowOnlyTheFirstQuestion() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            MetisQuestion first = mainQuestion(check, 1);
            MetisQuestion second = mainQuestion(check, 2);

            MetisStudentCheckDTO started = start(HttpStatus.OK);

            assertThat(started.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(started.currentQuestion().id()).isEqualTo(first.getId());
            assertThat(started.currentQuestion().questionText()).isEqualTo(first.getQuestionText());
            assertThat(started.currentQuestion().shownAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(started.currentQuestion().focusFilePath()).isEqualTo(first.getFocusFilePath());
            assertThat(started.answeredQuestions()).isEmpty();
            MetisCheck stored = reload(check);
            assertThat(stored.getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(stored.getStartedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(reload(second).getShownAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseToStartBeforeTheWindowOpens() throws Exception {
            enable(NOW.plusHours(1), NOW.plusHours(2));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);

            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.windowNotOpen");

            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseToStartAfterTheWindow() throws Exception {
            enable(WINDOW_START, NOW.minusMinutes(1));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);

            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.windowClosed");

            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseToStartWhenLessTimeThanOneQuestionNeedsIsLeft() throws Exception {
            enable(WINDOW_START, NOW.plusSeconds(30));
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);

            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.windowClosed");

            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldForbidTheStartWithoutConsent() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            mainQuestion(check, 1);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);

            start(HttpStatus.FORBIDDEN);

            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStartOnlyOnceAlsoWhenTheSecondStartComesWithTooLittleTimeLeft() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            MetisQuestion first = mainQuestion(check, 1);
            MetisQuestion second = mainQuestion(check, 2);
            start(HttpStatus.OK);

            freezeClockAt(WINDOW_END.minusSeconds(30));
            MetisStudentCheckDTO startedAgain = start(HttpStatus.OK);

            assertThat(startedAgain.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(startedAgain.currentQuestion().id()).isEqualTo(first.getId());
            assertThat(startedAgain.currentQuestion().shownAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(reload(check).getStartedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(reload(second).getShownAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseToStartWithoutCheckOrForAnExerciseWithoutEnabledCheck() throws Exception {
            participation("student1");
            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.notEnabled");

            enable(WINDOW_START, WINDOW_END);
            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.noCheck");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseToStartACheckWhoseQuestionsAreNotGenerated() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = metisUtilService.saveCheck(participation("student1").getId(), MetisCheckStatus.PENDING);

            request.postAndExpectError(startPath(), null, HttpStatus.CONFLICT, "metis.notReady");

            assertThat(reload(check).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }
    }

    @Nested
    class Answer {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStoreTheAnswerWithItsRatingAndShowTheNextMainQuestion() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            stubRating(MetisLlmTestResponses.ratingJson(4, "Correct and specific.", "", 0));

            MetisStudentCheckDTO answered = answer(check.first(), "The loop swaps neighbouring elements.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("The loop swaps neighbouring elements.");
            assertThat(stored.getAnsweredAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(stored.getLlmScore()).isEqualTo(4);
            assertThat(stored.getLlmRationale()).isEqualTo("Correct and specific.");
            assertThat(stored.getLlmRatedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(answered.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
            assertThat(answered.currentQuestion().shownAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(answered.answeredQuestions()).singleElement().satisfies(question -> {
                assertThat(question.id()).isEqualTo(check.first().getId());
                assertThat(question.answerText()).isEqualTo("The loop swaps neighbouring elements.");
                assertThat(question.llmScore()).isNull();
                assertThat(question.llmRationale()).isNull();
                assertThat(question.llmRatedAt()).isNull();
            });
            assertThat(answered.ratingsVisible()).isFalse();
            // This bucket cannot read the repository, so the rating prompt says that the code is not available
            assertThat(capturedPrompts(1).userPrompt()).contains("(code not available)");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldAskAFollowUpOnAWeakRatingAndShowItFromTheEndOfTheRating() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                freezeClockAt(NOW.plusSeconds(20));
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 90));
            });

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            MetisQuestionDTO followUp = answered.currentQuestion();
            assertThat(followUp.parentQuestionId()).isEqualTo(check.first().getId());
            assertThat(followUp.questionOrder()).isEqualTo(4);
            assertThat(followUp.questionText()).isEqualTo("Which pair does it swap first?");
            assertThat(followUp.timeLimitSeconds()).isEqualTo(90);
            assertThat(followUp.shownAt().toInstant()).isEqualTo(NOW.plusSeconds(20).toInstant());
            assertThat(followUp.focusFilePath()).isNull();
            assertThat(reload(check.first()).getLlmScore()).isEqualTo(2);
            assertThat(reload(check.second()).getShownAt()).isNull();
            assertThat(capturedPrompts(1).systemPrompt()).contains("If the score is 3 or lower").contains("followUpTimeLimitSeconds must be between 60 and 120.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldAskTheNextFollowUpOnTheMainQuestionWithinTheLimitOfTheMainQuestion() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            rated(answered(check.first(), NOW.minusMinutes(3), NOW.minusMinutes(2), "It sorts."), 2);
            MetisQuestion firstFollowUp = metisUtilService.saveFollowUp(check.check().getId(), 4, check.first().getId(), "Which pair does it swap first?", 60);
            // The model proposes far more time than the main question has
            stubRating(MetisLlmTestResponses.ratingJson(2, "Still vague.", "By what does it compare the pair?", 999));

            MetisStudentCheckDTO answered = answer(firstFollowUp, "The first one.");

            MetisQuestionDTO secondFollowUp = answered.currentQuestion();
            assertThat(secondFollowUp.parentQuestionId()).isEqualTo(check.first().getId());
            assertThat(secondFollowUp.questionOrder()).isEqualTo(5);
            assertThat(secondFollowUp.questionText()).isEqualTo("By what does it compare the pair?");
            assertThat(secondFollowUp.timeLimitSeconds()).isEqualTo(MAIN_TIME_LIMIT);
            assertThat(reload(firstFollowUp).getLlmScore()).isEqualTo(2);
            assertThat(reload(check.second()).getShownAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldGiveAFollowUpWithoutProposedLimitHalfTheLimitOfTheMainQuestionAndCutItsText() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            update(check.first(), question -> question.setTimeLimitSeconds(300));
            String longFollowUp = "Which pair does the loop swap first, and why? ".repeat(50);
            stubRating(MetisLlmTestResponses.ratingJson(2, "Vague.", longFollowUp, 0));

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            MetisQuestionDTO followUp = answered.currentQuestion();
            assertThat(followUp.parentQuestionId()).isEqualTo(check.first().getId());
            assertThat(followUp.timeLimitSeconds()).isEqualTo(150);
            // A follow-up keeps at most 2000 characters, like a generated question
            assertThat(followUp.questionText()).isEqualTo(longFollowUp.substring(0, 2000));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldShowTheNextMainQuestionOnceTheCapOfFollowUpsIsReached() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            update(configRepository.findByIdElseThrow(exerciseId), config -> config.setMaxFollowUpsPerQuestion(1));
            answered(check.first(), NOW.minusMinutes(2), NOW.minusMinutes(1), "It sorts.");
            MetisQuestion followUp = metisUtilService.saveFollowUp(check.check().getId(), 4, check.first().getId(), "Which pair does it swap first?", 60);
            stubRating(MetisLlmTestResponses.ratingJson(1, "Still vague.", "And then?", 60));

            MetisStudentCheckDTO answered = answer(followUp, "The first one.");

            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
            assertThat(questionsOf(check.check())).hasSize(4);
            assertThat(reload(followUp).getLlmScore()).isEqualTo(1);
            CapturedPrompts prompts = capturedPrompts(1);
            assertThat(prompts.systemPrompt()).contains("Do not write a follow-up question");
            assertThat(prompts.userPrompt()).contains("--- BEGIN EARLIER QUESTION 1 ---\nMain question 1\n").contains("--- BEGIN EARLIER ANSWER 1 ---\nIt sorts.\n")
                    .contains("--- BEGIN QUESTION ---\nWhich pair does it swap first?\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldCompleteTheCheckAfterTheLastAnswer() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 1);
            stubRating(MetisLlmTestResponses.ratingJson(5, "Full understanding.", "", 0));

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts in place.");

            assertThat(answered.status()).isEqualTo(MetisCheckStatus.COMPLETED);
            assertThat(answered.currentQuestion()).isNull();
            assertThat(answered.answeredQuestions()).hasSize(1);
            assertThat(answered.ratingsVisible()).isFalse();
            MetisCheck stored = reload(check.check());
            assertThat(stored.getStatus()).isEqualTo(MetisCheckStatus.COMPLETED);
            assertThat(stored.getCompletedAt().toInstant()).isEqualTo(NOW.toInstant());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStoreTheAnswerUnratedAndMoveOnWhenTheRatingFails() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "The endpoint is down"));

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("It sorts.");
            assertThat(stored.getLlmRatedAt()).isNull();
            assertThat(stored.getLlmScore()).isNull();
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStoreTheAnswerUnratedAndMoveOnWhenTheRatingFailsUnexpectedly() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenThrow(new IllegalStateException("unexpected"));

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("It sorts.");
            assertThat(stored.getLlmRatedAt()).isNull();
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldCallTheLlmOnceAndStoreTheAnswerUnratedWhenTheLiveRatingIsUnusable() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            stubRating("I would rate this answer as fairly good.");

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            verify(metisLlmService, times(1)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any());
            assertThat(reload(check.first()).getLlmRatedAt()).isNull();
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStoreALateAnswerEmptyAtTheDeadlineWithTheLowestScoreWithoutTheLlm() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            // The answer arrives one second after the grace that follows the deadline
            ZonedDateTime deadline = NOW.minusSeconds(answerGraceSeconds() + 1);
            shown(check.first(), deadline.minusSeconds(MAIN_TIME_LIMIT));

            MetisStudentCheckDTO answered = answer(check.first(), "An answer typed after the timer ran out.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEmpty();
            assertThat(stored.getAnsweredAt().toInstant()).isEqualTo(deadline.toInstant());
            assertThat(stored.getLlmScore()).isEqualTo(1);
            assertThat(stored.getLlmRationale()).isEqualTo("No answer was submitted.");
            verifyNoInteractions(metisLlmService);
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldKeepAnAnswerThatArrivesWithinTheGraceAfterTheDeadline() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            // The answer arrives at the last moment of the grace after the deadline, like the draft the client sends when its timer expires
            ZonedDateTime deadline = NOW.minusSeconds(answerGraceSeconds());
            shown(check.first(), deadline.minusSeconds(MAIN_TIME_LIMIT));
            stubRating(MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            MetisStudentCheckDTO answered = answer(check.first(), "The draft sent when the timer expired.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("The draft sent when the timer expired.");
            assertThat(stored.getAnsweredAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(stored.getLlmScore()).isEqualTo(4);
            assertThat(capturedPrompts(1).userPrompt()).contains("--- BEGIN STUDENT ANSWER ---\nThe draft sent when the timer expired.\n");
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldKeepAnAnswerThatArrivesWithinTheGraceAfterTheWindow() throws Exception {
            // The window end cuts the deadline of a question shown shortly before it, and the answer arrives at the last moment of the grace after it
            ZonedDateTime windowEnd = NOW.minusSeconds(answerGraceSeconds());
            InProgress check = inProgress(WINDOW_START, windowEnd, 3);
            shown(check.first(), windowEnd.minusSeconds(30));
            stubRating(MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 60));

            MetisStudentCheckDTO answered = answer(check.first(), "The draft sent when the window closed.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("The draft sent when the window closed.");
            assertThat(stored.getAnsweredAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(stored.getLlmScore()).isEqualTo(2);
            // The window has ended, so neither a follow-up nor the next main question is shown
            assertThat(capturedPrompts(1).systemPrompt()).contains("Do not write a follow-up question");
            assertThat(answered.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(answered.currentQuestion()).isNull();
            assertThat(questionsOf(check.check())).hasSize(3);
            assertThat(reload(check.second()).getShownAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRateABlankAnswerOneWithoutTheLlmAndAskNoFollowUp() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);

            MetisStudentCheckDTO answered = answer(check.first(), "   ");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getAnswerText()).isEqualTo("   ");
            assertThat(stored.getAnsweredAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(stored.getLlmScore()).isEqualTo(1);
            assertThat(stored.getLlmRationale()).isEqualTo("No answer was submitted.");
            verifyNoInteractions(metisLlmService);
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
            assertThat(questionsOf(check.check())).hasSize(3);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldNeitherAskAFollowUpNorShowTheNextQuestionWhenTheWindowLeavesTooLittleTime() throws Exception {
            InProgress check = inProgress(WINDOW_START, NOW.plusSeconds(30), 3);
            stubRating(MetisLlmTestResponses.ratingJson(1, "Wrong.", "What does the loop compare?", 60));

            MetisStudentCheckDTO answered = answer(check.first(), "It prints the array.");

            assertThat(answered.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(answered.currentQuestion()).isNull();
            assertThat(questionsOf(check.check())).hasSize(3);
            assertThat(reload(check.second()).getShownAt()).isNull();
            assertThat(reload(check.check()).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(capturedPrompts(1).systemPrompt()).contains("Do not write a follow-up question");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldIgnoreARepeatedAnswer() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            stubRating(MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));
            answer(check.first(), "The first answer.");

            MetisStudentCheckDTO repeated = answer(check.first(), "A second answer.");

            assertThat(reload(check.first()).getAnswerText()).isEqualTo("The first answer.");
            assertThat(repeated.currentQuestion().id()).isEqualTo(check.second().getId());
            verify(metisLlmService, times(1)).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldCutAnAnswerThatIsTooLongInsteadOfRejectingIt() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            stubRating(MetisLlmTestResponses.ratingJson(3, "Long.", "", 0));
            int maxAnswerCharacters = metisProperties.conversation().maxAnswerCharacters();

            answer(check.first(), "x".repeat(maxAnswerCharacters + 50));

            assertThat(reload(check.first()).getAnswerText()).hasSize(maxAnswerCharacters);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseAnAnswerToAnUnshownQuestion() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);

            request.postAndExpectError(answerPath(check.second().getId()), new MetisAnswerRequestDTO("Too early."), HttpStatus.CONFLICT, "metis.notCurrentQuestion");

            assertThat(reload(check.second()).getAnsweredAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseAnAnswerAfterTheWindowAndItsGrace() throws Exception {
            // The answer arrives one second after the grace that follows the window end
            InProgress check = inProgress(WINDOW_START, NOW.minusSeconds(answerGraceSeconds() + 1), 3);
            shown(check.first(), NOW.minusMinutes(5));

            request.postAndExpectError(answerPath(check.first().getId()), new MetisAnswerRequestDTO("Too late."), HttpStatus.CONFLICT, "metis.windowClosed");

            assertThat(reload(check.first()).getAnsweredAt()).isNull();
            verifyNoInteractions(metisLlmService);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRefuseAnAnswerToACheckThatIsNotInProgress() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.READY);
            MetisQuestion first = mainQuestion(check, 1);

            request.postAndExpectError(answerPath(first.getId()), new MetisAnswerRequestDTO("Not started."), HttpStatus.CONFLICT, "metis.notReady");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldNotFindTheQuestionOfAnotherStudent() throws Exception {
            inProgress(WINDOW_START, WINDOW_END, 1);
            MetisCheck otherCheck = check(participation("student2"), MetisCheckStatus.IN_PROGRESS);
            MetisQuestion otherQuestion = shown(mainQuestion(otherCheck, 1), NOW.minusMinutes(1));

            request.postWithResponseBody(answerPath(otherQuestion.getId()), new MetisAnswerRequestDTO("Not mine."), MetisStudentCheckDTO.class, HttpStatus.NOT_FOUND);

            assertThat(reload(otherQuestion).getAnsweredAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldForbidAnAnswerWithoutConsent() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);

            request.postWithResponseBody(answerPath(check.first().getId()), new MetisAnswerRequestDTO("No consent."), MetisStudentCheckDTO.class, HttpStatus.FORBIDDEN);

            assertThat(reload(check.first()).getAnsweredAt()).isNull();
        }
    }

    @Nested
    class Races {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldShowNoNewQuestionToAReadDuringTheRatingAndOpenOnlyTheFollowUpAfterwards() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            AtomicReference<MetisStudentCheckDTO> readDuringTheRating = new AtomicReference<>();
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                readDuringTheRating.set(studentCheckService.getOwnCheck(exerciseId));
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 90));
            });

            answer(check.first(), "It sorts.");

            assertThat(readDuringTheRating.get().status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(readDuringTheRating.get().currentQuestion()).isNull();
            List<MetisQuestion> open = questionsOf(check.check()).stream().filter(question -> question.getShownAt() != null && question.getAnsweredAt() == null).toList();
            assertThat(open).singleElement().satisfies(question -> assertThat(question.getParentQuestionId()).isEqualTo(check.first().getId()));
            assertThat(reload(check.second()).getShownAt()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldStoreTheRatingButAskNoFollowUpWhenAQuestionWasOpenedMeanwhile() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                questionRepository.markShown(check.second().getId(), NOW);
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 90));
            });

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            assertThat(reload(check.first()).getLlmScore()).isEqualTo(2);
            assertThat(questionsOf(check.check())).hasSize(3);
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldKeepTheRatingOfAnotherWriterAndAskNoFollowUp() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                questionRepository.saveRating(check.first().getId(), 5, "Rated by the backfill.", NOW);
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(1, "Wrong.", "What does the loop compare?", 60));
            });

            MetisStudentCheckDTO answered = answer(check.first(), "It sorts.");

            MetisQuestion stored = reload(check.first());
            assertThat(stored.getLlmScore()).isEqualTo(5);
            assertThat(stored.getLlmRationale()).isEqualTo("Rated by the backfill.");
            assertThat(questionsOf(check.check())).hasSize(3);
            assertThat(answered.currentQuestion().id()).isEqualTo(check.second().getId());
        }
    }

    @Nested
    class Repair {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRepairAStartedCheckWithoutOpenQuestionOnlyOnceTheLatestAnswerHasSettled() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            ZonedDateTime answeredAt = NOW.minusSeconds(10);
            rated(answered(check.first(), NOW.minusMinutes(1), answeredAt, "It sorts."), 4);

            MetisStudentCheckDTO beforeSettled = getOwnCheck();

            assertThat(beforeSettled.currentQuestion()).isNull();
            assertThat(reload(check.second()).getShownAt()).isNull();

            ZonedDateTime settled = answeredAt.plus(metisProperties.answerSettleTime());
            freezeClockAt(settled);
            MetisStudentCheckDTO afterSettled = getOwnCheck();

            assertThat(afterSettled.currentQuestion().id()).isEqualTo(check.second().getId());
            assertThat(reload(check.second()).getShownAt().toInstant()).isEqualTo(settled.toInstant());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldShowTheFirstQuestionOfAStartedCheckThatShowsNone() throws Exception {
            enable(WINDOW_START, WINDOW_END);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.IN_PROGRESS);
            MetisQuestion first = mainQuestion(check, 1);
            mainQuestion(check, 2);

            MetisStudentCheckDTO repaired = getOwnCheck();

            assertThat(repaired.currentQuestion().id()).isEqualTo(first.getId());
            assertThat(reload(first).getShownAt().toInstant()).isEqualTo(NOW.toInstant());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldCompleteAStartedCheckWhoseLastAnswerLeftItOpen() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 1);
            rated(answered(check.first(), NOW.minusMinutes(10), NOW.minusMinutes(9), "It sorts."), 4);

            MetisStudentCheckDTO repaired = getOwnCheck();

            assertThat(repaired.status()).isEqualTo(MetisCheckStatus.COMPLETED);
            assertThat(reload(check.check()).getStatus()).isEqualTo(MetisCheckStatus.COMPLETED);
        }
    }

    @Nested
    class Visibility {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldShowTheRatingsOnlyAfterTheWindowAlsoForACheckLeftInProgress() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 2);
            rated(answered(check.first(), NOW.minusMinutes(3), NOW.minusMinutes(2), "It sorts."), 4);
            shown(check.second(), NOW.minusMinutes(1));

            MetisStudentCheckDTO duringTheWindow = getOwnCheck();

            assertThat(duringTheWindow.ratingsVisible()).isFalse();
            assertThat(duringTheWindow.currentQuestion().id()).isEqualTo(check.second().getId());
            assertThat(duringTheWindow.answeredQuestions()).singleElement().satisfies(question -> assertThat(question.llmScore()).isNull());

            freezeClockAt(WINDOW_END);
            MetisStudentCheckDTO afterTheWindow = getOwnCheck();

            assertThat(afterTheWindow.status()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(afterTheWindow.ratingsVisible()).isTrue();
            assertThat(afterTheWindow.currentQuestion()).isNull();
            assertThat(afterTheWindow.answeredQuestions()).singleElement().satisfies(question -> {
                assertThat(question.llmScore()).isEqualTo(4);
                assertThat(question.llmRationale()).isEqualTo("Rationale 4");
                assertThat(question.llmRatedAt()).isNotNull();
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldShowTheRatingsOfACompletedCheckWithoutWindowEnd() throws Exception {
            enable(WINDOW_START, null);
            MetisCheck check = check(participation("student1"), MetisCheckStatus.COMPLETED);
            rated(answered(mainQuestion(check, 1), NOW.minusMinutes(3), NOW.minusMinutes(2), "It sorts."), 3);

            MetisStudentCheckDTO completed = getOwnCheck();

            assertThat(completed.ratingsVisible()).isTrue();
            assertThat(completed.answeredQuestions()).singleElement().satisfies(question -> assertThat(question.llmScore()).isEqualTo(3));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldNeverSendUnshownMainQuestionsTheReviewStateOrTheSnapshot() throws Exception {
            InProgress check = inProgress(WINDOW_START, WINDOW_END, 3);
            update(check.first(), question -> {
                question.setApprovedAt(NOW.minusDays(1));
                question.setRegenerationCount(2);
            });

            String json = request.get(checkPath(), HttpStatus.OK, String.class);

            assertThat(json).contains(check.first().getQuestionText()).doesNotContain(check.second().getQuestionText()).doesNotContain(check.third().getQuestionText())
                    .doesNotContain("approvedAt").doesNotContain(COMMIT_HASH).doesNotContain(MetisUtilService.TEST_LLM_MODEL).contains("\"regenerationCount\":0");
        }
    }

    /**
     * A started check with the given number of main questions, of which the first is shown a minute ago and the others are not shown yet.
     */
    private InProgress inProgress(ZonedDateTime windowStart, ZonedDateTime windowEnd, int mainQuestions) {
        enable(windowStart, windowEnd);
        MetisCheck check = check(participation("student1"), MetisCheckStatus.IN_PROGRESS);
        List<MetisQuestion> questions = new ArrayList<>();
        for (int order = 1; order <= mainQuestions; order++) {
            questions.add(mainQuestion(check, order));
        }
        shown(questions.getFirst(), NOW.minusMinutes(1));
        return new InProgress(check, questions);
    }

    /**
     * A started check and its main questions in their order.
     */
    private record InProgress(MetisCheck check, List<MetisQuestion> mainQuestions) {

        MetisQuestion first() {
            return mainQuestions.getFirst();
        }

        MetisQuestion second() {
            return mainQuestions.get(1);
        }

        MetisQuestion third() {
            return mainQuestions.get(2);
        }
    }

    private record CapturedPrompts(String systemPrompt, String userPrompt) {
    }

    private void enable(@Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd) {
        metisUtilService.saveConfig(exerciseId, true, false, windowStart, windowEnd);
        enabledExerciseIds.add(exerciseId);
    }

    private ProgrammingExerciseStudentParticipation participation(String userSuffix) {
        return participation(userSuffix, null);
    }

    private ProgrammingExerciseStudentParticipation participation(String userSuffix, @Nullable ZonedDateTime individualDueDate) {
        String repositoryName = (exercise.getProjectKey() + "-" + TEST_PREFIX + userSuffix).toLowerCase(Locale.ROOT);
        return saveParticipation(userSuffix, "http://localhost/git/" + exercise.getProjectKey() + "/" + repositoryName + ".git", individualDueDate);
    }

    private ProgrammingExerciseStudentParticipation saveParticipation(String userSuffix, @Nullable String repositoryUri, @Nullable ZonedDateTime individualDueDate) {
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise, user(userSuffix));
        participation.setRepositoryUri(repositoryUri);
        participation.setIndividualDueDate(individualDueDate);
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private MetisCheck check(ProgrammingExerciseStudentParticipation participation, MetisCheckStatus status) {
        return metisUtilService.saveGeneratedCheck(participation.getId(), status, COMMIT_HASH);
    }

    private MetisCheck skippedForMissingConsent(ProgrammingExerciseStudentParticipation participation) {
        MetisCheck check = metisUtilService.saveCheck(participation.getId(), MetisCheckStatus.SKIPPED);
        return update(check, skipped -> skipped.setFailureReason(MetisFailureReason.NO_AI_CONSENT.name()));
    }

    private MetisQuestion mainQuestion(MetisCheck check, int order) {
        return metisUtilService.saveMainQuestion(check.getId(), order, "src/Sorter" + order + ".java", 10 + order, "Main question " + order, MAIN_TIME_LIMIT);
    }

    private MetisQuestion shown(MetisQuestion question, ZonedDateTime shownAt) {
        return update(question, stored -> stored.setShownAt(shownAt));
    }

    private MetisQuestion answered(MetisQuestion question, ZonedDateTime shownAt, ZonedDateTime answeredAt, String answer) {
        return update(question, stored -> {
            stored.setShownAt(shownAt);
            stored.setAnswerText(answer);
            stored.setAnsweredAt(answeredAt);
        });
    }

    private MetisQuestion rated(MetisQuestion question, int score) {
        return update(question, stored -> {
            stored.setLlmScore(score);
            stored.setLlmRationale("Rationale " + score);
            stored.setLlmRatedAt(stored.getAnsweredAt());
        });
    }

    private MetisQuestion update(MetisQuestion question, Consumer<MetisQuestion> change) {
        MetisQuestion stored = reload(question);
        change.accept(stored);
        return questionRepository.save(stored);
    }

    private MetisCheck update(MetisCheck check, Consumer<MetisCheck> change) {
        MetisCheck stored = reload(check);
        change.accept(stored);
        return checkRepository.save(stored);
    }

    private void update(MetisExerciseConfig config, Consumer<MetisExerciseConfig> change) {
        change.accept(config);
        configRepository.save(config);
    }

    private MetisCheck reload(MetisCheck check) {
        return checkRepository.findByIdElseThrow(check.getId());
    }

    private MetisQuestion reload(MetisQuestion question) {
        return questionRepository.findByIdElseThrow(question.getId());
    }

    private List<MetisQuestion> questionsOf(MetisCheck check) {
        return questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
    }

    private void stubRating(String output) {
        when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenReturn(MetisLlmTestResponses.completion(output));
    }

    private CapturedPrompts capturedPrompts(int calls) {
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(metisLlmService, times(calls)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), systemPrompt.capture(), userPrompt.capture(), any());
        verify(metisLlmService, never()).complete(any(), eq(Mode.BATCH), anyString(), anyString(), any());
        return new CapturedPrompts(systemPrompt.getValue(), userPrompt.getValue());
    }

    private MetisStudentCheckDTO getOwnCheck() throws Exception {
        return request.get(checkPath(), HttpStatus.OK, MetisStudentCheckDTO.class);
    }

    private MetisStudentCheckDTO start(HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody(startPath(), null, MetisStudentCheckDTO.class, expectedStatus);
    }

    private MetisStudentCheckDTO answer(MetisQuestion question, String answer) throws Exception {
        return request.postWithResponseBody(answerPath(question.getId()), new MetisAnswerRequestDTO(answer), MetisStudentCheckDTO.class, HttpStatus.OK);
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(TEST_PREFIX + suffix);
    }

    /**
     * The grace after the deadline of a question and after the end of the window, within which an answer still counts as it is.
     */
    private int answerGraceSeconds() {
        return metisProperties.conversation().answerGraceSeconds();
    }

    private static void freezeClockAt(ZonedDateTime time) {
        TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
    }

    private String statusPath() {
        return "/api/metis/exercises/" + exerciseId + "/status";
    }

    private String checkPath() {
        return "/api/metis/exercises/" + exerciseId + "/check";
    }

    private String startPath() {
        return checkPath() + "/start";
    }

    private String answerPath(long questionId) {
        return checkPath() + "/questions/" + questionId + "/answer";
    }
}
