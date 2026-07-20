package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;

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

    private static final Pattern FINAL_TASK_BINDING = Pattern.compile("\\[task]\\[[^\\]]*\\]\\((.*)\\)");

    private static final List<Pattern> FORBIDDEN_DRAFT_ARTIFACTS = List.of(Pattern.compile("\\[\\s*tasks?\\s*]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("@(?:start|end)uml", Pattern.CASE_INSENSITIVE), Pattern.compile("\\b(?:solution|template|test) repository\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:verifier|test runner|hidden tests)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btest(?:Class|Methods|Attributes|Constructors)\\[[^\\]]+]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btest[A-Z][A-Za-z0-9_]*\\s*\\(", Pattern.CASE_INSENSITIVE), Pattern.compile("adjust accordingly in tests", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*#{1,6}\\s*(?:instructor decisions?|open questions?|authoring notes?|drafting notes?)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
            Pattern.compile("\\bconflict\\b[^\\n]{0,160}\\bdo\\s+\\*?\\*?not\\*?\\*?\\s+overlap\\b[^\\n]{0,160}\\bno conflict\\b", Pattern.CASE_INSENSITIVE));

    private static final Pattern REQUIRED_JSON_ARTIFACT = Pattern
            .compile("(?:\\b(?:must|should|shall|required|provide|return|print|read|write|submit|export|use|using)\\b[^\\n.!?]{0,80}\\bJSON(?:-like)?\\b|"
                    + "\\bJSON(?:-like)?\\b[^\\n.!?]{0,80}\\b(?:input|output|payload|file|format|submission)\\b)", Pattern.CASE_INSENSITIVE);

    private static final List<ConditionalDraftArtifact> CONDITIONAL_DRAFT_ARTIFACTS = List.of(
            new ConditionalDraftArtifact(Pattern.compile("(?:^\\s*#{1,6}\\s*(?:optional challenges?|extra credit)\\b|\\*\\*\\(Optional\\)|\\bif you choose to expose\\b)",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), Pattern.compile("\\b(?:optional|challenge|extra credit)\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(Pattern.compile("^\\s*#{1,6}[^\\n]*\\(optional\\)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                    Pattern.compile("\\boptional\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(Pattern.compile("^\\s*#{1,6}\\s*(?:submission|deliverable)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                    Pattern.compile("\\b(?:submission|deliverable)\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(
                    Pattern.compile("\\b(?:performance benchmark|benchmarking task|throughput benchmark|resource exhaustion|upper limit|maximum recurrence limit|"
                            + "thread-safe|thread safety|concurrent use)\\b", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("\\b(?:performance|benchmark|throughput|time complexity|resource|limit|thread|concurren)\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(Pattern.compile(
                    "\\b(?:provided test suite|test suite|unit tests?|students?\\s+(?:must|should|need to|are required to)\\s+(?:write|create|provide)\\s+unit tests?)\\b",
                    Pattern.CASE_INSENSITIVE), Pattern.compile("\\b(?:unit tests?|testing|test suite)\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(REQUIRED_JSON_ARTIFACT, Pattern.compile("\\bJSON\\b", Pattern.CASE_INSENSITIVE)),
            new ConditionalDraftArtifact(Pattern.compile("\\b(?:standard input|command[- ]line|CSV|database|web interface|printed (?:lines|output))\\b", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("\\b(?:standard input|command[- ]line|CSV|database|web interface|printed (?:lines|output))\\b", Pattern.CASE_INSENSITIVE)));

    private static final Pattern NEGATED_ARTIFACT_REQUEST = Pattern.compile("\\b(?:do\\s+not|don't|avoid|without|exclude|omit|never|no)\\b[^.!?;\\n]{0,100}$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PUBLIC_API_DETAILS = Pattern.compile("(?:^\\s*#{1,6}\\s*(?:public|required) API\\b|\\|\\s*Method\\s*\\|\\s*Purpose|"
            + "\\b(?:public\\s+)?(?:boolean|int|long|double|String|void|List<[^>]+>|Map<[^>]+>)\\s+[a-zA-Z_]\\w*\\s*\\()", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern API_AVOIDANCE_REQUEST = Pattern.compile(
            "\\b(?:avoid|do not|don't|without|not)\\b[^.\\n]{0,100}\\b(?:exact|specific|particular|prescrib|assum)\\w*\\b[^.\\n]{0,100}\\b(?:class|method|api|implementation)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * A brief that asks for a concrete API or for structural design (a pattern, a diagram, types the students define) is asking the draft to talk about design, so API details in
     * the
     * draft are on-topic rather than invented.
     */
    private static final Pattern API_PERMISSION_REQUEST = Pattern.compile(
            "\\b(?:public API|method signature|method signatures|specific class|specific method|fixed API|"
                    + "you may choose the public API|choose the public API|design pattern|UML|class diagram|architecture|define\\b[^.\\n]{0,60}\\binterface)\\b",
            Pattern.CASE_INSENSITIVE);

    private record ConditionalDraftArtifact(Pattern contentPattern, Pattern requestPattern) {
    }

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
     * Whether the problem statement already contains final Artemis task bindings.
     */
    static boolean containsFinalTaskBindings(String problemStatement) {
        return problemStatement != null && FINAL_TASK_BINDING.matcher(problemStatement).find();
    }

    /**
     * Rejects generation-only artifacts in early draft problem statements before they can seed exercise generation.
     */
    static void validateDraftProblemStatementHygiene(String problemStatement, String sanitizedPrompt, String errorKeyPrefix) {
        List<String> findings = new ArrayList<>();
        if (FORBIDDEN_DRAFT_ARTIFACTS.stream().anyMatch(pattern -> pattern.matcher(problemStatement).find())) {
            findings.add("forbidden draft artifact");
        }
        if (CONDITIONAL_DRAFT_ARTIFACTS.stream()
                .anyMatch(artifact -> artifact.contentPattern().matcher(problemStatement).find() && !explicitlyRequestsArtifact(sanitizedPrompt, artifact))) {
            findings.add("unrequested draft artifact");
        }
        boolean apiAvoidanceRequested = API_AVOIDANCE_REQUEST.matcher(sanitizedPrompt).find();
        boolean apiExplicitlyPermitted = API_PERMISSION_REQUEST.matcher(sanitizedPrompt).find();
        boolean contradictsApiAvoidance = PUBLIC_API_DETAILS.matcher(problemStatement).find() && (apiAvoidanceRequested || !apiExplicitlyPermitted);
        if (contradictsApiAvoidance) {
            findings.add("public API details");
        }

        if (!findings.isEmpty()) {
            throw new InternalServerErrorAlertException("Generated problem statement contains generation-only artifacts: " + String.join(", ", findings), "ProblemStatement",
                    errorKeyPrefix + ".generatedProblemStatementContainsArtifacts");
        }
    }

    private static boolean explicitlyRequestsArtifact(String prompt, ConditionalDraftArtifact artifact) {
        var matcher = artifact.requestPattern().matcher(prompt);
        while (matcher.find()) {
            String prefix = prompt.substring(Math.max(0, matcher.start() - 100), matcher.start());
            if (!NEGATED_ARTIFACT_REQUEST.matcher(prefix).find()) {
                return true;
            }
        }
        return false;
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
     * sequential prefix starting at 1. Blank lines are allowed to appear without
     * a prefix in the response even though the original prompt numbered them
     * (e.g. the LLM may return a bare empty line instead of {@code "2: "}); the
     * expected counter is still advanced for each blank line so that the subsequent
     * numbered lines remain in sequence. If any non-blank line is missing a prefix
     * or the sequence is not contiguous the text is returned unchanged, so that
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

        // Verify every non-blank line has a sequential "N: " prefix starting at 1.
        // Blank lines advance the counter even when they appear without a prefix,
        // because addLineNumbers() numbers every line (including blank ones) and the
        // LLM may choose to omit the prefix when echoing a blank line.
        int expectedNumber = 1;
        for (String line : lines) {
            if (line.isBlank()) {
                expectedNumber++;
                continue;
            }
            if (!LINE_NUMBER_PREFIX.matcher(line).find()) {
                return text;
            }
            int colonIndex = line.indexOf(": ");
            int number;
            try {
                number = Integer.parseInt(line.substring(0, colonIndex));
            }
            catch (NumberFormatException e) {
                return text;
            }
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
