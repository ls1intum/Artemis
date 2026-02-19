package de.tum.cit.aet.artemis.hyperion.service;

import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Shared utility class for sanitizing, validating, and post-processing inputs and
 * outputs used in Hyperion prompt templates.
 * Centralizes constants and methods shared across generation and refinement services.
 */
final class HyperionUtils {

    /**
     * Maximum allowed length for generated/refined problem statements (50,000 characters).
     * This prevents excessively long responses that could cause performance issues.
     */
    static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    /**
     * Maximum allowed length for user prompts (1,000 characters).
     */
    static final int MAX_USER_PROMPT_LENGTH = 1_000;

    /**
     * Maximum allowed length for targeted refinement instructions (500 characters).
     */
    static final int MAX_INSTRUCTION_LENGTH = 500;

    /**
     * Default course title when not specified.
     */
    static final String DEFAULT_COURSE_TITLE = "Programming Course";

    /**
     * Default course description when not specified.
     */
    static final String DEFAULT_COURSE_DESCRIPTION = "A programming course";

    /** Pattern matching control characters except newline (\n), carriage return (\r), and tab (\t). */
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    /**
     * Pattern matching prompt template delimiter lines (e.g. "--- BEGIN USER REQUIREMENTS ---").
     * Stripping these prevents users from injecting fake section boundaries that could break out of their designated prompt section.
     */
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("^\\s*-{3,}\\s*(BEGIN|END)\\s+.*-{3,}$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching template variable sequences (e.g. "{{variable}}").
     * Stripping these prevents users from injecting fake template placeholders that could be expanded by the template engine.
     */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{[^}]*\\}\\}");

    /**
     * Pattern matching HTML tags for stripping from rich-text content like course descriptions.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private HyperionUtils() {
        // utility class
    }

    /**
     * Validates that the user prompt is not empty and does not exceed the maximum allowed length.
     *
     * @param userPrompt     the sanitized user prompt to validate
     * @param errorKeyPrefix prefix for error keys (e.g. "ProblemStatementRefinement" or "ProblemStatementGeneration")
     * @throws BadRequestAlertException if the prompt is blank or exceeds the maximum length
     */
    static void validateUserPrompt(String userPrompt, String errorKeyPrefix) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BadRequestAlertException("User prompt cannot be empty", "ProblemStatement", errorKeyPrefix + ".userPromptEmpty");
        }
        if (userPrompt.length() > MAX_USER_PROMPT_LENGTH) {
            throw new BadRequestAlertException("User prompt exceeds maximum length of " + MAX_USER_PROMPT_LENGTH + " characters", "ProblemStatement",
                    errorKeyPrefix + ".userPromptTooLong");
        }
    }

    /**
     * Validates that the instruction is not empty and does not exceed the maximum allowed length.
     *
     * @param instruction    the sanitized instruction to validate
     * @param errorKeyPrefix prefix for error keys (e.g. "ProblemStatementRefinement")
     * @throws BadRequestAlertException if the instruction is blank or exceeds the maximum length
     */
    static void validateInstruction(String instruction, String errorKeyPrefix) {
        if (instruction == null || instruction.isBlank()) {
            throw new BadRequestAlertException("Instruction cannot be empty", "ProblemStatement", errorKeyPrefix + ".instructionEmpty");
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new BadRequestAlertException("Instruction exceeds maximum length of " + MAX_INSTRUCTION_LENGTH + " characters", "ProblemStatement",
                    errorKeyPrefix + ".instructionTooLong");
        }
    }

    /**
     * Sanitizes user input by stripping control characters (except newlines, carriage returns, and tabs)
     * and removing prompt template delimiter lines to prevent prompt injection.
     *
     * @param input the raw input string, may be null
     * @return the sanitized and trimmed string, never null
     */
    static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = DELIMITER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = TEMPLATE_VAR_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized.trim();
    }

    /**
     * Sanitizes user input while preserving line structure (line count and positions).
     * Unlike {@link #sanitizeInput(String)}, this method does not trim the result,
     * ensuring that line numbers from the client remain valid for targeted (line-based) refinement.
     * Delimiter lines are replaced with empty content but their newlines are preserved.
     *
     * @param input the raw input string, may be null
     * @return the sanitized string with preserved line structure, never null
     */
    static String sanitizeInputPreserveLines(String input) {
        if (input == null) {
            return "";
        }
        // Apply per-character sanitizations globally (these don't affect line count)
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = TEMPLATE_VAR_PATTERN.matcher(sanitized).replaceAll("");
        // DELIMITER_PATTERN uses MULTILINE so ^ and $ match per-line boundaries.
        // replaceAll("") blanks the content but preserves the newline, keeping line count stable.
        sanitized = DELIMITER_PATTERN.matcher(sanitized).replaceAll("");
        // Intentionally NO trim() — trimming could strip leading newlines, shifting all line numbers.
        return sanitized;
    }

    /**
     * Returns the sanitized course title, falling back to {@link #DEFAULT_COURSE_TITLE} if blank.
     */
    static String getSanitizedCourseTitle(Course course) {
        String sanitized = sanitizeInput(course.getTitle());
        return sanitized.isBlank() ? DEFAULT_COURSE_TITLE : sanitized;
    }

    /**
     * Returns the sanitized course description, falling back to {@link #DEFAULT_COURSE_DESCRIPTION} if blank.
     */
    static String getSanitizedCourseDescription(Course course) {
        String description = course.getDescription();
        if (description != null) {
            description = HTML_TAG_PATTERN.matcher(description).replaceAll("");
        }
        String sanitized = sanitizeInput(description);
        return sanitized.isBlank() ? DEFAULT_COURSE_DESCRIPTION : sanitized;
    }

    /**
     * Resolves the current user's database ID from the security context.
     *
     * @param userRepository the repository used to look up the user ID by login
     * @return the user ID, or {@code null} if the user is not authenticated or not found
     */
    static Long resolveCurrentUserId(UserRepository userRepository) {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
    }

}
