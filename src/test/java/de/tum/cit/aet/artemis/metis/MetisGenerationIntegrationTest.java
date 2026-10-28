package de.tum.cit.aet.artemis.metis;

import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerateResultDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisParticipationRowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisGenerationDispatchService;
import de.tum.cit.aet.artemis.metis.service.MetisLlmException;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Attribution;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Completion;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService.Outcome;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionReviewService;
import de.tum.cit.aet.artemis.metis.util.MetisLlmTestResponses;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * The generation of the comprehension checks end to end, against the database and real LocalVC repositories: the creation of the checks after the due date,
 * the sweeps of the poller, the claims and their order, the generation from the student's own code with its outcomes, the circuit breaker, "generate now",
 * the regeneration of one question and the rating backfill.
 * <p>
 * Each student's repository gets the template in a commit with the setup message and the student's own code in a second commit, and a submission with that
 * commit before the due date. The LLM transport is the shared mock of the test base; under the test profile the Metis executor runs every task synchronously,
 * so one dispatch processes the whole chain of claims. The clock is frozen per method and the same instant is handed to the dispatcher. Every method of this
 * lane shares one dispatcher and one database, so the dispatcher is reset before every method and every configuration a method switched on is switched off
 * again afterwards, which keeps its checks and answers away from the claims of later methods.
 */
class MetisGenerationIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "metisgeneration";

    /** The frozen time of every method. Every other time is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2024-05-06T12:00:00Z");

    private static final ZonedDateTime DUE = NOW.minusHours(2);

    /** How long the rating backfill waits before it rates an answer again whose rating failed. */
    private static final Duration RATING_RETRY_DELAY = Duration.ofMinutes(5);

    private static final String SORTER_PATH = "src/de/tum/Sorter.java";

    private static final String HELPER_PATH = "src/de/tum/SortHelper.java";

    private static final String TEST_PATH = "test/de/tum/SorterTest.java";

    /** Only in the student's test file, which must never reach a prompt. */
    private static final String TEST_FILE_CONTENT = "assertSortedAscendingInTheStudentsOwnTest";

    private static final String TEMPLATE_SORTER = """
            package de.tum;

            public class Sorter {

                public int[] sort(int[] input) {
                    // TODO: sort the input
                    return input;
                }
            }
            """;

    private static final String TEMPLATE_TEST = """
            package de.tum;

            class SorterTest {
            }
            """;

    private static final Map<String, String> TEMPLATE_FILES = Map.of(SORTER_PATH, TEMPLATE_SORTER, TEST_PATH, TEMPLATE_TEST, "README.md", "# Sorting\n");

    private static final List<String> QUESTIONS = List.of("What does the inner loop of sort do?", "Why does swap need a local variable?", "What does sort return for [3, 1]?");

    @Autowired
    private MetisGenerationDispatchService dispatchService;

    @Autowired
    private MetisQuestionGenerationService generationService;

    @Autowired
    private MetisQuestionReviewService questionReviewService;

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

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
        userUtilService.addUsers(TEST_PREFIX, 6, 1, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = pastExerciseOf(course);
        exerciseId = exercise.getId();
        // The fixture seeds cloud AI consent only for accounts without any decision, so a decision a method changed would otherwise reach the next one
        for (int number = 1; number <= 6; number++) {
            userUtilService.setAiSelectionDecision(user("student" + number), AiSelectionDecision.CLOUD_AI);
        }
        freezeClockAt(NOW);
        dispatchService.resetAfterStartup();
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
        enabledExerciseIds.forEach(id -> metisUtilService.saveConfig(id, false, false, null, null));
        enabledExerciseIds.clear();
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Nested
    class Generation {

        @Test
        void shouldGenerateTheQuestionsFromTheOwnCodeOfTheStudent() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            submitOwnCode("student2");
            stubConfiguredLlm();
            stubGeneration(MetisLlmTestResponses.questionsJson(QUESTIONS, List.of(5, 10000, 0)));

            dispatchAt(NOW);

            List<String> userPrompts = capturedGenerationUserPrompts(2);
            String prompt = userPrompts.stream().filter(candidate -> candidate.contains(marker("student1"))).findFirst().orElseThrow();
            assertThat(prompt)
                    .contains("### Question 1: focus file", "### Question 3:", SORTER_PATH, HELPER_PATH, "--- BEGIN STUDENT FILE 1 ---", "--- BEGIN DIFF 1",
                            "+        int[] result = input.clone();", "-        // TODO: sort the input", SORTER_PATH + ": +", HELPER_PATH + ": +")
                    .doesNotContain(TEST_FILE_CONTENT, TEST_PATH, "README.md", marker("student2"), "{{");

            MetisCheck check = checkOf(own.participation());
            assertThat(check.getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(check.getCommitHash()).isEqualTo(own.commitHash());
            assertThat(check.getLlmModel()).isEqualTo(MetisLlmTestResponses.TEST_MODEL);
            assertThat(check.getPromptVersion()).isEqualTo(MetisQuestionGenerationService.PROMPT_VERSION);
            assertThat(check.getGeneratedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(check.getFailureReason()).isNull();

            List<MetisQuestion> questions = questionsOf(check);
            assertThat(questions).extracting(MetisQuestion::getQuestionOrder).containsExactly(1, 2, 3);
            assertThat(questions).extracting(MetisQuestion::getQuestionText).containsExactlyElementsOf(QUESTIONS);
            // Below the shortest limit, above the longest one, and none at all, which becomes the middle of the range
            assertThat(questions).extracting(MetisQuestion::getTimeLimitSeconds).containsExactly(60, 300, 180);
            assertThat(questions).extracting(MetisQuestion::getFocusFilePath).containsOnly(SORTER_PATH, HELPER_PATH).contains(SORTER_PATH, HELPER_PATH);
            assertThat(questions).allSatisfy(question -> {
                assertThat(question.getFocusFileChangedLines()).isPositive();
                assertThat(question.getParentQuestionId()).isNull();
                assertThat(question.getRegenerationCount()).isZero();
                assertThat(question.getApprovedAt()).isNull();
                assertThat(question.getShownAt()).isNull();
            });
        }

        @Test
        void shouldBillTheGenerationToTheStudentWhoseCodeItProcesses() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());

            dispatchAt(NOW);

            ArgumentCaptor<Attribution> attribution = ArgumentCaptor.forClass(Attribution.class);
            verify(metisLlmService).complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), attribution.capture());
            assertThat(own.participation().getParticipantIdentifier()).isEqualTo(TEST_PREFIX + "student1");
            assertThat(attribution.getValue()).isEqualTo(new Attribution(course.getId(), exerciseId, user("student1").getId()));
        }

        @Test
        void shouldGenerateEveryClaimableCheckInOneDispatch() throws Exception {
            enable(false, null, null);
            List<Submitted> submitted = List.of(submitOwnCode("student1"), submitOwnCode("student2"), submitOwnCode("student3"));
            stubConfiguredLlm();
            stubGeneration(threeQuestions());

            dispatchAt(NOW);

            capturedGenerationUserPrompts(3);
            assertThat(submitted).allSatisfy(own -> assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY));
        }

        @Test
        void shouldClaimTheChecksInTheOrderOfTheWindowsOfTheStudents() throws Exception {
            enable(false, null, null);
            TutorialGroup late = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Metis late", "", 10, false, "Garching", "English", user("tutor1"),
                    Set.of(user("student1")));
            TutorialGroup early = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Metis early", "", 10, false, "Garching", "English", user("tutor1"),
                    Set.of(user("student2")));
            metisUtilService.saveGroupWindow(exerciseId, late.getId(), NOW.plusHours(3), NOW.plusHours(4));
            metisUtilService.saveGroupWindow(exerciseId, early.getId(), NOW.plusHours(1), NOW.plusHours(2));
            submitOwnCode("student1");
            submitOwnCode("student2");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());

            dispatchAt(NOW);

            List<String> userPrompts = capturedGenerationUserPrompts(2);
            assertThat(userPrompts.get(0)).contains(marker("student2"));
            assertThat(userPrompts.get(1)).contains(marker("student1"));
        }

        @Test
        void shouldPutTheChecksIntoReviewWhenTheExerciseRequiresIt() throws Exception {
            enable(true, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());

            dispatchAt(NOW);

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(questionsOf(checkOf(own.participation()))).hasSize(3).allSatisfy(question -> assertThat(question.getApprovedAt()).isNull());
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        void shouldApplyAReviewSettingThatChangedDuringTheGeneration(boolean reviewRequiredBefore) throws Exception {
            enable(reviewRequiredBefore, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any())).thenAnswer(invocation -> {
                // The editor switches the review while the model is working
                metisUtilService.saveConfig(exerciseId, true, !reviewRequiredBefore, null, null);
                return MetisLlmTestResponses.completion(threeQuestions());
            });

            dispatchAt(NOW);

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(reviewRequiredBefore ? MetisCheckStatus.READY : MetisCheckStatus.IN_REVIEW);
        }

        @Test
        void shouldParseTheOutputOfAReasoningModelAndMapTheQuestionsByTheirNumbers() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration("""
                    <think>The student wrote a bubble sort {"questions": []}</think>
                    ```json
                    {"questions": [
                      {"number": 3, "question": "  Third  ", "timeLimitSeconds": 120},
                      {"number": 1, "question": "First", "timeLimitSeconds": 120},
                      {"number": 2, "question": "Second", "timeLimitSeconds": 120},
                      {"number": 4, "question": "Fourth, more than asked for", "timeLimitSeconds": 120}
                    ]}
                    ```""");

            dispatchAt(NOW);

            assertThat(questionsOf(checkOf(own.participation()))).extracting(MetisQuestion::getQuestionText).containsExactly("First", "Second", "Third");
        }

        @Test
        void shouldMapTheQuestionsByTheirPositionWhenTheirNumbersDoNotFit() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration("""
                    {"questions": [
                      {"number": 0, "question": "First", "timeLimitSeconds": 120},
                      {"number": 0, "question": "Second", "timeLimitSeconds": 120},
                      {"number": 7, "question": "Third", "timeLimitSeconds": 120}
                    ]}""");

            dispatchAt(NOW);

            assertThat(questionsOf(checkOf(own.participation()))).extracting(MetisQuestion::getQuestionText).containsExactly("First", "Second", "Third");
        }

        @Test
        void shouldDeleteTheQuestionsOfARunThatLostItsCheckWhenTheCheckIsClaimedAgain() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            AtomicInteger calls = new AtomicInteger();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any())).thenAnswer(invocation -> {
                if (calls.incrementAndGet() == 1) {
                    // A restart of the scheduling node puts the check back into the queue while the model is working
                    checkRepository.releaseAllGenerating();
                }
                return MetisLlmTestResponses.completion(threeQuestions());
            });

            dispatchAt(NOW);

            // The first run stores its questions but cannot mark the check as generated; the next claim deletes them before it generates again
            capturedGenerationUserPrompts(2);
            MetisCheck check = checkOf(own.participation());
            assertThat(check.getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(questionsOf(check)).extracting(MetisQuestion::getQuestionOrder).containsExactly(1, 2, 3);
        }

        @Test
        void shouldCutTheQuestionTextToTheLongestStoredText() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(MetisLlmTestResponses.questionsJson(List.of("x".repeat(2500), "Second", "Third"), List.of(120, 120, 120)));

            dispatchAt(NOW);

            assertThat(questionsOf(checkOf(own.participation())).getFirst().getQuestionText()).hasSize(2000);
        }

        @Test
        void shouldStoreTheQuestionTextsWithoutControlCharacters() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            // The model writes the zero character of a C string as a JSON escape; PostgreSQL refuses to store that character
            String question = "Why does a C string end with '" + Character.toString(0) + "'?\n\tExplain.";
            stubGeneration(MetisLlmTestResponses.questionsJson(List.of(question, "Second", "Third"), List.of(120, 120, 120)));

            dispatchAt(NOW);

            MetisCheck check = checkOf(own.participation());
            assertThat(check.getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(questionsOf(check).getFirst().getQuestionText()).isEqualTo("Why does a C string end with ''?\n\tExplain.");
        }

        @Test
        void shouldKeepTheGenerationPromptWithinItsBudgetByLeavingOutFullContentsFirst() throws Exception {
            enable(false, null, null);
            Map<String, String> largeFiles = Map.of("src/First.java", largeFile("First"), "src/Second.java", largeFile("Second"), "src/Third.java", largeFile("Third"));
            Submitted own = submit("student1", largeFiles, DUE.minusMinutes(30));
            stubConfiguredLlm();
            stubGeneration(threeQuestions());

            dispatchAt(NOW);

            String prompt = capturedGenerationUserPrompts(1).getFirst();
            assertThat(prompt.length()).isLessThanOrEqualTo(metisProperties.generation().maxPromptCharacters());
            assertThat(prompt).contains("### Question 1: focus file", "### Question 2: focus file", "### Question 3: focus file", "(full file omitted, too large)");
            assertThat(prompt).contains("--- BEGIN DIFF 1", "--- BEGIN DIFF 2", "--- BEGIN DIFF 3");
            assertThat(countOccurrences(prompt, "--- BEGIN STUDENT FILE")).isLessThan(3);
            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }
    }

    @Nested
    class SkipsAndFailures {

        @Test
        void shouldSkipACheckWhoseWindowHasEndedWithoutReadingTheRepositoryOrCallingTheLlm() throws Exception {
            enable(false, DUE.plusMinutes(10), NOW.minusMinutes(1));
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            clearInvocations(bareGitRepositoryServiceSpy);

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED, null);
            assertNoRepositoryReadAndNoLlmCall();
        }

        @Test
        void shouldSkipACheckWhoseIndividualDueDateLiesAfterTheEndOfTheWindow() throws Exception {
            enable(false, NOW.minusHours(1), NOW.plusHours(1));
            Submitted own = submitOwnCode("student1");
            withIndividualDueDate(own.participation(), NOW.plusHours(2));
            stubConfiguredLlm();
            clearInvocations(bareGitRepositoryServiceSpy);

            dispatchAt(NOW.plusHours(2).plusMinutes(2));

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED, null);
            assertNoRepositoryReadAndNoLlmCall();
        }

        @Test
        void shouldSkipStudentsWithoutConsentWithoutReadingTheirRepositoriesOrCallingTheLlm() throws Exception {
            enable(false, null, null);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);
            userUtilService.clearAiSelectionDecision(user("student2"));
            Submitted optedOut = submitOwnCode("student1");
            Submitted undecided = submitOwnCode("student2");
            stubConfiguredLlm();
            clearInvocations(bareGitRepositoryServiceSpy);

            dispatchAt(NOW);

            assertUnavailable(optedOut, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT, null);
            assertUnavailable(undecided, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT, null);
            assertNoRepositoryReadAndNoLlmCall();
        }

        @Test
        void shouldSkipAPendingCheckWhoseStudentWithdrewTheConsentBeforeItWasClaimed() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            metisUtilService.saveCheck(own.participation().getId(), MetisCheckStatus.PENDING);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);
            stubConfiguredLlm();
            clearInvocations(bareGitRepositoryServiceSpy);

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT, null);
            assertNoRepositoryReadAndNoLlmCall();
        }

        @Test
        void shouldSkipACheckWithoutASubmissionBeforeTheDueDate() throws Exception {
            enable(false, null, null);
            Submitted own = submit("student1", ownCode("student1"), DUE.plusMinutes(10));
            stubConfiguredLlm();

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION, null);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldSkipACheckWhoseSubmissionIsTheTemplate() throws Exception {
            enable(false, null, null);
            Submitted own = submit("student1", Map.of(), DUE.minusMinutes(30));
            stubConfiguredLlm();

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE, own.commitHash());
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldSkipACheckWhoseOwnCodeOnlyChangedWhitespace() throws Exception {
            enable(false, null, null);
            Submitted own = submit("student1", Map.of(SORTER_PATH, TEMPLATE_SORTER.replace("    ", "\t")), DUE.minusMinutes(30));
            stubConfiguredLlm();

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE, own.commitHash());
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldFailACheckWhoseCommitIsNotInTheRepository() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            String unknownCommit = "f".repeat(40);
            addSubmission(own.participation(), unknownCommit, DUE.minusMinutes(10));
            stubConfiguredLlm();

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.REPOSITORY_UNAVAILABLE, unknownCommit);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldFailACheckAfterTwoUnusableOutputs() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            String twoQuestionsOnly = MetisLlmTestResponses.questionsJson(QUESTIONS.subList(0, 2), List.of(120, 120));
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenReturn(MetisLlmTestResponses.completion(twoQuestionsOnly), MetisLlmTestResponses.completion("I cannot answer that."));

            dispatchAt(NOW);

            capturedGenerationUserPrompts(2);
            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.INVALID_LLM_RESPONSE, own.commitHash());
            assertThat(questionsOf(checkOf(own.participation()))).isEmpty();
        }

        @Test
        void shouldTreatAnOutputOfTheWrongShapeOrWithABlankQuestionAsUnusable() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            String blankQuestion = MetisLlmTestResponses.questionsJson(List.of(QUESTIONS.get(0), " ", QUESTIONS.get(2)), List.of(120, 120, 120));
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenReturn(MetisLlmTestResponses.completion("{\"questions\": \"none\"}"), MetisLlmTestResponses.completion(blankQuestion));

            dispatchAt(NOW);

            capturedGenerationUserPrompts(2);
            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.INVALID_LLM_RESPONSE, own.commitHash());
        }

        @Test
        void shouldRetryAnEmptyOutputOnceAndUseTheSecondOne() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "empty answer")).thenReturn(MetisLlmTestResponses.completion(threeQuestions()));

            dispatchAt(NOW);

            capturedGenerationUserPrompts(2);
            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldFailACheckWhoseRequestTheEndpointRejects() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.REJECTED, "context too long"));

            dispatchAt(NOW);

            capturedGenerationUserPrompts(1);
            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.LLM_REJECTED, own.commitHash());
        }

        @Test
        void shouldFailACheckWithAnInternalErrorWhenTheGenerationBreaksUnexpectedly() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any())).thenThrow(new IllegalStateException("unexpected"));

            dispatchAt(NOW);

            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR, null);
        }
    }

    @Nested
    class ClaimedChecks {

        @Test
        void shouldPutAClaimedCheckBackIntoTheQueueWhenTheExerciseWasSwitchedOffMeanwhile() {
            metisUtilService.saveConfig(exerciseId, false, false, null, null);
            MetisCheck claimed = metisUtilService.saveCheck(participation("student1").getId(), MetisCheckStatus.GENERATING);

            assertThat(generationService.generate(claimed.getId(), false)).isEqualTo(Outcome.DEFERRED);

            assertThat(checkRepository.findByIdElseThrow(claimed.getId()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldPutAClaimedCheckBackIntoTheQueueWhenItsDueDateMovedIntoTheFuture() {
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation extended = withIndividualDueDate(participation("student1"), NOW.plusHours(1));
            MetisCheck claimed = metisUtilService.saveCheck(extended.getId(), MetisCheckStatus.GENERATING);

            assertThat(generationService.generate(claimed.getId(), false)).isEqualTo(Outcome.DEFERRED);

            assertThat(checkRepository.findByIdElseThrow(claimed.getId()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldLeaveACheckAloneThatIsNotBeingGenerated() {
            enable(false, null, null);
            MetisCheck ready = metisUtilService.saveCheck(participation("student1").getId(), MetisCheckStatus.READY);

            assertThat(generationService.generate(ready.getId(), false)).isEqualTo(Outcome.DEFERRED);
            assertThat(generationService.generate(Long.MAX_VALUE, false)).isEqualTo(Outcome.DEFERRED);

            assertThat(checkRepository.findByIdElseThrow(ready.getId()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }
    }

    @Nested
    class CircuitBreaker {

        @Test
        void shouldStopClaimingOnceTheEndpointIsUnavailableAndClaimAgainAfterTheBackoff() throws Exception {
            enable(false, null, null);
            Submitted first = submitOwnCode("student1");
            Submitted second = submitOwnCode("student2");
            stubConfiguredLlm();
            stubUnavailableGeneration();

            dispatchAt(NOW);

            capturedGenerationUserPrompts(1);
            assertThat(checkOf(first.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(checkOf(second.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);

            dispatchAt(NOW.plusSeconds(30));

            capturedGenerationUserPrompts(1);

            dispatchAt(NOW.plusSeconds(61));

            capturedGenerationUserPrompts(2);
            assertThat(checkOf(first.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(checkOf(second.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }

        @Test
        void shouldFailACheckAfterTheConfiguredAttemptsAndPauseBeforeTheNextCheck() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            Submitted next = submitOwnCode("student2");
            stubConfiguredLlm();
            stubUnavailableGeneration();
            assertThat(metisProperties.generation().maxTransientFailures()).isEqualTo(3);

            dispatchAt(NOW);
            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            // The backoff doubles: 60 seconds after the first failure, 120 after the second
            dispatchAt(NOW.plusSeconds(61));
            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            dispatchAt(NOW.plusSeconds(61 + 119));
            capturedGenerationUserPrompts(2);
            dispatchAt(NOW.plusSeconds(61 + 121));

            // The last attempt fails the check, and the endpoint is just as unavailable for the next check, which therefore waits for the next pause to end
            assertThat(capturedGenerationUserPrompts(3)).allSatisfy(prompt -> assertThat(prompt).contains(marker("student1")));
            assertUnavailable(own, MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE, own.commitHash());
            assertThat(checkOf(next.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);

            dispatchAt(NOW.plusSeconds(61 + 121 + 239));
            capturedGenerationUserPrompts(3);
            dispatchAt(NOW.plusSeconds(61 + 121 + 241));

            assertThat(capturedGenerationUserPrompts(4).getLast()).contains(marker("student2"));
            assertThat(checkOf(next.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }

        @Test
        void shouldCloseTheCircuitAndResetTheBackoffAfterASuccessfulGeneration() throws Exception {
            enable(false, null, null);
            Submitted first = submitOwnCode("student1");
            Submitted second = submitOwnCode("student2");
            stubConfiguredLlm();
            MetisLlmException unavailable = new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "down");
            Completion questions = MetisLlmTestResponses.completion(threeQuestions());
            when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any())).thenThrow(unavailable).thenReturn(questions)
                    .thenThrow(unavailable).thenReturn(questions);

            // The first failure pauses for 60 seconds and doubles the backoff to 120
            dispatchAt(NOW);
            // The first check succeeds, which closes the circuit; the chain claims the second check, whose failure pauses again
            dispatchAt(NOW.plusSeconds(61));

            capturedGenerationUserPrompts(3);
            assertThat(checkOf(first.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(checkOf(second.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);

            // The success reset the backoff, so this pause lasts 60 seconds again instead of 120
            dispatchAt(NOW.plusSeconds(61 + 59));
            capturedGenerationUserPrompts(3);
            dispatchAt(NOW.plusSeconds(61 + 61));

            capturedGenerationUserPrompts(4);
            assertThat(checkOf(second.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldNotClaimWhileLiveRatingsWaitForAPermit() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());
            when(metisLlmService.interactiveWaiting()).thenReturn(1);

            dispatchAt(NOW);

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());

            when(metisLlmService.interactiveWaiting()).thenReturn(0);
            dispatchAt(NOW.plusSeconds(30));

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldNotClaimWhileTheEndpointIsNotConfigured() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");

            dispatchAt(NOW);
            dispatchAt(NOW.plusMinutes(1));

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }
    }

    @Nested
    class CheckCreation {

        @Test
        void shouldCreateNoCheckBeforeTheEffectiveDueDateAndTheDelayAfterIt() throws Exception {
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation regular = participation("student1");
            ProgrammingExerciseStudentParticipation extended = withIndividualDueDate(participation("student2"), NOW.plusHours(1));
            ProgrammingExerciseStudentParticipation justDue = withIndividualDueDate(participation("student3"), NOW.minusSeconds(30));

            dispatchAt(NOW);

            assertThat(checkRepository.findByParticipationId(regular.getId())).isPresent();
            assertThat(checkRepository.findByParticipationId(extended.getId())).isEmpty();
            assertThat(checkRepository.findByParticipationId(justDue.getId())).isEmpty();
        }

        @Test
        void shouldCreateNoCheckBeforeTheDueDateOfTheExercise() throws Exception {
            exerciseUtilService.updateExerciseDueDate(exerciseId, NOW.minusSeconds(30));
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation own = participation("student1");

            dispatchAt(NOW);

            assertThat(checkRepository.findByParticipationId(own.getId())).isEmpty();

            dispatchAt(NOW.plusSeconds(31));

            assertThat(checkRepository.findByParticipationId(own.getId())).isPresent();
        }

        @Test
        void shouldCreateNoDuplicateChecks() throws Exception {
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation first = participation("student1");
            ProgrammingExerciseStudentParticipation second = participation("student2");
            ZonedDateTime cutoff = NOW.plusMinutes(1).minusSeconds(metisProperties.generation().delayAfterDueDateSeconds());

            dispatchAt(NOW);

            // The second sweep finds no candidate, so it does not even try to insert a check that the unique key would refuse
            List<Long> checkIds = List.of(checkOf(first).getId(), checkOf(second).getId());
            assertThat(checkRepository.findCheckCandidates(exerciseId, cutoff)).isEmpty();

            dispatchAt(NOW.plusMinutes(1));

            assertThat(checkRepository.findDashboardRows(exerciseId)).extracting(MetisParticipationRowDTO::checkId).containsExactlyInAnyOrderElementsOf(checkIds);
        }

        @Test
        void shouldWaitForTheEnableDelayOnlyForExercisesEnabledAfterTheFirstSweep() throws Exception {
            Course otherCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
            ProgrammingExercise enabledLater = pastExerciseOf(otherCourse);
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation enabledAtStartup = participation("student1");
            ProgrammingExerciseStudentParticipation ofLater = participationUtilService.addStudentParticipationForProgrammingExercise(enabledLater, TEST_PREFIX + "student1");

            dispatchAt(NOW);

            assertThat(checkRepository.findByParticipationId(enabledAtStartup.getId())).isPresent();

            enable(enabledLater.getId());
            dispatchAt(NOW.plusMinutes(1));
            dispatchAt(NOW.plusMinutes(10));

            assertThat(checkRepository.findByParticipationId(ofLater.getId())).isEmpty();

            dispatchAt(NOW.plusMinutes(11));

            assertThat(checkRepository.findByParticipationId(ofLater.getId())).isPresent();
        }

        @Test
        void shouldReleaseTheChecksACrashLeftInGenerationAfterTheStartup() throws Exception {
            ProgrammingExerciseStudentParticipation own = participation("student1");
            MetisCheck orphan = metisUtilService.saveCheck(own.getId(), MetisCheckStatus.GENERATING);

            dispatchService.resetAfterStartup();

            assertThat(checkRepository.findByIdElseThrow(orphan.getId()).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }

        @Test
        void shouldGenerateAgainAfterAnExtensionOfTheDueDate() throws Exception {
            enable(false, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());
            dispatchAt(NOW);
            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);

            withIndividualDueDate(own.participation(), NOW.plusHours(1));
            dispatchAt(NOW.plusMinutes(1));

            MetisCheck reset = checkOf(own.participation());
            assertThat(reset.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reset.getFailureReason()).isNull();

            Map<String, String> improvedHelper = Map.of(HELPER_PATH, ownHelper(marker("student1")) + "// improved\n");
            String laterCommit = RepositoryExportTestUtil.writeFilesAndPush(own.repository(), improvedHelper, "Improve the helper").name();
            addSubmission(own.participation(), laterCommit, NOW.plusMinutes(30));
            dispatchAt(NOW.plusHours(1).plusMinutes(2));

            capturedGenerationUserPrompts(2);
            MetisCheck regenerated = checkOf(own.participation());
            assertThat(regenerated.getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(regenerated.getCommitHash()).isEqualTo(laterCommit);
            assertThat(questionsOf(regenerated)).hasSize(3);
        }

        @Test
        void shouldQueueASkippedCheckAgainOnceTheStudentConsents() throws Exception {
            enable(false, null, null);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());
            dispatchAt(NOW);
            assertUnavailable(own, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT, null);

            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.CLOUD_AI);
            dispatchAt(NOW.plusMinutes(1));

            assertThat(checkOf(own.participation()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }
    }

    @Nested
    class GenerateNow {

        @Test
        void shouldCreateTheMissingChecksAndQueueTheFailedOnesAgain() throws Exception {
            enable(false, null, null);
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student1");
            MetisCheck failed = unavailableCheck(participation("student2"), MetisCheckStatus.FAILED, MetisFailureReason.INVALID_LLM_RESPONSE);
            MetisCheck withoutConsent = unavailableCheck(participation("student3"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            MetisCheck windowClosed = unavailableCheck(participation("student4"), MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            MetisCheck withoutSubmission = unavailableCheck(participation("student5"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            MetisCheck withoutOwnCode = unavailableCheck(participation("student6"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE);

            MetisGenerateResultDTO result = dispatchService.generateNow(exerciseId);

            assertThat(result).isEqualTo(new MetisGenerateResultDTO(1, 3));
            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).hasValueSatisfying(check -> assertThat(check.getStatus()).isEqualTo(MetisCheckStatus.PENDING));
            for (MetisCheck requeued : List.of(failed, withoutConsent, windowClosed)) {
                MetisCheck stored = checkRepository.findByIdElseThrow(requeued.getId());
                assertThat(stored.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
                assertThat(stored.getFailureReason()).isNull();
            }
            assertThat(checkRepository.findByIdElseThrow(withoutSubmission.getId()).getFailureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            assertThat(checkRepository.findByIdElseThrow(withoutOwnCode.getId()).getFailureReason()).isEqualTo(MetisFailureReason.NO_OWN_CODE.name());
            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        @Test
        void shouldRefuseAnExerciseWithoutAnEnabledCheck() {
            metisUtilService.saveConfig(exerciseId, false, false, null, null);

            assertGenerateNowFails(exerciseId, ConflictException.class, "metis.notEnabled");
        }

        @Test
        void shouldRefuseAnExerciseWithoutADueDate() {
            enable(false, null, null);
            exerciseUtilService.updateExerciseDueDate(exerciseId, null);

            assertGenerateNowFails(exerciseId, ConflictException.class, "metis.noDueDate");
        }

        @Test
        void shouldRefuseAnUnknownExercise() {
            assertGenerateNowFails(Long.MAX_VALUE, BadRequestAlertException.class, "metis.unsupportedExercise");
        }

        @Test
        void shouldRefuseATeamExercise() {
            enable(false, null, null);
            Exercise teamExercise = exerciseRepository.findByIdElseThrow(exerciseId);
            teamExercise.setMode(ExerciseMode.TEAM);
            exerciseRepository.save(teamExercise);

            assertGenerateNowFails(exerciseId, BadRequestAlertException.class, "metis.unsupportedExercise");
        }

        private void assertGenerateNowFails(long requestedExerciseId, Class<? extends Exception> exceptionType, String errorKey) {
            assertThatThrownBy(() -> dispatchService.generateNow(requestedExerciseId)).isInstanceOf(exceptionType).extracting("errorKey").isEqualTo(errorKey);
        }
    }

    @Nested
    class Regeneration {

        private static final String NEW_QUESTION = "Which value does sort compare first?";

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReplaceTheQuestionAndKeepTheCheckInReviewWhileTheExerciseRequiresIt() throws Exception {
            Generated generated = generatedAndApproved(true);
            MetisQuestion question = generated.questions().getFirst();
            AtomicReference<MetisCheckStatus> statusDuringTheCall = new AtomicReference<>();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                statusDuringTheCall.set(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus());
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.questionsJson(List.of(NEW_QUESTION), List.of(150)));
            });

            // Through the endpoint, so that the resource and the review service that hands the request on are covered as well
            String path = "/api/metis/exercises/" + exerciseId + "/questions/" + question.getId() + "/regenerate";
            MetisQuestionDTO regenerated = request.postWithResponseBody(path, null, MetisQuestionDTO.class, HttpStatus.OK);

            assertThat(statusDuringTheCall.get()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(regenerated.id()).isEqualTo(question.getId());
            assertThat(regenerated.questionText()).isEqualTo(NEW_QUESTION);
            assertThat(regenerated.timeLimitSeconds()).isEqualTo(150);
            assertThat(regenerated.regenerationCount()).isEqualTo(1);
            assertThat(regenerated.approvedAt()).isNull();
            assertThat(regenerated.focusFilePath()).isEqualTo(question.getFocusFilePath());
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(questionsOf(generated.check()).subList(1, 3)).allSatisfy(other -> assertThat(other.getApprovedAt()).isNotNull());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldAskForADifferentQuestionAboutTheSameFocusFileAndBillTheEditor() throws Exception {
            Generated generated = generated(false);
            MetisQuestion question = generated.questions().getFirst();
            stubRegeneration(MetisLlmTestResponses.questionsJson(List.of(NEW_QUESTION), List.of(150)));

            generationService.regenerateQuestion(exerciseId, question.getId());

            ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Attribution> attribution = ArgumentCaptor.forClass(Attribution.class);
            verify(metisLlmService).complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), systemPrompt.capture(), userPrompt.capture(), attribution.capture());
            assertThat(systemPrompt.getValue()).contains("Write exactly 1 questions").doesNotContain("{{");
            String prompt = userPrompt.getValue();
            assertThat(prompt).contains("--- BEGIN OLD QUESTION ---\n" + question.getQuestionText(), "### Question 1: focus file " + question.getFocusFilePath());
            assertThat(prompt).contains(generated.questions().get(1).getQuestionText(), generated.questions().get(2).getQuestionText());
            assertThat(prompt).contains("--- BEGIN DIFF 1", marker("student1")).doesNotContain(TEST_FILE_CONTENT, "{{");
            assertThat(attribution.getValue()).isEqualTo(new Attribution(course.getId(), exerciseId, user("instructor1").getId()));
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getQuestionText()).isEqualTo(NEW_QUESTION);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReleaseTheCheckAgainWhenTheLlmIsUnavailable() throws Exception {
            Generated generated = generatedAndApproved(true);
            MetisQuestion question = generated.questions().getFirst();
            AtomicReference<MetisCheckStatus> statusDuringTheCall = new AtomicReference<>();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                statusDuringTheCall.set(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus());
                throw new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "down");
            });

            assertRegenerationFails(question, InternalServerErrorAlertException.class, "metis.llmUnavailable");

            assertThat(statusDuringTheCall.get()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.READY);
            MetisQuestion unchanged = questionRepository.findByIdElseThrow(question.getId());
            assertThat(unchanged.getQuestionText()).isEqualTo(question.getQuestionText());
            assertThat(unchanged.getRegenerationCount()).isZero();
            assertThat(unchanged.getApprovedAt()).isNotNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAnUnusableOutputAfterOneCall() throws Exception {
            Generated generated = generated(false);
            stubRegeneration("I would rather not.");

            assertRegenerationFails(generated.questions().getFirst(), InternalServerErrorAlertException.class, "metis.invalidLlmResponse");

            verify(metisLlmService, times(1)).complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseARejectedRequestAsUnavailable() throws Exception {
            Generated generated = generated(false);
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.REJECTED, "context too long"));

            assertRegenerationFails(generated.questions().getFirst(), InternalServerErrorAlertException.class, "metis.llmUnavailable");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldKeepTheTextWhenTheStudentStartsTheCheckDuringTheCall() throws Exception {
            Generated generated = generated(false);
            MetisQuestion question = generated.questions().getFirst();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                checkRepository.markStarted(generated.check().getId(), NOW);
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.questionsJson(List.of(NEW_QUESTION), List.of(150)));
            });

            assertRegenerationFails(question, ConflictException.class, "metis.notEditable");

            assertThat(questionRepository.findByIdElseThrow(question.getId()).getQuestionText()).isEqualTo(question.getQuestionText());
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseToOverwriteAQuestionAnotherEditorChangedDuringTheCall() throws Exception {
            Generated generated = generated(false);
            MetisQuestion question = generated.questions().getFirst();
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                update(question, changed -> changed.setRegenerationCount(1));
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.questionsJson(List.of(NEW_QUESTION), List.of(150)));
            });

            assertRegenerationFails(question, ConflictException.class, "metis.questionChanged");

            assertThat(questionRepository.findByIdElseThrow(question.getId()).getQuestionText()).isEqualTo(question.getQuestionText());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldKeepTheTextAnotherEditorEditedDuringTheCall() throws Exception {
            Generated generated = generatedAndApproved(true);
            MetisQuestion question = generated.questions().getFirst();
            String edited = "Which value does sort compare last?";
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any())).thenAnswer(invocation -> {
                // The check is in review during the call, which allows an edit, and an edit leaves the regeneration count as it is
                questionReviewService.updateQuestionText(exerciseId, question.getId(), edited);
                return MetisLlmTestResponses.completion(MetisLlmTestResponses.questionsJson(List.of(NEW_QUESTION), List.of(150)));
            });

            assertRegenerationFails(question, ConflictException.class, "metis.questionChanged");

            MetisQuestion kept = questionRepository.findByIdElseThrow(question.getId());
            assertThat(kept.getQuestionText()).isEqualTo(edited);
            assertThat(kept.getRegenerationCount()).isZero();
            assertThat(kept.getApprovedAt()).isNotNull();
            // Nothing was replaced, so the check, whose main questions are all still approved, is ready again
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAShownQuestion() throws Exception {
            Generated generated = generated(false);
            MetisQuestion question = update(generated.questions().getFirst(), shown -> shown.setShownAt(NOW));

            assertRegenerationFails(question, ConflictException.class, "metis.questionShown");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAFollowUpQuestion() throws Exception {
            Generated generated = generated(false);
            MetisQuestion followUp = metisUtilService.saveFollowUp(generated.check().getId(), 4, generated.questions().getFirst().getId(), "Why?", 60);

            assertRegenerationFails(followUp, BadRequestAlertException.class, "metis.notMainQuestion");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAQuestionOfAnotherExercise() throws Exception {
            Generated generated = generated(false);
            ProgrammingExercise otherExercise = pastExerciseOf(programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX));

            assertThatThrownBy(() -> generationService.regenerateQuestion(otherExercise.getId(), generated.questions().getFirst().getId()))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAQuestionRegeneratedTooOften() throws Exception {
            Generated generated = generated(false);
            MetisQuestion question = update(generated.questions().getFirst(), often -> often.setRegenerationCount(20));

            assertRegenerationFails(question, ConflictException.class, "metis.regenerationLimit");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAQuestionOfAStartedCheck() throws Exception {
            Generated generated = generated(false);
            checkRepository.markStarted(generated.check().getId(), NOW);

            assertRegenerationFails(generated.questions().get(1), ConflictException.class, "metis.notEditable");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAQuestionOfAStudentWithoutConsent() throws Exception {
            Generated generated = generated(false);
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);

            assertRegenerationFails(generated.questions().getFirst(), ConflictException.class, "metis.aiConsentMissing");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseAQuestionWhoseRepositoryCannotBeRead() throws Exception {
            Generated generated = generated(false);
            update(generated.check(), check -> check.setCommitHash("0".repeat(40)));

            assertRegenerationFails(generated.questions().getFirst(), ConflictException.class, "metis.repositoryUnavailable");
            verify(metisLlmService, never()).complete(eq(Pipeline.QUESTION_REGENERATION), any(), anyString(), anyString(), any());
        }

        /**
         * A generated check of student1 with its three main questions, ready, or in review when the exercise requires it.
         */
        private Generated generated(boolean reviewRequired) throws Exception {
            enable(reviewRequired, null, null);
            Submitted own = submitOwnCode("student1");
            stubConfiguredLlm();
            stubGeneration(threeQuestions());
            dispatchAt(NOW);
            MetisCheck check = checkOf(own.participation());
            assertThat(check.getStatus()).isEqualTo(reviewRequired ? MetisCheckStatus.IN_REVIEW : MetisCheckStatus.READY);
            return new Generated(check, questionsOf(check));
        }

        /**
         * A generated check whose questions an editor approved, which released it to ready.
         */
        private Generated generatedAndApproved(boolean reviewRequired) throws Exception {
            Generated generated = generated(reviewRequired);
            questionRepository.approveAllOpenMainQuestionsOfExercise(exerciseId, NOW);
            checkRepository.releaseApprovedChecksOfExercise(exerciseId);
            assertThat(checkRepository.findByIdElseThrow(generated.check().getId()).getStatus()).isEqualTo(MetisCheckStatus.READY);
            return new Generated(checkRepository.findByIdElseThrow(generated.check().getId()), questionsOf(generated.check()));
        }

        private void stubRegeneration(String output) {
            when(metisLlmService.complete(eq(Pipeline.QUESTION_REGENERATION), eq(Mode.INTERACTIVE), anyString(), anyString(), any()))
                    .thenReturn(MetisLlmTestResponses.completion(output));
        }

        private void assertRegenerationFails(MetisQuestion question, Class<? extends Exception> exceptionType, String errorKey) {
            assertThatThrownBy(() -> generationService.regenerateQuestion(exerciseId, question.getId())).isInstanceOf(exceptionType).extracting("errorKey").isEqualTo(errorKey);
        }
    }

    @Nested
    class RatingBackfill {

        @Test
        void shouldRateAStoredAnswerOnlyOnceItIsOlderThanTheSettleTime() throws Exception {
            MetisQuestion question = storedAnswer("It sorts the array in place with a bubble sort.", NOW.minus(metisProperties.answerSettleTime()).plusSeconds(1));
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any())).thenReturn(rating(4, "Correct and specific."));

            dispatchAt(NOW);

            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getLlmRatedAt()).isNull();

            dispatchAt(NOW.plusSeconds(2));
            dispatchAt(NOW.plusSeconds(3));

            verify(metisLlmService, times(1)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
            MetisQuestion rated = questionRepository.findByIdElseThrow(question.getId());
            assertThat(rated.getLlmScore()).isEqualTo(4);
            assertThat(rated.getLlmRationale()).isEqualTo("Correct and specific.");
            assertThat(rated.getLlmRatedAt().toInstant()).isEqualTo(NOW.plusSeconds(2).toInstant());
        }

        @Test
        void shouldRateAFailedAnswerAgainOnlyAfterTheRetryDelayAndGiveUpAfterThreeFailures() throws Exception {
            MetisQuestion question = storedAnswer("It sorts.", NOW.minusMinutes(9));
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenReturn(MetisLlmTestResponses.completion("No rating today."));

            // Every failed rating makes two calls, since an unusable output is asked for once more; the claim the task chains does not pick the answer again
            dispatchAt(NOW);
            verifyRatingCalls(2);
            dispatchAt(NOW.plus(RATING_RETRY_DELAY).minusSeconds(1));
            verifyRatingCalls(2);
            dispatchAt(NOW.plus(RATING_RETRY_DELAY));
            verifyRatingCalls(4);
            dispatchAt(NOW.plus(RATING_RETRY_DELAY.multipliedBy(2)));
            verifyRatingCalls(6);

            // After three failed ratings the backfill leaves the answer alone until the next restart
            dispatchAt(NOW.plus(RATING_RETRY_DELAY.multipliedBy(3)));
            verifyRatingCalls(6);
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getLlmRatedAt()).isNull();
        }

        @Test
        void shouldStoreTheRatingOfTheAttemptAfterTheRetryDelay() throws Exception {
            MetisQuestion question = storedAnswer("It sorts.", NOW.minusMinutes(9));
            stubConfiguredLlm();
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenReturn(MetisLlmTestResponses.completion("No rating today."), MetisLlmTestResponses.completion("Still none."), rating(2, "Vague."));

            dispatchAt(NOW);
            dispatchAt(NOW.plus(RATING_RETRY_DELAY));

            verifyRatingCalls(3);
            MetisQuestion rated = questionRepository.findByIdElseThrow(question.getId());
            assertThat(rated.getLlmScore()).isEqualTo(2);
            assertThat(rated.getLlmRatedAt().toInstant()).isEqualTo(NOW.plus(RATING_RETRY_DELAY).toInstant());
        }

        @Test
        void shouldLeaveAStoredAnswerUnratedWhoseStudentWithdrewTheConsent() throws Exception {
            MetisQuestion question = storedAnswer("It sorts.", NOW.minusMinutes(9));
            userUtilService.setAiSelectionDecision(user("student1"), AiSelectionDecision.NO_AI);
            stubConfiguredLlm();

            // The backfill gives up on the answer at once; otherwise the chain of claims would pick it again and again
            dispatchAt(NOW);
            dispatchAt(NOW.plusMinutes(1));

            verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getLlmRatedAt()).isNull();
        }

        @Test
        void shouldPauseTheBackfillWhileTheEndpointIsUnavailable() throws Exception {
            MetisQuestion question = storedAnswer("It sorts.", NOW.minusMinutes(9));
            stubConfiguredLlm();
            Completion rating = rating(3, "Partly right.");
            when(metisLlmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "down")).thenReturn(rating);

            dispatchAt(NOW);
            dispatchAt(NOW.plusSeconds(30));

            verify(metisLlmService, times(1)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getLlmRatedAt()).isNull();

            dispatchAt(NOW.plusSeconds(61));

            verify(metisLlmService, times(2)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
            assertThat(questionRepository.findByIdElseThrow(question.getId()).getLlmScore()).isEqualTo(3);
        }

        /**
         * An answered and unrated main question of the started check of student1, in an exercise whose check is enabled and whose window is open for another
         * hour.
         */
        private MetisQuestion storedAnswer(String answerText, ZonedDateTime answeredAt) {
            enable(false, null, NOW.plusHours(1));
            MetisCheck check = metisUtilService.saveGeneratedCheck(participation("student1").getId(), MetisCheckStatus.IN_PROGRESS, "c".repeat(40));
            MetisQuestion question = metisUtilService.saveMainQuestion(check.getId(), 1, SORTER_PATH, 12, "What does sort do?", 120);
            return update(question, answered -> {
                answered.setShownAt(answeredAt.minusSeconds(30));
                answered.setAnswerText(answerText);
                answered.setAnsweredAt(answeredAt);
            });
        }

        private static Completion rating(int score, String rationale) {
            return MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(score, rationale, "", 0));
        }

        private void verifyRatingCalls(int calls) {
            verify(metisLlmService, times(calls)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
        }
    }

    /**
     * A student's repository with the template and the student's own code, and a submission of the own code.
     *
     * @param participation the student's graded participation
     * @param repository    the repository with a working copy for further commits
     * @param commitHash    the commit of the submission
     */
    private record Submitted(ProgrammingExerciseStudentParticipation participation, LocalVCTestRepository repository, String commitHash) {
    }

    /**
     * A generated check with its main questions in their order.
     */
    private record Generated(MetisCheck check, List<MetisQuestion> questions) {
    }

    private Submitted submitOwnCode(String userSuffix) throws Exception {
        return submit(userSuffix, ownCode(userSuffix), DUE.minusMinutes(30));
    }

    /**
     * Pushes the template in a commit with the setup message and then the given own files, and submits the last commit at the given time. Without own files the
     * submission is the template commit.
     */
    private Submitted submit(String userSuffix, Map<String, String> ownFiles, ZonedDateTime submissionDate) throws Exception {
        ProgrammingExerciseStudentParticipation participation = participation(userSuffix);
        LocalVCTestRepository repository = RepositoryExportTestUtil.getWorkingCopyForParticipation(localVCLocalCITestService, participation);
        String templateCommit = RepositoryExportTestUtil.writeFilesAndPush(repository, TEMPLATE_FILES, SET_UP_TEMPLATE_FOR_EXERCISE).name();
        String commitHash = ownFiles.isEmpty() ? templateCommit : RepositoryExportTestUtil.writeFilesAndPush(repository, ownFiles, "Solve the exercise").name();
        addSubmission(participation, commitHash, submissionDate);
        return new Submitted(participation, repository, commitHash);
    }

    private void addSubmission(ProgrammingExerciseStudentParticipation participation, String commitHash, ZonedDateTime submissionDate) {
        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true, commitHash, SubmissionType.MANUAL);
        submission.setSubmissionDate(submissionDate);
        programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, participation.getParticipantIdentifier());
    }

    private ProgrammingExerciseStudentParticipation participation(String userSuffix) {
        return participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + userSuffix);
    }

    private ProgrammingExerciseStudentParticipation withIndividualDueDate(ProgrammingExerciseStudentParticipation participation, ZonedDateTime individualDueDate) {
        ProgrammingExerciseStudentParticipation stored = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation.getId());
        stored.setIndividualDueDate(individualDueDate);
        return programmingExerciseStudentParticipationRepository.save(stored);
    }

    /**
     * The programming exercise of a course, released a week before {@link #DUE} and due at it.
     */
    private ProgrammingExercise pastExerciseOf(Course exerciseCourse) {
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(exerciseCourse, ProgrammingExercise.class);
        Exercise stored = exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        stored.setReleaseDate(DUE.minusDays(7));
        exerciseRepository.save(stored);
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), DUE);
        return programmingExercise;
    }

    private MetisCheck unavailableCheck(ProgrammingExerciseStudentParticipation participation, MetisCheckStatus status, MetisFailureReason reason) {
        MetisCheck check = metisUtilService.saveCheck(participation.getId(), status);
        return update(check, unavailable -> unavailable.setFailureReason(reason.name()));
    }

    private void enable(boolean reviewRequired, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd) {
        metisUtilService.saveConfig(exerciseId, true, reviewRequired, windowStart, windowEnd);
        enabledExerciseIds.add(exerciseId);
    }

    private void enable(long otherExerciseId) {
        metisUtilService.saveConfig(otherExerciseId, true, false, null, null);
        enabledExerciseIds.add(otherExerciseId);
    }

    private void stubConfiguredLlm() {
        when(metisLlmService.isConfigured()).thenReturn(true);
    }

    private void stubGeneration(String output) {
        when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any())).thenReturn(MetisLlmTestResponses.completion(output));
    }

    private void stubUnavailableGeneration() {
        when(metisLlmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                .thenThrow(new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "down"));
    }

    /**
     * Verifies that exactly the given number of generation calls were made, and returns their user prompts in the order of the calls.
     */
    private List<String> capturedGenerationUserPrompts(int calls) {
        ArgumentCaptor<String> userPrompts = ArgumentCaptor.forClass(String.class);
        verify(metisLlmService, times(calls)).complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), userPrompts.capture(), any());
        return userPrompts.getAllValues();
    }

    private void assertNoRepositoryReadAndNoLlmCall() {
        verify(bareGitRepositoryServiceSpy, never()).getBareRepository(argThat(uri -> exercise.getProjectKey().equals(uri.getProjectKey())), anyBoolean());
        verify(metisLlmService, never()).complete(any(), any(), anyString(), anyString(), any());
    }

    private void assertUnavailable(Submitted submitted, MetisCheckStatus status, MetisFailureReason reason, @Nullable String commitHash) {
        MetisCheck check = checkOf(submitted.participation());
        assertThat(check.getStatus()).isEqualTo(status);
        assertThat(check.getFailureReason()).isEqualTo(reason.name());
        assertThat(check.getCommitHash()).isEqualTo(commitHash);
        assertThat(questionsOf(check)).isEmpty();
    }

    private void dispatchAt(ZonedDateTime time) {
        freezeClockAt(time);
        dispatchService.dispatch(time);
    }

    private MetisCheck checkOf(ProgrammingExerciseStudentParticipation participation) {
        return checkRepository.findByParticipationId(participation.getId()).orElseThrow();
    }

    private List<MetisQuestion> questionsOf(MetisCheck check) {
        return questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
    }

    private MetisQuestion update(MetisQuestion question, Consumer<MetisQuestion> change) {
        MetisQuestion stored = questionRepository.findByIdElseThrow(question.getId());
        change.accept(stored);
        return questionRepository.save(stored);
    }

    private MetisCheck update(MetisCheck check, Consumer<MetisCheck> change) {
        MetisCheck stored = checkRepository.findByIdElseThrow(check.getId());
        change.accept(stored);
        return checkRepository.save(stored);
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(TEST_PREFIX + suffix);
    }

    private static void freezeClockAt(ZonedDateTime time) {
        TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
    }

    private static String threeQuestions() {
        return MetisLlmTestResponses.questionsJson(QUESTIONS, List.of(120, 90, 150));
    }

    private static String marker(String userSuffix) {
        return "metis-marker-" + userSuffix;
    }

    /**
     * The own code of a student: a sort in the template file, a new helper file, and a test that never counts as own code. Each file names the student, so a
     * prompt shows whose code it holds.
     */
    private static Map<String, String> ownCode(String userSuffix) {
        String marker = marker(userSuffix);
        return Map.of(SORTER_PATH, ownSorter(marker), HELPER_PATH, ownHelper(marker), TEST_PATH, """
                package de.tum;

                class SorterTest {

                    void sortsAscending() {
                        // assertSortedAscendingInTheStudentsOwnTest
                    }
                }
                """);
    }

    private static String ownSorter(String marker) {
        return """
                package de.tum;

                public class Sorter {

                    public int[] sort(int[] input) {
                        // MARKER
                        int[] result = input.clone();
                        for (int pass = 0; pass < result.length; pass++) {
                            for (int index = 0; index + 1 < result.length - pass; index++) {
                                if (result[index] > result[index + 1]) {
                                    SortHelper.swap(result, index, index + 1);
                                }
                            }
                        }
                        return result;
                    }
                }
                """.replace("MARKER", marker);
    }

    private static String ownHelper(String marker) {
        return """
                package de.tum;

                final class SortHelper {

                    // MARKER
                    static void swap(int[] values, int first, int second) {
                        int saved = values[first];
                        values[first] = values[second];
                        values[second] = saved;
                    }
                }
                """.replace("MARKER", marker);
    }

    /**
     * A source file just below {@code max-focus-file-characters}, so its full content is sent unless the prompt budget leaves it out.
     */
    private static String largeFile(String className) {
        StringBuilder content = new StringBuilder("package de.tum;\n\nclass ").append(className).append(" {\n");
        for (int line = 0; line < 160; line++) {
            content.append("    int field").append(line).append(" = ").append(line).append("; // a generated line that fills the focus file\n");
        }
        return content.append("}\n").toString();
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
            count++;
        }
        return count;
    }
}
