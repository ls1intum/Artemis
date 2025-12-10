package de.tum.cit.aet.artemis.hyperion.service;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuestionSubtype;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.McOptionDTO;

/**
 * Service for generating quiz questions using Hyperion.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class AiQuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationService.class);

    private static final String ENTITY_NAME = "aiQuizGeneration";

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final Validator validator;

    @SuppressWarnings("resource")
    public AiQuizGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Generates quiz questions for a given course using Hyperion.
     * <p>
     * If an unexpected error occurs, a runtime exception is thrown which is
     * translated by the REST layer into an HTTP 500 response.
     * </p>
     *
     * @param courseId         the id of the course for which questions are generated
     * @param generationParams the quiz generation request parameters
     * @return the AI quiz generation response with the generated questions
     */
    public AiQuizGenerationResponseDTO generate(long courseId, AiQuizGenerationRequestDTO generationParams) {
        log.debug("Generating quiz for course {}", courseId);

        final List<GeneratedQuizQuestionDTO> questions;
        try {
            String systemPrompt = templateService.render("/prompts/hyperion/quiz_generation_system.st", Map.of());
            String userPrompt = templateService.render("/prompts/hyperion/quiz_generation_user.st", generationParams.toTemplateVariables());

            questions = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(new ParameterizedTypeReference<>() {
            });
        }
        catch (RuntimeException e) {
            if (isJsonProcessingError(e)) {
                log.warn("AI quiz generation returned an invalid JSON structure for course {}: {}", courseId, e.getMessage());
                throw new InternalServerErrorAlertException("The AI response could not be processed. Please try again with a different topic or adjust the settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidModelResponse");
            }

            log.error("Unexpected error while contacting AI service for course {}: {}", courseId, e.getMessage(), e);
            throw new InternalServerErrorAlertException("An unexpected error occurred while contacting the AI service.", ENTITY_NAME, "aiQuizGeneration.unexpectedError");
        }

        if (questions == null || questions.isEmpty()) {
            log.warn("AI quiz generation returned no questions for course {}", courseId);
            throw new InternalServerErrorAlertException("The AI did not return any quiz questions.", ENTITY_NAME, "aiQuizGeneration.noQuestionsGenerated");
        }

        for (int i = 0; i < questions.size(); i++) {
            validateQuestion(questions.get(i), i + 1);
        }

        log.info("Successfully validated {} quiz questions for course {}", questions.size(), courseId);
        return new AiQuizGenerationResponseDTO(questions);
    }

    private boolean isJsonProcessingError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonProcessingException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateQuestion(GeneratedQuizQuestionDTO question, int index) {
        Set<ConstraintViolation<GeneratedQuizQuestionDTO>> dtoViolations = validator.validate(question);
        if (!dtoViolations.isEmpty()) {
            String detail = dtoViolations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
            log.warn("Question {} violated constraints: {}", index, detail);

            throw new InternalServerErrorAlertException("The generated quiz question was incomplete or invalid.", ENTITY_NAME, "aiQuizGeneration.invalidQuestion");
        }

        if (question.options() == null || question.options().isEmpty()) {
            log.warn("Question {} has no options", index);
            throw new InternalServerErrorAlertException("The generated quiz question was missing answer options.", ENTITY_NAME, "aiQuizGeneration.invalidQuestionRules");
        }

        if (question.subtype() == null) {
            log.warn("Question {} has no subtype", index);
            throw new InternalServerErrorAlertException("The generated quiz question had no subtype.", ENTITY_NAME, "aiQuizGeneration.invalidQuestionRules");
        }

        long correctCount = question.options().stream().filter(McOptionDTO::correct).count();
        int optionCount = question.options().size();

        if (question.subtype() == AiQuestionSubtype.SINGLE_CORRECT || question.subtype() == AiQuestionSubtype.TRUE_FALSE) {
            if (correctCount != 1) {
                log.warn("Question {} subtype {} must have exactly 1 correct answer, found {}", index, question.subtype(), correctCount);
                throw new InternalServerErrorAlertException("The generated quiz question did not follow the requested quiz settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidQuestionRules");
            }

            if (question.subtype() == AiQuestionSubtype.SINGLE_CORRECT && optionCount < 2) {
                log.warn("Question {} SINGLE_CORRECT must have at least 2 options, found {}", index, optionCount);
                throw new InternalServerErrorAlertException("The generated quiz question did not follow the requested quiz settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidQuestionRules");
            }

            if (question.subtype() == AiQuestionSubtype.TRUE_FALSE && optionCount != 2) {
                log.warn("Question {} TRUE_FALSE must have exactly 2 options, found {}", index, optionCount);
                throw new InternalServerErrorAlertException("The generated quiz question did not follow the requested quiz settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidQuestionRules");
            }
        }
        else if (question.subtype() == AiQuestionSubtype.MULTI_CORRECT) {
            if (correctCount < 1) {
                log.warn("Question {} MULTI_CORRECT must have at least 1 correct answer", index);
                throw new InternalServerErrorAlertException("The generated quiz question did not follow the requested quiz settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidQuestionRules");
            }
            if (optionCount < 2) {
                log.warn("Question {} MULTI_CORRECT must have at least 2 options, found {}", index, optionCount);
                throw new InternalServerErrorAlertException("The generated quiz question did not follow the requested quiz settings.", ENTITY_NAME,
                        "aiQuizGeneration.invalidQuestionRules");
            }
        }
    }
}
