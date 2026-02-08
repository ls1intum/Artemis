package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Shared utility class for sanitizing and validating inputs used in Hyperion prompt templates.
 * Centralizes constants and methods shared across generation and refinement services.
 */
final class HyperionPromptSanitizer {

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
     * Case-insensitive patterns that indicate prompt injection attempts.
     * Each pattern targets a specific class of instruction-override or role-reassignment attack.
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(Pattern.compile("(?i)\\bignore\\s+(all\\s+)?(previous|above|prior|earlier)\\s+(instructions|rules|context)\\b"),
            Pattern.compile("(?i)\\bdisregard\\s+(all\\s+)?(previous|above|prior|earlier)\\b"),
            Pattern.compile("(?i)\\bforget\\s+(your|all|the)\\s+(instructions|rules|guidelines|prompt)\\b"), Pattern.compile("(?i)\\byou\\s+are\\s+now\\b"),
            Pattern.compile("(?i)\\bnew\\s+(instructions|task|role)\\s*:"), Pattern.compile("(?i)\\boverride\\s+(system|instructions|rules)\\b"),
            Pattern.compile("(?i)\\bpretend\\s+(to\\s+be|you\\s+are)\\b"), Pattern.compile("(?i)\\breveal\\s+(your|the|system)\\s+(prompt|instructions)\\b"),
            Pattern.compile("(?i)^\\s*system\\s*:", Pattern.MULTILINE));

    /**
     * Patterns in LLM output that indicate the model included meta-commentary instead of
     * returning only the requested problem statement content.
     */
    private static final List<Pattern> OUTPUT_META_PATTERNS = List.of(Pattern.compile("(?i)^\\s*(here\\s+is|i('ve|\\s+have)\\s+(refined|updated|made|revised|modified))"),
            Pattern.compile("(?i)^\\s*(the\\s+following|below\\s+is)\\s+(is\\s+)?(the\\s+)?(refined|updated|revised)"),
            Pattern.compile("(?i)(changes\\s+made|summary\\s+of\\s+changes|key\\s+changes)\\s*:"));

    private HyperionPromptSanitizer() {
        // utility class
    }

    /**
     * Sanitizes user input by stripping control characters (except newlines, carriage returns, and tabs).
     *
     * @param input the raw input string, may be null
     * @return the sanitized and trimmed string, never null
     */
    static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        return sanitized.trim();
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
        String sanitized = sanitizeInput(course.getDescription());
        return sanitized.isBlank() ? DEFAULT_COURSE_DESCRIPTION : sanitized;
    }

    /**
     * Checks whether the input contains any known prompt injection patterns.
     *
     * @param input the sanitized user input
     * @return {@code true} if a dangerous pattern is detected
     */
    static boolean containsInjectionPattern(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(input).find());
    }

    /**
     * Validates that the user prompt does not contain known prompt injection patterns.
     *
     * @param userPrompt the sanitized user prompt
     * @param entityName the entity name for the error key prefix (e.g. "ProblemStatementGeneration" or "ProblemStatementRefinement")
     * @throws BadRequestAlertException if an injection pattern is detected
     */
    static void validateNoInjectionPatterns(String userPrompt, String entityName) {
        if (containsInjectionPattern(userPrompt)) {
            throw new BadRequestAlertException("User prompt contains disallowed instruction patterns", "ProblemStatement", entityName + ".userPromptContainsInjection");
        }
    }

    /**
     * Validates that the LLM output looks like a problem statement and does not contain
     * meta-commentary or extraneous instructions.
     *
     * @param output the raw LLM response
     * @return {@code true} if the output passes validation
     */
    static boolean isValidLlmOutput(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        return OUTPUT_META_PATTERNS.stream().noneMatch(pattern -> pattern.matcher(output).find());
    }
}
