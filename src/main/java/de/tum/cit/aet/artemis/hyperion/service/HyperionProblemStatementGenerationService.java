package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.sanitizeInput;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;

/**
 * Service for generating initial draft problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementGenerationService.class);

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    /**
     * Creates a new HyperionProblemStatementGenerationService.
     *
     * @param chatClient      the AI chat client, may be null if AI is not configured
     * @param templateService prompt template service
     */
    public HyperionProblemStatementGenerationService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    /**
     * Generate a problem statement for an exercise
     *
     * @param course     the course context for the problem statement
     * @param userPrompt the user's requirements and instructions for the problem statement
     * @return the generated problem statement response
     * @throws InternalServerErrorAlertException if generation fails or response is too long
     */
    public ProblemStatementGenerationResponseDTO generateProblemStatement(Course course, String userPrompt) {
        log.debug("Generating problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "Hyperion", "ProblemStatementGeneration.generationFailed");
        }

        String sanitizedPrompt = sanitizeInput(userPrompt != null ? userPrompt : "Generate a programming exercise problem statement");
        validateUserPrompt(sanitizedPrompt);

        Map<String, String> templateVariables = Map.of("userPrompt", sanitizedPrompt, "courseTitle", getSanitizedCourseTitle(course), "courseDescription",
                getSanitizedCourseDescription(course));

        String prompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement.st", templateVariables);

        String generatedProblemStatement;
        try {
            generatedProblemStatement = chatClient.prompt().user(prompt).call().content();
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement", "ProblemStatement", "ProblemStatementGeneration.generationFailed");
        }

        if (generatedProblemStatement == null) {
            throw new InternalServerErrorAlertException("Generated problem statement is null", "ProblemStatement", "ProblemStatementGeneration.generationFailed");
        }

        // Validate response length
        if (generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            log.warn("Generated problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), generatedProblemStatement.length());
            throw new InternalServerErrorAlertException(
                    "Generated problem statement is too long (" + generatedProblemStatement.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                    "ProblemStatement", "ProblemStatementGeneration.generatedProblemStatementTooLong");
        }

        return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
    }

    /**
     * Validates that the user prompt is not empty and does not exceed the maximum allowed length.
     */
    private void validateUserPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BadRequestAlertException("User prompt cannot be empty", "ProblemStatement", "ProblemStatementGeneration.userPromptEmpty");
        }
        if (userPrompt.length() > MAX_USER_PROMPT_LENGTH) {
            throw new BadRequestAlertException("User prompt exceeds maximum length of " + MAX_USER_PROMPT_LENGTH + " characters", "ProblemStatement",
                    "ProblemStatementGeneration.userPromptTooLong");
        }
    }
}
