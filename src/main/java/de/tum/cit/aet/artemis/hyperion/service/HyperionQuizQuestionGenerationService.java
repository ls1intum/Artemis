package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementResponseDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Service for generating quiz questions with Hyperion.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionGenerationService.class);

    private static final String PROMPT_GENERATE_QUIZ_QUESTIONS_SYSTEM = "/prompts/hyperion/generate_quiz_questions_system.st";

    private static final String PROMPT_GENERATE_QUIZ_QUESTIONS_USER = "/prompts/hyperion/generate_quiz_questions_user.st";

    private static final String PROMPT_REFINE_QUIZ_QUESTION_SYSTEM = "/prompts/hyperion/refine_quiz_question_system.st";

    private static final String PROMPT_REFINE_QUIZ_QUESTION_USER = "/prompts/hyperion/refine_quiz_question_user.st";

    private static final int MAX_QUESTION_TEXT_LENGTH = 10_000;

    private static final int MAX_QUESTION_TITLE_LENGTH = 500;

    private static final int MAX_OPTION_TEXT_LENGTH = 2_000;

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    public HyperionQuizQuestionGenerationService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    /**
     * Generate quiz questions for the provided course and configuration.
     *
     * @param course  the course context
     * @param request the quiz generation configuration
     * @return generated quiz questions
     */
    @Observed(name = "hyperion.quiz.generate", contextualName = "quiz question generation", lowCardinalityKeyValues = { "ai.span", "true" })
    public QuizQuestionGenerationResponseDTO generateQuizQuestions(Course course, QuizQuestionGenerationRequestDTO request) {
        log.debug("Generating quiz questions for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "QuizQuestionGeneration", "QuizQuestionGeneration.chatClientNotConfigured");
        }

        String topic = sanitizeInput(request.topic());
        String optionalPrompt = sanitizeInput(request.optionalPrompt());
        String requestedQuestionTypes = request.questionTypes().stream().map(QuizQuestionGenerationType::getValue).collect(Collectors.joining(", "));

        var outputConverter = new BeanOutputConverter<>(GeneratedQuestionsOutput.class);
        String systemPrompt = templateService.render(PROMPT_GENERATE_QUIZ_QUESTIONS_SYSTEM, Map.of());
        String userPrompt = templateService.renderObject(PROMPT_GENERATE_QUIZ_QUESTIONS_USER,
                Map.of("courseTitle", getSanitizedCourseTitle(course), "courseDescription", getSanitizedCourseDescription(course), "topic", topic, "optionalPrompt", optionalPrompt,
                        "language", request.language().getValue(), "numberOfQuestions", request.numberOfQuestions(), "difficulty", request.difficulty(), "questionTypes",
                        requestedQuestionTypes, "format", outputConverter.getFormat()));

        GeneratedQuestionsOutput generatedQuestions;
        try {
            generatedQuestions = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(outputConverter);
        }
        catch (Exception e) {
            log.error("Failed to generate quiz questions for course [{}]", course.getId(), e);
            throw new InternalServerErrorAlertException("Failed to generate quiz questions", "QuizQuestionGeneration", "QuizQuestionGeneration.generationFailed");
        }

        List<GeneratedQuizQuestionDTO> questions = mapAndValidateGeneratedQuestions(generatedQuestions);
        return new QuizQuestionGenerationResponseDTO(questions);
    }

    /**
     * Refine an existing quiz question based on user instructions.
     *
     * @param course  the course context
     * @param request the refinement request containing the original question and user instructions
     * @return the refined question and an explanation of the changes
     */
    @Observed(name = "hyperion.quiz.refine", contextualName = "quiz question refinement", lowCardinalityKeyValues = { "ai.span", "true" })
    public QuizQuestionRefinementResponseDTO refineQuizQuestion(Course course, QuizQuestionRefinementRequestDTO request) {
        log.debug("Refining quiz question for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "QuizQuestionRefinement", "QuizQuestionRefinement.chatClientNotConfigured");
        }

        String refinementPrompt = sanitizeInput(request.refinementPrompt());
        GeneratedQuizQuestionDTO originalQuestion = request.question();

        String questionHintLine = originalQuestion.hint() != null && !originalQuestion.hint().isBlank() ? "Hint: " + sanitizeInput(originalQuestion.hint()) : "";
        String questionExplanationLine = originalQuestion.explanation() != null && !originalQuestion.explanation().isBlank()
                ? "Explanation: " + sanitizeInput(originalQuestion.explanation())
                : "";

        String answerOptionsText = originalQuestion.options().stream().map(opt -> {
            StringBuilder sb = new StringBuilder("- [" + (opt.correct() ? "correct" : "wrong") + "] " + sanitizeInput(opt.text()));
            if (opt.hint() != null && !opt.hint().isBlank()) {
                sb.append("\n  Hint: ").append(sanitizeInput(opt.hint()));
            }
            if (opt.explanation() != null && !opt.explanation().isBlank()) {
                sb.append("\n  Explanation: ").append(sanitizeInput(opt.explanation()));
            }
            return sb.toString();
        }).collect(Collectors.joining("\n"));

        var outputConverter = new BeanOutputConverter<>(RefinedQuestionWithExplanationOutput.class);
        String systemPrompt = templateService.render(PROMPT_REFINE_QUIZ_QUESTION_SYSTEM, Map.of());
        String userPrompt = templateService.renderObject(PROMPT_REFINE_QUIZ_QUESTION_USER,
                Map.of("courseTitle", getSanitizedCourseTitle(course), "courseDescription", getSanitizedCourseDescription(course), "questionType",
                        originalQuestion.type().getValue(), "questionTitle", sanitizeInput(originalQuestion.title()), "questionText",
                        sanitizeInput(originalQuestion.questionText()), "questionHintLine", questionHintLine, "questionExplanationLine", questionExplanationLine, "answerOptions",
                        answerOptionsText, "refinementPrompt", refinementPrompt, "format", outputConverter.getFormat()));

        RefinedQuestionWithExplanationOutput output;
        try {
            output = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(outputConverter);
        }
        catch (Exception e) {
            log.error("Failed to refine quiz question for course [{}]", course.getId(), e);
            throw new InternalServerErrorAlertException("Failed to refine quiz question", "QuizQuestionRefinement", "QuizQuestionRefinement.refinementFailed");
        }

        if (output == null || output.question() == null) {
            throw new InternalServerErrorAlertException("Refined quiz question is empty", "QuizQuestionRefinement", "QuizQuestionRefinement.emptyResponse");
        }

        GeneratedQuizQuestionDTO refinedQuestion = mapAndValidateQuestion(output.question());
        String reasoning = sanitizeInput(output.reasoning());
        return new QuizQuestionRefinementResponseDTO(refinedQuestion, reasoning);
    }

    private List<GeneratedQuizQuestionDTO> mapAndValidateGeneratedQuestions(@Nullable GeneratedQuestionsOutput generatedQuestions) {
        if (generatedQuestions == null || generatedQuestions.questions() == null || generatedQuestions.questions().isEmpty()) {
            throw new InternalServerErrorAlertException("Generated quiz questions are empty", "QuizQuestionGeneration", "QuizQuestionGeneration.emptyResponse");
        }

        return generatedQuestions.questions().stream().map(this::mapAndValidateQuestion).toList();
    }

    private GeneratedQuizQuestionDTO mapAndValidateQuestion(@Nullable GeneratedQuestionOutput generatedQuestion) {
        if (generatedQuestion == null) {
            throw new InternalServerErrorAlertException("Generated quiz question is null", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidQuestion");
        }

        QuizQuestionGenerationType questionType;
        try {
            questionType = QuizQuestionGenerationType.fromValue(generatedQuestion.type());
        }
        catch (IllegalArgumentException e) {
            throw new InternalServerErrorAlertException("Generated quiz question type is invalid", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidQuestionType");
        }

        String questionTitle = sanitizeInput(generatedQuestion.title());
        if (questionTitle.isBlank() || questionTitle.length() > MAX_QUESTION_TITLE_LENGTH) {
            throw new InternalServerErrorAlertException("Generated quiz question title is invalid", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidQuestionTitle");
        }

        String questionText = sanitizeInput(generatedQuestion.questionText());
        if (questionText.isBlank() || questionText.length() > MAX_QUESTION_TEXT_LENGTH) {
            throw new InternalServerErrorAlertException("Generated quiz question text is invalid", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidQuestionText");
        }

        if (generatedQuestion.options() == null || generatedQuestion.options().size() < 2) {
            throw new InternalServerErrorAlertException("Generated quiz question options are invalid", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidOptions");
        }

        List<GeneratedQuizAnswerOptionDTO> options = generatedQuestion.options().stream().map(this::mapAndValidateOption).toList();
        validateCorrectOptionCount(questionType, options);

        String questionHint = generatedQuestion.hint() != null ? sanitizeInput(generatedQuestion.hint()) : null;
        String questionExplanation = generatedQuestion.explanation() != null ? sanitizeInput(generatedQuestion.explanation()) : null;

        return new GeneratedQuizQuestionDTO(questionType, questionTitle, questionText, options, questionHint, questionExplanation);
    }

    private GeneratedQuizAnswerOptionDTO mapAndValidateOption(@Nullable GeneratedOptionOutput generatedOption) {
        if (generatedOption == null) {
            throw new InternalServerErrorAlertException("Generated quiz answer option is null", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidOption");
        }

        String optionText = sanitizeInput(generatedOption.text());
        if (optionText.isBlank() || optionText.length() > MAX_OPTION_TEXT_LENGTH) {
            throw new InternalServerErrorAlertException("Generated quiz answer option text is invalid", "QuizQuestionGeneration", "QuizQuestionGeneration.invalidOptionText");
        }

        boolean isCorrect = generatedOption.correct() != null && generatedOption.correct();
        String optionHint = generatedOption.hint() != null ? sanitizeInput(generatedOption.hint()) : null;
        String optionExplanation = generatedOption.explanation() != null ? sanitizeInput(generatedOption.explanation()) : null;
        return new GeneratedQuizAnswerOptionDTO(optionText, isCorrect, optionHint, optionExplanation);
    }

    private static void validateCorrectOptionCount(QuizQuestionGenerationType questionType, List<GeneratedQuizAnswerOptionDTO> options) {
        long correctOptions = options.stream().filter(GeneratedQuizAnswerOptionDTO::correct).count();

        switch (questionType) {
            case SINGLE_CHOICE -> {
                if (correctOptions != 1) {
                    throw new InternalServerErrorAlertException("Single choice question must have exactly one correct answer", "QuizQuestionGeneration",
                            "QuizQuestionGeneration.invalidSingleChoiceCorrectCount");
                }
            }
            case MULTIPLE_CHOICE -> {
                if (correctOptions < 1) {
                    throw new InternalServerErrorAlertException("Multiple choice question must have at least one correct answer", "QuizQuestionGeneration",
                            "QuizQuestionGeneration.invalidMultipleChoiceCorrectCount");
                }
            }
            case TRUE_FALSE -> {
                if (options.size() != 2 || correctOptions != 1) {
                    throw new InternalServerErrorAlertException("True/false question must have exactly two options and one correct answer", "QuizQuestionGeneration",
                            "QuizQuestionGeneration.invalidTrueFalseStructure");
                }
            }
            default -> throw new InternalServerErrorAlertException("Unknown generated question type", "QuizQuestionGeneration", "QuizQuestionGeneration.unknownType");
        }
    }

    private record GeneratedQuestionsOutput(List<GeneratedQuestionOutput> questions) {
    }

    private record GeneratedQuestionOutput(String type, String title, String questionText, List<GeneratedOptionOutput> options,
            @JsonProperty(required = false) @JsonPropertyDescription("optional, omit if not needed") @Nullable String hint,
            @JsonProperty(required = false) @JsonPropertyDescription("optional, omit if not needed") @Nullable String explanation) {
    }

    private record GeneratedOptionOutput(String text, Boolean correct,
            @JsonProperty(required = false) @JsonPropertyDescription("optional, omit if not needed") @Nullable String hint,
            @JsonProperty(required = false) @JsonPropertyDescription("optional, omit if not needed") @Nullable String explanation) {
    }

    private record RefinedQuestionWithExplanationOutput(GeneratedQuestionOutput question, String reasoning) {
    }
}
