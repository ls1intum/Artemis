package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionPromptSanitizer.sanitizeInputPreserveLines;

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
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementTargetedRefinementRequestDTO;

/**
 * Service for refining existing problem statements using Spring AI. Supports two refinement modes:
 * 1. Global refinement: Apply user prompt to the entire problem statement
 * 2. Targeted refinement (Canvas-style): Apply selection-based instructions to specific text ranges
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

    /**
     * Maximum length for displaying selected text in prompts.
     */
    private static final int MAX_SELECTED_TEXT_DISPLAY_LENGTH = 100;

    /**
     * Ellipsis suffix appended when truncating text for display.
     */
    private static final String ELLIPSIS = "...";

    /**
     * Default column value when no column is specified (first column, 1-indexed).
     */
    private static final int DEFAULT_COLUMN_ONE_INDEXED = 1;

    /**
     * Offset to convert a 1-indexed column to a 0-indexed Java string position.
     */
    private static final int ONE_INDEXED_TO_ZERO_INDEXED_OFFSET = 1;

    /**
     * Default course title when not specified.
     */
    private static final String DEFAULT_COURSE_TITLE = "Programming Course";

    /**
     * Default course description when not specified.
     */
    private static final String DEFAULT_COURSE_DESCRIPTION = "A programming course";

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

        return validateAndReturnResponse(sanitizedProblemStatement, refinedProblemStatementText);
    }

    /**
     * Refine a problem statement using targeted selection-based instructions (Canvas-style).
     * The instruction specifies a text selection (line/column range) and what change to apply.
     *
     * @param course  the course context
     * @param request the targeted refinement request containing text and instruction
     * @return the refinement response
     * @throws BadRequestAlertException          if the input is invalid (empty problem statement)
     * @throws InternalServerErrorAlertException if the AI chat client is not configured or refinement fails
     */
    @Observed(name = "hyperion.refine.targeted", contextualName = "problem statement targeted refinement", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementRefinementResponseDTO refineProblemStatementTargeted(Course course, ProblemStatementTargetedRefinementRequestDTO request) {
        log.debug("Refining problem statement with targeted instruction for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementRefinement.chatClientNotConfigured");
        }

        String originalProblemStatementText = request.problemStatementText();
        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            throw new BadRequestAlertException("Cannot refine empty problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementEmpty");
        }
        String sanitizedInstruction = sanitizeInput(request.instruction());
        HyperionPromptSanitizer.validateInstruction(sanitizedInstruction, "ProblemStatementRefinement");

        // For targeted refinement, use line-preserving sanitization to keep line numbers aligned
        // with the client's selection. Full sanitizeInput() could remove entire lines (e.g., delimiter
        // lines), shifting all subsequent line numbers and causing the LLM to edit the wrong lines.
        String sanitizedProblemStatement = sanitizeInputPreserveLines(originalProblemStatementText);
        validateSanitizedProblemStatement(sanitizedProblemStatement);
        String[] lines = sanitizedProblemStatement.split("\n", -1);
        validateLineRange(request.startLine() - 1, request.endLine() - 1, lines.length);
        String locationRef = buildLocationReference(request, lines);
        String targetedInstruction = locationRef + ": " + sanitizedInstruction;

        // Add line numbers to help the LLM identify exact lines to modify
        String textWithLineNumbers = addLineNumbers(sanitizedProblemStatement);

        // Validate total input length (use sanitized problem statement length to avoid line number prefix inflation)
        int totalLength = sanitizedProblemStatement.length() + targetedInstruction.length();
        if (totalLength > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new BadRequestAlertException("Input is too long (including instructions)", "ProblemStatement", "ProblemStatementRefinement.problemStatementTooLong");
        }

        TargetedRefinementPromptVariables variables = new TargetedRefinementPromptVariables(textWithLineNumbers, targetedInstruction, getSanitizedCourseTitle(course),
                getSanitizedCourseDescription(course));

        // Use separate system/user templates to prevent prompt injection from user-provided content
        String systemPrompt = templateService.render("/prompts/hyperion/refine_problem_statement_targeted_system.st", Map.of());
        String userMessage = templateService.render("/prompts/hyperion/refine_problem_statement_targeted_user.st", variables.asMap());

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

        return validateAndReturnResponse(sanitizedProblemStatement, refinedProblemStatementText);
    }

    /**
     * Builds a human-readable location reference for the LLM.
     * When column positions are provided, quotes the exact text to be modified.
     */
    private String buildLocationReference(ProblemStatementTargetedRefinementRequestDTO request, String[] lines) {
        boolean singleLine = request.startLine().equals(request.endLine());

        if (singleLine) {
            if (request.hasColumnRange()) {
                String selectedText = extractSelectedText(request, lines);
                return String.format("Line %d, columns %d-%d (modify ONLY the text: \"%s\")", request.startLine(), request.startColumn(), request.endColumn() - 1, selectedText);
            }
            return "Line " + request.startLine();
        }
        else {
            if (request.hasColumnRange()) {
                return String.format("Lines %d-%d, from column %d on line %d to column %d on line %d", request.startLine(), request.endLine(), request.startColumn(),
                        request.startLine(), request.endColumn() - 1, request.endLine());
            }
            return "Lines " + request.startLine() + "-" + request.endLine();
        }
    }

    /**
     * Extracts the selected text from the original content based on line and column positions.
     */
    private String extractSelectedText(ProblemStatementTargetedRefinementRequestDTO request, String[] lines) {
        int startLineIdx = request.startLine() - 1;
        int endLineIdx = request.endLine() - 1;

        validateLineRange(startLineIdx, endLineIdx, lines.length);

        if (startLineIdx == endLineIdx) {
            return extractSingleLineSelection(lines[startLineIdx], request.startColumn(), request.endColumn());
        }
        else {
            return extractMultiLineSelection(lines[startLineIdx], lines[endLineIdx], request.startColumn(), request.endColumn());
        }
    }

    /**
     * Validates that the line range is within bounds.
     */
    private void validateLineRange(int startLineIdx, int endLineIdx, int totalLines) {
        if (startLineIdx < 0 || endLineIdx >= totalLines) {
            throw new BadRequestAlertException("Invalid line range", "ProblemStatement", "ProblemStatementRefinement.invalidLineRange");
        }
    }

    /**
     * Extracts selected text from a single line.
     */
    private String extractSingleLineSelection(String line, Integer startColObj, Integer endColObj) {
        int startCol = resolveStartColumn(startColObj);
        int endCol = resolveEndColumn(endColObj, line.length());

        if (startCol < endCol && startCol < line.length()) {
            String text = line.substring(startCol, endCol);
            return truncateForDisplay(text);
        }
        throw new BadRequestAlertException("Failed to extract text for targeted refinement", "ProblemStatement", "ProblemStatementRefinement.textExtractionFailed");
    }

    /**
     * Extracts selected text spanning multiple lines.
     */
    private String extractMultiLineSelection(String firstLine, String lastLine, Integer startColObj, Integer endColObj) {
        int startCol = resolveStartColumn(startColObj);
        int endCol = resolveEndColumn(endColObj, lastLine.length());

        String startPart = startCol < firstLine.length() ? firstLine.substring(startCol) : "";
        String endPart = endCol <= lastLine.length() ? lastLine.substring(0, endCol) : lastLine;

        return truncateForDisplay(startPart + "..." + endPart);
    }

    /**
     * Converts a 1-indexed nullable column to a 0-indexed start column.
     */
    private int resolveStartColumn(Integer colObj) {
        return Math.max(0, (colObj != null ? colObj : DEFAULT_COLUMN_ONE_INDEXED) - ONE_INDEXED_TO_ZERO_INDEXED_OFFSET);
    }

    /**
     * Converts a 1-indexed exclusive nullable column to a 0-indexed exclusive end column suitable for {@link String#substring(int, int)}.
     * When colObj is null, defaults to the full line length.
     * <p>
     * Example: endColumn=5 (1-indexed, exclusive → chars 1-4 selected) is converted to
     * 0-indexed value 4, which is then used as the exclusive end in {@code String.substring(start, 4)},
     * correctly selecting characters at 0-indexed positions 0-3.
     */
    private int resolveEndColumn(Integer colObj, int lineLength) {
        if (colObj == null) {
            return lineLength;
        }
        return Math.min(lineLength, colObj - ONE_INDEXED_TO_ZERO_INDEXED_OFFSET);
    }

    /**
     * Truncates text for display in prompts if it exceeds the maximum length.
     */
    private String truncateForDisplay(String text) {
        if (text.length() > MAX_SELECTED_TEXT_DISPLAY_LENGTH) {
            return text.substring(0, MAX_SELECTED_TEXT_DISPLAY_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
        }
        return text;
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
     * Both inputs are trimmed before comparison to avoid false positives from
     * whitespace-only differences (e.g. targeted refinement uses line-preserving
     * sanitization which intentionally skips trimming).
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(String originalProblemStatementText, String refinedProblemStatementText) {
        String trimmedRefined = refinedProblemStatementText.trim();

        if (trimmedRefined.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            log.warn("Refined problem statement exceeds maximum length: {} characters (max {})", trimmedRefined.length(), MAX_PROBLEM_STATEMENT_LENGTH);
            throw new InternalServerErrorAlertException("Refined problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementRefinement.refinedProblemStatementTooLong");
        }

        if (trimmedRefined.equals(originalProblemStatementText.trim())) {
            throw new BadRequestAlertException("Problem statement is the same after refinement", "ProblemStatement", "ProblemStatementRefinement.refinedProblemStatementUnchanged");
        }

        return new ProblemStatementRefinementResponseDTO(trimmedRefined);
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

    private record TargetedRefinementPromptVariables(String textWithLineNumbers, String targetedInstructions, String courseTitle, String courseDescription) {

        Map<String, String> asMap() {
            return Map.of("textWithLineNumbers", textWithLineNumbers, "targetedInstructions", targetedInstructions, "courseTitle", courseTitle, "courseDescription",
                    courseDescription);
        }
    }
}
