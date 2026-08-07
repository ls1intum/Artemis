package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structured feedback returned by the agent's in-loop {@code verify} tool. A mechanical precheck only: post-loop verification decides whether the candidate can be saved, and
 * quality review can still request repairs or flag the saved exercise for instructor review.
 *
 * @param solutionTests           the number of tests the solution ran (parser form, {@code <skipped>} excluded as production grades)
 * @param solutionPassed          whether the solution compiled, ran at least one test, and passed every test
 * @param solutionFailedNames     the parser-form names the solution failed/errored (empty when {@code solutionPassed}); the agent's reference solution must pass every test
 * @param solutionFailureEvidence bounded, sanitized failure-message excerpts for solution tests
 * @param templateTests           the number of tests the template ran (must equal {@code solutionTests}; zero means the template did not compile)
 * @param templateCompiled        whether the template compiled and ran at least one test
 * @param templateFailed          whether the template compiled and (correctly) failed enough tests; {@code false} when it compiled but passes too many (a near-complete template)
 * @param templateFailureEvidence bounded, sanitized failure-message excerpts for template tests
 * @param templateWronglyPassing  the parser-form names that pass on the template but must fail; each has to be made to fail
 * @param exactTestNames          every parser-form test name (suite-prefixed, verbatim); only its visible, non-build-gate subset is offered for {@code [task]} bindings
 * @param hiddenTestNames         the subset the grading plan hides until the due date: they grade silently and must NEVER be bound to a {@code [task]} line
 * @param unresolvedTaskBindings  {@code [task]} bindings that reference a name matching no real test
 * @param possiblyDeadFiles       best-effort, language-agnostic: files present in only one assignment repository (advisory only; expected for student-created types)
 * @param wouldBeAccepted         whether the in-loop differential + actionable mechanical gates currently hold; this does not establish semantic quality or instructor approval
 * @param blockingReasons         the human-readable reasons the verdict would currently reject (empty when {@code wouldBeAccepted}); the same wording the post-loop reasons carry
 * @param solutionBuildDiagnostic bounded build output shown only when the solution ran no tests
 * @param templateBuildDiagnostic bounded build output shown only when the template ran no tests
 */
public record AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, List<TestFailureEvidence> solutionFailureEvidence, int templateTests,
        boolean templateCompiled, boolean templateFailed, List<TestFailureEvidence> templateFailureEvidence, List<String> templateWronglyPassing, List<String> exactTestNames,
        List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted, List<String> blockingReasons, List<String> hiddenTestNames,
        String solutionBuildDiagnostic, String templateBuildDiagnostic) {

    /** The longest list rendered inline before it is truncated with a remaining-count, so a huge suite never floods the agent's context. */
    private static final int MAX_RENDERED_NAMES = 40;

    private static final int MAX_RENDERED_FAILURES = 8;

    private static final int MAX_EVIDENCE_TEST_NAME_LENGTH = 200;

    private static final int MAX_EVIDENCE_MESSAGE_LENGTH = 400;

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private static final Pattern UNSAFE_CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String ARES_CONSTRUCTOR_MISMATCH = "does not have a constructor with the arguments";

    public AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, int templateTests, boolean templateCompiled, boolean templateFailed,
            List<String> templateWronglyPassing, List<String> exactTestNames, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted,
            List<String> blockingReasons) {
        this(solutionTests, solutionPassed, solutionFailedNames, List.of(), templateTests, templateCompiled, templateFailed, List.of(), templateWronglyPassing, exactTestNames,
                unresolvedTaskBindings, possiblyDeadFiles, wouldBeAccepted, blockingReasons, List.of(), "", "");
    }

    /** Hidden names are unavailable before the grading plan exists, or when none is readable. */
    public AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, List<TestFailureEvidence> solutionFailureEvidence, int templateTests,
            boolean templateCompiled, boolean templateFailed, List<TestFailureEvidence> templateFailureEvidence, List<String> templateWronglyPassing, List<String> exactTestNames,
            List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted, List<String> blockingReasons) {
        this(solutionTests, solutionPassed, solutionFailedNames, solutionFailureEvidence, templateTests, templateCompiled, templateFailed, templateFailureEvidence,
                templateWronglyPassing, exactTestNames, unresolvedTaskBindings, possiblyDeadFiles, wouldBeAccepted, blockingReasons, List.of(), "", "");
    }

    public AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, List<TestFailureEvidence> solutionFailureEvidence, int templateTests,
            boolean templateCompiled, boolean templateFailed, List<TestFailureEvidence> templateFailureEvidence, List<String> templateWronglyPassing, List<String> exactTestNames,
            List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted, List<String> blockingReasons, List<String> hiddenTestNames) {
        this(solutionTests, solutionPassed, solutionFailedNames, solutionFailureEvidence, templateTests, templateCompiled, templateFailed, templateFailureEvidence,
                templateWronglyPassing, exactTestNames, unresolvedTaskBindings, possiblyDeadFiles, wouldBeAccepted, blockingReasons, hiddenTestNames, "", "");
    }

    /** One test failure and its first useful message, normalized to a single line. */
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
     * @return the observation text the {@code verify} tool returns: one actionable fact per line, name lists truncated to the agent's per-tool-result context budget, and the
     *         verdict on the final line
     */
    public String toObservation() {
        return toObservation(true);
    }

    /** TESTS-stage rendering: the names feed the grading plan, while task binding belongs to the later statement stage. */
    public String toTestsStageObservation() {
        return toObservation(false);
    }

    private String toObservation(boolean includeStatementGuidance) {
        StringBuilder builder = new StringBuilder();

        if (solutionPassed) {
            builder.append("Solution: ").append(solutionTests).append('/').append(solutionTests).append(" tests pass.\n");
        }
        else if (solutionTests == 0) {
            builder.append("Solution FAILS: it ran no tests (it did not compile, or no test was discovered) — fix it so it builds and runs the tests.\n");
            appendBuildDiagnostic(builder, "Solution", solutionBuildDiagnostic);
        }
        else {
            builder.append("Solution FAILS: ").append(renderNames(solutionFailedNames)).append(" — your reference solution must pass every test.\n");
        }
        appendFailureEvidence(builder, "Solution", solutionFailureEvidence);
        appendReflectionConstructorGuidance(builder, solutionFailureEvidence);

        if (!templateCompiled) {
            builder.append("Template: did NOT compile (ran no tests). It must compile and FAIL the tests — give the stubs the same signatures as the solution with wrong "
                    + "placeholder bodies.\n");
            appendBuildDiagnostic(builder, "Template", templateBuildDiagnostic);
        }
        else if (!templateWronglyPassing.isEmpty()) {
            builder.append("Template WRONGLY PASSES (these must FAIL — make the stub return a value wrong for them, or throw/panic): ").append(renderNames(templateWronglyPassing))
                    .append('\n');
        }
        else if (!templateFailed) {
            // The template compiled but failed too few tests, often passing everything, so there are no failed names to list. Flagging the shape here stops the agent from
            // misreading an empty wrongly-passing list as "correctly fails"; the blocking reasons carry the precise count.
            builder.append("Template does NOT fail enough tests (it is nearly complete or passes them) — strip its bodies to wrong placeholders so every test fails.\n");
        }
        else {
            builder.append("Template: all required gradable tests fail; build/configuration gates may pass.\n");
        }
        appendFailureEvidence(builder, "Template", templateFailureEvidence);

        List<String> bindableNames = ProblemStatementBindingChecker.bindableTestNames(exactTestNames, Set.copyOf(hiddenTestNames));
        String exactNamesLabel;
        if (includeStatementGuidance) {
            exactNamesLabel = "Exact test names — bind each [task] to one of these VERBATIM: ";
        }
        else if (solutionPassed && templateFailed) {
            exactNamesLabel = "Exact test names — use these VERBATIM in test-plan.json; the later STATEMENT stage will bind the visible names: ";
        }
        else {
            exactNamesLabel = "Exact test names discovered so far — use these verbatim after the differential is green: ";
        }
        builder.append(exactNamesLabel).append(renderNames(bindableNames)).append('\n');
        if (!hiddenTestNames.isEmpty()) {
            builder.append("Hidden until the due date (they grade silently; copy these into test-plan.json, NEVER into a [task] line): ").append(renderNames(hiddenTestNames))
                    .append('\n');
        }

        if (includeStatementGuidance && !unresolvedTaskBindings.isEmpty()) {
            builder.append("[task] binding problems (these reference no real test — copy a name from the list above): ").append(renderNames(unresolvedTaskBindings)).append('\n');
        }

        if (!possiblyDeadFiles.isEmpty()) {
            builder.append("Assignment-specific files (present in only solution or template; expected for intentional student-created types, otherwise review): ")
                    .append(renderNames(possiblyDeadFiles)).append('\n');
        }

        // No structured line above reflects prose hygiene, so surface its reason verbatim.
        for (String reason : blockingReasons) {
            if (reason.startsWith(ProblemStatementBindingChecker.PROSE_HYGIENE_REJECTION_PREFIX)) {
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

    private static void appendReflectionConstructorGuidance(StringBuilder builder, List<TestFailureEvidence> evidence) {
        if (evidence.stream().map(TestFailureEvidence::message).noneMatch(message -> message.contains(ARES_CONSTRUCTOR_MISMATCH))) {
            return;
        }
        builder.append(
                """
                        REFLECTION HARNESS DIAGNOSTIC: Ares newInstance(className, arguments...) infers exact runtime argument classes. When the approved constructor declares an interface \
                        or supertype, do NOT add concrete overloads to production code. Resolve the declared signature explicitly, for example \
                        getConstructor(getClazz("package.Owner"), List.class, getClazz("package.Collaborator")), then pass that Constructor and the runtime arguments to newInstance.
                        """);
    }

    private static void appendBuildDiagnostic(StringBuilder builder, String assignment, String diagnostic) {
        if (diagnostic != null && !diagnostic.isBlank() && !"[no diagnostic output]".equals(diagnostic)) {
            builder.append(assignment).append(" build diagnostic (bounded, sanitized, untrusted output):\n").append(diagnostic.strip()).append('\n');
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
