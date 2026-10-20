package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;

/**
 * Judges a generated quiz question on every {@link QuizQuestionFailureMode} in a single model call and decides whether to accept it.
 * <p>
 * Every mode is recorded, but only a gating mode's severity can reject: the aggregate score is one minus the worst gating severity,
 * and the question is accepted when the aggregate reaches {@link #ACCEPT_THRESHOLD}. A verdict that does not cover every mode is
 * discarded rather than defaulted. The call goes to the judge model configured under {@code artemis.hyperion.quiz-pool.judge-model},
 * or to the default chat model when none is configured.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionFilterService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionFilterService.class);

    private static final String PROMPT_FILTER_QUIZ_QUESTION_SYSTEM = "/prompts/hyperion/filter_quiz_question_system.st";

    private static final String PROMPT_FILTER_QUIZ_QUESTION_USER = "/prompts/hyperion/filter_quiz_question_user.st";

    private static final String FILTER_PIPELINE_ID = "HYPERION_QUIZ_QUESTION_FILTER";

    /** Minimum aggregate score in [0, 1] a question needs to be accepted. */
    static final double ACCEPT_THRESHOLD = 0.7;

    /**
     * Modes whose severity decides acceptance. {@link QuizQuestionFailureMode#NEAR_DUPLICATE} is judged and reported but never
     * rejects on its own, because a question grounded in lecture material necessarily resembles it.
     */
    static final Set<QuizQuestionFailureMode> GATING_MODES = Set.of(QuizQuestionFailureMode.FACTUAL_ERROR, QuizQuestionFailureMode.AMBIGUOUS_CORRECT_ANSWER,
            QuizQuestionFailureMode.OFF_TOPIC, QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS);

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final String judgeModel;

    public HyperionQuizQuestionFilterService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            @Value("${artemis.hyperion.quiz-pool.judge-model:}") String judgeModel) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.judgeModel = judgeModel == null ? "" : judgeModel.trim();
    }

    /**
     * Returns the configured judge model, or an empty string when the default chat model is used.
     *
     * @return the model name sent with every filter call, or empty
     */
    public String getJudgeModel() {
        return judgeModel;
    }

    /**
     * Judges one question against the material it was generated from.
     *
     * @param course         the course the question was generated for
     * @param userId         the user the token usage is attributed to; may be {@code null}
     * @param question       the question to judge
     * @param groundingBlock the material block the generator saw, or a note that none was supplied
     * @return the decision, or empty when the filter call failed or returned no usable verdict
     */
    public Optional<QuizQuestionFilterDecisionDTO> evaluate(Course course, @Nullable Long userId, GeneratedQuizQuestionDTO question, String groundingBlock) {
        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "QuizQuestionFilter", "QuizQuestionFilter.chatClientNotConfigured");
        }

        var outputConverter = HyperionStructuredOutputs.converterFor(FilterOutput.class);
        String systemPrompt = templateService.render(PROMPT_FILTER_QUIZ_QUESTION_SYSTEM, Map.of());
        String userPrompt = templateService.renderObject(PROMPT_FILTER_QUIZ_QUESTION_USER,
                Map.ofEntries(Map.entry("groundingBlock", groundingBlock), Map.entry("questionType", question.type().getValue()), Map.entry("questionTitle", question.title()),
                        Map.entry("questionText", question.questionText()), Map.entry("options", renderOptions(question.options())),
                        Map.entry("explanation", question.explanation() == null ? "" : question.explanation()), Map.entry("format", outputConverter.getFormat())));

        ChatResponse chatResponse;
        try {
            var request = chatClient.prompt().system(systemPrompt).user(userPrompt);
            if (!judgeModel.isEmpty()) {
                request = request.options(ChatOptions.builder().model(judgeModel));
            }
            chatResponse = request.call().chatResponse();
        }
        catch (Exception e) {
            log.warn("Failed to filter quiz question '{}' for course [{}]", question.title(), course.getId(), e);
            return Optional.empty();
        }

        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, FILTER_PIPELINE_ID, builder -> builder.withCourse(course.getId()).withUser(userId));

        String responseText = LLMTokenUsageService.extractResponseText(chatResponse);
        if (responseText == null) {
            log.warn("Filter returned an empty response for quiz question '{}' in course [{}]", question.title(), course.getId());
            return Optional.empty();
        }

        FilterOutput output;
        try {
            output = outputConverter.convert(responseText);
        }
        catch (Exception e) {
            log.warn("Failed to parse the filter verdict for quiz question '{}' in course [{}]: {}", question.title(), course.getId(), e.getMessage());
            return Optional.empty();
        }
        if (output == null || output.modes() == null) {
            return Optional.empty();
        }

        Map<QuizQuestionFailureMode, QuizQuestionFilterVerdictDTO> verdicts = toVerdicts(output.modes());
        if (verdicts.size() != QuizQuestionFailureMode.values().length) {
            log.warn("Filter judged {} of {} failure modes for quiz question '{}'; discarding the incomplete verdict", verdicts.size(), QuizQuestionFailureMode.values().length,
                    question.title());
            return Optional.empty();
        }
        return Optional.of(decide(verdicts, ACCEPT_THRESHOLD, GATING_MODES, sanitizeInput(output.rationale())));
    }

    /**
     * Derives a decision from judged verdicts.
     *
     * @param verdicts    every judged mode and its verdict, in the order they are reported
     * @param threshold   minimum aggregate score in [0, 1] required for acceptance
     * @param gatingModes modes whose severity decides acceptance; the aggregate is one minus the worst severity among these
     * @param rationale   the filter's overall rationale
     * @return the decision
     */
    static QuizQuestionFilterDecisionDTO decide(Map<QuizQuestionFailureMode, QuizQuestionFilterVerdictDTO> verdicts, double threshold, Set<QuizQuestionFailureMode> gatingModes,
            String rationale) {
        double worstSeverity = verdicts.entrySet().stream().filter(entry -> gatingModes.contains(entry.getKey())).mapToDouble(entry -> entry.getValue().severity()).max().orElse(0);
        double aggregate = 1 - worstSeverity;
        return new QuizQuestionFilterDecisionDTO(aggregate >= threshold, aggregate, List.copyOf(verdicts.values()), rationale);
    }

    /**
     * Maps the judged entries to verdicts. Entries that are {@code null}, name no known mode or carry no severity are skipped, so a reply
     * with such entries fails the completeness check and is retried instead of defaulting a missing severity to "absent".
     */
    private static Map<QuizQuestionFailureMode, QuizQuestionFilterVerdictDTO> toVerdicts(@Nullable List<ModeScore> scores) {
        Map<QuizQuestionFailureMode, QuizQuestionFilterVerdictDTO> verdicts = new EnumMap<>(QuizQuestionFailureMode.class);
        if (scores == null) {
            return verdicts;
        }
        for (ModeScore score : scores) {
            if (score == null || score.severity() == null) {
                continue;
            }
            parseMode(score.mode()).ifPresent(mode -> verdicts.put(mode,
                    new QuizQuestionFilterVerdictDTO(mode, clamp(score.severity()), Boolean.TRUE.equals(score.triggered()), sanitizeInput(score.justification()))));
        }
        return verdicts;
    }

    private static Optional<QuizQuestionFailureMode> parseMode(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Optional.of(QuizQuestionFailureMode.valueOf(normalised));
        }
        catch (IllegalArgumentException e) {
            log.debug("Unknown failure mode in filter output: {}", raw);
            return Optional.empty();
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String renderOptions(List<GeneratedQuizAnswerOptionDTO> options) {
        StringBuilder rendered = new StringBuilder();
        for (GeneratedQuizAnswerOptionDTO option : options) {
            rendered.append("- [").append(option.correct() ? "correct" : "wrong").append("] ").append(option.text()).append('\n');
        }
        return rendered.toString().strip();
    }

    private record FilterOutput(@JsonPropertyDescription("one entry per failure mode") List<ModeScore> modes,
            @JsonPropertyDescription("overall rationale in one or two sentences") String rationale) {
    }

    private record ModeScore(@JsonPropertyDescription("the failure mode name") String mode, @JsonPropertyDescription("severity from 0.0 (absent) to 1.0 (severe)") Double severity,
            @JsonPropertyDescription("whether an instructor would reject or rewrite the question for this defect") Boolean triggered,
            @JsonPropertyDescription("one-sentence justification of the severity") String justification) {
    }
}
