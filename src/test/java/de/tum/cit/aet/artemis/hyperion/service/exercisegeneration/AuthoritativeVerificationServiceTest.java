package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Deterministic unit test for the differential oracle in the NEW copyOut+production-parser flow: a fake sandbox serves the {@code copyOut} of the verifier-owned reports directory
 * as a tar of REAL JUnit (and, where exercised, real SCA) report files for the solution and the template builds. The verifier parses those with the production parsers
 * ({@code TestResultXmlParser}, {@code ReportParser}) — exactly as it does against a live container — so the accept/reject branch matrix is exercised end to end without Docker or
 * a
 * real build. This is the regression guard for the verdict rules; the live build behaviour is covered by the gated end-to-end test.
 */
class AuthoritativeVerificationServiceTest {

    /**
     * A {@link SandboxBuildCommandService} backed by a stub phase service, so the verifier-under-test can render and place a pristine {@code verify.sh} exactly as in production.
     * The rendered script's content is irrelevant to these deterministic tests (the {@link ScriptedSandbox} serves canned report tars regardless of which script runs), but
     * rendering it exercises the real re-seed path.
     */
    private static SandboxBuildCommandService sandboxBuildCommandService() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        return new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
    }

    private static AuthoritativeVerificationService newVerifier() {
        return new AuthoritativeVerificationService(sandboxBuildCommandService());
    }

    /**
     * One build's report fixture: the reports tar the verifier {@code copyOut}s (real JUnit/SCA XML keyed by the collected {@code <seq>__<canonical>} names) plus the build's exit
     * code and timeout flag (read by the solution/template gates). A {@code null} reports tar models a build whose collection produced no files at all.
     *
     * @param allNames    every test name the JUnit report carries (one {@code <testcase>} each)
     * @param failedNames the subset that carry a {@code <failure>}
     * @param scaReports  SCA reports keyed by their canonical per-tool file name (e.g. {@code spotbugsXml.xml}); empty for the common non-SCA case
     * @param exitCode    the build's exit code (the script's {@code exit $rc}); the solution gate requires 0
     * @param timedOut    whether the build was killed for exceeding its timeout
     */
    private record BuildReportSpec(List<String> allNames, List<String> failedNames, Map<String, String> scaReports, int exitCode, boolean timedOut, String explicitJunitXml) {

        static BuildReportSpec of(List<String> allNames, List<String> failedNames, int exitCode) {
            return new BuildReportSpec(allNames, failedNames, Map.of(), exitCode, false, null);
        }

        static BuildReportSpec withScaReports(List<String> allNames, List<String> failedNames, Map<String, String> scaReports, int exitCode) {
            return new BuildReportSpec(allNames, failedNames, scaReports, exitCode, false, null);
        }

        /** A spec whose JUnit report is the given verbatim XML (for the skipped/multi-suite shapes the production parser must interpret); names/fails are unused. */
        static BuildReportSpec withJunitXml(String junitXml, int exitCode) {
            return new BuildReportSpec(List.of(), List.of(), Map.of(), exitCode, false, junitXml);
        }

        static BuildReportSpec timedOutBuild() {
            return new BuildReportSpec(List.of(), List.of(), Map.of(), 124, true, null);
        }

        TarArchiveInputStream reportsTar(String assignment) {
            if (explicitJunitXml != null) {
                return ReportTarFixtures.tar(assignment, Map.of("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                        explicitJunitXml.getBytes(StandardCharsets.UTF_8)));
            }
            return ReportTarFixtures.junitAndScaReports(assignment, allNames, failedNames, scaReports);
        }
    }

    /** The two graded test names the default {@link #PROBLEM_STATEMENT_WITH_TASK} binds; a build's reports must include them so the [task] bindings resolve. */
    private static final String[] DEFAULT_BOUND_NAMES = { "sortsUnsortedArray", "sortsArrayWithDuplicates" };

    /**
     * A build spec with {@code tests} testcases (the two default-bound names first, then fillers), where — when the run has failures/errors — EVERY test is marked failed (the
     * canonical failing template, so every bound test fails on it). Mirrors what a real failing template's JUnit report shows.
     */
    private static BuildReportSpec result(int tests, int failures, int errors, int exit) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < tests; i++) {
            names.add(i < DEFAULT_BOUND_NAMES.length ? DEFAULT_BOUND_NAMES[i] : "hyperionTest" + i);
        }
        List<String> failed = (failures + errors) > 0 ? names : List.of();
        return BuildReportSpec.of(names, failed, exit);
    }

    /** A build spec with explicit names and the subset that fail; the JUnit report carries a {@code <failure>} for each failed name. */
    private static BuildReportSpec resultWithFails(int tests, int exit, List<String> allNames, List<String> failedNames) {
        return BuildReportSpec.of(allNames, failedNames, exit);
    }

    private static final String PROBLEM_STATEMENT_WITH_TASK = "# Sort\n[task][Sort an array](sortsUnsortedArray,sortsArrayWithDuplicates)\n";

    /**
     * A sandbox that serves the solution/template report tars on {@code copyOut} (routed by the reports-dir path the verifier asks for) and the build exit code/timeout on
     * {@code exec}. The verifier re-seeds and runs {@code sh /opt/hyperion/verify.sh <assignment>}; the build exec returns the spec's exit code, and the subsequent
     * {@code copyOut} of {@code /opt/hyperion/reports/<assignment>} returns that assignment's report tar.
     */
    private static final class ScriptedSandbox implements InteractiveSandbox {

        private final BuildReportSpec solution;

        private final BuildReportSpec template;

        private final String problemStatement;

        private ScriptedSandbox(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
            this.solution = solution;
            this.template = template;
            this.problemStatement = problemStatement;
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            String joined = String.join(" ", command);
            if ("cat".equals(command[0])) {
                return new SandboxExecResult(0, problemStatement, "", false);
            }
            if (!joined.contains("verify.sh")) {
                // The pristine-script mkdir or any other non-build command.
                return new SandboxExecResult(0, "", "", false);
            }
            BuildReportSpec spec = joined.contains("solution") ? solution : template;
            return new SandboxExecResult(spec.exitCode(), "build ran", "", spec.timedOut());
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            if (path.endsWith("/solution")) {
                return solution.reportsTar("solution");
            }
            if (path.endsWith("/template")) {
                return template.reportsTar("template");
            }
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    private static VerificationResult verify(BuildReportSpec solution, BuildReportSpec template) {
        return verify(solution, template, PROBLEM_STATEMENT_WITH_TASK);
    }

    private static VerificationResult verify(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise());
    }

    /** Runs the in-loop self-check (the agent's {@code verify} tool) against the same scripted sandbox, so its report shares the differential with the post-loop {@code verify}. */
    private static AgentVerifyReport selfCheck(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
        return newVerifier().selfCheck(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise());
    }

    /** Runs the 9-arg verify with the authoritative auto-seeded structural test names, so the W1 structural-binding exemption is exercised with (and without) that set. */
    private static VerificationResult verifyWithSeededStructural(BuildReportSpec solution, BuildReportSpec template, String problemStatement, Set<String> seededStructural) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(),
                seededStructural);
    }

    /**
     * A sandbox that serves DIFFERENT report tars for the agent's {@code /workspace/verify.sh} versus the verifier-owned reports dir, and records every exec so a test can assert
     * the verifier ran the PRISTINE path and never the agent's copy. The forged specs would be served if the verifier ever ran the agent's script; the pristine specs are what the
     * verifier-owned reports dir actually holds.
     */
    private static final class PathDispatchingSandbox implements InteractiveSandbox {

        private final BuildReportSpec pristineSolution;

        private final BuildReportSpec pristineTemplate;

        private final String problemStatement;

        private final List<String> execCommands = new ArrayList<>();

        private PathDispatchingSandbox(BuildReportSpec pristineSolution, BuildReportSpec pristineTemplate, String problemStatement) {
            this.pristineSolution = pristineSolution;
            this.pristineTemplate = pristineTemplate;
            this.problemStatement = problemStatement;
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            String joined = String.join(" ", command);
            execCommands.add(joined);
            if ("cat".equals(command[0])) {
                return new SandboxExecResult(0, problemStatement, "", false);
            }
            if (!joined.contains("verify.sh")) {
                return new SandboxExecResult(0, "", "", false);
            }
            BuildReportSpec spec = joined.contains(" solution") ? pristineSolution : pristineTemplate;
            return new SandboxExecResult(spec.exitCode(), "", "", spec.timedOut());
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            // The verifier only ever copies the verifier-owned reports dir; serve the pristine reports for whichever assignment it asks for.
            if (path.endsWith("/solution")) {
                return pristineSolution.reportsTar("solution");
            }
            if (path.endsWith("/template")) {
                return pristineTemplate.reportsTar("template");
            }
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    @Test
    void shouldRunThePristineScriptAndReadTheVerifierOwnedReportsDir() {
        // The verifier must invoke the PRISTINE /opt/hyperion/verify.sh and read its verdict from the verifier-owned reports dir, never the agent's /workspace copy. A
        // genuinely-good
        // pristine build is accepted, proving the re-seed + copyOut path works end to end.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        PathDispatchingSandbox sandbox = new PathDispatchingSandbox(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), PROBLEM_STATEMENT_WITH_TASK);
        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise());
        assertThat(result.accepted()).isTrue();
        assertThat(sandbox.execCommands).anyMatch(c -> c.contains(SandboxBuildCommandService.PRISTINE_VERIFY_PATH + " solution"));
        assertThat(sandbox.execCommands).noneMatch(c -> c.contains("/workspace/verify.sh"));
    }

    @Test
    void shouldRejectWhenPristineReportsShowNoTests() {
        // The pristine reports dir holds no JUnit report (a compile failure produced none), so tests=0 both runs -> rejection. The verdict is read from the reports dir, not
        // stdout.
        PathDispatchingSandbox sandbox = new PathDispatchingSandbox(result(0, 0, 0, 0), result(0, 0, 0, 0), PROBLEM_STATEMENT_WITH_TASK);
        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise());
        assertThat(result.accepted()).isFalse();
        assertThat(result.testCount()).isZero();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No tests were detected"));
        assertThat(sandbox.execCommands).anyMatch(c -> c.contains(SandboxBuildCommandService.PRISTINE_VERIFY_PATH + " solution"));
    }

    @Test
    void shouldAcceptWhenSolutionPassesAndTemplateFailsSameTests() {
        VerificationResult result = verify(result(5, 0, 0, 0), result(5, 3, 0, 1));
        assertThat(result.accepted()).isTrue();
        assertThat(result.solutionPassed()).isTrue();
        assertThat(result.templateFailed()).isTrue();
        assertThat(result.testCount()).isEqualTo(5);
    }

    /**
     * Regression for the task-binding parser: a JVM/Ares (or Rust/Kotlin) test identifier is reported WITH parentheses — {@code testBubbleSort()} — and the agent is instructed to
     * copy the verbatim test name into the {@code [task]} binding. The paren-aware capture plus the {@code ()}-stripping normalisation must resolve the paren form against the
     * {@code testBubbleSort()} the runner reported, so the exercise is accepted.
     */
    @Test
    void shouldAcceptWhenTaskBindingTestNamesCarryParentheses() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort(),testMergeSort())\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), problemStatement);
        assertThat(result.reasons()).as("paren-bearing [task] bindings must resolve, not be flagged as unresolved").noneMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.accepted()).isTrue();
        assertThat(result.testCount()).isEqualTo(2);
    }

    /** The no-parentheses binding form (the prompt example: {@code (sortsAscendingArray)}) must keep resolving against a {@code sortsAscendingArray()} the runner reported. */
    @Test
    void shouldAcceptWhenTaskBindingOmitsParenthesesButTestNameHasThem() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort,testMergeSort)\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), problemStatement);
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.accepted()).isTrue();
    }

    /** Runs the full 8-arg verify with the integrity-gate inputs, so the harness-immutability and solution-leak gates are exercised alongside the differential oracle. */
    private static VerificationResult verifyWithFiles(BuildReportSpec solution, BuildReportSpec template, Map<String, String> seedTests, Map<String, String> producedTests,
            Map<String, String> producedTemplate, Map<String, String> producedSolution) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", new ProgrammingExercise(), seedTests, producedTests,
                producedTemplate, producedSolution, Set.of());
    }

    private static final String SOLUTION_BODY = "module Exercise (factorial) where\n\nfactorial :: Integer -> Integer\nfactorial 0 = 1\nfactorial n = n * factorial (n - 1)\n";

    private static final String SEED_CABAL = "library solution\n  hs-source-dirs: ${solutionWorkingDirectory}/src\n  exposed-modules: Exercise\n";

    @Test
    void integrityGates_acceptWhenHarnessUnchangedAndNoLeak() {
        var seedTests = Map.of("test.cabal", SEED_CABAL);
        var producedTests = Map.of("test.cabal", SEED_CABAL.replace("${solutionWorkingDirectory}", "assignment"));
        var producedTemplate = Map.of("src/Exercise.hs", "factorial _ = error \"todo: implement factorial here\"\n");
        var producedSolution = Map.of("src/Exercise.hs", SOLUTION_BODY);
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), seedTests, producedTests, producedTemplate, producedSolution);
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void integrityGates_rejectWhenHarnessBuildLayoutTampered() {
        var seedTests = Map.of("test.cabal", SEED_CABAL);
        var producedTests = Map.of("test.cabal", SEED_CABAL.replace("${solutionWorkingDirectory}/src", "assignment/solution/src"));
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), seedTests, producedTests, Map.of(), Map.of());
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("tests/test.cabal") && r.contains("harness is graded"));
    }

    @Test
    void integrityGates_rejectWhenTemplateLeaksSolutionToANonGradedPath() {
        var producedTemplate = Map.of("src/Exercise.hs", "factorial _ = error \"todo: implement the factorial function here\"\n", "doc/reference_solution.hs", SOLUTION_BODY);
        var producedSolution = Map.of("src/Exercise.hs", SOLUTION_BODY);
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), Map.of(), Map.of(), producedTemplate, producedSolution);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("template leaks the reference solution"));
    }

    @Test
    void integrityGates_failOpenWhenNoFilesProvided() {
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), Map.of(), Map.of(), Map.of(), Map.of());
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldAcceptWhenSolutionPassesAndTemplateFailsViaRealJUnitReports() {
        // A plain JUnit report with three testcases, all passing on the solution and all failing on the template; parsed by the production TestResultXmlParser via copyOut.
        List<String> names = List.of("stack_initially_empty", "push_then_pop", "size_tracks_elements");
        VerificationResult result = verify(resultWithFails(3, 0, names, List.of()), resultWithFails(3, 1, names, names),
                "# Stack\n[task][Empty](stack_initially_empty)\n[task][Push/Pop](push_then_pop)\n[task][Size](size_tracks_elements)\n");
        assertThat(result.accepted()).isTrue();
        assertThat(result.testCount()).isEqualTo(3);
        assertThat(result.templateFailed()).isTrue();
    }

    @Test
    void shouldAcceptWhenTemplateFailsButBuildExitCodeIsZero() {
        // Languages that pipe the test run through a report converter (Go's go-junit-report, Dart's tojunit, …) exit 0 even when tests failed. The oracle must trust the JUnit
        // failure counts (from the report), not the exit code: a template that ran the same tests and failed enough of them is valid even with exit=0.
        VerificationResult result = verify(result(14, 0, 0, 0), result(14, 14, 0, 0));
        assertThat(result.accepted()).isTrue();
        assertThat(result.templateFailed()).isTrue();
    }

    @Test
    void shouldRejectWhenTemplateDoesNotCompile() {
        // No JUnit report collected for the template -> tests=0 against the template -> it did not compile.
        VerificationResult result = verify(result(5, 0, 0, 0), result(0, 0, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("does not compile"));
    }

    @Test
    void shouldRejectWhenTemplatePasses() {
        VerificationResult result = verify(result(5, 0, 0, 0), result(5, 0, 0, 0));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("passes the tests"));
    }

    @Test
    void shouldRejectWhenSolutionFails() {
        VerificationResult result = verify(result(5, 2, 0, 1), result(5, 5, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.solutionPassed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("does not pass its own tests"));
    }

    @Test
    void shouldRejectWhenSolutionHasNoTests() {
        VerificationResult result = verify(result(0, 0, 0, 0), result(0, 0, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.testCount()).isZero();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No tests were detected"));
    }

    @Test
    void shouldRejectWhenTemplateRunsFewerTestsThanSolution() {
        // A class present in the solution but missing from the template would drop tests; the suites must be identical.
        VerificationResult result = verify(result(5, 0, 0, 0), result(3, 3, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("different number of tests"));
    }

    @Test
    void shouldRejectWhenSolutionTimesOut() {
        VerificationResult result = verify(BuildReportSpec.timedOutBuild(), result(5, 5, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("timed out"));
    }

    @Test
    void shouldRejectWhenTemplateFailsTooFewTests() {
        // A template that fails only 1 of 6 tests is nearly complete; it must fail at least half to be a meaningful starting point.
        List<String> names = List.of("t0", "t1", "t2", "t3", "t4", "t5");
        VerificationResult result = verify(resultWithFails(6, 0, names, List.of()), resultWithFails(6, 1, names, List.of("t0")), "# X\n[task][One](t0)\n[task][Two](t1)\n");
        assertThat(result.accepted()).isFalse();
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("nearly complete"));
    }

    @Test
    void shouldRejectWhenProblemStatementHasNoTaskBindings() {
        VerificationResult result = verify(result(4, 0, 0, 0), result(4, 4, 0, 1), "# Sort\nImplement the sort method. The tests will check correctness.\n");
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("task bindings"));
    }

    @Test
    void shouldAcceptWhenTaskBindingsResolveToRealTestNames() {
        List<String> names = List.of("sortsUnsortedArray()", "sortsArrayWithDuplicates()");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names));
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectWhenTaskBindingReferencesDisplayNameInsteadOfMethodName() {
        // The classic defect: the [task] names are @DisplayName / prose text, while the real test method names differ — the task would bind to nothing in Artemis.
        String problemStatement = "# Sort\n[task][Sort an unsorted array](Sort an unsorted array)\n[task][Sort with duplicates](Sort with duplicates)\n";
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), problemStatement);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test"));
    }

    @Test
    void buildSummary_fromReports_recordsACompleteSoundPerTestView_thatTheFailOpenGatesRelyOn() {
        // The per-test differential gates fail OPEN when the solution recorded fewer names than tests, or a failing template recorded no failing-test names — so the
        // emitter-soundness
        // gate and the whole "fail-open is safe" argument rest on fromReports producing a COMPLETE, SOUND per-test view from the real reports. Pin that invariant directly: every
        // counted test is named, and every failed test is named AND a member of the full set. A regression (counting tests from a <testsuite tests=N> attribute, or de-duplicating
        // the
        // name list) would silently re-open the hole the emitter-soundness gate is meant to close.
        var summary = AuthoritativeVerificationService.BuildSummary
                .fromReports(Map.of("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                        ReportTarFixtures.junitXml(List.of("passes_a", "fails_b", "passes_c"), List.of("fails_b")).getBytes(StandardCharsets.UTF_8)), 0);
        assertThat(summary.tests()).as("every counted test is named").isEqualTo(summary.testNames().size()).isEqualTo(3);
        assertThat(summary.testNames()).containsExactlyInAnyOrder("passes_a", "fails_b", "passes_c");
        assertThat(summary.testFailedNames()).as("the failing test is recorded by name").containsExactly("fails_b");
        assertThat(summary.testNames()).as("every failing-test name is also in the full set").containsAll(summary.testFailedNames());
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Strict per-test soundness gate (Defect 2): every [task]-bound test the SOLUTION passes must FAIL on the template.
    // A graded test the template accidentally satisfies (e.g. fibonacci(0)==0 for a `return 0` stub, or pop()->undefined
    // for an empty-stack assertion) hands the student a free point and must be rejected even if the count gate passes.
    // ----------------------------------------------------------------------------------------------------------------

    @Test
    void shouldAcceptWhenEveryTaskBoundTestFailsOnTemplate() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names));
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectWhenTaskBoundTestPassesOnTemplateRustFibonacciZero() {
        // Rust: template `fibonacci(_n)->0` makes test_fibonacci_of_0 (asserts fib(0)==0) PASS on the template, the other six fail. The count gate is satisfied, but the strict
        // per-test gate rejects: a graded test must not earn the student a free point.
        List<String> all = List.of("test_factorial_of_0", "test_factorial_of_5", "test_factorial_of_20", "test_fibonacci_of_0", "test_fibonacci_of_1", "test_fibonacci_of_10",
                "test_fibonacci_of_50");
        List<String> failed2 = new ArrayList<>(
                List.of("test_factorial_of_5", "test_factorial_of_20", "test_fibonacci_of_1", "test_fibonacci_of_10", "test_fibonacci_of_50", "test_factorial_of_0"));
        String ps = "# Factorial & Fibonacci\n[task][Factorial of 0](test_factorial_of_0)\n[task][Fibonacci of 0](test_fibonacci_of_0)\n[task][Fibonacci of 10](test_fibonacci_of_10)\n";
        VerificationResult result = verify(resultWithFails(7, 0, all, List.of()), resultWithFails(7, 1, all, failed2), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("test_fibonacci_of_0"));
    }

    @Test
    void shouldRejectWhenTaskBoundTestPassesOnTemplateTsPopUndefined() {
        List<String> all = List.of("Stack_push_pop_cycle", "Stack_peek_returns_top_without_removal", "Stack_isEmpty_behaviour", "Stack_pop_on_empty_returns_undefined");
        List<String> failedOnTemplate = List.of("Stack_push_pop_cycle", "Stack_peek_returns_top_without_removal", "Stack_isEmpty_behaviour");
        String ps = "# Stack\n[task][Push/pop](Stack_push_pop_cycle)\n[task][Pop on empty returns undefined](Stack_pop_on_empty_returns_undefined)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("Stack_pop_on_empty_returns_undefined"));
    }

    @Test
    void shouldAcceptWhenUnboundHarnessTestPassesOnTemplateButAllTaskBoundTestsFail() {
        // C++-style: a non-gradable harness testcase (CompileStack) legitimately passes on BOTH solution and template (the template compiles); it is NOT [task]-bound. Every
        // [task]-bound behavioural test fails on the template. The gate must look ONLY at [task]-bound names plus exempt the build gate, so this is accepted.
        List<String> all = List.of("CompileStack", "stack_initially_empty", "stack_push_top", "stack_pop");
        List<String> failedOnTemplate = List.of("stack_initially_empty", "stack_push_top", "stack_pop");
        String ps = "# Stack\n[task][Initially empty](stack_initially_empty)\n[task][Push/top](stack_push_top)\n[task][Pop](stack_pop)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).isTrue();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Gap 1 — production-parity gate: production grades EVERY discovered test at default weight, not only the
    // [task]-bound subset. So an UNBOUND behaviour test (or a too-lucky placeholder) that passes on the template makes a
    // bare-template student score above 0%. The sandbox oracle must reject it; a build/compile/configure gate that passes
    // on both (the template compiles by design) is the only legitimate exception.
    // ----------------------------------------------------------------------------------------------------------------

    @Test
    void shouldRejectWhenUnboundBehaviourTestPassesOnTemplate() {
        // The Python-at-22.2% scenario: the agent wrote four behaviour tests but bound only two as [task]s. The template's placeholder accidentally passes the UNBOUND
        // reverse_empty_string test. The [task]-only gate is satisfied, but production grades the unbound test too, so the bare template would score >0%. Must REJECT.
        List<String> all = List.of("reverse_non_empty", "reverse_empty_string", "is_palindrome_true", "is_palindrome_false");
        List<String> failedOnTemplate = List.of("reverse_non_empty", "is_palindrome_true", "is_palindrome_false");
        String ps = "# Strings\n[task][Reverse](reverse_non_empty)\n[task][Palindrome](is_palindrome_true)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("an unbound test passing on the template would give a bare-template student >0% in production").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("reverse_empty_string"));
    }

    @Test
    void shouldAcceptWhenOnlyBuildConfigureGatePassesOnTemplateAndEveryBehaviourTestFails() {
        List<String> all = List.of("TestConfigure", "CompileSort", "sorts_ascending", "sorts_with_duplicates", "stable_on_equal_keys");
        List<String> failedOnTemplate = List.of("sorts_ascending", "sorts_with_duplicates", "stable_on_equal_keys");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\n[task][Duplicates](sorts_with_duplicates)\n[task][Stable](stable_on_equal_keys)\n";
        VerificationResult result = verify(resultWithFails(5, 0, all, List.of()), resultWithFails(5, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("build/configure gates legitimately pass on both; every behaviour test fails on the template").isTrue();
    }

    @Test
    void shouldAcceptWhenFrameworkPrefixedBuildGatePassesOnTemplate() {
        // The REAL C++ harness reports the build gates WITH the framework suite prefix; the gate word is the final dot-segment. Behaviour tests carry the same suite prefix and
        // must
        // STILL be required to fail on the template. With a multi-suite report (GBS-Tester / sort-test top-level suites), production composes "<suite>.<testcase>" names.
        List<String> all = List.of("GBS-Tester-1.36.TestConfigure", "GBS-Tester-1.36.CompileSort", "sort-test.empty_initial", "sort-test.push_top");
        List<String> failedOnTemplate = List.of("sort-test.empty_initial", "sort-test.push_top");
        String ps = "# Stack\n[task][Empty](sort-test.empty_initial)\n[task][Push/top](sort-test.push_top)\n";
        VerificationResult result = verify(multiSuiteSolution(all), multiSuiteTemplate(all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("a framework-prefixed build gate (GBS-Tester-1.36.TestConfigure/CompileSort) is exempted by its final segment").isTrue();
    }

    @Test
    void shouldRejectWhenUnboundBehaviourPassesEvenThoughABuildGateAlsoPasses() {
        List<String> all = List.of("CompileSort", "push_grows", "peek_returns_top");
        List<String> failedOnTemplate = List.of("push_grows");
        String ps = "# Stack\n[task][Push](push_grows)\n";
        VerificationResult result = verify(resultWithFails(3, 0, all, List.of()), resultWithFails(3, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("peek_returns_top"));
    }

    @Test
    void rejects_whenABoundStructuralTestPassesOnTemplate_rubyStackStructure() {
        // A structural test that only checks method existence/arity passes on the template; because it IS [task]-bound, the strict per-test gate flags it as a free graded point.
        List<String> all = List.of("test_new_stack_is_empty", "test_push_makes_not_empty", "test_peek_returns_last_without_removing", "test_pop_returns_last_and_removes",
                "test_pop_on_empty_raises", "test_peek_on_empty_raises", "test_stack_structure");
        List<String> behaviouralFailOnTemplate = List.of("test_new_stack_is_empty", "test_push_makes_not_empty", "test_peek_returns_last_without_removing",
                "test_pop_returns_last_and_removes", "test_pop_on_empty_raises", "test_peek_on_empty_raises");
        String ps = "# Stack\n[task][Empty](test_new_stack_is_empty)\n[task][Structure](test_stack_structure)\n";
        VerificationResult result = verify(resultWithFails(7, 0, all, List.of()), resultWithFails(7, 1, all, behaviouralFailOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("test_stack_structure"));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Skipped-test parity (formerly Defect D1): production's TestResultXmlParser drops a <testcase><skipped/></testcase>
    // from BOTH the successful and failed lists. The verifier now uses that SAME parser, so skipped-test parity is
    // guaranteed by construction. The dangerous case — a test SKIPPED on the solution but FAILING on the template —
    // makes the solution run fewer tests than the template, tripping the "different number of tests" gate.
    // ----------------------------------------------------------------------------------------------------------------

    @Test
    void shouldRejectWhenATestSkippedOnSolutionFailsOnTemplate() {
        // The solution skips peek_does_not_remove (2 executed tests); the template runs it as a real failing test (3 tests). The production parser drops the skipped case on the
        // solution, so solution.tests()=2 != template.tests()=3 -> rejection, exactly as the divergence demands.
        BuildReportSpec solution = skippedSolution();
        BuildReportSpec template = resultWithFails(3, 1, List.of("push_then_pop", "size_tracks_elements", "peek_does_not_remove"),
                List.of("push_then_pop", "size_tracks_elements", "peek_does_not_remove"));
        String ps = "# Stack\n[task][Push/Pop](push_then_pop)\n[task][Size](size_tracks_elements)\n";
        VerificationResult result = verify(solution, template, ps);
        assertThat(result.testCount()).as("the skipped solution test is not counted by the production parser").isEqualTo(2);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("different number of tests"));
    }

    /** A solution build whose report has two passing tests and one {@code <skipped/>} test; the production parser drops the skipped case from both lists. */
    private static BuildReportSpec skippedSolution() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="0" errors="0" skipped="1">
                  <testcase name="push_then_pop" classname="StackTest"/>
                  <testcase name="size_tracks_elements" classname="StackTest"/>
                  <testcase name="peek_does_not_remove" classname="StackTest"><skipped/></testcase>
                </testsuite>
                """;
        return BuildReportSpec.withJunitXml(xml, 0);
    }

    /** A multi-top-level-suite solution report so production composes {@code <suite>.<testcase>} names (used for the framework-prefixed build-gate test). */
    private static BuildReportSpec multiSuiteSolution(List<String> dotPrefixedNames) {
        return multiSuiteSpec(dotPrefixedNames, List.of());
    }

    private static BuildReportSpec multiSuiteTemplate(List<String> dotPrefixedNames, List<String> failedDotPrefixed) {
        return multiSuiteSpec(dotPrefixedNames, failedDotPrefixed);
    }

    /**
     * Builds a {@code <testsuites>} report whose top-level suites are the part of each name before the LAST dot, so production's name composition yields exactly the given
     * dot-prefixed names (multiple top-level suites each contribute their name as a prefix).
     */
    private static BuildReportSpec multiSuiteSpec(List<String> dotPrefixedNames, List<String> failedDotPrefixed) {
        java.util.LinkedHashMap<String, List<String[]>> bySuite = new java.util.LinkedHashMap<>();
        for (String full : dotPrefixedNames) {
            int lastDot = full.lastIndexOf('.');
            String suite = full.substring(0, lastDot);
            String testName = full.substring(lastDot + 1);
            bySuite.computeIfAbsent(suite, k -> new ArrayList<>()).add(new String[] { testName, Boolean.toString(failedDotPrefixed.contains(full)) });
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n");
        for (var suiteEntry : bySuite.entrySet()) {
            sb.append("  <testsuite name=\"").append(suiteEntry.getKey()).append("\">\n");
            for (String[] tc : suiteEntry.getValue()) {
                sb.append("    <testcase name=\"").append(tc[0]).append("\"");
                if (Boolean.parseBoolean(tc[1])) {
                    sb.append("><failure message=\"x\"/></testcase>\n");
                }
                else {
                    sb.append("/>\n");
                }
            }
            sb.append("  </testsuite>\n");
        }
        sb.append("</testsuites>\n");
        return BuildReportSpec.withJunitXml(sb.toString(), failedDotPrefixed.isEmpty() ? 0 : 1);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Hardened copyOut consumer (integration level): when the reports tar is rejected by the hardened reader (a
    // symlinked/escaping/oversize entry), the build is treated as having no tests (fail-closed) so the differential
    // rejects rather than trusting partial input. The precise per-shape rejections are covered by CollectedReportsTest.
    // ----------------------------------------------------------------------------------------------------------------

    @Test
    void shouldRejectWhenTheReportsArchiveContainsASymlinkedEntry() {
        // The solution copyOut returns a tar with a symlinked report entry; the hardened reader rejects the whole archive, so the solution is seen to run no tests and the verdict
        // is a rejection. (Without the hardening a planted symlink could redirect the verifier to read an out-of-tree file.)
        TarArchiveInputStream tamperedSolution = symlinkedReportsTar("solution");
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        BuildReportSpec template = resultWithFails(2, 1, names, names);
        InteractiveSandbox sandbox = new InteractiveSandbox() {

            @Override
            public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
                if ("cat".equals(command[0])) {
                    return new SandboxExecResult(0, PROBLEM_STATEMENT_WITH_TASK, "", false);
                }
                return new SandboxExecResult(0, "", "", false);
            }

            @Override
            public String createSession(SandboxSessionSpec spec) {
                return "s";
            }

            @Override
            public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
            }

            @Override
            public TarArchiveInputStream copyOut(String sessionId, String path) {
                return path.endsWith("/solution") ? tamperedSolution : template.reportsTar("template");
            }

            @Override
            public void destroySession(String sessionId) {
            }
        };
        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise());
        assertThat(result.accepted()).as("a reports archive with a symlinked entry must be rejected, not parsed").isFalse();
        assertThat(result.testCount()).isZero();
    }

    /** A reports tar carrying a single symlinked entry under the given assignment prefix. */
    private static TarArchiveInputStream symlinkedReportsTar(String prefix) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry link = new TarArchiveEntry(prefix + "/0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                    TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("/etc/passwd");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    /**
     * The static-code-analysis parity gate (Defect D2), now driven through the NEW flow: the verifier collects the SCA report files, parses them with the production
     * {@link de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser} (so each finding carries the REAL derived category, including SARIF/GCC), and {@link ScaPenaltyParity}
     * flags those production would penalise. When SCA cannot dock the solution, the gate stays silent and the verdict is unchanged.
     */
    @Nested
    class StaticCodeAnalysisParityGate {

        private static final String SPOTBUGS_STYLE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <BugCollection version="4.7.3">
                  <Project><SrcDir>src</SrcDir></Project>
                  <BugInstance type="DM_DEFAULT_ENCODING" priority="2" category="STYLE"><SourceLine sourcepath="de/test/Stack.java" start="12" end="12"/></BugInstance>
                </BugCollection>
                """;

        /** A solution build that passes its two bound tests AND ships the given SCA reports (keyed by canonical name); paired with a normal failing template. */
        private BuildReportSpec solutionWithScaReports(Map<String, String> scaReports) {
            return BuildReportSpec.withScaReports(List.of(DEFAULT_BOUND_NAMES), List.of(), scaReports, 0);
        }

        private BuildReportSpec failingTemplate() {
            List<String> names = List.of(DEFAULT_BOUND_NAMES);
            return resultWithFails(2, 1, names, names);
        }

        private StaticCodeAnalysisCategory category(String name, CategoryState state, double penalty) {
            var c = new StaticCodeAnalysisCategory();
            c.setName(name);
            c.setState(state);
            c.setPenalty(penalty);
            c.setMaxPenalty(penalty * 10);
            return c;
        }

        private VerificationResult verifyScaExercise(Integer maxPenalty, Boolean scaEnabled, Set<StaticCodeAnalysisCategory> categories, BuildReportSpec solution,
                BuildReportSpec template) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(4242L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setStaticCodeAnalysisEnabled(scaEnabled);
            exercise.setMaxStaticCodeAnalysisPenalty(maxPenalty);

            var repo = mock(StaticCodeAnalysisCategoryRepository.class);
            when(repo.findByExerciseId(4242L)).thenReturn(categories);
            var verifier = new AuthoritativeVerificationService(sandboxBuildCommandService(), Optional.of(repo));
            return verifier.verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", exercise);
        }

        @Test
        void shouldRejectWhenScaEnabledAndSolutionHasGradedViolation() {
            // "Code Style" is GRADED with a positive penalty; the SpotBugs STYLE finding maps to it (StaticCodeAnalysisConfigurer Java default), so production would dock the
            // score.
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate());
            assertThat(result.accepted()).as("a solution with a graded SCA violation must be rejected").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("static-code-analysis findings that production would penalise"));
        }

        @Test
        void shouldAcceptWhenScaEnabledButSolutionIsScaClean() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            // No SCA reports collected: the solution build produced no findings.
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaReports(Map.of()), failingTemplate());
            assertThat(result.accepted()).as("a clean solution is still accepted when SCA is graded").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenScaEnabledButCategoryRepositoryIsAbsent_failingOpenOnABuildAgentOnlyNode() {
            // On a build-agent-only node the SCA category repository is not injected (Optional.empty()). The SCA parity gate must FAIL OPEN — accept — rather than crash or
            // spuriously reject, even when the solution ships a graded-looking finding: without the categories the verifier cannot know what production grades, so it defers (the
            // integrated node, which HAS the repository, is where SCA parity is enforced). This is the real configuration the present-repo tests above never exercise.
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(909L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setStaticCodeAnalysisEnabled(true);
            exercise.setMaxStaticCodeAnalysisPenalty(50);
            // newVerifier() builds with Optional.empty() SCA repository — the build-agent-only configuration.
            VerificationResult result = newVerifier()
                    .verify(new ScriptedSandbox(solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate(), PROBLEM_STATEMENT_WITH_TASK), "s", exercise);
            assertThat(result.accepted()).as("the SCA gate fails open when the category repository is absent").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenScaDisabledEvenIfFindingsLeakIntoOutput() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(50, false, categories, solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate());
            assertThat(result.accepted()).as("SCA disabled => no SCA rejection").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenMaxPenaltyIsZeroSoScaCannotAffectTheScore() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(0, true, categories, solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate());
            assertThat(result.accepted()).as("maxStaticCodeAnalysisPenalty == 0 => SCA penalty is disabled => no rejection").isTrue();
        }

        @Test
        void shouldAcceptWhenFindingIsInANonGradedCategory() {
            // The finding maps to "Code Style"; but only "Security" is GRADED here. Production would not penalise a Code Style finding, so the oracle must not reject.
            var categories = Set.of(category("Code Style", CategoryState.FEEDBACK, 0.2), category("Security", CategoryState.GRADED, 2.5));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate());
            assertThat(result.accepted()).as("a finding in a non-graded category must not be rejected").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenGradedCategoryHasZeroPenalty() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.0));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate());
            assertThat(result.accepted()).as("a graded category with zero penalty deducts nothing => no rejection").isTrue();
        }

        @Test
        void shouldNotPenaliseWhenSarifFindingIsInANonGradedCategory() {
            // SARIF (ruff) category derivation is now done by the PRODUCTION categorizer (the old <tool>|* sentinel would have conservatively over-rejected). A ruff finding whose
            // derived category is NOT the graded one must NOT penalise, proving the real category is used. We grade a category that ruff's finding does not map to.
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(7L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
            exercise.setStaticCodeAnalysisEnabled(true);
            exercise.setMaxStaticCodeAnalysisPenalty(50);
            // Grade a category that the ruff finding's derived category will not match (so a correctly-derived category means no penalty).
            var graded = category("Security", CategoryState.GRADED, 2.0);
            var repo = mock(StaticCodeAnalysisCategoryRepository.class);
            when(repo.findByExerciseId(7L)).thenReturn(Set.of(graded));
            var verifier = new AuthoritativeVerificationService(sandboxBuildCommandService(), Optional.of(repo));
            BuildReportSpec solution = BuildReportSpec.withScaReports(List.of(DEFAULT_BOUND_NAMES), List.of(), Map.of("ruff.sarif", RUFF_STYLE_SARIF), 0);
            VerificationResult result = verifier.verify(new ScriptedSandbox(solution, failingTemplate(), PROBLEM_STATEMENT_WITH_TASK), "s", exercise);
            assertThat(result.accepted()).as("a SARIF finding in a non-graded derived category must not penalise (production category derivation, not <tool>|*)").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        /** A minimal ruff SARIF report with a single style ({@code E501}) finding; the production SARIF categorizer derives a concrete category, not the old {@code *} sentinel. */
        private static final String RUFF_STYLE_SARIF = """
                {
                  "version": "2.1.0",
                  "runs": [
                    {
                      "tool": { "driver": { "name": "ruff", "rules": [ { "id": "E501" } ] } },
                      "results": [
                        { "ruleId": "E501", "level": "warning", "message": { "text": "line too long" },
                          "locations": [ { "physicalLocation": { "artifactLocation": { "uri": "main.py" }, "region": { "startLine": 1 } } } ] }
                      ]
                    }
                  ]
                }
                """;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // W1 — auto-seeded structural-test binding exemption. A structural-SHAPED binding must NOT be required to resolve,
    // while the differential (solution passes / template fails) stays fully enforced for every REAL test regardless of
    // its name shape — so the exemption cannot be abused to evade grading on a real behaviour test named structurally.
    // ----------------------------------------------------------------------------------------------------------------

    @Nested
    class StructuralBindingExemption {

        @ParameterizedTest(name = "{0}")
        @MethodSource("structuralBindingAcceptCases")
        void shouldAcceptWhenStructuralBindingDoesNotResolveButDifferentialHolds(String caseName, String problemStatement, List<String> allNames, Set<String> seededStructural) {
            VerificationResult result = verifyWithSeededStructural(resultWithFails(allNames.size(), 0, allNames, List.of()),
                    resultWithFails(allNames.size(), 1, allNames, allNames), problemStatement, seededStructural);
            assertThat(result.reasons()).as("a structural-shaped binding must not be reported as unresolved").noneMatch(r -> r.contains("match no actual test"));
            assertThat(result.accepted()).as("a structural binding/test must not block acceptance while the differential holds").isTrue();
        }

        private static Stream<Arguments> structuralBindingAcceptCases() {
            List<String> behaviourPlusStructural = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]", "testMethods[Sorter]");
            return Stream.of(Arguments.of("unbound auto-seeded structural tests", PROBLEM_STATEMENT_WITH_TASK, behaviourPlusStructural, Set.of()),
                    Arguments.of("structural binding resolves to nothing because the seeder declined",
                            "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Helper structure](testClass[Helper],testMethods[Helper])\n",
                            List.of("sortsUnsortedArray", "sortsArrayWithDuplicates"), Set.of()),
                    Arguments.of("structural binding resolves via the authoritative seeded set",
                            "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Create Sorter](testClass[Sorter],testMethods[Sorter])\n",
                            behaviourPlusStructural, Set.of("testClass[Sorter]", "testMethods[Sorter]")));
        }

        @Test
        void stillRejects_whenAREALBehaviourTestIsLeftUnboundAndDanglingBinding() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Mystery](aDisplayNameNotAMethodName)\n";
            VerificationResult result = verify(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, all), ps);
            assertThat(result.accepted()).isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test") && r.contains("aDisplayNameNotAMethodName"));
        }

        @Test
        void forgeryResistance_aRealBehaviourTestNamedStructurallyThatPassesOnTemplate_isStillRejected() {
            // The agent names a REAL behaviour test testClass[Evil] hoping the W1 exemption lets it dodge the differential. It does NOT: the exemption only relaxes binding
            // resolution. testClass[Evil] PASSES on the template, so the production-parity gate (keyed on REAL test names) rejects it.
            List<String> all = List.of("realBehaviour", "testClass[Evil]");
            List<String> failedOnTemplate = List.of("realBehaviour");
            String ps = "# X\n[task][Real](realBehaviour)\n[task][Disguised](testClass[Evil])\n";
            VerificationResult result = verify(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, failedOnTemplate), ps);
            assertThat(result.accepted()).as("a structurally-named real test that passes on the template must still be rejected by the differential").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("testClass[Evil]"));
        }

        @Test
        void differentialStillEnforced_whenAnAutoSeededStructuralTestPassesOnTemplate() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]");
            List<String> failedOnTemplate = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            VerificationResult result = verifyWithSeededStructural(resultWithFails(3, 0, all, List.of()), resultWithFails(3, 1, all, failedOnTemplate), PROBLEM_STATEMENT_WITH_TASK,
                    Set.of("testClass[Sorter]"));
            assertThat(result.accepted()).as("a seeded structural test that passes on the template is a free point and must be rejected").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("testClass[Sorter]"));
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // In-loop self-check (the agent's `verify` tool). It shares the SAME differential + actionable gates as the
    // post-loop verify(...) — proven by running both against the same scripted sandbox — and renders structured,
    // agent-readable feedback: which tests pass/fail on the solution and template, the EXACT parser-form names to bind
    // [task]s to (fixes the C++/Catch2 bare-name trap), the tests that wrongly pass on the template (fixes the Go
    // zero-value-stub trap), the unresolved [task] bindings, and the would-be verdict.
    // ----------------------------------------------------------------------------------------------------------------

    @Nested
    class InLoopSelfCheck {

        @Test
        void reportsAcceptedWhenSolutionPassesAndTemplateFailsSameTests() {
            List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.wouldBeAccepted()).isTrue();
            assertThat(report.solutionPassed()).isTrue();
            assertThat(report.solutionTests()).isEqualTo(2);
            assertThat(report.templateCompiled()).isTrue();
            assertThat(report.templateWronglyPassing()).isEmpty();
            assertThat(report.toObservation()).contains("Solution: 2/2 tests pass.").contains("Template: correctly fails all 2.").contains("VERDICT: would be ACCEPTED");
        }

        @Test
        void returnsTheExactParserFormTestNamesToBind() {
            // The C++/Catch2 fix: with multiple top-level suites production composes the suite-prefixed <suite>.<case> names, and the agent must bind those VERBATIM rather than
            // the
            // bare case names it would otherwise grep. The self-check surfaces exactly the parser-form names, so the agent never has to derive the prefix itself.
            List<String> all = List.of("sort-test.stack_empty_initially", "size-test.size_tracks_elements");
            AgentVerifyReport report = selfCheck(multiSuiteSolution(all), multiSuiteTemplate(all, all),
                    "# Stack\n[task][Empty](sort-test.stack_empty_initially)\n[task][Size](size-test.size_tracks_elements)\n");
            assertThat(report.exactTestNames()).containsExactlyInAnyOrderElementsOf(all);
            assertThat(report.wouldBeAccepted()).isTrue();
            assertThat(report.toObservation()).contains("bind each [task] to one of these VERBATIM").contains("sort-test.stack_empty_initially");
        }

        @Test
        void reportsTemplateTestsThatWronglyPass() {
            // The Go/no-exception fix: a `return ""`/`false`/`0` stub passes a test that expects exactly that. The self-check must NAME the wrongly-passing test so the agent fixes
            // the stub in-loop, rather than only learning post-loop that "template-bound tests pass".
            List<String> all = List.of("returns_empty_for_empty_input", "reverses_non_empty");
            List<String> failedOnTemplate = List.of("reverses_non_empty");
            AgentVerifyReport report = selfCheck(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, failedOnTemplate),
                    "# Reverse\n[task][Empty](returns_empty_for_empty_input)\n[task][Non-empty](reverses_non_empty)\n");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.templateWronglyPassing()).containsExactly("returns_empty_for_empty_input");
            assertThat(report.toObservation()).contains("Template WRONGLY PASSES").contains("returns_empty_for_empty_input").contains("VERDICT: NOT YET");
        }

        @Test
        void reportsSolutionFailuresByName() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(2, 1, all, List.of("sortsArrayWithDuplicates")), resultWithFails(2, 1, all, all), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.solutionPassed()).isFalse();
            assertThat(report.solutionFailedNames()).contains("sortsArrayWithDuplicates");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("Solution FAILS").contains("sortsArrayWithDuplicates").contains("must pass every test");
        }

        @Test
        void flagsTaskBindingsThatReferenceNoRealTest() {
            // The agent bound a display name; the self-check must list it as a binding problem so the agent copies a real name from `exactTestNames` instead.
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Mystery](aDisplayNameNotAMethodName)\n";
            AgentVerifyReport report = selfCheck(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, all), ps);
            assertThat(report.unresolvedTaskBindings()).contains("aDisplayNameNotAMethodName");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("[task] binding problems").contains("aDisplayNameNotAMethodName");
        }

        @Test
        void reportsTemplateThatDidNotCompile() {
            AgentVerifyReport report = selfCheck(result(5, 0, 0, 0), result(0, 0, 0, 1), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.templateCompiled()).isFalse();
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("Template: did NOT compile");
        }

        @Test
        void doesNotClaimTheTemplateCorrectlyFailsWhenItPassesEveryTest() {
            // A template that compiles but passes EVERY test leaves no failed names, so the wrongly-passing list is empty (it fails open). The observation must NOT then read
            // "correctly fails all N" — it must flag that the template does not fail enough tests, so the agent fixes the stubs instead of misreading an empty list as success.
            List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 0, names, List.of()), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.templateCompiled()).isTrue();
            assertThat(report.templateFailed()).isFalse();
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).doesNotContain("correctly fails all").contains("Template does NOT fail enough tests");
        }

        @Test
        void selfCheckAndPostLoopVerifyAgreeOnTheVerdict() {
            // The contract: the in-loop self-check's wouldBeAccepted matches the post-loop verify's accepted on the same workspace (the integrity gates, which the self-check
            // skips,
            // fail OPEN with no files), so the agent sees exactly what the acceptance gate will conclude.
            List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            BuildReportSpec solution = resultWithFails(2, 0, names, List.of());
            BuildReportSpec template = resultWithFails(2, 1, names, names);
            assertThat(selfCheck(solution, template, PROBLEM_STATEMENT_WITH_TASK).wouldBeAccepted()).isEqualTo(verify(solution, template).accepted()).isTrue();

            BuildReportSpec passingTemplate = resultWithFails(2, 0, names, List.of());
            assertThat(selfCheck(solution, passingTemplate, PROBLEM_STATEMENT_WITH_TASK).wouldBeAccepted()).isEqualTo(verify(solution, passingTemplate).accepted()).isFalse();
        }
    }
}
