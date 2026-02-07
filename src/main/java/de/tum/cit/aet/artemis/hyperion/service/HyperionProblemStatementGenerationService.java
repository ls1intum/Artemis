package de.tum.cit.aet.artemis.hyperion.service;

import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
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

    /**
     * Maximum allowed length for generated problem statements (50,000 characters).
     * This prevents excessively long responses that could cause performance issues.
     */
    private static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    /** Pattern matching control characters except newline (\n), carriage return (\r), and tab (\t). */
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    /**
     * Creates a new HyperionProblemStatementGenerationService.
     *
     * @param chatClient      the AI chat client
     * @param templateService prompt template service
     */
    public HyperionProblemStatementGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    /**
     * Sanitizes user input by stripping control characters (except newlines, carriage returns, and tabs)
     * to reduce prompt injection risk.
     */
    private static String sanitizeUserInput(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = sanitized.replace("</user_input>", "");
        return sanitized.trim();
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

        try {

            String sanitizedPrompt = sanitizeUserInput(userPrompt != null ? userPrompt : "Generate a programming exercise problem statement");
            if (sanitizedPrompt.isBlank()) {
                throw new InternalServerErrorAlertException("User prompt is empty after sanitization", "ProblemStatement", "ProblemStatementGeneration.generationFailed");
            }
            String sanitizedTitle = course.getTitle() != null ? sanitizeUserInput(course.getTitle()) : "Programming Course";
            String sanitizedDescription = course.getDescription() != null ? sanitizeUserInput(course.getDescription()) : "A programming course";
            Map<String, String> templateVariables = Map.of("userPrompt", sanitizedPrompt, "courseTitle", sanitizedTitle, "courseDescription", sanitizedDescription);

            String prompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement.st", templateVariables);
            String generatedProblemStatement = chatClient.prompt().user(prompt).call().content();

            // Validate response length
            if (generatedProblemStatement != null && generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
                log.warn("Generated problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), generatedProblemStatement.length());
                throw new InternalServerErrorAlertException(
                        "Generated problem statement is too long (" + generatedProblemStatement.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                        "ProblemStatement", "ProblemStatementGeneration.generatedProblemStatementTooLong");
            }

            return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
        }
        catch (InternalServerErrorAlertException e) {
            // Re-throw our own exceptions
            throw e;
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement", "ProblemStatement", "ProblemStatementGeneration.generationFailed");
        }
    }
}
