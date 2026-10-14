package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.BackfillOutcome;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.Rating;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.RatingRequest;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.util.MetisLlmTestResponses;

/**
 * Unit tests of the rating of an answer: the rendered prompts with the real templates, the parsing and clamping of the model's output, the retry rules of the
 * two modes, the outcomes of the rating backfill, and the limit the answer flow keeps for a follow-up the model asks for. The transport, the repository access
 * and the database are mocks.
 */
class MetisAnswerRatingServiceTest {

    private static final long COURSE_ID = 1L;

    private static final long EXERCISE_ID = 2L;

    private static final long STUDENT_ID = 3L;

    private static final long CHECK_ID = 4L;

    private static final String REPOSITORY_URI = "http://localhost/git/METIS/metis-student1.git";

    private static final String COMMIT_HASH = "c".repeat(40);

    private static final String FOCUS_FILE = "src/Sorter.java";

    private static final ZonedDateTime SHOWN = ZonedDateTime.parse("2026-03-02T10:00:00Z");

    /** The effective due date of the student whose stored answers are rated. */
    private static final ZonedDateTime DUE = SHOWN.minusDays(1);

    /** The line of the user prompt that the schema of the output follows. */
    private static final String SCHEMA_HEADING = "Produce JSON that strictly follows this schema:";

    private final MetisLlmService llmService = mock(MetisLlmService.class);

    private final MetisDiffService diffService = mock(MetisDiffService.class);

    private final MetisAiConsentService consentService = mock(MetisAiConsentService.class);

    private final MetisCheckRepository checkRepository = mock(MetisCheckRepository.class);

    private final MetisQuestionRepository questionRepository = mock(MetisQuestionRepository.class);

    private final MetisExerciseConfigRepository configRepository = mock(MetisExerciseConfigRepository.class);

    private final MetisAnswerRatingService ratingService = new MetisAnswerRatingService(llmService, new MetisPromptTemplateService(), diffService, consentService, checkRepository,
            questionRepository, configRepository);

    private MetisExerciseConfig config;

    private MetisQuestion mainQuestion;

    @BeforeEach
    void setUp() {
        config = new MetisExerciseConfig();
        config.setExerciseId(EXERCISE_ID);
        config.setEnabled(true);
        mainQuestion = question(10L, 1, null, "What does the loop in sort do?", 120);
        mainQuestion.setFocusFilePath(FOCUS_FILE);
        mainQuestion.setFocusFileChangedLines(12);
        when(diffService.readFocusFileContext(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Nested
    class Prompts {

        @Test
        void shouldRenderTheAnchorsOfAFivePointScale() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", true, Mode.INTERACTIVE));

            String systemPrompt = capturedPrompts(Mode.INTERACTIVE, 1).systemPrompt();
            assertThat(systemPrompt).contains("""
                    Rate on a scale from 1 to 5:
                    1: no understanding (missing, wrong or unrelated)
                    2: little understanding
                    3: partial understanding (correct in parts, important gaps)
                    4: good understanding (minor gaps)
                    5: full understanding (correct, specific, complete)
                    """);
            assertThat(systemPrompt).contains("the expected length was two to four sentences.");
        }

        @Test
        void shouldRenderTheAnchorsOfASevenPointScale() {
            config.setLikertScalePoints(7);
            config.setAnswerLength(1);
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(6, "Very good.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", true, Mode.INTERACTIVE));

            String systemPrompt = capturedPrompts(Mode.INTERACTIVE, 1).systemPrompt();
            assertThat(systemPrompt).contains("""
                    Rate on a scale from 1 to 7:
                    1: no understanding
                    2: very little understanding
                    3: little understanding
                    4: partial understanding
                    5: good understanding
                    6: very good understanding
                    7: full understanding
                    """);
            assertThat(systemPrompt).contains("the expected length was one short sentence.");
        }

        @Test
        void shouldWriteTheThresholdAndTheTimeRangeOfAnAllowedFollowUpAsNumbers() {
            config.setFollowUpThreshold(2);
            config.setMinSecondsPerQuestion(90);
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", true, Mode.INTERACTIVE));

            String systemPrompt = capturedPrompts(Mode.INTERACTIVE, 1).systemPrompt();
            assertThat(systemPrompt).contains("First decide the score, then write the rationale. If the score is 2 or lower, followUpQuestion must contain one short follow-up");
            assertThat(systemPrompt).contains("followUpTimeLimitSeconds must be between 90 and 120.").contains("If the score is higher, leave followUpQuestion empty");
            assertThat(systemPrompt).doesNotContain(MetisAnswerRatingService.NO_FOLLOW_UP_INSTRUCTION).doesNotContain("{{");
        }

        @Test
        void shouldBoundTheFollowUpRangeByTheLimitOfAShortMainQuestion() {
            config.setMinSecondsPerQuestion(60);
            mainQuestion.setTimeLimitSeconds(45);
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", true, Mode.INTERACTIVE));

            assertThat(capturedPrompts(Mode.INTERACTIVE, 1).systemPrompt()).contains("followUpTimeLimitSeconds must be between 45 and 45.");
        }

        @Test
        void shouldForbidAFollowUpWhenNoneIsAllowed() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", false, Mode.INTERACTIVE));

            String systemPrompt = capturedPrompts(Mode.INTERACTIVE, 1).systemPrompt();
            assertThat(systemPrompt).contains(MetisAnswerRatingService.NO_FOLLOW_UP_INSTRUCTION).doesNotContain("If the score is").doesNotContain("{{");
        }

        /**
         * The model writes the properties in the order of the schema, and whether a follow-up is due depends on the score. With the alphabetical order of the
         * schema generator, the model wrote the follow-up first and left it empty, so the schema must list the score before the rationale and both before the
         * follow-up.
         */
        @Test
        void shouldListTheScoreBeforeTheRationaleAndTheFollowUpInTheSchemaOfThePrompt() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 75));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array.", true, Mode.INTERACTIVE));

            JsonNode schema = schemaInPrompt(capturedPrompts(Mode.INTERACTIVE, 1).userPrompt());
            assertThat(schema.get("properties").propertyNames()).containsExactly("score", "rationale", "followUpQuestion", "followUpTimeLimitSeconds");
        }

        @Test
        void shouldShowTheNumberedFocusFileTheQuestionAndTheAnswer() {
            var focusFile = new MetisDiffService.FocusFileContext(FOCUS_FILE, "class Sorter {\n    void sort() {}\n}\n", "@@ -0,0 +1,3 @@\n+class Sorter {", 3);
            when(diffService.readFocusFileContext(REPOSITORY_URI, COMMIT_HASH, FOCUS_FILE)).thenReturn(Optional.of(focusFile));
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts the array\u0007 in place.", true, Mode.INTERACTIVE));

            String userPrompt = capturedPrompts(Mode.INTERACTIVE, 1).userPrompt();
            assertThat(userPrompt).startsWith("""
                    Focus file: src/Sorter.java
                    --- BEGIN STUDENT FILE ---
                       1| class Sorter {
                       2|     void sort() {}
                       3| }
                    --- END STUDENT FILE ---
                    """);
            assertThat(userPrompt).contains("Earlier in this conversation:\n(none)\n");
            assertThat(userPrompt).contains("--- BEGIN QUESTION ---\nWhat does the loop in sort do?\n--- END QUESTION ---");
            assertThat(userPrompt).contains("--- BEGIN STUDENT ANSWER ---\nIt sorts the array in place.\n--- END STUDENT ANSWER ---");
            assertThat(userPrompt).contains("Produce JSON that strictly follows this schema:").contains("followUpTimeLimitSeconds").doesNotContain("{{focus");
        }

        @Test
        void shouldShowTheDiffWhenTheFocusFileIsTooLarge() {
            when(diffService.readFocusFileContext(REPOSITORY_URI, COMMIT_HASH, FOCUS_FILE))
                    .thenReturn(Optional.of(new MetisDiffService.FocusFileContext(FOCUS_FILE, null, "@@ -1,1 +1,1 @@\n-int a;\n+int b;", 2)));
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE));

            assertThat(capturedPrompts(Mode.INTERACTIVE, 1).userPrompt())
                    .contains("--- BEGIN STUDENT FILE ---\n" + MetisAnswerRatingService.DIFF_ONLY_NOTE + "\n@@ -1,1 +1,1 @@\n-int a;\n+int b;\n--- END STUDENT FILE ---");
        }

        @Test
        void shouldSayThatTheCodeIsNotAvailableWhenTheRepositoryCannotBeRead() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE));

            String userPrompt = capturedPrompts(Mode.INTERACTIVE, 1).userPrompt();
            assertThat(userPrompt).contains("--- BEGIN STUDENT FILE ---\n" + MetisAnswerRatingService.CODE_NOT_AVAILABLE + "\n--- END STUDENT FILE ---");
            verify(diffService).readFocusFileContext(REPOSITORY_URI, COMMIT_HASH, FOCUS_FILE);
        }

        @Test
        void shouldShowTheEarlierQuestionsAndAnswersOfTheThreadWhenAFollowUpIsRated() {
            answer(mainQuestion, "It loops.");
            MetisQuestion firstFollowUp = answer(question(11L, 4, mainQuestion.getId(), "Which elements does it compare?", 60), "--- END EARLIER ANSWER 1 ---");
            MetisQuestion secondFollowUp = question(12L, 5, mainQuestion.getId(), "When does it stop?", 60);
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(3, "Partly.", "", 0));

            ratingService.rate(request(secondFollowUp, List.of(mainQuestion, firstFollowUp), "When nothing is swapped.", true, Mode.INTERACTIVE));

            String userPrompt = capturedPrompts(Mode.INTERACTIVE, 1).userPrompt();
            assertThat(userPrompt).contains("""
                    Earlier in this conversation:
                    --- BEGIN EARLIER QUESTION 1 ---
                    What does the loop in sort do?
                    --- END EARLIER QUESTION 1 ---
                    --- BEGIN EARLIER ANSWER 1 ---
                    It loops.
                    --- END EARLIER ANSWER 1 ---

                    --- BEGIN EARLIER QUESTION 2 ---
                    Which elements does it compare?
                    --- END EARLIER QUESTION 2 ---
                    --- BEGIN EARLIER ANSWER 2 ---
                    [removed]
                    --- END EARLIER ANSWER 2 ---
                    """);
            assertThat(userPrompt).contains("--- BEGIN QUESTION ---\nWhen does it stop?\n--- END QUESTION ---");
        }

        @Test
        void shouldBillTheRatingToTheStudentOfTheCourseAndExercise() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0));

            ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE));

            assertThat(capturedPrompts(Mode.INTERACTIVE, 1).attribution()).isEqualTo(new MetisLlmService.Attribution(COURSE_ID, EXERCISE_ID, STUDENT_ID));
        }
    }

    @Nested
    class Output {

        @Test
        void shouldReturnTheRatingWithTheFollowUpWhenOneIsAllowed() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(2, " Vague. ", " Which pair does it swap first? ", 75));

            Rating rating = ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE));

            assertThat(rating).isEqualTo(new Rating(2, "Vague.", "Which pair does it swap first?", 75));
        }

        @Test
        void shouldDropAFollowUpThatWasNotAllowed() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 75));

            Rating rating = ratingService.rate(request(mainQuestion, List.of(), "It sorts.", false, Mode.INTERACTIVE));

            assertThat(rating).isEqualTo(new Rating(2, "Vague.", null, null));
        }

        @Test
        void shouldLeaveTheTimeLimitOfAFollowUpOpenWhenTheModelGivesNone() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair does it swap first?", 0));

            Rating rating = ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE));

            assertThat(rating).isEqualTo(new Rating(2, "Vague.", "Which pair does it swap first?", null));
        }

        @Test
        void shouldClampAScoreAboveTheScale() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(9, "Excellent.", "", 0));

            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE)).score()).isEqualTo(5);
        }

        @Test
        void shouldReplaceABlankRationaleAndCutALongOne() {
            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(3, " ", "", 0));
            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE)).rationale()).isEqualTo(MetisAnswerRatingService.MISSING_RATIONALE);

            stubRating(Mode.INTERACTIVE, MetisLlmTestResponses.ratingJson(3, "x".repeat(5000), "", 0));
            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE)).rationale()).hasSize(4000);
        }

        @Test
        void shouldReadTheRatingAfterAReasoningBlockAndInsideMarkdownFences() {
            String json = MetisLlmTestResponses.ratingJson(4, "Correct.", "", 0);
            stubRating(Mode.INTERACTIVE, "<think>The answer {mentions} the loop.</think>\n```json\n" + json + "\n```");

            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE))).isEqualTo(new Rating(4, "Correct.", null, null));
        }

        @Test
        void shouldRetryAnUnusableBatchRatingOnceAndThenGiveUp() {
            stubRating(Mode.BATCH, MetisLlmTestResponses.ratingJson(0, "No score.", "", 0));

            assertThatThrownBy(() -> ratingService.rate(request(mainQuestion, List.of(), "It sorts.", false, Mode.BATCH))).isInstanceOf(MetisLlmException.class)
                    .extracting(exception -> ((MetisLlmException) exception).getKind()).isEqualTo(MetisLlmException.Kind.INVALID_OUTPUT);
            verify(llmService, times(2)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
        }

        @Test
        void shouldUseTheRetryOfABatchRatingWhenItIsUsable() {
            when(llmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any())).thenReturn(MetisLlmTestResponses.completion("no json at all"))
                    .thenReturn(MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(3, "Partly.", "", 0)));

            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", false, Mode.BATCH))).isEqualTo(new Rating(3, "Partly.", null, null));
        }

        @Test
        void shouldGiveUpOnAnUnusableInteractiveRatingAfterOneCall() {
            stubRating(Mode.INTERACTIVE, "{\"rationale\": \"No score at all.\"}");

            assertThatThrownBy(() -> ratingService.rate(request(mainQuestion, List.of(), "It sorts.", true, Mode.INTERACTIVE))).isInstanceOf(MetisLlmException.class)
                    .extracting(exception -> ((MetisLlmException) exception).getKind()).isEqualTo(MetisLlmException.Kind.INVALID_OUTPUT);
            verify(llmService, times(1)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.INTERACTIVE), anyString(), anyString(), any());
        }

        @Test
        void shouldTreatAnEmptyAnswerOfTheTransportAsUnusable() {
            when(llmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any()))
                    .thenThrow(new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "blank"))
                    .thenReturn(MetisLlmTestResponses.completion(MetisLlmTestResponses.ratingJson(5, "Full.", "", 0)));

            assertThat(ratingService.rate(request(mainQuestion, List.of(), "It sorts.", false, Mode.BATCH)).score()).isEqualTo(5);
        }

        @ParameterizedTest
        @EnumSource(value = MetisLlmException.Kind.class, names = { "UNAVAILABLE", "REJECTED" })
        void shouldPassOnAFailedCallWithoutRetry(MetisLlmException.Kind kind) {
            when(llmService.complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any())).thenThrow(new MetisLlmException(kind, "failed"));

            assertThatThrownBy(() -> ratingService.rate(request(mainQuestion, List.of(), "It sorts.", false, Mode.BATCH))).isInstanceOf(MetisLlmException.class)
                    .extracting(exception -> ((MetisLlmException) exception).getKind()).isEqualTo(kind);
            verify(llmService, times(1)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
        }

        @Test
        void shouldRateAnEmptyAnswerOneWithoutAFollowUp() {
            assertThat(MetisAnswerRatingService.emptyAnswerRating()).isEqualTo(new Rating(1, MetisAnswerRatingService.EMPTY_ANSWER_RATIONALE, null, null));
        }

        @Test
        void shouldExtractNoJsonFromAnOutputWithoutObject() {
            assertThat(MetisAnswerRatingService.extractJson("I cannot rate this.")).isNull();
            assertThat(MetisAnswerRatingService.extractJson("} {")).isNull();
        }
    }

    @Nested
    class RatingOfStoredAnswers {

        private MetisQuestion stored;

        @BeforeEach
        void setUpStoredAnswer() {
            stored = answer(mainQuestion, "It sorts the array.");
            when(questionRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
            when(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(CHECK_ID)).thenReturn(List.of(stored));
            var target = new MetisGenerationTargetDTO(CHECK_ID, MetisCheckStatus.IN_PROGRESS, COMMIT_HASH, 5L, STUDENT_ID, REPOSITORY_URI, DUE, EXERCISE_ID, COURSE_ID);
            when(checkRepository.findGenerationTarget(CHECK_ID)).thenReturn(Optional.of(target));
            when(configRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(config));
            when(consentService.hasConsent(STUDENT_ID)).thenReturn(true);
        }

        @Test
        void shouldRateAStoredAnswerWithTheBatchClientWithoutFollowUp() {
            stubRating(Mode.BATCH, MetisLlmTestResponses.ratingJson(2, "Vague.", "Which pair?", 60));

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.RATED);

            verify(questionRepository).saveRating(eq(stored.getId()), eq(2), eq("Vague."), any());
            CapturedPrompts prompts = capturedPrompts(Mode.BATCH, 1);
            assertThat(prompts.systemPrompt()).contains(MetisAnswerRatingService.NO_FOLLOW_UP_INSTRUCTION);
            assertThat(prompts.attribution()).isEqualTo(new MetisLlmService.Attribution(COURSE_ID, EXERCISE_ID, STUDENT_ID));
            verify(diffService).readFocusFileContext(REPOSITORY_URI, COMMIT_HASH, FOCUS_FILE);
        }

        @Test
        void shouldRateAStoredFollowUpWithItsThread() {
            MetisQuestion followUp = answer(question(11L, 2, mainQuestion.getId(), "When does it stop?", 60), "When nothing is swapped.");
            when(questionRepository.findById(followUp.getId())).thenReturn(Optional.of(followUp));
            when(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(CHECK_ID)).thenReturn(List.of(stored, followUp));
            stubRating(Mode.BATCH, MetisLlmTestResponses.ratingJson(4, "Right.", "", 0));

            assertThat(ratingService.rateStoredAnswer(followUp.getId())).isEqualTo(BackfillOutcome.RATED);

            String userPrompt = capturedPrompts(Mode.BATCH, 1).userPrompt();
            assertThat(userPrompt).contains("--- BEGIN EARLIER QUESTION 1 ---\nWhat does the loop in sort do?").contains("--- BEGIN QUESTION ---\nWhen does it stop?");
            verify(questionRepository).saveRating(eq(followUp.getId()), eq(4), eq("Right."), any());
        }

        @Test
        void shouldRateAnEmptyStoredAnswerOneWithoutTheLlm() {
            stored.setAnswerText(" ");

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.RATED);

            verify(questionRepository).saveRating(eq(stored.getId()), eq(1), eq(MetisAnswerRatingService.EMPTY_ANSWER_RATIONALE), any());
            verifyNoInteractions(llmService);
        }

        @Test
        void shouldSkipTheRatingWhenTheStudentWithdrewTheConsent() {
            when(consentService.hasConsent(STUDENT_ID)).thenReturn(false);

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.SKIPPED);

            verifyNoInteractions(llmService);
            verify(questionRepository, never()).saveRating(anyLong(), anyInt(), anyString(), any());
        }

        @Test
        void shouldSkipAQuestionThatIsGoneOrUnanswered() {
            when(questionRepository.findById(99L)).thenReturn(Optional.empty());
            assertThat(ratingService.rateStoredAnswer(99L)).isEqualTo(BackfillOutcome.SKIPPED);

            stored.setAnsweredAt(null);
            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.SKIPPED);
            verifyNoInteractions(llmService);
        }

        @Test
        void shouldSkipAnAnswerWhoseCheckIsGone() {
            when(checkRepository.findGenerationTarget(CHECK_ID)).thenReturn(Optional.empty());

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.SKIPPED);
            verifyNoInteractions(llmService);
        }

        @Test
        void shouldReportAnAnswerAsRatedThatAnotherWriterRated() {
            stored.setLlmRatedAt(SHOWN.plusMinutes(2));

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.RATED);
            verifyNoInteractions(llmService);
        }

        @Test
        void shouldReportAnUnavailableEndpoint() {
            when(llmService.complete(any(), any(), anyString(), anyString(), any())).thenThrow(new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "down"));

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.UNAVAILABLE);
            verify(questionRepository, never()).saveRating(anyLong(), anyInt(), anyString(), any());
        }

        @Test
        void shouldReportAFailureAfterTwoUnusableOutputs() {
            stubRating(Mode.BATCH, "not a rating");

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.FAILED);
            verify(llmService, times(2)).complete(eq(Pipeline.ANSWER_RATING), eq(Mode.BATCH), anyString(), anyString(), any());
            verify(questionRepository, never()).saveRating(anyLong(), anyInt(), anyString(), any());
        }

        @Test
        void shouldReportAFailureWhenTheRequestIsRejected() {
            when(llmService.complete(any(), any(), anyString(), anyString(), any())).thenThrow(new MetisLlmException(MetisLlmException.Kind.REJECTED, "too long"));

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.FAILED);
        }

        @Test
        void shouldReportAFailureOnAnUnexpectedError() {
            when(checkRepository.findGenerationTarget(CHECK_ID)).thenThrow(new IllegalStateException("database gone"));

            assertThat(ratingService.rateStoredAnswer(stored.getId())).isEqualTo(BackfillOutcome.FAILED);
        }
    }

    /**
     * The limit the answer flow gives a follow-up question the model asks for: it stays inside the range the follow-up instruction names, from the shortest
     * limit of the configuration (or the main question's limit if that is shorter) to the main question's limit.
     */
    @Nested
    class FollowUpTimeLimit {

        @Test
        void shouldKeepTheProposedLimitInsideTheRangeOfTheMainQuestion() {
            assertThat(MetisStudentCheckService.followUpTimeLimit(90, 120, 60)).isEqualTo(90);
            assertThat(MetisStudentCheckService.followUpTimeLimit(999, 120, 60)).isEqualTo(120);
            assertThat(MetisStudentCheckService.followUpTimeLimit(10, 120, 60)).isEqualTo(60);
        }

        @Test
        void shouldGiveHalfTheLimitOfTheMainQuestionRoundedToTenSecondsWhenTheModelProposesNone() {
            assertThat(MetisStudentCheckService.followUpTimeLimit(null, 150, 60)).isEqualTo(80);
            assertThat(MetisStudentCheckService.followUpTimeLimit(null, 300, 60)).isEqualTo(150);
            assertThat(MetisStudentCheckService.followUpTimeLimit(null, 100, 60)).isEqualTo(60);
        }

        @Test
        void shouldGiveTheLimitOfAMainQuestionShorterThanTheShortestLimitToItsFollowUp() {
            assertThat(MetisStudentCheckService.followUpTimeLimit(90, 45, 60)).isEqualTo(45);
            assertThat(MetisStudentCheckService.followUpTimeLimit(10, 45, 60)).isEqualTo(45);
            assertThat(MetisStudentCheckService.followUpTimeLimit(null, 45, 60)).isEqualTo(45);
        }
    }

    private RatingRequest request(MetisQuestion question, List<MetisQuestion> earlierInThread, String answer, boolean followUpAllowed, Mode mode) {
        return new RatingRequest(COURSE_ID, EXERCISE_ID, STUDENT_ID, REPOSITORY_URI, COMMIT_HASH, config, mainQuestion, earlierInThread, question, answer, followUpAllowed, mode);
    }

    private void stubRating(Mode mode, String output) {
        when(llmService.complete(eq(Pipeline.ANSWER_RATING), eq(mode), anyString(), anyString(), any())).thenReturn(MetisLlmTestResponses.completion(output));
    }

    /**
     * The JSON schema of a user prompt as the model reads it: from the first opening brace after the line that introduces the schema to the last closing brace,
     * since the schema ends the prompt.
     */
    private static JsonNode schemaInPrompt(String userPrompt) {
        int heading = userPrompt.indexOf(SCHEMA_HEADING);
        assertThat(heading).isNotNegative();
        return JsonObjectMapper.get().readTree(userPrompt.substring(userPrompt.indexOf('{', heading), userPrompt.lastIndexOf('}') + 1));
    }

    private CapturedPrompts capturedPrompts(Mode mode, int calls) {
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MetisLlmService.Attribution> attribution = ArgumentCaptor.forClass(MetisLlmService.Attribution.class);
        verify(llmService, times(calls)).complete(eq(Pipeline.ANSWER_RATING), eq(mode), systemPrompt.capture(), userPrompt.capture(), attribution.capture());
        return new CapturedPrompts(systemPrompt.getValue(), userPrompt.getValue(), attribution.getValue());
    }

    private static MetisQuestion question(long id, int order, @Nullable Long parentQuestionId, String text, int timeLimitSeconds) {
        MetisQuestion question = new MetisQuestion();
        question.setId(id);
        question.setCheckId(CHECK_ID);
        question.setQuestionOrder(order);
        question.setParentQuestionId(parentQuestionId);
        question.setQuestionText(text);
        question.setTimeLimitSeconds(timeLimitSeconds);
        question.setShownAt(SHOWN);
        return question;
    }

    private static MetisQuestion answer(MetisQuestion question, String answer) {
        question.setAnswerText(answer);
        question.setAnsweredAt(SHOWN.plusMinutes(1));
        return question;
    }

    private record CapturedPrompts(String systemPrompt, String userPrompt, MetisLlmService.Attribution attribution) {
    }
}
