package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.GeneratedMcQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.McOptionDTO;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class AiQuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationService.class);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    public AiQuizGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    public AiQuizGenerationResponseDTO generate(long courseId, AiQuizGenerationRequestDTO generationParams) {
        log.debug("Generating quiz for course {}", courseId);

        try {
            // load prompts from external templates
            String systemPrompt = templateService.render("/prompts/hyperion/quiz_generation_system.st", Map.of());
            String userPrompt = templateService.render("/prompts/hyperion/quiz_generation_user.st", generationParams.toTemplateVariables());

            // Use Spring AI's structured output for better parsing
            List<GeneratedMcQuestionDTO> questions = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(new ParameterizedTypeReference<>() {
            });

            // Validate all questions
            validateQuestions(questions);

            log.info("Successfully generated {} quiz questions for course {}", questions.size(), courseId);
            return new AiQuizGenerationResponseDTO(questions, List.of());
        }
        catch (IllegalArgumentException e) {
            log.error("Validation failed for generated questions in course {}: {}", courseId, e.getMessage(), e);
            return new AiQuizGenerationResponseDTO(List.of(), List.of("Generated questions failed validation: " + e.getMessage()));
        }
        catch (Exception e) {
            log.error("Unexpected error during quiz generation for course {}: {}", courseId, e.getMessage(), e);
            return new AiQuizGenerationResponseDTO(List.of(), List.of("Error during quiz generation: " + e.getMessage()));
        }
    }

    /**
     * Validates all generated questions according to business rules.
     * Throws IllegalArgumentException if any question fails validation.
     */
    private void validateQuestions(List<GeneratedMcQuestionDTO> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("No questions were generated");
        }

        for (int i = 0; i < questions.size(); i++) {
            GeneratedMcQuestionDTO question = questions.get(i);
            try {
                validateQuestion(question);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Question " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Validates a single question according to business rules.
     */
    private void validateQuestion(GeneratedMcQuestionDTO question) {
        // Check required fields
        if (question.title() == null || question.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (question.text() == null || question.text().isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (question.options() == null || question.options().isEmpty()) {
            throw new IllegalArgumentException("At least one option is required");
        }
        if (question.subtype() == null) {
            throw new IllegalArgumentException("Question subtype is required");
        }

        // Count correct answers
        long correctCount = question.options().stream().filter(McOptionDTO::correct).count();

        // Validate subtype-specific rules
        switch (question.subtype()) {
            case SINGLE_CORRECT -> {
                if (correctCount != 1) {
                    log.warn("SINGLE_CORRECT question '{}' has {} correct answers, expected exactly 1", question.title(), correctCount);
                    throw new IllegalArgumentException("SINGLE_CORRECT must have exactly 1 correct answer, found " + correctCount);
                }
                if (question.options().size() < 2) {
                    throw new IllegalArgumentException("SINGLE_CORRECT must have at least 2 options");
                }
            }
            case MULTI_CORRECT -> {
                if (correctCount < 1) {
                    log.warn("MULTI_CORRECT question '{}' has no correct answers", question.title());
                    throw new IllegalArgumentException("MULTI_CORRECT must have at least 1 correct answer");
                }
                if (question.options().size() < 2) {
                    throw new IllegalArgumentException("MULTI_CORRECT must have at least 2 options");
                }
            }
            case TRUE_FALSE -> {
                if (question.options().size() != 2) {
                    log.warn("TRUE_FALSE question '{}' has {} options, expected exactly 2", question.title(), question.options().size());
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 2 options, found " + question.options().size());
                }
                if (correctCount != 1) {
                    log.warn("TRUE_FALSE question '{}' has {} correct answers, expected exactly 1", question.title(), correctCount);
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 1 correct answer, found " + correctCount);
                }
            }
        }

        // Log non-critical warnings (won't fail validation)
        if (question.hint() == null || question.hint().isBlank()) {
            log.debug("Question '{}' has no hint", question.title());
        }
        if (question.explanation() == null || question.explanation().isBlank()) {
            log.debug("Question '{}' has no explanation", question.title());
        }
    }
}
