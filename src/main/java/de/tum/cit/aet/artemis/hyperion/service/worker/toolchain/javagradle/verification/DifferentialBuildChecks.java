package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;

/**
 * The verdict checks {@link DifferentialVerifier} applies to the parsed solution and template builds: the solution passes, the template builds soundly and fails every
 * assessed test, test names are unique, the statement's task bindings resolve and cover every gradable test, and the candidate was extracted completely. A structural check
 * that fails on a reference solution matching the approved Public API is classified as a server seeding defect rather than an authoring error.
 * <p>
 * Each check appends its actionable rejection reason to the given list and returns whether it holds, so the verdict and the agent's feedback are computed by the same code.
 * Static and side-effect-free apart from that list.
 */
final class DifferentialBuildChecks {

    private DifferentialBuildChecks() {
    }

    static boolean checkSolutionPasses(BuildSummary solution, List<String> reasons) {
        boolean solutionPassed = !solution.timedOut() && solution.exitCode() == 0 && solution.tests() > 0 && solution.failures() == 0;
        if (solution.timedOut()) {
            reasons.add("The solution build timed out. The solution must compile and pass every test within the time limit." + buildEvidence(solution));
        }
        else if (solution.tests() == 0) {
            reasons.add("No tests were detected when building against the solution. The exercise must contain at least one meaningful test, and the solution must compile so the "
                    + "tests can run." + buildEvidence(solution));
        }
        else if (!solutionPassed && solution.failures() == 0) {
            // Reporting "0 failing of N" here read as a contradiction and named no test to repair. The build broke for a reason the test reports do not carry, so say that and
            // hand over the build output instead.
            reasons.add("The solution build did not succeed even though all " + solution.tests() + " of its tests passed, so the failure is outside the tests — a compile error "
                    + "in code the tests do not reach, a build-script or dependency fault, or a crash after the tests ran." + buildEvidence(solution));
        }
        else if (!solutionPassed) {
            // The counts alone gave a fresh repair session nothing to act on, so the reason carries each failing test and its failure message.
            reasons.add("The solution does not pass its own tests (" + solution.failures() + " failing of " + solution.tests()
                    + "). The solution must compile and pass every test.\n" + AgentVerifyReport.renderFailureEvidence("Solution", solution.failureEvidence()).stripTrailing());
        }
        return solutionPassed;
    }

    /**
     * Names server-owned structural checks that fail on a reference solution matching the approved Public API. The server derived them from that contract, so the failure is a
     * seeding defect the agent cannot repair. The checks still fail verification; this only keeps the diagnostic from presenting the defect as an authoring error.
     */
    static List<String> structuralSeedingDefectReasons(BuildSummary solution, Set<String> seededStructuralTestNames, List<String> contractSpecifications,
            Map<String, String> solutionFiles) {
        Set<String> seeded = seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> failingSeeded = solution.testFailedNames().stream().filter(name -> seeded.contains(ProblemStatementBindingChecker.normalizeTestName(name))).distinct().sorted()
                .toList();
        if (failingSeeded.isEmpty() || contractSpecifications.isEmpty()
                || !contractSpecifications.stream().allMatch(spec -> ApprovedOwnershipGate.solutionMatchesApprovedApi(spec, solutionFiles))) {
            return List.of();
        }
        return List.of(VerificationResult.SERVER_DEFECT_PREFIX + " the server-owned structural check(s) " + failingSeeded + " fail on the reference solution although it "
                + "declares exactly the approved Public API those checks were derived from. Authoring cannot repair this: do not change the solution, template, tests, or "
                + "SPEC.md to satisfy them. The exercise cannot be verified until the Artemis administrators fix the structural seeding.");
    }

    /** The bounded build output of a build whose test reports cannot explain its failure, such as one that did not compile. */
    static String buildEvidence(BuildSummary build) {
        return "\nBuild output: (bounded, sanitized, untrusted)\n" + (build.buildDiagnostic().isBlank() ? "not captured." : build.buildDiagnostic());
    }

    static boolean checkNoDuplicateTestNames(BuildSummary solution, List<String> reasons) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String name : solution.testNames()) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty()) {
            reasons.add("Duplicate test names are not production-gradeable: " + duplicates
                    + ". Rename the generated tests so every test case name is unique (case-insensitive), otherwise Artemis production grading marks duplicates and keeps the "
                    + "result at zero.");
            return false;
        }
        return true;
    }

    /**
     * The template must compile and run exactly the tests the solution ran, without timing out. This is a prerequisite for {@link #gradableTestsPassingOnTemplate}: a template
     * that fails it cannot be checked test-by-test at all.
     */
    static boolean checkTemplateBuildSound(BuildSummary solution, BuildSummary template, List<String> reasons) {
        if (template.timedOut()) {
            reasons.add("The template build timed out; it must compile and fail the tests quickly.");
            return false;
        }
        if (template.tests() == 0) {
            reasons.add("The template does not compile (the tests never ran). The template must compile and only fail because the student's work is missing — use placeholder "
                    + "method bodies (returning null, 0, false) with the same signatures as the solution." + buildEvidence(template));
            return false;
        }
        if (solution.tests() > 0 && template.tests() != solution.tests()) {
            // A differing count means the template silently dropped tests, so it can "fail" vacuously without the tests discriminating anything.
            reasons.add("The template runs a different number of tests (" + template.tests() + ") than the solution (" + solution.tests()
                    + "). Both must run the same tests; the template must differ only in its (unimplemented) method bodies, not in which tests compile and run.");
            return false;
        }
        return true;
    }

    static List<String> behaviouralTestNames(BuildSummary solution, Set<String> seededStructuralTestNames) {
        Set<String> structural = seededStructuralTestNames == null ? Set.of()
                : seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> behavioural = new ArrayList<>();
        for (String rawName : solution.testNames()) {
            String normalized = ProblemStatementBindingChecker.normalizeTestName(rawName);
            if (structural.contains(normalized)) {
                continue;
            }
            behavioural.add(rawName);
        }
        return behavioural;
    }

    static boolean checkHasBehaviouralTests(List<String> behaviouralTestNames, List<String> reasons) {
        if (!behaviouralTestNames.isEmpty()) {
            return true;
        }
        reasons.add("The exercise has no behavioural tests. Add at least one behavioural test that passes on the solution and fails on the template; build gates and structural "
                + "checks alone do not verify the required student behaviour.");
        return false;
    }

    static List<String> gradableTestsPassingOnTemplate(List<String> behaviouralTestNames, BuildSummary template) {
        Set<String> templateFailed = template.testFailedNames().stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> passing = new ArrayList<>();
        for (String rawName : behaviouralTestNames) {
            String normalized = ProblemStatementBindingChecker.normalizeTestName(rawName);
            if (!templateFailed.contains(normalized)) {
                passing.add(rawName);
            }
        }
        return passing;
    }

    static boolean checkNoGradableTestPassesTemplate(List<String> gradableTestsPassingOnTemplate, List<String> reasons) {
        if (gradableTestsPassingOnTemplate.isEmpty()) {
            return true;
        }
        reasons.add("These non-structural gradable tests pass on the template but must fail: " + gradableTestsPassingOnTemplate
                + ". Remove only the assigned student work, not supplied behavior. Checks solely for supplied behavior belong in test-plan.json as zero-credit PRESERVATION "
                + "checks, which must pass on both repositories and cannot cover a student-work seam.");
        return false;
    }

    /** A binding that resolves to no runner test name shows the student no progress in Artemis, and the differential build cannot detect it. */
    static boolean checkTaskBindingsResolve(List<String> unresolvedTaskBindings, List<String> bindableTestNames, boolean problemStatementHasTasks, List<String> reasons) {
        boolean taskBindingsResolve = unresolvedTaskBindings.isEmpty();
        if (problemStatementHasTasks && !taskBindingsResolve) {
            reasons.add("These [task] bindings reference names that match no actual test: " + unresolvedTaskBindings + ". A [task]'s parenthesised names must be the exact test "
                    + "method/function names (e.g. testSortsAscending), not a @DisplayName or a prose title — otherwise the task shows no result in Artemis. The actual test names are: "
                    + bindableTestNames + ". Fix the [task] lines (or rename the tests) so every binding references a real, visible test name.");
        }
        return taskBindingsResolve;
    }

    static boolean checkNoDuplicateTaskBindings(List<String> duplicateTaskBindings, boolean problemStatementHasTasks, List<String> reasons) {
        boolean noDuplicateTaskBindings = duplicateTaskBindings.isEmpty();
        if (problemStatementHasTasks && !noDuplicateTaskBindings) {
            reasons.add("These tests are bound more than once by [task] entries: " + duplicateTaskBindings
                    + ". Bind each graded test exactly once so the student-facing checklist is unambiguous and matches production grading.");
        }
        return noDuplicateTaskBindings;
    }

    static boolean checkAllGradableTestsBound(List<String> unboundGradableTests, boolean problemStatementHasTasks, boolean taskBindingsResolve, List<String> reasons) {
        boolean allGradableTestsBound = unboundGradableTests.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !allGradableTestsBound) {
            reasons.add("These real gradable tests are not bound by any [task] entry even though production will grade them: " + unboundGradableTests
                    + ". Add each to the [task] line of the seam it belongs to — one line takes a comma-separated list, [task][Title](testA,testB,testC) — never a new [task] "
                    + "line per test. Tests the grading plan marks AFTER_DUE_DATE are exempt and must stay unbound.");
        }
        return allGradableTestsBound;
    }

    /**
     * Fail-closed on a read-back gap: a repository seeded non-empty but extracted empty would silently disable the harness and leak gates, so the candidate is rejected rather
     * than accepted on that doubt. A genuinely empty repository is reported as not failed and stays fail-open.
     */
    static boolean checkExtractionSound(Set<String> extractionFailedRepositories, List<String> reasons) {
        boolean extractionSound = extractionFailedRepositories == null || extractionFailedRepositories.isEmpty();
        if (!extractionSound) {
            reasons.add("The generated files for these repositories could not be safely extracted for integrity verification: " + extractionFailedRepositories
                    + ". The harness-immutability and solution-leak checks cannot run on an incomplete or unrepresentable tree, so the exercise cannot be verified. This can be "
                    + "caused by a temporary copy-out failure, generated binary or mode changes, or repository-layout residue. Retry once; if it persists, inspect the generated "
                    + "workspace and build outputs.");
        }
        return extractionSound;
    }
}
