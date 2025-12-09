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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuestionSubtype;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.McOptionDTO;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class AiQuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationService.class);

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
     * Generates an AI-based quiz for a given course using the specified generation parameters.
     *
     * @param courseId         the ID of the course for which the quiz should be generated (currently used for logging)
     * @param generationParams the parameters that define how the quiz should be generated
     * @return an {@link AiQuizGenerationResponseDTO} containing the generated quiz data
     */
    public AiQuizGenerationResponseDTO generate(long courseId, AiQuizGenerationRequestDTO generationParams) {
        log.debug("Generating quiz for course {}", courseId);
        try {
            String systemPrompt = templateService.render("/prompts/hyperion/quiz_generation_system.st", Map.of());
            String userPrompt = templateService.render("/prompts/hyperion/quiz_generation_user.st", generationParams.toTemplateVariables());

            // Use Spring AI's structured output for parsing
            List<GeneratedQuizQuestionDTO> questions = chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(new ParameterizedTypeReference<>() {
            });

            List<String> warnings = new ArrayList<>();
            List<GeneratedQuizQuestionDTO> validQuestions = new ArrayList<>();

            if (questions == null || questions.isEmpty()) {
                warnings.add("No questions were generated");
                log.warn("AI quiz generation returned no questions for course {}", courseId);
            }
            else {
                for (int i = 0; i < questions.size(); i++) {
                    GeneratedQuizQuestionDTO question = questions.get(i);

                    // 1) run Bean Validation on the DTO (title/text/not blank etc.)
                    Set<ConstraintViolation<GeneratedQuizQuestionDTO>> dtoViolations = validator.validate(question);
                    if (!dtoViolations.isEmpty()) {
                        String msg = "Question " + (i + 1) + " violated constraints: "
                                + dtoViolations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
                        log.warn(msg);
                        warnings.add(msg);
                        continue;
                    }

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
        catch (RuntimeException e) {
            if (isJsonProcessingError(e)) {
                log.warn("AI quiz generation returned an invalid JSON structure for course {}: {}", courseId, e.getMessage());
                String msg = "The model's response could not be processed. Please try again with a different topic or adjust the settings.";
                return new AiQuizGenerationResponseDTO(List.of(), List.of(msg));
            }
            throw e;
        }
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

    /**
     * Validates a single question according to quiz-specific business rules.
     * <p>
     * Bean Validation on {@link GeneratedQuizQuestionDTO} is executed before this method
     * is called, so annotation-based constraints (e.g. @NotBlank, @Size) are already enforced.
     */
    private void validateQuestion(GeneratedQuizQuestionDTO question) {
        // Bean Validation already ensures basic fields like title/text are not blank.
        // Here we only enforce quiz-specific business rules.

        if (question.options() == null || question.options().isEmpty()) {
            throw new IllegalArgumentException("At least one option is required");
        }
        if (question.subtype() == null) {
            throw new IllegalArgumentException("Question subtype is required");
        }

        // Count correct answers
        long correctCount = question.options().stream().filter(McOptionDTO::correct).count();
        switch (question.subtype()) {
            case SINGLE_CORRECT, TRUE_FALSE -> {
                if (correctCount != 1) {
                    throw new IllegalArgumentException(question.subtype() + " must have exactly 1 correct answer, found " + correctCount);
                }

                int optionCount = question.options().size();

                if (question.subtype() == AiQuestionSubtype.SINGLE_CORRECT && optionCount < 2) {
                    throw new IllegalArgumentException("SINGLE_CORRECT must have at least 2 options");
                }
                if (question.subtype() == AiQuestionSubtype.TRUE_FALSE && optionCount != 2) {
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 2 options, found " + optionCount);
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
        }
    }
}
