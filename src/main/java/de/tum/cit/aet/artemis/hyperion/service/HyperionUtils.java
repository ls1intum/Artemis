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
     * Uses a non-greedy match so that inner '}' characters don't leave orphaned braces.
     * Note: regex cannot perfectly handle arbitrarily nested braces, but this non-greedy
     * approach addresses the observed orphaned '}' issue with the previous [^}]* pattern.
     * May match across newlines; use {@link #TEMPLATE_VAR_LINE_PATTERN} when line structure must be preserved.
     */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{[\\s\\S]*?\\}\\}");

    /**
     * Line-scoped variant of {@link #TEMPLATE_VAR_PATTERN} that never matches across newlines.
     * Uses a negated-newline class ({@code [^\n]}) so that embedded newlines inside a
     * multi-line template variable are preserved, keeping line counts stable.
     * Used by {@link #sanitizeInputPreserveLines(String)}.
     */
    private static final Pattern TEMPLATE_VAR_LINE_PATTERN = Pattern.compile("\\{\\{[^\\n]*?\\}\\}");

    /**
     * Pattern matching HTML tags for stripping from rich-text content like course descriptions.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Pattern matching wrapper marker lines that the LLM may copy from the prompt template
     * (e.g. {@code "--- BEGIN PROBLEM STATEMENT ---"}, {@code "--- END PROBLEM STATEMENT ---"}).
     * Matches lines consisting of three or more dashes, optional whitespace, BEGIN/END, any label, and closing dashes.
     */
    private static final Pattern WRAPPER_MARKER_LINE = Pattern.compile("^\\s*-{3,}\\s*(?:BEGIN|END)\\s+.*-{3,}\\s*$", Pattern.CASE_INSENSITIVE);

    /** Pattern matching a line that starts with a line-number prefix: one or more digits followed by a colon and a space. */
    private static final Pattern LINE_NUMBER_PREFIX = Pattern.compile("^\\d+: ");

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
        // Use the line-scoped pattern so template vars spanning multiple lines
        // don't collapse embedded newlines and break line numbering.
        sanitized = TEMPLATE_VAR_LINE_PATTERN.matcher(sanitized).replaceAll("");
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

    /**
     * Strips sequential line-number prefixes ({@code "1: "}, {@code "2: "}, …) that
     * the LLM may have copied from the numbered prompt context.
     * <p>
     * Prefixes are only removed when <em>every</em> non-blank line carries a
     * sequential prefix starting at 1. If any non-blank line is missing a prefix or
     * the sequence is not contiguous the text is returned unchanged, so that
     * legitimate content like numbered lists ({@code "1. "}) is never altered.
     *
     * @param text the raw LLM output, never null
     * @return the text with line-number prefixes stripped, or the original text
     */
    static String stripLineNumbers(String text) {
        if (text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\n", -1);

        // Verify every non-blank line has a sequential "N: " prefix starting at 1
        int expectedNumber = 1;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (!LINE_NUMBER_PREFIX.matcher(line).find()) {
                return text;
            }
            int colonIndex = line.indexOf(": ");
            int number = Integer.parseInt(line.substring(0, colonIndex));
            if (number != expectedNumber) {
                return text;
            }
            expectedNumber++;
        }

        // All non-blank lines are sequential — strip the prefixes
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                result.append(lines[i]);
            }
            else {
                result.append(LINE_NUMBER_PREFIX.matcher(lines[i]).replaceFirst(""));
            }
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Strips wrapper marker lines (e.g. {@code "--- BEGIN PROBLEM STATEMENT ---"},
     * {@code "--- END PROBLEM STATEMENT ---"}) that an LLM may copy from the prompt
     * template into its response.
     * <p>
     * Only the first and last non-blank lines are checked, since the LLM typically
     * wraps the entire output. Interior lines that happen to match are left intact
     * to avoid stripping legitimate content.
     *
     * @param text the raw LLM output, never null
     * @return the text with leading/trailing wrapper markers removed, trimmed
     */
    static String stripWrapperMarkers(String text) {
        String[] lines = text.split("\n", -1);

        int start = 0;
        int end = lines.length - 1;

        // Skip leading blank lines, then check for a wrapper marker
        while (start <= end && lines[start].isBlank()) {
            start++;
        }
        if (start <= end && WRAPPER_MARKER_LINE.matcher(lines[start]).matches()) {
            start++;
        }

        // Skip trailing blank lines, then check for a wrapper marker
        while (end >= start && lines[end].isBlank()) {
            end--;
        }
        if (end >= start && WRAPPER_MARKER_LINE.matcher(lines[end]).matches()) {
            end--;
        }

        if (start > end) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i <= end; i++) {
            result.append(lines[i]);
            if (i < end) {
                result.append("\n");
            }
        }
        return result.toString();
    }

}
