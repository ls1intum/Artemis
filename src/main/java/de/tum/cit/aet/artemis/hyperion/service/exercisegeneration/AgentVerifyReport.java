package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.List;

/**
 * The IN-LOOP self-check report the agent's {@code verify} tool returns: the SAME differential analysis (two pristine builds parsed with the production parsers, then the
 * actionable
 * acceptance gates) the post-loop {@link AuthoritativeVerificationService#verify} acceptance decision uses, rendered as compact, structured, agent-readable feedback.
 * <p>
 * <strong>This is feedback, not a verdict.</strong> The post-loop {@code verify(...)} remains the SOLE acceptance truth; this report exists only so the agent can SEE — every time
 * it asks — exactly what that verdict will conclude (which tests pass/fail on the solution and template, the EXACT parser-form names to bind {@code [task]}s to, and which gates it
 * is currently failing) instead of misreading raw exit codes and bare {@code grep name=} output. It deliberately runs only the gates that depend on the live sandbox builds; the
 * sandbox-free integrity gates (harness immutability, solution leak) stay post-loop-only.
 *
 * @param solutionTests          the number of tests the solution ran (parser form, {@code <skipped>} excluded exactly as production grades)
 * @param solutionPassed         whether the solution compiled, ran at least one test, and passed every test
 * @param solutionFailedNames    the parser-form names the solution FAILED/ERRORED (empty when {@code solutionPassed}); the agent's reference solution must pass every test
 * @param templateTests          the number of tests the template ran (must equal {@code solutionTests}; zero means the template did not compile)
 * @param templateCompiled       whether the template compiled and ran at least one test
 * @param templateFailed         whether the template compiled AND (correctly) failed enough tests; {@code false} when it compiled but passes too many (a near-complete template)
 * @param templateWronglyPassing the parser-form names that PASS on the template but should FAIL (the Go/no-exception zero-value-stub trap); each must be made to fail
 * @param exactTestNames         every parser-form test name (suite-prefixed, verbatim) the agent must copy into {@code [task]} bindings — never guessed
 * @param unresolvedTaskBindings {@code [task]} bindings that reference a name matching no real test (the C++/Catch2 bare-name trap)
 * @param possiblyDeadFiles      best-effort, language-agnostic: workspace files no build phase appears to read (advisory only; empty when the probe is unavailable)
 * @param wouldBeAccepted        whether the differential + actionable gates currently hold, i.e. the post-loop verdict would ACCEPT (modulo the post-loop-only integrity gates)
 * @param blockingReasons        the human-readable reasons the verdict would currently reject (empty when {@code wouldBeAccepted}); the SAME wording the post-loop reasons carry
 */
public record AgentVerifyReport(int solutionTests, boolean solutionPassed, List<String> solutionFailedNames, int templateTests, boolean templateCompiled, boolean templateFailed,
        List<String> templateWronglyPassing, List<String> exactTestNames, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, boolean wouldBeAccepted,
        List<String> blockingReasons) {

    /** The longest list rendered inline before it is truncated with a remaining-count, so a huge suite never floods the agent's context. */
    private static final int MAX_RENDERED_NAMES = 40;

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
            builder.append("Template: correctly fails all ").append(templateTests).append(".\n");
        }

        builder.append("Exact test names — bind each [task] to one of these VERBATIM: ").append(renderNames(exactTestNames)).append('\n');

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
            builder.append("VERDICT: would be ACCEPTED.");
        }
        else {
            builder.append("VERDICT: NOT YET — fix the above, then run verify again.");
            if (!blockingReasons.isEmpty()) {
                builder.append("\nWhy: - ").append(String.join("\n- ", blockingReasons));
            }
        }
        return builder.toString();
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
