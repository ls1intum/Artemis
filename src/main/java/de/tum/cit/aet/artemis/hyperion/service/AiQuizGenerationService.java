package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.GeneratedMcQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.McOptionDTO;

@Profile({ "localci & hyperion" })
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class AiQuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationService.class);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final Validator validator;

    public AiQuizGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        // will be reused for all requests
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Generates an AI-based quiz for a given course using the specified generation parameters.
     *
     * @param courseId         the ID of the course for which the quiz should be generated (currently used for logging)
     * @param generationParams the parameters that define how the quiz should be generated
     * @return an {@link AiQuizGenerationResponseDTO} containing the generated quiz data
     */
    public AiQuizGenerationResponseDTO generate(long courseId, AiQuizGenerationRequestDTO generationParams) {
        log.debug("Generating quiz for course {}", courseId);

        try {
            // load prompts from external templates
            String systemPrompt = templateService.render("/prompts/hyperion/quiz_generation_system.st", Map.of());
            String userPrompt = templateService.render("/prompts/hyperion/quiz_generation_user.st", generationParams.toTemplateVariables());

            // Use Spring AI's structured output for better parsing
            List<GeneratedMcQuestionDTO> questions = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(new ParameterizedTypeReference<>() {
            });

            List<String> warnings = new ArrayList<>();
            List<GeneratedMcQuestionDTO> validQuestions = new ArrayList<>();

            if (questions == null || questions.isEmpty()) {
                warnings.add("No questions were generated");
                log.warn("AI quiz generation returned no questions for course {}", courseId);
            }
            else {
                for (int i = 0; i < questions.size(); i++) {
                    GeneratedMcQuestionDTO question = questions.get(i);

                    // 1) run Bean Validation on the DTO (title/text/not blank etc.)
                    Set<ConstraintViolation<GeneratedMcQuestionDTO>> dtoViolations = validator.validate(question);
                    if (!dtoViolations.isEmpty()) {
                        String msg = "Question " + (i + 1) + " violated constraints: "
                                + dtoViolations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
                        log.warn(msg);
                        warnings.add(msg);
                        // skip this one, continue with the others
                        continue;
                    }

                    // 2) run business-rule validation (subtype-specific stuff)
                    try {
                        validateQuestion(question);
                        validQuestions.add(question);
                    }
                    catch (IllegalArgumentException e) {
                        String msg = "Question " + (i + 1) + " failed business validation: " + e.getMessage();
                        log.warn(msg);
                        warnings.add(msg);
                    }
                }
            }

            if (validQuestions.isEmpty()) {
                log.warn("All generated questions failed validation for course {}", courseId);
            }
            else {
                log.info("Successfully validated {} quiz questions ({} warnings) for course {}", validQuestions.size(), warnings.size(), courseId);
            }

            return new AiQuizGenerationResponseDTO(validQuestions, warnings);
        }
        catch (Exception e) {
            // this is the true unexpected-path fallback
            log.error("Unexpected error during quiz generation for course {}: {}", courseId, e.getMessage(), e);
            return new AiQuizGenerationResponseDTO(List.of(), List.of("Error during quiz generation: " + e.getMessage()));
        }
    }

    /**
     * Validates a single question according to business rules.
     */
    private void validateQuestion(GeneratedMcQuestionDTO question) {
        // Check required fields (Bean Validation already covers most of these, but we keep
        // the business rules here because they're quiz-specific and not just "not blank")
        if (question.options() == null || question.options().isEmpty()) {
            throw new IllegalArgumentException("At least one option is required");
        }
        if (question.subtype() == null) {
            throw new IllegalArgumentException("Question subtype is required");
        }
        // Check length constraints according to Artemis quiz model

        if (question.explanation() != null && question.explanation().length() > 500) {
            throw new IllegalArgumentException("The provided explanation is too long. Please shorten it to 500 characters.");
        }
        if (question.hint() != null && question.hint().length() > 500) {
            throw new IllegalArgumentException("The provided hint is too long. Please shorten it to 500 characters.");
        }

        // Count correct answers
        long correctCount = question.options().stream().filter(McOptionDTO::correct).count();

        // Validate subtype-specific rules
        switch (question.subtype()) {
            case SINGLE_CORRECT -> {
                if (correctCount != 1) {
                    throw new IllegalArgumentException("SINGLE_CORRECT must have exactly 1 correct answer, found " + correctCount);
                }
                if (question.options().size() < 2) {
                    throw new IllegalArgumentException("SINGLE_CORRECT must have at least 2 options");
                }
            }
            case MULTI_CORRECT -> {
                if (correctCount < 1) {
                    throw new IllegalArgumentException("MULTI_CORRECT must have at least 1 correct answer");
                }
                if (question.options().size() < 2) {
                    throw new IllegalArgumentException("MULTI_CORRECT must have at least 2 options");
                }
            }
            case TRUE_FALSE -> {
                if (question.options().size() != 2) {
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 2 options, found " + question.options().size());
                }
                if (correctCount != 1) {
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 1 correct answer, found " + correctCount);
                }
            }
        }

        // Non-critical fields -> just log
        if (question.hint() == null || question.hint().isBlank()) {
            log.debug("Question '{}' has no hint", question.title());
        }
        if (question.explanation() == null || question.explanation().isBlank()) {
            log.debug("Question '{}' has no explanation", question.title());
        }
    }
}
