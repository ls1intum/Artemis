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
 * Sanitization, validation, and post-processing of the text that Hyperion's prompt-driven services put into prompts and take back out of them.
 */
final class HyperionUtils {

    static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    static final int MAX_USER_PROMPT_LENGTH = 1_000;

    static final int MAX_INSTRUCTION_LENGTH = 500;

    static final String DEFAULT_COURSE_TITLE = "Programming Course";

    static final String DEFAULT_COURSE_DESCRIPTION = "A programming course";

    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    /** Section boundary of the prompt templates (e.g. "--- BEGIN USER REQUIREMENTS ---"); stripped so user text cannot forge one and escape its own section. */
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("^\\s*-{3,}\\s*(BEGIN|END)\\s+.*-{3,}$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * Template placeholder (e.g. "{{variable}}"); stripped so user text cannot smuggle in a placeholder the template engine would expand. Non-greedy, because a greedy match would
     * run from the first opening braces to the last closing ones and take everything between them. This may span newlines; use {@link #TEMPLATE_VAR_LINE_PATTERN} where line
     * structure must survive.
     */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{[\\s\\S]*?\\}\\}");

    /** Line-scoped {@link #TEMPLATE_VAR_PATTERN}: a placeholder spanning lines is left alone rather than collapsed, so the line count stays stable. */
    private static final Pattern TEMPLATE_VAR_LINE_PATTERN = Pattern.compile("\\{\\{[^\\n]*?\\}\\}");

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /** Same shape as {@link #DELIMITER_PATTERN}, but matched against a single line of model output rather than replaced across the whole input. */
    private static final Pattern WRAPPER_MARKER_LINE = Pattern.compile("^\\s*-{3,}\\s*(?:BEGIN|END)\\s+.*-{3,}\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_NUMBER_PREFIX = Pattern.compile("^\\d+: ");

    private static final Pattern FINAL_TASK_BINDING = Pattern.compile("\\[task]\\[[^\\]]*\\]\\((.*)\\)");

    /**
     * Artifacts that would mechanically corrupt downstream exercise generation: raw Artemis task bindings, PlantUML markers, and vocabulary leaking the grading/repository
     * machinery. Matching one of these is certain enough to reject the draft; every softer finding is a quality opinion the instructor edits anyway and is only advisory.
     */
    private static final List<Pattern> BLOCKING_DRAFT_ARTIFACTS = List.of(Pattern.compile("\\[\\s*tasks?\\s*]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("@(?:start|end)uml", Pattern.CASE_INSENSITIVE), Pattern.compile("\\b(?:solution|template|test) repository\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:verifier|test runner|hidden tests)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btest(?:Class|Methods|Attributes|Constructors)\\[[^\\]]+]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btest[A-Z][A-Za-z0-9_]*\\s*\\(", Pattern.CASE_INSENSITIVE));

    /** Plausible but not certain: these phrasings occur in legitimate drafts too, so they are only ever reported alongside the draft. */
    private static final List<Pattern> ADVISORY_DRAFT_ARTIFACTS = List.of(Pattern.compile("adjust accordingly in tests", Pattern.CASE_INSENSITIVE),
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
     * A brief asking for a concrete API or for structural design (a pattern, a diagram, types the students define) makes API details in the draft on-topic rather than invented.
     */
    private static final Pattern API_PERMISSION_REQUEST = Pattern.compile(
            "\\b(?:public API|method signature|method signatures|specific class|specific method|fixed API|"
                    + "you may choose the public API|choose the public API|design pattern|UML|class diagram|architecture|define\\b[^.\\n]{0,60}\\binterface)\\b",
            Pattern.CASE_INSENSITIVE);

    private record ConditionalDraftArtifact(Pattern contentPattern, Pattern requestPattern) {
    }

    private HyperionUtils() {
    }

    static void validateUserPrompt(String userPrompt, String errorKeyPrefix) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BadRequestAlertException("User prompt cannot be empty", "ProblemStatement", errorKeyPrefix + ".userPromptEmpty");
        }
        if (userPrompt.length() > MAX_USER_PROMPT_LENGTH) {
            throw new BadRequestAlertException("User prompt exceeds maximum length of " + MAX_USER_PROMPT_LENGTH + " characters", "ProblemStatement",
                    errorKeyPrefix + ".userPromptTooLong");
        }
    }

    static void validateInstruction(String instruction, String errorKeyPrefix) {
        if (instruction == null || instruction.isBlank()) {
            throw new BadRequestAlertException("Instruction cannot be empty", "ProblemStatement", errorKeyPrefix + ".instructionEmpty");
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new BadRequestAlertException("Instruction exceeds maximum length of " + MAX_INSTRUCTION_LENGTH + " characters", "ProblemStatement",
                    errorKeyPrefix + ".instructionTooLong");
        }
    }

    /** Strips prompt-injection vectors out of caller-supplied text and trims it; never returns null. */
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
     * Sanitizes like {@link #sanitizeInput(String)}, but every line stays at its original position: matches are blanked rather than removed, no line-spanning pattern is applied,
     * and the result is not trimmed. Targeted refinement addresses text by line number, so any shift would make the client's selection point at the wrong line.
     */
    static String sanitizeInputPreserveLines(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = TEMPLATE_VAR_LINE_PATTERN.matcher(sanitized).replaceAll("");
        return DELIMITER_PATTERN.matcher(sanitized).replaceAll("");
    }

    static String getSanitizedCourseTitle(Course course) {
        String sanitized = sanitizeInput(course.getTitle());
        return sanitized.isBlank() ? DEFAULT_COURSE_TITLE : sanitized;
    }

    static String getSanitizedCourseDescription(Course course) {
        String description = course.getDescription();
        if (description != null) {
            description = HTML_TAG_PATTERN.matcher(description).replaceAll("");
        }
        String sanitized = sanitizeInput(description);
        return sanitized.isBlank() ? DEFAULT_COURSE_DESCRIPTION : sanitized;
    }

    static boolean containsFinalTaskBindings(String problemStatement) {
        return problemStatement != null && FINAL_TASK_BINDING.matcher(problemStatement).find();
    }

    /**
     * Rejects a draft problem statement that carries generation-only artifacts, and returns everything else it noticed as advisory warnings.
     * <p>
     * The heuristics below (unrequested scope, public API details, contradictory examples, ...) are regex approximations of a quality opinion and do produce false positives. The
     * instructor reviews and edits every draft anyway, so a wrong heuristic must never fail the whole flow; only {@link #BLOCKING_DRAFT_ARTIFACTS} throws.
     * {@code sanitizedPrompt} is the request that produced the draft and decides whether flagged content was actually asked for.
     */
    static List<String> validateDraftProblemStatementHygiene(String problemStatement, String sanitizedPrompt, String errorKeyPrefix) {
        if (BLOCKING_DRAFT_ARTIFACTS.stream().anyMatch(pattern -> pattern.matcher(problemStatement).find())) {
            throw new InternalServerErrorAlertException("Generated problem statement contains generation-only artifacts: forbidden draft artifact", "ProblemStatement",
                    errorKeyPrefix + ".generatedProblemStatementContainsArtifacts");
        }

        List<String> warnings = new ArrayList<>();
        if (ADVISORY_DRAFT_ARTIFACTS.stream().anyMatch(pattern -> pattern.matcher(problemStatement).find())) {
            warnings.add(
                    "The draft may reference authoring-process or grading-adjacent content (e.g. drafting notes, references to tests, or contradictory examples). Review and remove it if not intended for students.");
        }
        if (CONDITIONAL_DRAFT_ARTIFACTS.stream()
                .anyMatch(artifact -> artifact.contentPattern().matcher(problemStatement).find() && !explicitlyRequestsArtifact(sanitizedPrompt, artifact))) {
            warnings.add(
                    "The draft may include optional extras, benchmarks, or file/interface assumptions (JSON, CSV, standard input, ...) that weren't explicitly requested. Review and trim if not intended.");
        }
        boolean apiAvoidanceRequested = API_AVOIDANCE_REQUEST.matcher(sanitizedPrompt).find();
        boolean apiExplicitlyPermitted = API_PERMISSION_REQUEST.matcher(sanitizedPrompt).find();
        boolean contradictsApiAvoidance = PUBLIC_API_DETAILS.matcher(problemStatement).find() && (apiAvoidanceRequested || !apiExplicitlyPermitted);
        if (contradictsApiAvoidance) {
            warnings.add("The draft may include public API details (method signatures, class names) that weren't explicitly requested. Review and generalize if not intended.");
        }
        return warnings;
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

    /** Resolves the current user's id, or {@code null} when there is no authenticated user behind the call: token usage is still recorded, just without an owner. */
    static Long resolveCurrentUserId(UserRepository userRepository) {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
    }

    /**
     * Strips the {@code "1: "}, {@code "2: "}, … prefixes that a model may copy back from numbered prompt context.
     * <p>
     * All or nothing: unless <em>every</em> non-blank line carries the prefix it is due, the text is returned untouched, so an author's own numbered list survives. A blank line
     * may drop its prefix and still count, because the prompt numbers blank lines too but a model tends to echo them bare.
     */
    static String stripLineNumbers(String text) {
        if (text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\n", -1);

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
     * Strips the section markers (e.g. {@code "--- BEGIN PROBLEM STATEMENT ---"}) that a model may copy from the prompt template around its answer. Only the first and last
     * non-blank line are considered: a marker-shaped line in the middle is content the author wrote and is left alone.
     */
    static String stripWrapperMarkers(String text) {
        String[] lines = text.split("\n", -1);

        int start = 0;
        int end = lines.length - 1;

        while (start <= end && lines[start].isBlank()) {
            start++;
        }
        if (start <= end && WRAPPER_MARKER_LINE.matcher(lines[start]).matches()) {
            start++;
        }

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
