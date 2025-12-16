package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.InlineCommentDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;

/**
 * Service for refining existing problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementRefinementService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRefinementService.class);

    /**
     * Maximum allowed length for generated problem statements (50,000 characters).
     * This prevents excessively long responses that could cause performance issues.
     */
    private static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    /**
     * Creates a new HyperionProblemStatementRefinementService.
     *
     * @param chatClient      the AI chat client for refining problem statements,
     *                            may be null if AI is not configured
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
     * @throws IllegalStateException if the AI chat client is not configured
     */
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            log.warn("Cannot refine empty problem statement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", java.util.Objects.toString(originalProblemStatementText, ""));
        }

        if (chatClient == null) {
            log.error("Cannot refine problem statement: AI chat client is not configured");
            throw new IllegalStateException("AI chat client is not configured. Please ensure Hyperion AI service is properly configured.");
        }

        try {
            Map<String, String> templateVariables = Map.of("text", originalProblemStatementText.trim(), "userPrompt",
                    userPrompt != null ? userPrompt : "Refine a programming exercise problem statement", "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement.st", templateVariables);
            String refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();

            if (refinedProblemStatementText == null) {
                log.warn("Refined problem statement is null for course [{}]", course.getId());
                throw new InternalServerErrorAlertException("Refined problem statement is null", "ProblemStatement", "problemStatementRefinementNull");
            }

            return validateAndReturnResponse(course, originalProblemStatementText, refinedProblemStatementText);
        }
        catch (Exception e) {
            return handleRefinementError(course, originalProblemStatementText, e);
        }
    }

    /**
     * Refine a problem statement using targeted inline comments.
     * Each comment specifies a line range and instruction for that specific
     * section.
     *
     * @param course                       the course context
     * @param originalProblemStatementText the original problem statement text
     * @param inlineComments               list of inline comments with line ranges
     *                                         and instructions
     * @return the refinement response
     * @throws IllegalStateException if the AI chat client is not configured
     */
    public ProblemStatementRefinementResponseDTO refineProblemStatementWithComments(Course course, String originalProblemStatementText, List<InlineCommentDTO> inlineComments) {
        log.debug("Refining problem statement with {} inline comments for course [{}]", inlineComments.size(), course.getId());

        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            log.warn("Cannot refine empty problem statement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", originalProblemStatementText);
        }

        if (inlineComments == null || inlineComments.isEmpty()) {
            log.warn("No inline comments provided for refinement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", originalProblemStatementText);
        }

        if (chatClient == null) {
            log.error("Cannot refine problem statement with comments: AI chat client is not configured");
            throw new IllegalStateException("AI chat client is not configured. Please ensure Hyperion AI service is properly configured.");
        }

        try {
            // Build a combined prompt from all inline comments
            String combinedInstructions = buildCombinedInstructions(inlineComments);

            // Add line numbers to help the LLM identify exact lines to modify
            String textWithLineNumbers = addLineNumbers(originalProblemStatementText.trim());

            Map<String, String> templateVariables = Map.of("textWithLineNumbers", textWithLineNumbers, "targetedInstructions", combinedInstructions, "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            // Use the targeted refinement template for inline comments
            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement_targeted.st", templateVariables);
            String refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();

            return validateAndReturnResponse(course, originalProblemStatementText, refinedProblemStatementText);
        }
        catch (Exception e) {
            return handleRefinementError(course, originalProblemStatementText, e);
        }
    }

    /**
     * Builds a combined instruction string from multiple inline comments.
     * Format: "Lines X-Y: instruction\nLines A-B: instruction"
     */
    private String buildCombinedInstructions(List<InlineCommentDTO> inlineComments) {
        return inlineComments.stream().map(comment -> {
            String lineRef = comment.startLine().equals(comment.endLine()) ? "Line " + comment.startLine() : "Lines " + comment.startLine() + "-" + comment.endLine();
            return lineRef + ": " + comment.instruction();
        }).collect(Collectors.joining("\n"));
    }

    /**
     * Adds line numbers to each line of the problem statement.
     * Format: "1: first line\n2: second line\n..."
     * This helps the LLM accurately identify which lines to modify.
     */
    private String addLineNumbers(String text) {
        String[] lines = text.split("\n", -1); // -1 to preserve trailing empty lines
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            result.append(i + 1).append(": ").append(lines[i]);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Validates the refined response and returns the appropriate DTO.
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(Course course, String originalProblemStatementText, String refinedProblemStatementText) {
        // Validate response length
        if (refinedProblemStatementText != null && refinedProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            log.warn("Refined problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), refinedProblemStatementText.length());
            throw new InternalServerErrorAlertException(
                    "Refined problem statement is too long (" + refinedProblemStatementText.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                    "ProblemStatement", "refinedProblemStatementTooLong");
        }

        // Refinement didn't change content
        if (refinedProblemStatementText != null && refinedProblemStatementText.equals(originalProblemStatementText)) {
            log.warn("Refined problem statement unchanged for course [{}]", course.getId());
            throw new InternalServerErrorAlertException("Problem statement is the same after refinement", "ProblemStatement", "refinedProblemStatementUnchanged");
        }

        return new ProblemStatementRefinementResponseDTO(refinedProblemStatementText);
    }

    /**
     * Handles refinement errors by logging and throwing appropriate exception.
     * Re-throws InternalServerErrorAlertException unchanged to preserve specific
     * error keys.
     */
    private ProblemStatementRefinementResponseDTO handleRefinementError(Course course, String originalProblemStatementText, Exception e) {
        // Re-throw InternalServerErrorAlertException unchanged to preserve specific
        // error keys
        if (e instanceof InternalServerErrorAlertException alertException) {
            throw alertException;
        }

        // Log the error with the original problem statement length for debugging
        // purposes
        log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(),
                originalProblemStatementText != null ? originalProblemStatementText.length() : 0, e.getMessage(), e);

        throw new InternalServerErrorAlertException("Failed to refine problem statement: " + e.getMessage(), "ProblemStatement", "problemStatementRefinementFailed");
    }
}
