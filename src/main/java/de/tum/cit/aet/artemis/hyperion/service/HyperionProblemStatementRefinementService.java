package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInputPreserveLines;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripLineNumbers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripWrapperMarkers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.validateInstruction;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.validateUserPrompt;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementTargetedRefinementRequestDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Refines an existing problem statement, either as a whole against a free-text instruction, or within one selected line/column range.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementRefinementService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRefinementService.class);

    private static final String GLOBAL_REFINEMENT_PIPELINE_ID = "HYPERION_PROBLEM_REFINEMENT_GLOBAL";

    private static final String TARGETED_REFINEMENT_PIPELINE_ID = "HYPERION_PROBLEM_REFINEMENT_TARGETED";

    private static final int MAX_SELECTED_TEXT_DISPLAY_LENGTH = 100;

    private static final String ELLIPSIS = "...";

    /** Columns arrive 1-indexed from the client and are used as 0-indexed offsets into a Java string. */
    private static final int DEFAULT_COLUMN_ONE_INDEXED = 1;

    private static final int ONE_INDEXED_TO_ZERO_INDEXED_OFFSET = 1;

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    public HyperionProblemStatementRefinementService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    /**
     * Refines a whole problem statement against a free-text instruction.
     *
     * @param course                       supplies the title and description that frame the refinement
     * @param originalProblemStatementText the statement to refine
     * @param userPrompt                   what to change about it
     * @return the refined statement
     * @throws BadRequestAlertException          if the statement or prompt is empty or over length, or the refinement returned the statement unchanged
     * @throws InternalServerErrorAlertException if no chat client is configured, the model call fails, or its answer is unusable
     */
    @Observed(name = "hyperion.refine_global", contextualName = "problem statement refinement", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementRefinement.chatClientNotConfigured");
        }

        // Validation runs on the sanitized text, so input that is non-empty only because of characters sanitization removes is still rejected.
        String sanitizedProblemStatement = sanitizeInput(originalProblemStatementText);
        validateSanitizedProblemStatement(sanitizedProblemStatement);

        String sanitizedPrompt = sanitizeInput(userPrompt);
        validateUserPrompt(sanitizedPrompt, "ProblemStatementRefinement");

        String systemPrompt = templateService.render("/prompts/hyperion/refine_problem_statement_system.st", Map.of());

        GlobalRefinementPromptVariables variables = new GlobalRefinementPromptVariables(sanitizedProblemStatement, sanitizedPrompt, getSanitizedCourseTitle(course),
                getSanitizedCourseDescription(course));
        String userMessage = templateService.render("/prompts/hyperion/refine_problem_statement_user.st", variables.asMap());

        ChatResponse chatResponse;
        String refinedProblemStatementText;
        try {
            chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
            refinedProblemStatementText = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (Exception e) {
            log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(), originalProblemStatementText.length(),
                    e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to refine problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementFailed");
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GLOBAL_REFINEMENT_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        if (refinedProblemStatementText == null || refinedProblemStatementText.isBlank()) {
            throw new InternalServerErrorAlertException("Refined problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementRefinement.problemStatementRefinementNull");
        }

        refinedProblemStatementText = stripLineNumbers(refinedProblemStatementText);
        refinedProblemStatementText = stripWrapperMarkers(refinedProblemStatementText);

        return validateAndReturnResponse(sanitizedProblemStatement, refinedProblemStatementText, sanitizedPrompt);
    }

    /**
     * Refines the selected line/column range of a problem statement and returns the whole statement with that change applied.
     *
     * @param course  supplies the title and description that frame the refinement
     * @param request the statement, the selected range, and the instruction for it
     * @return the refined statement
     * @throws BadRequestAlertException          if the statement or instruction is empty or over length, the range is out of bounds, or the refinement returned it unchanged
     * @throws InternalServerErrorAlertException if no chat client is configured, the model call fails, or its answer is unusable
     */
    @Observed(name = "hyperion.refine_targeted", contextualName = "problem statement targeted refinement", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementRefinementResponseDTO refineProblemStatementTargeted(Course course, ProblemStatementTargetedRefinementRequestDTO request) {
        log.debug("Refining problem statement with targeted instruction for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementRefinement.chatClientNotConfigured");
        }

        String sanitizedInstruction = sanitizeInput(request.instruction());
        validateInstruction(sanitizedInstruction, "ProblemStatementRefinement");

        // The request addresses text by line number, so sanitization must not shift lines; sanitizeInput() would.
        String sanitizedProblemStatement = sanitizeInputPreserveLines(request.problemStatementText());
        validateSanitizedProblemStatement(sanitizedProblemStatement);
        String[] lines = sanitizedProblemStatement.split("\n", -1);
        validateLineRange(request.startLine() - 1, request.endLine() - 1, lines.length);
        String locationRef = buildLocationReference(request, lines);
        String targetedInstruction = locationRef + ": " + sanitizedInstruction;

        String textWithLineNumbers = addLineNumbers(sanitizedProblemStatement);

        // Measured on the text without the line-number prefixes, so the limit means the same thing here as everywhere else.
        int totalLength = sanitizedProblemStatement.length() + targetedInstruction.length();
        if (totalLength > MAX_PROBLEM_STATEMENT_LENGTH) {
            throw new BadRequestAlertException("Input is too long (including instructions)", "ProblemStatement", "ProblemStatementRefinement.problemStatementTooLong");
        }

        TargetedRefinementPromptVariables variables = new TargetedRefinementPromptVariables(textWithLineNumbers, targetedInstruction, getSanitizedCourseTitle(course),
                getSanitizedCourseDescription(course));

        // Instructions and user-provided content go into separate messages, so content cannot present itself as instruction.
        String systemPrompt = templateService.render("/prompts/hyperion/refine_problem_statement_targeted_system.st", Map.of());
        String userMessage = templateService.render("/prompts/hyperion/refine_problem_statement_targeted_user.st", variables.asMap());

        ChatResponse chatResponse;
        String refinedProblemStatementText;
        try {
            chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
            refinedProblemStatementText = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (Exception e) {
            log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(), request.problemStatementText().length(),
                    e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to refine problem statement", "ProblemStatement", "ProblemStatementRefinement.problemStatementRefinementFailed");
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, TARGETED_REFINEMENT_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        if (refinedProblemStatementText == null || refinedProblemStatementText.isBlank()) {
            throw new InternalServerErrorAlertException("Refined problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementRefinement.problemStatementRefinementNull");
        }

        refinedProblemStatementText = stripLineNumbers(refinedProblemStatementText);
        refinedProblemStatementText = stripWrapperMarkers(refinedProblemStatementText);

        return validateAndReturnResponse(sanitizedProblemStatement, refinedProblemStatementText, sanitizedInstruction);
    }

    /** Names the selection in prose, quoting the selected text itself when the request narrowed it down to columns, because the model has no other way to see column offsets. */
    private String buildLocationReference(ProblemStatementTargetedRefinementRequestDTO request, String[] lines) {
        boolean singleLine = request.startLine().equals(request.endLine());

        if (singleLine) {
            if (request.hasColumnRange()) {
                String selectedText = extractSelectedText(request, lines);
                return "Line %d, columns %d-%d (modify ONLY the text: \"%s\")".formatted(request.startLine(), request.startColumn(), request.endColumn() - 1, selectedText);
            }
            return "Line " + request.startLine();
        }
        else {
            if (request.hasColumnRange()) {
                String selectedText = extractSelectedText(request, lines);
                return "Lines %d-%d, from column %d on line %d to column %d on line %d (modify ONLY the text: \"%s\")".formatted(request.startLine(), request.endLine(),
                        request.startColumn(), request.startLine(), request.endColumn() - 1, request.endLine(), selectedText);
            }
            return "Lines " + request.startLine() + "-" + request.endLine();
        }
    }

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

    private void validateLineRange(int startLineIdx, int endLineIdx, int totalLines) {
        if (startLineIdx < 0 || endLineIdx >= totalLines || startLineIdx > endLineIdx) {
            throw new BadRequestAlertException("Invalid line range", "ProblemStatement", "ProblemStatementRefinement.invalidLineRange");
        }
    }

    private String extractSingleLineSelection(String line, Integer startColumnOneIndexed, Integer endColumnOneIndexedExclusive) {
        int startOffset = toStartOffset(startColumnOneIndexed);
        int endOffset = toEndOffset(endColumnOneIndexedExclusive, line.length());

        if (startOffset < endOffset && startOffset < line.length()) {
            return truncateForDisplay(line.substring(startOffset, endOffset));
        }
        throw new BadRequestAlertException("Invalid column range for line selection: startCol=%d, endCol=%d, lineLength=%d".formatted(startOffset, endOffset, line.length()),
                "ProblemStatement", "ProblemStatementRefinement.textExtractionFailed");
    }

    /** Quotes the head and tail of the selection with the middle elided: the model only needs enough to recognise the range, not all of it. */
    private String extractMultiLineSelection(String firstLine, String lastLine, Integer startColumnOneIndexed, Integer endColumnOneIndexedExclusive) {
        int startOffset = toStartOffset(startColumnOneIndexed);
        int endOffset = toEndOffset(endColumnOneIndexedExclusive, lastLine.length());

        String startPart = startOffset < firstLine.length() ? firstLine.substring(startOffset) : "";
        String endPart = lastLine.substring(0, endOffset);

        return truncateForDisplay(startPart + ELLIPSIS + endPart);
    }

    private int toStartOffset(Integer startColumnOneIndexed) {
        return Math.max(0, (startColumnOneIndexed != null ? startColumnOneIndexed : DEFAULT_COLUMN_ONE_INDEXED) - ONE_INDEXED_TO_ZERO_INDEXED_OFFSET);
    }

    /** An absent end column selects to the end of the line. */
    private int toEndOffset(Integer endColumnOneIndexedExclusive, int lineLength) {
        if (endColumnOneIndexedExclusive == null) {
            return lineLength;
        }
        return Math.max(0, Math.min(lineLength, endColumnOneIndexedExclusive - ONE_INDEXED_TO_ZERO_INDEXED_OFFSET));
    }

    private String truncateForDisplay(String text) {
        if (text.length() > MAX_SELECTED_TEXT_DISPLAY_LENGTH) {
            return text.substring(0, MAX_SELECTED_TEXT_DISPLAY_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
        }
        return text;
    }

    /**
     * Prefixes every line, blank ones included, with {@code "<n>: "} so the instruction can name lines the model can find. {@link HyperionUtils#stripLineNumbers} takes them off
     * again when the model echoes them back.
     */
    private String addLineNumbers(String text) {
        String[] lines = text.split("\n", -1);
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
     * Both sides are trimmed before the unchanged-check, so that the whitespace the line-preserving sanitization deliberately keeps cannot pass as a refinement. The returned text
     * is trimmed for the same reason: leading and trailing blank lines carry no meaning in a problem statement.
     */
    private ProblemStatementRefinementResponseDTO validateAndReturnResponse(String originalProblemStatementText, String refinedProblemStatementText, String sanitizedInstruction) {
        String trimmedRefined = refinedProblemStatementText.trim();

        // A statement that already carries task bindings is a finished exercise, not a draft, and the draft-only artifact rules do not apply to it. The advisory findings are
        // dropped: this endpoint's response has no field for them.
        if (!HyperionUtils.containsFinalTaskBindings(originalProblemStatementText)) {
            HyperionUtils.validateDraftProblemStatementHygiene(trimmedRefined, sanitizedInstruction, "ProblemStatementRefinement");
        }

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

        private Map<String, String> asMap() {
            return Map.of("problemStatement", problemStatement, "userPrompt", userPrompt, "courseTitle", courseTitle, "courseDescription", courseDescription);
        }
    }

    private record TargetedRefinementPromptVariables(String textWithLineNumbers, String targetedInstructions, String courseTitle, String courseDescription) {

        private Map<String, String> asMap() {
            return Map.of("textWithLineNumbers", textWithLineNumbers, "targetedInstructions", targetedInstructions, "courseTitle", courseTitle, "courseDescription",
                    courseDescription);
        }
    }
}
