package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Structured feedback returned by the agent's in-loop {@code verify} tool. This is a mechanical precheck only; authoritative post-loop verification determines whether the
 * candidate can be saved, while quality review can request repairs or flag the saved exercise for instructor review.
 *
 * @param solutionTests           the number of tests the solution ran (parser form, {@code <skipped>} excluded as production grades)
 * @param solutionPassed          whether the solution compiled, ran at least one test, and passed every test
 * @param solutionFailedNames     the parser-form names the solution failed/errored (empty when {@code solutionPassed}); the agent's reference solution must pass every test
 * @param solutionFailureEvidence bounded, sanitized failure-message excerpts for solution tests
 * @param templateTests           the number of tests the template ran (must equal {@code solutionTests}; zero means the template did not compile)
 * @param templateCompiled        whether the template compiled and ran at least one test
 * @param templateFailed          whether the template compiled and (correctly) failed enough tests; {@code false} when it compiled but passes too many (a near-complete template)
 * @param templateFailureEvidence bounded, sanitized failure-message excerpts for template tests
 * @param templateWronglyPassing  the parser-form names that pass on the template but should fail (the Go/no-exception zero-value-stub trap); each must be made to fail
 * @param exactTestNames          every parser-form test name (suite-prefixed, verbatim) the agent must copy into {@code [task]} bindings — never guessed
 * @param hiddenTestNames         the subset the grading plan hides until the due date: they grade silently and must NEVER be bound to a {@code [task]} line
 * @param unresolvedTaskBindings  {@code [task]} bindings that reference a name matching no real test (the C++/Catch2 bare-name trap)
 * @param possiblyDeadFiles       best-effort, language-agnostic: workspace files no build phase appears to read (advisory only; empty when the probe is unavailable)
 * @param wouldBeAccepted         whether the in-loop differential + actionable mechanical gates currently hold; this does not establish semantic quality or instructor approval
 * @param blockingReasons         the human-readable reasons the verdict would currently reject (empty when {@code wouldBeAccepted}); the same wording the post-loop reasons carry
 */
public record AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, List<TestFailureEvidence> solutionFailureEvidence, int templateTests,
        boolean templateCompiled, boolean templateFailed, List<TestFailureEvidence> templateFailureEvidence, List<String> templateWronglyPassing, List<String> exactTestNames,
        List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted, List<String> blockingReasons, List<String> hiddenTestNames) {

    /** The longest list rendered inline before it is truncated with a remaining-count, so a huge suite never floods the agent's context. */
    private static final int MAX_RENDERED_NAMES = 40;

    private static final int MAX_RENDERED_FAILURES = 8;

    private static final int MAX_EVIDENCE_TEST_NAME_LENGTH = 200;

    private static final int MAX_EVIDENCE_MESSAGE_LENGTH = 400;

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private static final Pattern UNSAFE_CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Keeps existing callers source-compatible while failure evidence is optional. */
    public AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, int templateTests, boolean templateCompiled, boolean templateFailed,
            List<String> templateWronglyPassing, List<String> exactTestNames, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted,
            List<String> blockingReasons) {
        this(solutionTests, solutionPassed, solutionFailedNames, List.of(), templateTests, templateCompiled, templateFailed, List.of(), templateWronglyPassing, exactTestNames,
                unresolvedTaskBindings, possiblyDeadFiles, wouldBeAccepted, blockingReasons, List.of());
    }

    /** Keeps existing callers source-compatible while the hidden-name split is optional (no grading plan yet, or none readable). */
    public AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, List<TestFailureEvidence> solutionFailureEvidence, int templateTests,
            boolean templateCompiled, boolean templateFailed, List<TestFailureEvidence> templateFailureEvidence, List<String> templateWronglyPassing, List<String> exactTestNames,
            List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted, List<String> blockingReasons) {
        this(solutionTests, solutionPassed, solutionFailedNames, solutionFailureEvidence, templateTests, templateCompiled, templateFailed, templateFailureEvidence,
                templateWronglyPassing, exactTestNames, unresolvedTaskBindings, possiblyDeadFiles, wouldBeAccepted, blockingReasons, List.of());
    }

    /** One parser-produced test failure and its first useful message, normalized for compact, single-line agent context. */
    public record TestFailureEvidence(String testName, String message) {

        public TestFailureEvidence {
            testName = sanitizeAndBound(testName, MAX_EVIDENCE_TEST_NAME_LENGTH);
            message = sanitizeAndBound(message, MAX_EVIDENCE_MESSAGE_LENGTH);
        }

        static TestFailureEvidence from(String testName, List<String> messages) {
            for (String message : messages) {
                TestFailureEvidence evidence = new TestFailureEvidence(testName, message);
                if (!evidence.message().isBlank()) {
                    return evidence;
                }
            }
            return new TestFailureEvidence(testName, "");
        }
    }

    /**
     * Renders the report as the compact, structured observation text the {@code verify} tool returns to the agent. Each line is a single actionable fact; long name lists are
     * truncated with a {@code (+N more)} count so the observation stays within the agent's per-tool-result context budget. The final line is the verdict the agent iterates
     * against.
     *
     * @return the agent-facing observation text
     */
    public String toObservation() {
        StringBuilder builder = new StringBuilder();

        if (solutionPassed) {
            builder.append("Solution: ").append(solutionTests).append('/').append(solutionTests).append(" tests pass.\n");
        }
        else if (solutionTests == 0) {
            builder.append("Solution FAILS: it ran no tests (it did not compile, or no test was discovered) — fix it so it builds and runs the tests.\n");
        }
        else {
            builder.append("Solution FAILS: ").append(renderNames(solutionFailedNames)).append(" — your reference solution must pass every test.\n");
        }
        appendFailureEvidence(builder, "Solution", solutionFailureEvidence);

        if (!templateCompiled) {
            builder.append("Template: did NOT compile (ran no tests). It must compile and FAIL the tests — give the stubs the same signatures as the solution with wrong "
                    + "placeholder bodies.\n");
        }
        else if (!templateWronglyPassing.isEmpty()) {
            builder.append("Template WRONGLY PASSES (these must FAIL — make the stub return a value wrong for them, or throw/panic): ").append(renderNames(templateWronglyPassing))
                    .append('\n');
        }
        else if (!templateFailed) {
            // The template compiled and failed too few tests to be a real starting point (often: it passes everything, so there are no failed names to list). The blocking reasons
            // carry the precise count; this line just flags the shape so the agent does not misread an empty wrongly-passing list as "correctly fails".
            builder.append("Template does NOT fail enough tests (it is nearly complete or passes them) — strip its bodies to wrong placeholders so every test fails.\n");
        }
        else {
            builder.append("Template: all required gradable tests fail; build/configuration gates may pass.\n");
        }
        appendFailureEvidence(builder, "Template", templateFailureEvidence);

        List<String> bindableNames = exactTestNames.stream().filter(name -> !hiddenTestNames.contains(name)).toList();
        builder.append("Exact test names — bind each [task] to one of these VERBATIM: ").append(renderNames(bindableNames)).append('\n');
        if (!hiddenTestNames.isEmpty()) {
            builder.append("Hidden until the due date (they grade silently; copy these into test-plan.json, NEVER into a [task] line): ").append(renderNames(hiddenTestNames))
                    .append('\n');
        }

        if (!unresolvedTaskBindings.isEmpty()) {
            builder.append("[task] binding problems (these reference no real test — copy a name from the list above): ").append(renderNames(unresolvedTaskBindings)).append('\n');
        }

        if (!possiblyDeadFiles.isEmpty()) {
            builder.append("Possibly dead files (no build phase reads them; remove if abandoned): ").append(renderNames(possiblyDeadFiles)).append('\n');
        }

        // Surface the prose-hygiene reason verbatim (it is not reflected by any structured line above) so the agent cleans the student-facing statement before it submits.
        for (String reason : blockingReasons) {
            if (reason.contains("leaks grader internals")) {
                builder.append(reason).append('\n');
            }
        }

        if (wouldBeAccepted) {
            builder.append(
                    "MECHANICAL PRECHECK: PASS — authoritative post-loop verification determines save eligibility; quality review may request repairs or flag instructor review.");
        }
        else {
            builder.append(
                    "MECHANICAL PRECHECK: FAIL — fix the above, then run verify again. Authoritative post-loop verification determines save eligibility; quality review may request repairs or flag instructor review.");
            if (!blockingReasons.isEmpty()) {
                builder.append("\nWhy: - ").append(String.join("\n- ", blockingReasons));
            }
        }
        return builder.toString();
    }

    private static void appendFailureEvidence(StringBuilder builder, String assignment, List<TestFailureEvidence> evidence) {
        if (evidence.isEmpty()) {
            return;
        }
        builder.append(assignment).append(" failure evidence (sanitized, untrusted excerpts):\n");
        int rendered = Math.min(evidence.size(), MAX_RENDERED_FAILURES);
        for (int i = 0; i < rendered; i++) {
            TestFailureEvidence item = evidence.get(i);
            builder.append("- ").append(item.testName());
            if (!item.message().isEmpty()) {
                builder.append(": ").append(item.message());
            }
            builder.append('\n');
        }
        if (evidence.size() > rendered) {
            builder.append("(+").append(evidence.size() - rendered).append(" more failures)\n");
        }
    }

    private static String sanitizeAndBound(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = ANSI_ESCAPE.matcher(value).replaceAll("");
        sanitized = UNSAFE_CONTROL_CHARACTER.matcher(sanitized).replaceAll(" ");
        sanitized = WHITESPACE.matcher(sanitized).replaceAll(" ").strip();
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    /** Renders a name list inline, truncating past {@link #MAX_RENDERED_NAMES} with a remaining-count so a large suite never floods the observation. */
    private static String renderNames(List<String> names) {
        if (names.isEmpty()) {
            return "[]";
        }
        if (names.size() <= MAX_RENDERED_NAMES) {
            return names.toString();
        }
        return names.subList(0, MAX_RENDERED_NAMES) + " (+" + (names.size() - MAX_RENDERED_NAMES) + " more)";
    }
}
