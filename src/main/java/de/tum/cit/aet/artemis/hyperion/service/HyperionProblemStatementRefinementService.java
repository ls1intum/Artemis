package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.isValidLlmOutput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.validateNoInjectionPatterns;

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
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        validateRefinementPrerequisites(originalProblemStatementText);

        String sanitizedPrompt = sanitizeInput(userPrompt);
        validateUserPrompt(sanitizedPrompt);
        validateNoInjectionPatterns(sanitizedPrompt, "ProblemStatementRefinement");

        String sanitizedProblemStatement = sanitizeInput(originalProblemStatementText);

        GlobalRefinementPromptVariables variables = new GlobalRefinementPromptVariables(sanitizedProblemStatement, sanitizedPrompt, getSanitizedCourseTitle(course),
                getSanitizedCourseDescription(course));

        String prompt = templateService.render("/prompts/hyperion/refine_problem_statement.st", variables.asMap());

        String refinedProblemStatementText;
        try {
            refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();
        }
        catch (Exception e) {
            throw handleRefinementError(course, originalProblemStatementText, e);
        }

        if (refinedProblemStatementText == null) {
            throw new InternalServerErrorAlertException("Refined problem statement is null", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementNull");
        }

        if (!isValidLlmOutput(refinedProblemStatementText)) {
            log.warn("LLM output for course [{}] contains meta-commentary, stripping preamble", course.getId());
            refinedProblemStatementText = stripMetaPreamble(refinedProblemStatementText);
        }

        return validateAndReturnResponse(originalProblemStatementText, refinedProblemStatementText);
    }

    /**
     * Validates the refined response and returns the appropriate DTO.
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(String originalProblemStatementText, String refinedProblemStatementText) {
        if (refinedProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new InternalServerErrorAlertException(
                    "Refined problem statement is too long (" + refinedProblemStatementText.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                    "ProblemStatement", "ProblemStatementRefinement.refinedProblemStatementTooLong");
        }

        // Refinement didn't change content (compare trimmed values to detect semantically unchanged content)
        if (refinedProblemStatementText.trim().equals(originalProblemStatementText.trim())) {
            throw new InternalServerErrorAlertException("Problem statement is the same after refinement", "ProblemStatement",
                    "ProblemStatementRefinement.refinedProblemStatementUnchanged");
        }

        return new ProblemStatementRefinementResponseDTO(refinedProblemStatementText);
    }

    /**
     * Handles refinement errors by logging and returning an appropriate exception to throw.
     * Re-throws InternalServerErrorAlertException and BadRequestAlertException unchanged to preserve specific error keys.
     *
     * @return a RuntimeException that the caller must throw
     */
    private RuntimeException handleRefinementError(Course course, String originalProblemStatementText, Exception e) {
        if (e instanceof InternalServerErrorAlertException alertException) {
            return alertException;
        }
        if (e instanceof BadRequestAlertException badRequestException) {
            return badRequestException;
        }

        log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(), originalProblemStatementText.length(),
                e.getMessage(), e);

        return new InternalServerErrorAlertException("Failed to refine problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementFailed");
    }

    /**
     * Validates that the problem statement and chat client are ready for refinement.
     */
    private void validateRefinementPrerequisites(String problemStatementText) {
        if (problemStatementText == null || problemStatementText.isBlank()) {
            throw new BadRequestAlertException("Cannot refine empty problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementEmpty");
        }
        if (problemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new BadRequestAlertException("Problem statement exceeds maximum length of " + MAX_PROBLEM_STATEMENT_LENGTH + " characters", "ProblemStatement",
                    "ProblemStatementRefinement.problemStatementTooLong");
        }
        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "Hyperion", "ProblemStatementRefinement.chatClientNotConfigured");
        }
    }

    /**
     * Validates that the user prompt is not empty and does not exceed the maximum allowed length.
     */
    private void validateUserPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BadRequestAlertException("User prompt cannot be empty", "ProblemStatement", "ProblemStatementRefinement.userPromptEmpty");
        }
        if (userPrompt.length() > MAX_USER_PROMPT_LENGTH) {
            throw new BadRequestAlertException("User prompt exceeds maximum length of " + MAX_USER_PROMPT_LENGTH + " characters", "ProblemStatement",
                    "ProblemStatementRefinement.userPromptTooLong");
        }
    }

    /**
     * Strips leading meta-commentary lines (e.g. "Here is the refined problem statement:") from LLM output,
     * returning content starting from the first Markdown heading or non-empty content line.
     */
    private static String stripMetaPreamble(String output) {
        String[] lines = output.split("\n");
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("#") || (trimmed.startsWith("[") && trimmed.contains("[task]")) || trimmed.startsWith("@startuml")) {
                start = i;
                break;
            }
            if (!trimmed.isEmpty() && !isValidLlmOutput(trimmed)) {
                continue;
            }
            if (!trimmed.isEmpty()) {
                start = i;
                break;
            }
        }
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length)).trim();
    }

    private interface RefinementPromptVariables {

        Map<String, String> asMap();
    }

    private record GlobalRefinementPromptVariables(String problemStatement, String userPrompt, String courseTitle, String courseDescription) implements RefinementPromptVariables {

        @Override
        public Map<String, String> asMap() {
            return Map.of("problemStatement", problemStatement, "userPrompt", userPrompt, "courseTitle", courseTitle, "courseDescription", courseDescription);
        }
    }
}
