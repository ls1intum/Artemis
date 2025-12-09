package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    /**
     * @param chatClient      the AI chat client (optional)
     * @param templateService prompt template service
     */
    public HyperionProblemStatementRefinementService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
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
     */
    public ProblemStatementRefinementResponseDTO refineProblemStatement(Course course, String originalProblemStatementText, String userPrompt) {
        log.debug("Refining problem statement for course [{}]", course.getId());

        if (originalProblemStatementText == null || originalProblemStatementText.isBlank()) {
            log.warn("Cannot refine empty problem statement for course [{}]", course.getId());
            return new ProblemStatementRefinementResponseDTO("", java.util.Objects.toString(originalProblemStatementText, ""));
        }

        try {
            Map<String, String> templateVariables = Map.of("text", originalProblemStatementText.trim(), "userPrompt",
                    userPrompt != null ? userPrompt : "Refine a programming exercise problem statement", "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement.st", templateVariables);
            String refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();

            // Validate AI response is not null
            if (refinedProblemStatementText == null) {
                log.warn("AI returned null response for problem statement refinement for course [{}]", course.getId());
                throw new InternalServerErrorAlertException("Refined problem statement is missing", "ProblemStatement", "refinedProblemStatementMissing");
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

        try {
            // Build a combined prompt from all inline comments
            String combinedInstructions = buildCombinedInstructions(inlineComments);

            Map<String, String> templateVariables = Map.of("text", originalProblemStatementText.trim(), "userPrompt", combinedInstructions, "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            String prompt = templateService.render("/prompts/hyperion/refine_problem_statement.st", templateVariables);
            String refinedProblemStatementText = chatClient.prompt().user(prompt).call().content();

            // Validate AI response is not null
            if (refinedProblemStatementText == null) {
                log.warn("AI returned null response for problem statement refinement with comments for course [{}]", course.getId());
                throw new InternalServerErrorAlertException("Refined problem statement is missing", "ProblemStatement", "refinedProblemStatementMissing");
            }

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

        // Validate response is not null
        if (refinedProblemStatementText == null) {
            log.warn("Refined problem statement is null for course [{}]", course.getId());
            throw new InternalServerErrorAlertException("Refined problem statement is missing", "ProblemStatement", "refinedProblemStatementMissing");
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

        // Log the error with the original problem statement for debugging purposes
        log.error("Error refining problem statement for course [{}]. Original statement length: {}. Error: {}", course.getId(),
                originalProblemStatementText != null ? originalProblemStatementText.length() : 0, e.getMessage(), e);

        throw new InternalServerErrorAlertException("Failed to refine problem statement: " + e.getMessage(), "ProblemStatement", "problemStatementRefinementFailed");
    }
}
