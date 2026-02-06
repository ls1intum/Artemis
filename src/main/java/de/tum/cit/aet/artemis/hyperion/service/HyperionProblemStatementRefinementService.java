package de.tum.cit.aet.artemis.hyperion.service;

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
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        validateRefinementPrerequisites(originalProblemStatementText);
        validateUserPrompt(userPrompt);

        try {
            // Validate input length
            if (originalProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
                throw new InternalServerErrorAlertException("Original problem statement is too long", "ProblemStatement", "ProblemStatementRefinement.problemStatementTooLong");
            }

            GlobalRefinementPromptVariables variables = new GlobalRefinementPromptVariables(originalProblemStatementText.trim(), userPrompt, getCourseTitleOrDefault(course),
                    getCourseDescriptionOrDefault(course));

            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement.st", variables.asMap());
            String refinedProblemStatementText = null;
            if (chatClient != null) {
                refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();
            }

            if (refinedProblemStatementText == null) {
                throw new InternalServerErrorAlertException("Refined problem statement is null", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementNull");
            }

            return validateAndReturnResponse(originalProblemStatementText, refinedProblemStatementText);
        }
        catch (Exception e) {
            return handleRefinementError(course, originalProblemStatementText, e);
        }
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
    public ProblemStatementRefinementResponseDTO refineProblemStatementTargeted(Course course, ProblemStatementTargetedRefinementRequestDTO request) {
        log.debug("Refining problem statement with targeted instruction for course [{}]", course.getId());

        String originalProblemStatementText = request.problemStatementText();
        validateRefinementPrerequisites(originalProblemStatementText);

        try {
            // Build the instruction string
            String[] lines = originalProblemStatementText.split("\n", -1);
            String locationRef = buildLocationReference(request, lines);
            String targetedInstruction = locationRef + ": " + request.instruction();

            // Add line numbers to help the LLM identify exact lines to modify
            String textWithLineNumbers = addLineNumbers(originalProblemStatementText);

            // Validate total input length
            int totalLength = textWithLineNumbers.length() + targetedInstruction.length();
            if (totalLength > MAX_PROBLEM_STATEMENT_LENGTH) {
                throw new InternalServerErrorAlertException("Input is too long (including instructions)", "ProblemStatement", "ProblemStatementRefinement.problemStatementTooLong");
            }

            TargetedRefinementPromptVariables variables = new TargetedRefinementPromptVariables(textWithLineNumbers, targetedInstruction, getCourseTitleOrDefault(course),
                    getCourseDescriptionOrDefault(course));

            // Use the targeted refinement template for selection-based instructions
            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement_targeted.st", variables.asMap());
            String refinedProblemStatementText = null;
            if (chatClient != null) {
                refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();
            }

            if (refinedProblemStatementText == null) {
                throw new InternalServerErrorAlertException("AI returned null when refining the problem statement", "ProblemStatement",
                        "ProblemStatementRefinement.problemStatementRefinementNull");
            }

            return validateAndReturnResponse(originalProblemStatementText, refinedProblemStatementText);
        }
        catch (Exception e) {
            return handleRefinementError(course, originalProblemStatementText, e);
        }
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
                return String.format("Line %d, columns %d-%d (modify ONLY the text: \"%s\")", request.startLine(), request.startColumn(), request.endColumn(), selectedText);
            }
            return "Line " + request.startLine();
        }
        else {
            if (request.hasColumnRange()) {
                return String.format("Lines %d-%d, from column %d on line %d to column %d on line %d", request.startLine(), request.endLine(), request.startColumn(),
                        request.startLine(), request.endColumn(), request.endLine());
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
     * Converts a 1-indexed nullable column to a clamped end column.
     */
    private int resolveEndColumn(Integer colObj, int lineLength) {
        return Math.min(lineLength, colObj != null ? colObj : DEFAULT_COLUMN_ONE_INDEXED);
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
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(String originalProblemStatementText, String refinedProblemStatementText) {
        if (refinedProblemStatementText != null && refinedProblemStatementText.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new InternalServerErrorAlertException(
                    "Refined problem statement is too long (" + refinedProblemStatementText.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                    "ProblemStatement", "ProblemStatementRefinement.refinedProblemStatementTooLong");
        }

        // Refinement didn't change content (compare trimmed values to detect semantically unchanged content)
        String originalTrimmed = originalProblemStatementText == null ? null : originalProblemStatementText.trim();
        String refinedTrimmed = refinedProblemStatementText == null ? null : refinedProblemStatementText.trim();
        if (refinedTrimmed != null && refinedTrimmed.equals(originalTrimmed)) {
            throw new InternalServerErrorAlertException("Problem statement is the same after refinement", "ProblemStatement",
                    "ProblemStatementRefinement.refinedProblemStatementUnchanged");
        }

        return new ProblemStatementRefinementResponseDTO(refinedProblemStatementText);
    }

    /**
     * Handles refinement errors by logging and throwing appropriate exception.
     * Re-throws InternalServerErrorAlertException unchanged to preserve specific error keys.
     */
    private ProblemStatementRefinementResponseDTO handleRefinementError(Course course, String originalProblemStatementText, Exception e) {
        // Re-throw InternalServerErrorAlertException unchanged to preserve specific error keys
        if (e instanceof InternalServerErrorAlertException alertException) {
            throw alertException;
        }
        if (e instanceof BadRequestAlertException badRequestException) {
            throw badRequestException;
        }

        log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(),
                originalProblemStatementText != null ? originalProblemStatementText.length() : 0, e.getMessage(), e);

        String errorMessage = "Failed to refine problem statement: " + e.getMessage();
        throw new InternalServerErrorAlertException(errorMessage, "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementFailed");
    }

    /**
     * Validates that the problem statement and chat client are ready for refinement.
     */
    private void validateRefinementPrerequisites(String problemStatementText) {
        if (problemStatementText == null || problemStatementText.isBlank()) {
            throw new BadRequestAlertException("Cannot refine empty problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementEmpty");
        }
        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "Hyperion", "ProblemStatementRefinement.chatClientNotConfigured");
        }
    }

    /**
     * Validates that the user prompt is not empty.
     */
    private void validateUserPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BadRequestAlertException("User prompt cannot be empty", "ProblemStatement", "ProblemStatementRefinement.userPromptEmpty");
        }
    }

    /**
     * Returns the course title or a default value if not set.
     */
    private String getCourseTitleOrDefault(Course course) {
        return course.getTitle() != null ? course.getTitle() : DEFAULT_COURSE_TITLE;
    }

    /**
     * Returns the course description or a default value if not set.
     */
    private String getCourseDescriptionOrDefault(Course course) {
        return course.getDescription() != null ? course.getDescription() : DEFAULT_COURSE_DESCRIPTION;
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

    private record TargetedRefinementPromptVariables(String textWithLineNumbers, String targetedInstructions, String courseTitle, String courseDescription)
            implements RefinementPromptVariables {

        @Override
        public Map<String, String> asMap() {
            return Map.of("textWithLineNumbers", textWithLineNumbers, "targetedInstructions", targetedInstructions, "courseTitle", courseTitle, "courseDescription",
                    courseDescription);
        }
    }
}
