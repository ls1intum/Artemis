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
     * @throws InternalServerErrorAlertException if the AI chat client is not
     *                                               configured or refinement fails
     */
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            log.warn("Cannot refine empty problem statement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", java.util.Objects.toString(originalProblemStatementText, ""));
        }

        if (chatClient == null) {
            log.error("AI chat client is not configured for course {}. Please ensure Hyperion AI service is properly configured.", course.getId());
            throw new InternalServerErrorAlertException("AI chat client is not configured", "Hyperion", "chatClientNotConfigured");
        }

        try {
            // Validate input length
            if (originalProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
                log.warn("Original problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), originalProblemStatementText.length());
                throw new InternalServerErrorAlertException("Original problem statement is too long", "ProblemStatement", "problemStatementTooLong");
            }

            Map<String, String> templateVariables = Map.of("problemStatement", originalProblemStatementText.trim(), "userPrompt",
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
     * @throws InternalServerErrorAlertException if the AI chat client is not
     *                                               configured or refinement fails
     */
    public ProblemStatementRefinementResponseDTO refineProblemStatementWithComments(Course course, String originalProblemStatementText, List<InlineCommentDTO> inlineComments) {
        int commentCount = inlineComments == null ? 0 : inlineComments.size();
        log.debug("Refining problem statement with {} inline comments for course [{}]", commentCount, course.getId());

        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            log.warn("Cannot refine empty problem statement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", java.util.Objects.toString(originalProblemStatementText, ""));
        }

        if (inlineComments == null || inlineComments.isEmpty()) {
            log.warn("No inline comments provided for refinement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", java.util.Objects.toString(originalProblemStatementText, ""));
        }

        if (chatClient == null) {
            log.error("AI chat client is not configured for course {}. Please ensure Hyperion AI service is properly configured.", course.getId());
            throw new InternalServerErrorAlertException("AI chat client is not configured", "Hyperion", "chatClientNotConfigured");
        }

        try {
            // Build a combined prompt from all inline comments
            String combinedInstructions = buildCombinedInstructions(inlineComments, originalProblemStatementText.trim());

            // Add line numbers to help the LLM identify exact lines to modify
            String textWithLineNumbers = addLineNumbers(originalProblemStatementText.trim());

            // Validate total input length
            int totalLength = textWithLineNumbers.length() + combinedInstructions.length();
            if (totalLength > MAX_PROBLEM_STATEMENT_LENGTH) {
                log.warn("Combined input for course [{}] exceeds maximum length: {} characters", course.getId(), totalLength);
                throw new InternalServerErrorAlertException("Input is too long (including instructions)", "ProblemStatement", "problemStatementTooLong");
            }

            Map<String, String> templateVariables = Map.of("textWithLineNumbers", textWithLineNumbers, "targetedInstructions", combinedInstructions, "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            // Use the targeted refinement template for inline comments
            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement_targeted.st", templateVariables);
            String refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();

            if (refinedProblemStatementText == null) {
                log.warn("Refined problem statement is null for course [{}]", course.getId());
                throw new InternalServerErrorAlertException("AI returned null when refining the problem statement", "ProblemStatement", "problemStatementRefinementNull");
            }

            return validateAndReturnResponse(course, originalProblemStatementText, refinedProblemStatementText);
        }
        catch (Exception e) {
            return handleRefinementError(course, originalProblemStatementText, e);
        }
    }

    /**
     * Builds a combined instruction string from multiple inline comments.
     * Includes column positions when available for character-level targeting.
     * Format examples:
     * - "Line 5: instruction" (whole line)
     * - "Line 5, columns 10-25: instruction" (partial line)
     * - "Lines 5-7: instruction" (multiple lines)
     */
    private String buildCombinedInstructions(List<InlineCommentDTO> inlineComments, String originalText) {
        String[] lines = originalText.split("\n", -1);
        return inlineComments.stream().map(comment -> {
            String locationRef = buildLocationReference(comment, lines);
            return locationRef + ": " + comment.instruction();
        }).collect(Collectors.joining("\n"));
    }

    /**
     * Builds a human-readable location reference for the LLM.
     * When column positions are provided, quotes the exact text to be modified.
     */
    private String buildLocationReference(InlineCommentDTO comment, String[] lines) {
        boolean singleLine = comment.startLine().equals(comment.endLine());

        if (singleLine) {
            if (comment.hasColumnRange()) {
                String selectedText = extractSelectedText(comment, lines);
                return String.format("Line %d, columns %d-%d (modify ONLY the text: \"%s\")", comment.startLine(), comment.startColumn(), comment.endColumn(), selectedText);
            }
            return "Line " + comment.startLine();
        }
        else {
            if (comment.hasColumnRange()) {
                return String.format("Lines %d-%d, from column %d on line %d to column %d on line %d", comment.startLine(), comment.endLine(), comment.startColumn(),
                        comment.startLine(), comment.endColumn(), comment.endLine());
            }
            return "Lines " + comment.startLine() + "-" + comment.endLine();
        }
    }

    /**
     * Extracts the selected text from the original content based on line and column
     * positions.
     */
    private String extractSelectedText(InlineCommentDTO comment, String[] lines) {
        try {
            int startLineIdx = comment.startLine() - 1;
            int endLineIdx = comment.endLine() - 1;

            if (startLineIdx < 0 || endLineIdx >= lines.length) {
                return "[text]";
            }

            if (startLineIdx == endLineIdx) {
                // Single line selection
                String line = lines[startLineIdx];
                int startCol = Math.max(0, comment.startColumn() - 1);
                int endCol = Math.min(line.length(), comment.endColumn());
                if (startCol < endCol && startCol < line.length()) {
                    String text = line.substring(startCol, endCol);
                    // Truncate if too long for the prompt
                    return text.length() > 100 ? text.substring(0, 97) + "..." : text;
                }
            }
            else {
                // Multi-line selection - just return first and last parts
                String firstLine = lines[startLineIdx];
                String lastLine = lines[endLineIdx];
                int startCol = Math.max(0, comment.startColumn() - 1);
                int endCol = Math.min(lastLine.length(), comment.endColumn());

                String startPart = startCol < firstLine.length() ? firstLine.substring(startCol) : "";
                String endPart = endCol <= lastLine.length() ? lastLine.substring(0, endCol) : lastLine;

                String combined = startPart + "..." + endPart;
                return combined.length() > 100 ? combined.substring(0, 97) + "..." : combined;
            }
        }
        catch (Exception e) {
            // Fallback if extraction fails
            return "[selected text]";
        }
        return "[text]";
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

        // Refinement didn't change content (compare trimmed values to detect
        // semantically unchanged content)
        String originalTrimmed = originalProblemStatementText == null ? null : originalProblemStatementText.trim();
        String refinedTrimmed = refinedProblemStatementText == null ? null : refinedProblemStatementText.trim();
        if (refinedTrimmed != null && refinedTrimmed.equals(originalTrimmed)) {
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

        throw new InternalServerErrorAlertException("Failed to refine problem statement", "ProblemStatement", "problemStatementRefinementFailed");
    }
}
