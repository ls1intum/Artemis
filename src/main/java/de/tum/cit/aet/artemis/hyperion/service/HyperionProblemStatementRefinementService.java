package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH;
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
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Service for refining existing problem statements using Spring AI. Supports two refinement modes:
 * 1. Global refinement: Apply user prompt to the entire problem statement
 * TODO in follow up PR: 2. Targeted refinement (Canvas-style): Apply selection-based instructions to specific text ranges
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementRefinementService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRefinementService.class);

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    /**
     * Creates a new HyperionProblemStatementRefinementService.
     *
     * @param chatClient      the AI chat client for refining problem statements, may be null if AI is not configured
     * @param templateService the prompt template service for rendering AI prompts
     */
    public HyperionProblemStatementRefinementService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    /**
     * Refine a problem statement using a global user prompt.
     *
     * @param course                       the course context
     * @param originalProblemStatementText the original problem statement text
     * @param userPrompt                   the user's refinement instructions
     * @return the refinement response
     * @throws BadRequestAlertException          if the input is invalid (empty problem statement)
     * @throws InternalServerErrorAlertException if the AI chat client is not configured or refinement fails
     */
    @Observed(name = "hyperion.refine", contextualName = "problem statement refinement", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementRefinement.chatClientNotConfigured");
        }

        // Sanitize inputs first, then validate the sanitized versions to prevent
        // edge cases where raw input passes validation but becomes empty after sanitization
        String sanitizedProblemStatement = sanitizeInput(originalProblemStatementText);
        validateSanitizedProblemStatement(sanitizedProblemStatement);

        String sanitizedPrompt = sanitizeInput(userPrompt);
        HyperionPromptSanitizer.validateUserPrompt(sanitizedPrompt, "ProblemStatementRefinement");

        String systemPrompt = templateService.render("/prompts/hyperion/refine_problem_statement_system.st", Map.of());

        GlobalRefinementPromptVariables variables = new GlobalRefinementPromptVariables(sanitizedProblemStatement, sanitizedPrompt, getSanitizedCourseTitle(course),
                getSanitizedCourseDescription(course));
        String userMessage = templateService.render("/prompts/hyperion/refine_problem_statement_user.st", variables.asMap());

        String refinedProblemStatementText;
        try {
            refinedProblemStatementText = chatClient.prompt().system(systemPrompt).user(userMessage).call().content();
        }
        catch (Exception e) {
            log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(), originalProblemStatementText.length(),
                    e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to refine problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementFailed");
        }

        if (refinedProblemStatementText == null || refinedProblemStatementText.isBlank()) {
            throw new InternalServerErrorAlertException("Refined problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementRefinement.problemStatementRefinementNull");
        }

        return validateAndReturnResponse(sanitizedProblemStatement, refinedProblemStatementText.trim());
    }

    /**
     * Validates the refined response and returns the appropriate DTO.
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(String originalProblemStatementText, String refinedProblemStatementText) {
        if (refinedProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            log.warn("Refined problem statement exceeds maximum length: {} characters (max {})", refinedProblemStatementText.length(), MAX_PROBLEM_STATEMENT_LENGTH);
            throw new InternalServerErrorAlertException("Refined problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementRefinement.refinedProblemStatementTooLong");
        }

        // Refinement didn't change content — both sides are already sanitized/trimmed.
        if (refinedProblemStatementText.equals(originalProblemStatementText)) {
            throw new BadRequestAlertException("Problem statement is the same after refinement", "ProblemStatement", "ProblemStatementRefinement.refinedProblemStatementUnchanged");
        }

        return new ProblemStatementRefinementResponseDTO(refinedProblemStatementText);
    }

    /**
     * Validates the sanitized problem statement is non-empty and within length limits.
     * Called after sanitization to ensure edge cases (e.g., input consisting entirely of
     * control characters) are properly rejected.
     */
    private void validateSanitizedProblemStatement(String sanitizedProblemStatement) {
        if (sanitizedProblemStatement.isBlank()) {
            throw new BadRequestAlertException("Cannot refine empty problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementEmpty");
        }
        if (sanitizedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new BadRequestAlertException("Problem statement exceeds maximum length of " + MAX_PROBLEM_STATEMENT_LENGTH + " characters", "ProblemStatement",
                    "ProblemStatementRefinement.problemStatementTooLong");
        }
    }

    private record GlobalRefinementPromptVariables(String problemStatement, String userPrompt, String courseTitle, String courseDescription) {

        Map<String, String> asMap() {
            return Map.of("problemStatement", problemStatement, "userPrompt", userPrompt, "courseTitle", courseTitle, "courseDescription", courseDescription);
        }
    }
}
