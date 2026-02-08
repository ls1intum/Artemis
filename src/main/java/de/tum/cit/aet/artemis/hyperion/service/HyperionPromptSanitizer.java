package de.tum.cit.aet.artemis.hyperion.service;

import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.Course;

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

    private HyperionPromptSanitizer() {
        // utility class
    }

    /**
     * Sanitizes user input by stripping control characters (except newlines, carriage returns, and tabs)
     * and the closing {@code </user_input>} delimiter to reduce prompt injection risk.
     *
     * @param input the raw input string, may be null
     * @return the sanitized and trimmed string, never null
     */
    static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = sanitized.replace("</user_input>", "");
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
}
