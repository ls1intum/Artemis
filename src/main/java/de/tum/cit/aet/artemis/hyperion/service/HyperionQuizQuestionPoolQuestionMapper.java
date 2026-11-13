package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.QuizQuestionCodeFormatting.formatCode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

/**
 * Maps the answer of a quiz question pool generation call to questions: parses the structured output, maps every question onto
 * {@link GeneratedQuizQuestionDTO} and drops the questions that fail {@link HyperionQuizQuestionChecks}. Source code given as plain text
 * is set in Markdown code, math in {@code \(…\)} or {@code \[…\]} is rewritten to dollar signs, and Markdown is removed from titles.
 */
final class HyperionQuizQuestionPoolQuestionMapper {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolQuestionMapper.class);

    /** Markdown in a title: backticks, asterisk bold and italic markers, and dollar-sign math delimiters. Underscores are kept, since identifiers contain them. */
    private static final Pattern TITLE_MARKDOWN = Pattern.compile("`|\\*|\\$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Fenced code blocks and code spans, inside which math delimiters are left as they are. */
    private static final Pattern MARKDOWN_CODE = Pattern.compile("```.*?```|`[^`]*`", Pattern.DOTALL);

    private static final Pattern INLINE_MATH_PARENTHESES = Pattern.compile("\\\\\\((.+?)\\\\\\)", Pattern.DOTALL);

    private static final Pattern DISPLAY_MATH_BRACKETS = Pattern.compile("\\\\\\[(.+?)\\\\]", Pattern.DOTALL);

    private HyperionQuizQuestionPoolQuestionMapper() {
    }

    /**
     * Returns the description of the output format that a generation prompt passes to the model.
     *
     * @return the format instructions
     */
    static String outputFormat() {
        return HyperionStructuredOutputs.converterFor(PoolQuestionsOutput.class).getFormat();
    }

    /**
     * Maps the model's answer to one question per position, {@code null} where the question at that position is unusable, so that the
     * question written for an item reaches that item. A question whose correct options do not number what its position asked for is
     * unusable.
     *
     * @param chatResponse        the model's answer
     * @param requestedType       the question type the call asked for
     * @param courseId            the course, for logging
     * @param correctOptionCounts the number of correct options asked for at each position
     * @param maxCorrectOptions   the most options a multiple-choice question may mark correct
     * @return the questions by position; shorter than the batch when the model returned fewer questions
     */
    static List<@Nullable GeneratedQuizQuestionDTO> parseQuestions(ChatResponse chatResponse, QuizQuestionGenerationType requestedType, long courseId,
            List<Integer> correctOptionCounts, int maxCorrectOptions) {
        String responseText = LLMTokenUsageService.extractResponseText(chatResponse);
        if (responseText == null || responseText.isBlank()) {
            log.warn("The model answered the generation of pool questions for course [{}] with no text", courseId);
            return List.of();
        }
        PoolQuestionsOutput output;
        try {
            output = HyperionStructuredOutputs.converterFor(PoolQuestionsOutput.class).convert(responseText);
        }
        catch (Exception e) {
            log.warn("Failed to parse generated pool questions for course [{}]: {}", courseId, e.getMessage());
            return List.of();
        }
        if (output == null || output.questions() == null || output.questions().isEmpty()) {
            log.warn("The model's answer to the generation of pool questions for course [{}] contains no questions", courseId);
            return List.of();
        }
        List<@Nullable GeneratedQuizQuestionDTO> questions = new ArrayList<>();
        Map<HyperionQuizQuestionChecks.Defect, Integer> defects = new EnumMap<>(HyperionQuizQuestionChecks.Defect.class);
        for (int index = 0; index < output.questions().size(); index++) {
            Optional<GeneratedQuizQuestionDTO> question = toQuestion(output.questions().get(index), requestedType);
            Optional<HyperionQuizQuestionChecks.Defect> defect = question.isPresent()
                    ? HyperionQuizQuestionChecks.defectOf(question.orElseThrow(), Set.of(requestedType), maxCorrectOptions)
                    : Optional.of(HyperionQuizQuestionChecks.Defect.UNMAPPABLE);
            if (defect.isEmpty() && index < correctOptionCounts.size() && correctOptionCount(question.orElseThrow()) != correctOptionCounts.get(index)) {
                defect = Optional.of(HyperionQuizQuestionChecks.Defect.CORRECT_COUNT);
            }
            if (defect.isPresent()) {
                defects.merge(defect.orElseThrow(), 1, Integer::sum);
                questions.add(null);
                continue;
            }
            questions.add(question.orElseThrow());
        }
        if (!defects.isEmpty()) {
            log.info("Discarded {} of {} generated pool questions for course [{}]: {}", defects.values().stream().mapToInt(Integer::intValue).sum(), output.questions().size(),
                    courseId, defects);
        }
        return questions;
    }

    private static int correctOptionCount(GeneratedQuizQuestionDTO question) {
        return (int) question.options().stream().filter(GeneratedQuizAnswerOptionDTO::correct).count();
    }

    /**
     * Maps a generated question to the DTO the checks and the pool use. A missing type counts as the requested one, because the model
     * was asked for exactly that type; a named type is matched ignoring case and separator characters, and an unknown name makes the
     * question unmappable. Source code the question text, an option or the explanation carries as plain text is set in Markdown code.
     *
     * @param generated     the model's question, or {@code null}
     * @param requestedType the type the generation call asked for
     * @return the question, or empty when it cannot be mapped
     */
    static Optional<GeneratedQuizQuestionDTO> toQuestion(@Nullable PoolQuestionOutput generated, QuizQuestionGenerationType requestedType) {
        if (generated == null || generated.options() == null || generated.options().stream().anyMatch(option -> option == null)) {
            return Optional.empty();
        }
        QuizQuestionGenerationType questionType = requestedType;
        if (generated.type() != null && !generated.type().isBlank()) {
            try {
                questionType = QuizQuestionGenerationType.fromValue(generated.type().trim().replace('_', '-').replace(' ', '-'));
            }
            catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        List<GeneratedQuizAnswerOptionDTO> options = generated.options().stream()
                .map(option -> new GeneratedQuizAnswerOptionDTO(format(option.text()), Boolean.TRUE.equals(option.correct()), null, null)).toList();
        String explanation = format(generated.explanation());
        return Optional.of(new GeneratedQuizQuestionDTO(questionType, plainTitle(generated.title()), format(generated.questionText()), options, null,
                explanation.isBlank() ? null : explanation));
    }

    /**
     * Removes the Markdown a model sets in a title, which is shown as plain text: backticks, asterisk bold and italic markers and dollar-sign
     * math delimiters. Underscores stay, since they belong to identifiers such as {@code __init__}.
     *
     * @param title the model's title, or {@code null}
     * @return the title without Markdown
     */
    static String plainTitle(@Nullable String title) {
        return WHITESPACE.matcher(TITLE_MARKDOWN.matcher(sanitizeInput(title)).replaceAll("")).replaceAll(" ").strip();
    }

    private static String format(@Nullable String text) {
        return formatCode(normaliseMathDelimiters(sanitizeInput(text)));
    }

    /**
     * Rewrites math delimited by {@code \(…\)} as {@code $…$} and math delimited by {@code \[…\]} as {@code $$…$$}, outside Markdown code,
     * since the Markdown renderer only recognises dollar signs.
     *
     * @param text a text of a generated question
     * @return the text with dollar-sign math delimiters
     */
    static String normaliseMathDelimiters(String text) {
        if (!text.contains("\\(") && !text.contains("\\[")) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        Matcher code = MARKDOWN_CODE.matcher(text);
        int start = 0;
        while (code.find()) {
            result.append(replaceMathDelimiters(text.substring(start, code.start()))).append(code.group());
            start = code.end();
        }
        return result.append(replaceMathDelimiters(text.substring(start))).toString();
    }

    private static String replaceMathDelimiters(String text) {
        String inline = INLINE_MATH_PARENTHESES.matcher(text).replaceAll(match -> Matcher.quoteReplacement("$" + match.group(1) + "$"));
        return DISPLAY_MATH_BRACKETS.matcher(inline).replaceAll(match -> Matcher.quoteReplacement("$$" + match.group(1) + "$$"));
    }

    private record PoolQuestionsOutput(List<PoolQuestionOutput> questions) {
    }

    record PoolQuestionOutput(@JsonPropertyDescription("the requested question type, exactly as given") String type, String title, String questionText,
            @JsonPropertyDescription("how many options the task requires this question to mark correct; decide it before writing the options") Integer correctOptionCount,
            List<PoolOptionOutput> options,
            @JsonPropertyDescription("why the correct option is correct, why the distractors are wrong, and what work the reader must do to reach the answer") String explanation) {
    }

    record PoolOptionOutput(String text, Boolean correct) {
    }
}
