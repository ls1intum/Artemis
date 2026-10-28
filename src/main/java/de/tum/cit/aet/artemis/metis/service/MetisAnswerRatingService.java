package de.tum.cit.aet.artemis.metis.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;

/**
 * Rates a student's answer to a question of their comprehension check with the LLM, and asks the LLM for a follow-up question when the answer is weak.
 * <p>
 * Every answer gets one rating call. The prompt holds the focus file of the main question as the student submitted it (or its diff, or nothing when the
 * repository cannot be read), the earlier questions and answers of the same thread, the question being rated and the answer, together with the anchors of
 * the configured Likert scale and the instruction whether and in which range a follow-up may be asked. The model's output is parsed and clamped: a score
 * above the scale is cut to its maximum, a missing or non-positive score makes the output unusable. An empty answer is rated 1 without the LLM.
 * <p>
 * Live ratings, which the student waits for, use the interactive client and are never retried: an unusable or failed rating leaves the answer unrated, and
 * the rating backfill rates it later ({@link #rateStoredAnswer(long)}), with the batch client, one retry on unusable output and without a follow-up.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisAnswerRatingService {

    private static final Logger log = LoggerFactory.getLogger(MetisAnswerRatingService.class);

    /** The system prompt of a rating. */
    static final String SYSTEM_PROMPT_TEMPLATE = "prompts/metis/rate_answer_system.st";

    /** The user prompt of a rating. */
    static final String USER_PROMPT_TEMPLATE = "prompts/metis/rate_answer_user.st";

    /**
     * The instruction for a rating that may come with a follow-up question: the threshold and the time range are formatted into it before it is rendered,
     * because the prompt renderer never fills a placeholder inside a value. It names the properties of the output, and it has the model decide the score
     * before anything else, since whether a follow-up is due depends on the score.
     */
    static final String FOLLOW_UP_INSTRUCTION = "First decide the score, then write the rationale. If the score is %d or lower, followUpQuestion must contain one short "
            + "follow-up question that lets the student show their understanding of the same aspect, without revealing the answer, and followUpTimeLimitSeconds "
            + "must be between %d and %d. If the score is higher, leave followUpQuestion empty and followUpTimeLimitSeconds 0.";

    /** The instruction for a rating that must not come with a follow-up question, because the cap is reached or the window leaves no time. */
    static final String NO_FOLLOW_UP_INSTRUCTION = "Do not write a follow-up question: leave it empty and its time limit 0.";

    /** The rationale of an empty answer, which is rated without the LLM. */
    static final String EMPTY_ANSWER_RATIONALE = "No answer was submitted.";

    /** The rationale stored when the model rated an answer without explaining why. */
    static final String MISSING_RATIONALE = "No rationale given.";

    /** The longest rationale that is stored. */
    static final int MAX_RATIONALE_CHARACTERS = 4000;

    /** Stands for the focus file in the prompt when the repository cannot be read. */
    static final String CODE_NOT_AVAILABLE = "(code not available)";

    /** Introduces the diff of a focus file whose full content is too large for the prompt. */
    static final String DIFF_ONLY_NOTE = "(full file omitted, too large; its changes against the template follow, + marks lines the student added, - lines the student removed)";

    /** Stands for an empty list or a missing value in the prompt. */
    private static final String NONE = "(none)";

    /** Stands for an earlier question of the thread that has no answer. */
    private static final String NO_ANSWER = "(no answer)";

    /** One earlier question of the thread with its answer, both fenced as data; filled with the number, the question and the answer. */
    private static final String EARLIER_QUESTION_BLOCK = """
            --- BEGIN EARLIER QUESTION %d ---
            %s
            --- END EARLIER QUESTION %d ---
            --- BEGIN EARLIER ANSWER %d ---
            %s
            --- END EARLIER ANSWER %d ---""";

    /** The expected length of an answer, for the five steps of the answer length setting. */
    private static final List<String> ANSWER_LENGTHS = List.of("one short sentence", "one or two sentences", "two to four sentences", "a short paragraph", "a detailed paragraph");

    private static final List<String> FIVE_POINT_ANCHORS = List.of("no understanding (missing, wrong or unrelated)", "little understanding",
            "partial understanding (correct in parts, important gaps)", "good understanding (minor gaps)", "full understanding (correct, specific, complete)");

    private static final List<String> SEVEN_POINT_ANCHORS = List.of("no understanding", "very little understanding", "little understanding", "partial understanding",
            "good understanding", "very good understanding", "full understanding");

    /** A reasoning block that some models write before their answer. */
    private static final Pattern LEADING_THINK_BLOCK = Pattern.compile("^\\s*<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final MetisLlmService llmService;

    private final MetisPromptTemplateService templateService;

    private final MetisDiffService diffService;

    private final MetisAiConsentService consentService;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    private final MetisExerciseConfigRepository configRepository;

    private final BeanOutputConverter<RatingOutput> outputConverter = new BeanOutputConverter<>(RatingOutput.class);

    public MetisAnswerRatingService(MetisLlmService llmService, MetisPromptTemplateService templateService, MetisDiffService diffService, MetisAiConsentService consentService,
            MetisCheckRepository checkRepository, MetisQuestionRepository questionRepository, MetisExerciseConfigRepository configRepository) {
        this.llmService = llmService;
        this.templateService = templateService;
        this.diffService = diffService;
        this.consentService = consentService;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
        this.configRepository = configRepository;
    }

    /**
     * Everything one rating needs.
     *
     * @param courseId        the course of the exercise, for the token usage records
     * @param exerciseId      the exercise
     * @param studentId       the student whose answer is rated, who the call is billed to
     * @param repositoryUri   the student's repository, or null if it is unknown (the prompt then says the code is not available)
     * @param commitHash      the commit the questions were generated from, or null if it is unknown
     * @param config          the configuration of the exercise: the scale, the threshold, the expected answer length and the shortest time limit
     * @param mainQuestion    the main question of the thread, whose focus file and time limit apply
     * @param earlierInThread the questions of the thread asked before the rated one, with their answers, in their order; empty when the main question is rated
     * @param question        the question whose answer is rated, the main question itself or one of its follow-ups
     * @param answerText      the answer as stored, not blank
     * @param followUpAllowed whether the rating may come with a follow-up question; the server decides this before the call
     * @param mode            the interactive client for a live rating, the batch client for the backfill
     */
    public record RatingRequest(long courseId, long exerciseId, long studentId, @Nullable String repositoryUri, @Nullable String commitHash, MetisExerciseConfig config,
            MetisQuestion mainQuestion, List<MetisQuestion> earlierInThread, MetisQuestion question, String answerText, boolean followUpAllowed, MetisLlmService.Mode mode) {

        /**
         * Keeps the earlier questions unmodifiable.
         */
        public RatingRequest {
            earlierInThread = List.copyOf(earlierInThread);
        }
    }

    /**
     * The rating of one answer.
     *
     * @param score                    the score, from 1 to the points of the scale
     * @param rationale                one to three sentences for the instructor, never blank
     * @param followUpQuestion         the follow-up question the model asks, or null if it asks none or none was allowed
     * @param followUpTimeLimitSeconds the time limit the model proposes for the follow-up question, or null if it proposes none
     */
    public record Rating(int score, String rationale, @Nullable String followUpQuestion, @Nullable Integer followUpTimeLimitSeconds) {
    }

    /**
     * What the rating of a stored answer achieved, which the dispatcher of the backfill acts on.
     */
    public enum BackfillOutcome {
        /** The answer is rated now, by this call or by another writer. */
        RATED,
        /** There is nothing to rate or the answer must not be rated: the question or its check is gone, or the student has withdrawn the AI consent. */
        SKIPPED,
        /** The LLM endpoint is unavailable; a later attempt may succeed. */
        UNAVAILABLE,
        /** The rating failed: the model returned unusable output twice, rejected the request, or an unexpected error occurred. */
        FAILED
    }

    /**
     * The output the model is asked for: the rating, its rationale and an optional follow-up question.
     * <p>
     * The model writes the properties in the order in which the schema of the prompt lists them, and whether a follow-up is due depends on the score. The
     * schema therefore lists the score first and the follow-up last. Without the explicit order the schema generator sorts the properties alphabetically, and
     * the model wrote the follow-up before it had decided the score and left it empty.
     *
     * @param score                    the rating
     * @param rationale                the explanation for the instructor
     * @param followUpQuestion         the follow-up question, empty for none
     * @param followUpTimeLimitSeconds the time limit of the follow-up question, 0 for none
     */
    @JsonPropertyOrder({ "score", "rationale", "followUpQuestion", "followUpTimeLimitSeconds" })
    record RatingOutput(@JsonPropertyDescription("The rating, an integer from 1 to the maximum of the scale") int score,
            @JsonPropertyDescription("One to three sentences for the instructor that explain the rating") String rationale,
            @JsonPropertyDescription("A short follow-up question when one is requested, otherwise an empty string") String followUpQuestion,
            @JsonPropertyDescription("Seconds for the follow-up question, or 0 when there is none") int followUpTimeLimitSeconds) {
    }

    /**
     * Rates one answer with the LLM. A follow-up question the model writes although none was allowed is dropped.
     *
     * @param request what to rate and how
     * @return the rating, its score clamped to the scale
     * @throws MetisLlmException {@code INVALID_OUTPUT} if the output is unusable, after one retry in batch mode and at once in interactive mode, which never
     *                               retries; {@code UNAVAILABLE} or {@code REJECTED} if the call failed
     */
    public Rating rate(RatingRequest request) {
        String systemPrompt = templateService.render(SYSTEM_PROMPT_TEMPLATE, systemPromptVariables(request));
        String userPrompt = templateService.render(USER_PROMPT_TEMPLATE, userPromptVariables(request));
        var attribution = new MetisLlmService.Attribution(request.courseId(), request.exerciseId(), request.studentId());
        int attempts = request.mode() == MetisLlmService.Mode.BATCH ? 2 : 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Optional<Rating> rating = callOnce(request, systemPrompt, userPrompt, attribution);
            if (rating.isPresent()) {
                return rating.get();
            }
            log.debug("Attempt {} of {} to rate the answer to Metis question {} returned unusable output", attempt, attempts, request.question().getId());
        }
        throw new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "The LLM returned no usable rating of the answer to Metis question " + request.question().getId());
    }

    /**
     * Rates an answer that was stored without a rating, for the rating backfill: the live rating failed, the answer found no free LLM permit in time, or the
     * server stopped between storing the answer and its rating. The rating uses the batch client, retries unusable output once and never asks a follow-up, since
     * the conversation has moved on. An empty answer is rated 1 without the LLM. Nothing is rated for a student who has withdrawn the AI consent.
     *
     * @param questionId the answered question
     * @return what the rating achieved
     */
    public BackfillOutcome rateStoredAnswer(long questionId) {
        try {
            Optional<MetisQuestion> stored = questionRepository.findById(questionId);
            if (stored.isEmpty() || stored.get().getAnsweredAt() == null) {
                return BackfillOutcome.SKIPPED;
            }
            MetisQuestion question = stored.get();
            if (question.getLlmRatedAt() != null) {
                return BackfillOutcome.RATED;
            }
            Optional<MetisGenerationTargetDTO> target = checkRepository.findGenerationTarget(question.getCheckId());
            Optional<MetisExerciseConfig> config = target.flatMap(found -> configRepository.findById(found.exerciseId()));
            if (target.isEmpty() || config.isEmpty()) {
                return BackfillOutcome.SKIPPED;
            }
            if (!consentService.hasConsent(target.get().studentId())) {
                log.debug("The student of Metis question {} has withdrawn the AI consent, so the stored answer stays unrated", questionId);
                return BackfillOutcome.SKIPPED;
            }
            Rating rating = rateStored(question, target.get(), config.get());
            questionRepository.saveRating(questionId, rating.score(), rating.rationale(), TimeUtil.now());
            return BackfillOutcome.RATED;
        }
        catch (MetisLlmException e) {
            log.info("The rating of the stored answer to Metis question {} failed as {}", questionId, e.getKind());
            return e.getKind() == MetisLlmException.Kind.UNAVAILABLE ? BackfillOutcome.UNAVAILABLE : BackfillOutcome.FAILED;
        }
        catch (RuntimeException e) {
            log.error("The rating of the stored answer to Metis question {} failed unexpectedly", questionId, e);
            return BackfillOutcome.FAILED;
        }
    }

    /**
     * The rating of an empty answer: the lowest score, without the LLM and without a follow-up.
     *
     * @return score 1 with a rationale saying that no answer was submitted
     */
    static Rating emptyAnswerRating() {
        return new Rating(1, EMPTY_ANSWER_RATIONALE, null, null);
    }

    /**
     * The anchors of a Likert scale for the prompt, one line per point, e.g. {@code 1: no understanding (missing, wrong or unrelated)}.
     *
     * @param likertScalePoints the points of the scale, 5 or 7
     * @return the anchors, separated by line feeds
     * @throws IllegalArgumentException for any other number of points, which the configuration never stores; the answer then stays unrated
     */
    static String scaleAnchors(int likertScalePoints) {
        List<String> anchors = switch (likertScalePoints) {
            case 5 -> FIVE_POINT_ANCHORS;
            case 7 -> SEVEN_POINT_ANCHORS;
            default -> throw new IllegalArgumentException("Unsupported Likert scale: " + likertScalePoints);
        };
        List<String> lines = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            lines.add(String.format(Locale.ROOT, "%d: %s", index + 1, anchors.get(index)));
        }
        return String.join("\n", lines);
    }

    /**
     * The expected length of an answer for the prompts.
     *
     * @param answerLength the step of the answer length setting, from 1 to 5; values outside are clamped
     * @return the expected length, e.g. {@code one or two sentences}
     */
    static String answerLengthInstruction(int answerLength) {
        return ANSWER_LENGTHS.get(Math.clamp(answerLength, 1, ANSWER_LENGTHS.size()) - 1);
    }

    /**
     * The instruction of the system prompt whether the rating may come with a follow-up question. An allowed follow-up gets the time range the server keeps
     * for it: from the shortest time limit of the configuration, or the limit of the main question if that is shorter, to the limit of the main question.
     *
     * @param request the rating
     * @return the instruction with every number in it
     */
    static String followUpInstruction(RatingRequest request) {
        if (!request.followUpAllowed()) {
            return NO_FOLLOW_UP_INSTRUCTION;
        }
        MetisExerciseConfig config = request.config();
        int mainTimeLimit = request.mainQuestion().getTimeLimitSeconds();
        return String.format(Locale.ROOT, FOLLOW_UP_INSTRUCTION, config.getFollowUpThreshold(), Math.min(config.getMinSecondsPerQuestion(), mainTimeLimit), mainTimeLimit);
    }

    /**
     * The main question of the thread a question belongs to.
     *
     * @param question  a main question or a follow-up
     * @param questions the questions of the question's check
     * @return the question itself if it is a main question, else the main question it follows up on (the question itself if that is missing)
     */
    static MetisQuestion mainQuestionOf(MetisQuestion question, Collection<MetisQuestion> questions) {
        if (!question.isFollowUp()) {
            return question;
        }
        return questions.stream().filter(candidate -> Objects.equals(candidate.getId(), question.getParentQuestionId())).findFirst().orElse(question);
    }

    /**
     * The questions of a thread that were asked before a given question of it.
     *
     * @param questions    the questions of the check
     * @param mainQuestion the main question of the thread
     * @param question     the question of the thread that is rated
     * @return the main question and its follow-ups with a lower order than the rated question, in their order; empty when the main question is rated
     */
    static List<MetisQuestion> threadBefore(Collection<MetisQuestion> questions, MetisQuestion mainQuestion, MetisQuestion question) {
        return questions.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), mainQuestion.getId()) || Objects.equals(candidate.getParentQuestionId(), mainQuestion.getId()))
                .filter(candidate -> candidate.getQuestionOrder() < question.getQuestionOrder()).sorted(Comparator.comparingInt(MetisQuestion::getQuestionOrder)).toList();
    }

    /**
     * The JSON object in a model's output: a leading reasoning block is dropped, and the text is cut from the first opening to the last closing brace, which
     * removes Markdown fences and remarks around the object.
     *
     * @param text the raw output of the model
     * @return the JSON object as text, or null if the output contains none
     */
    @Nullable
    static String extractJson(String text) {
        String withoutReasoning = LEADING_THINK_BLOCK.matcher(text).replaceFirst("");
        int start = withoutReasoning.indexOf('{');
        int end = withoutReasoning.lastIndexOf('}');
        return start >= 0 && end > start ? withoutReasoning.substring(start, end + 1) : null;
    }

    /**
     * Rates a stored answer with the batch client, without a follow-up; an empty answer without the LLM.
     */
    private Rating rateStored(MetisQuestion question, MetisGenerationTargetDTO target, MetisExerciseConfig config) {
        String answer = Objects.requireNonNullElse(question.getAnswerText(), "");
        if (answer.isBlank()) {
            return emptyAnswerRating();
        }
        List<MetisQuestion> questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(question.getCheckId());
        MetisQuestion mainQuestion = mainQuestionOf(question, questions);
        var request = new RatingRequest(target.courseId(), target.exerciseId(), target.studentId(), target.repositoryUri(), target.commitHash(), config, mainQuestion,
                threadBefore(questions, mainQuestion, question), question, answer, false, MetisLlmService.Mode.BATCH);
        return rate(request);
    }

    /**
     * Makes one rating call.
     *
     * @return the rating, or empty if the output is unusable
     * @throws MetisLlmException {@code UNAVAILABLE} or {@code REJECTED} if the call failed
     */
    private Optional<Rating> callOnce(RatingRequest request, String systemPrompt, String userPrompt, MetisLlmService.Attribution attribution) {
        MetisLlmService.Completion completion;
        try {
            completion = llmService.complete(MetisLlmService.Pipeline.ANSWER_RATING, request.mode(), systemPrompt, userPrompt, attribution);
        }
        catch (MetisLlmException e) {
            if (e.getKind() == MetisLlmException.Kind.INVALID_OUTPUT) {
                return Optional.empty();
            }
            throw e;
        }
        return parseRating(completion.text(), request);
    }

    /**
     * Parses and clamps the output of the model. The output is unusable if it holds no JSON object of the expected shape or a score below 1, which is also
     * what a missing score reads as.
     */
    private Optional<Rating> parseRating(String text, RatingRequest request) {
        String json = extractJson(text);
        if (json == null) {
            return Optional.empty();
        }
        RatingOutput output;
        try {
            output = outputConverter.convert(json);
        }
        catch (RuntimeException e) {
            // The message of the parser can quote the output, which may quote the student's code, so it is not logged
            log.debug("The rating of the answer to Metis question {} is no valid JSON of the expected shape", request.question().getId());
            return Optional.empty();
        }
        if (output == null || output.score() < 1) {
            return Optional.empty();
        }
        int score = Math.min(output.score(), request.config().getLikertScalePoints());
        // Both texts are stored; a control character such as U+0000 would make PostgreSQL refuse the rating or the follow-up
        String rationaleText = MetisPromptTemplateService.withoutControlCharacters(output.rationale()).strip();
        String rationale = rationaleText.isEmpty() ? MISSING_RATIONALE : MetisPromptTemplateService.truncate(rationaleText, MAX_RATIONALE_CHARACTERS);
        String followUpText = MetisPromptTemplateService.withoutControlCharacters(output.followUpQuestion()).strip();
        String followUpQuestion = request.followUpAllowed() && !followUpText.isEmpty() ? followUpText : null;
        Integer followUpTimeLimit = followUpQuestion != null && output.followUpTimeLimitSeconds() > 0 ? output.followUpTimeLimitSeconds() : null;
        return Optional.of(new Rating(score, rationale, followUpQuestion, followUpTimeLimit));
    }

    private static Map<String, Object> systemPromptVariables(RatingRequest request) {
        MetisExerciseConfig config = request.config();
        return Map.of("likertScalePoints", config.getLikertScalePoints(), "scaleAnchors", scaleAnchors(config.getLikertScalePoints()), "answerLengthInstruction",
                answerLengthInstruction(config.getAnswerLength()), "followUpInstruction", followUpInstruction(request));
    }

    private Map<String, Object> userPromptVariables(RatingRequest request) {
        String focusFilePath = request.mainQuestion().getFocusFilePath();
        Optional<MetisDiffService.FocusFileContext> focusFile = diffService.readFocusFileContext(request.repositoryUri(), request.commitHash(), focusFilePath);
        return Map.of("focusFilePath", isBlank(focusFilePath) ? NONE : MetisPromptTemplateService.sanitize(focusFilePath), "focusFileContent",
                focusFile.map(MetisAnswerRatingService::focusFileContent).orElse(CODE_NOT_AVAILABLE), "thread", thread(request.earlierInThread()), "question",
                MetisPromptTemplateService.sanitize(request.question().getQuestionText()), "answer", MetisPromptTemplateService.sanitize(request.answerText()), "format",
                outputConverter.getFormat());
    }

    /**
     * The focus file for the prompt: its numbered content, else its diff, else a note that the code is not available.
     */
    private static String focusFileContent(MetisDiffService.FocusFileContext focusFile) {
        if (focusFile.content() != null) {
            return MetisPromptTemplateService.numberLines(MetisPromptTemplateService.sanitize(focusFile.content()));
        }
        if (focusFile.unifiedDiff() != null) {
            return DIFF_ONLY_NOTE + "\n" + MetisPromptTemplateService.sanitize(focusFile.unifiedDiff());
        }
        return CODE_NOT_AVAILABLE;
    }

    /**
     * The earlier questions of the thread with their answers, each fenced as data, or a note that there are none.
     */
    private static String thread(List<MetisQuestion> earlierInThread) {
        if (earlierInThread.isEmpty()) {
            return NONE;
        }
        List<String> entries = new ArrayList<>(earlierInThread.size());
        for (int index = 0; index < earlierInThread.size(); index++) {
            MetisQuestion earlier = earlierInThread.get(index);
            String answer = isBlank(earlier.getAnswerText()) ? NO_ANSWER : MetisPromptTemplateService.sanitize(earlier.getAnswerText());
            int number = index + 1;
            entries.add(String.format(Locale.ROOT, EARLIER_QUESTION_BLOCK, number, MetisPromptTemplateService.sanitize(earlier.getQuestionText()), number, number, answer, number));
        }
        return String.join("\n\n", entries);
    }

    private static boolean isBlank(@Nullable String text) {
        return text == null || text.isBlank();
    }
}
