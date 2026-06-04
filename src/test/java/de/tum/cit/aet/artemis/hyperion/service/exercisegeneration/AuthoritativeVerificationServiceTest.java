package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * Deterministic unit test for the differential oracle: a fake sandbox returns scripted {@code HYPERION_RESULT} summary lines for the solution and template build commands, so
 * the accept/reject logic is exercised without Docker or a real build. This is the regression guard for the verdict rules; the live build behaviour is covered by the gated
 * end-to-end test.
 */
class AuthoritativeVerificationServiceTest {

    /**
     * A {@link SandboxBuildCommandService} backed by a stub phase service, so the verifier-under-test can render and place a pristine {@code verify.sh} exactly as in production.
     * The
     * rendered script's content is irrelevant to these deterministic tests (the {@link ScriptedSandbox} returns canned marker output regardless of which script runs), but
     * rendering
     * it exercises the real re-seed path.
     */
    private static SandboxBuildCommandService buildCommandFactory() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        return new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
    }

    /**
     * The fixed anti-forgery nonce the test verifier stamps onto the pristine markers. In production this is a fresh, unguessable per-run token; the scripted fixtures below emit
     * markers carrying this exact value so the (nonce-anchored) parser honors them, mirroring what the real pristine {@code verify.sh} prints.
     */
    private static final String TEST_NONCE = "HNtestnonce0123456789abcdef0123456789";

    private static AuthoritativeVerificationService newVerifier() {
        return new AuthoritativeVerificationService(buildCommandFactory(), () -> TEST_NONCE);
    }

    /**
     * A sandbox whose exec() returns a canned result depending on the command: the problem-statement read, or the PRISTINE solution / template build. The verifier re-seeds and
     * runs
     * {@code sh /opt/hyperion/verify.sh <assignment>}; that invocation still contains "solution"/"template", so the dispatch is unchanged. The verifier's {@code mkdir} and
     * {@code copyIn} of the pristine script are no-ops here (the {@code mkdir} exec matches neither "cat" nor an assignment build and its result is discarded).
     */
    private static final class ScriptedSandbox implements InteractiveSandbox {

        private final SandboxExecResult solution;

        private final SandboxExecResult template;

        private final String problemStatement;

        private ScriptedSandbox(SandboxExecResult solution, SandboxExecResult template, String problemStatement) {
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
            // Only the verify-build invocations dispatch to the scripted results; any other exec (the pristine-script mkdir) returns a harmless success.
            if (!joined.contains("verify.sh")) {
                return new SandboxExecResult(0, "", "", false);
            }
            return joined.contains("solution") ? solution : template;
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
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    /** The two graded test names the default {@link #PROBLEM_STATEMENT_WITH_TASK} binds; a complete emission must include them so the [task] bindings resolve. */
    private static final String[] DEFAULT_BOUND_NAMES = { "sortsUnsortedArray", "sortsArrayWithDuplicates" };

    /**
     * A build result with a COMPLETE, self-consistent per-test emission: one {@code HYPERION_TESTNAME} per test (the two default-bound names first, then fillers up to
     * {@code tests}), and — when the run has failures/errors — one {@code HYPERION_TESTFAIL} per test (so a failing template fails EVERY graded test, satisfying the strict
     * per-test
     * gate). This mirrors what the pristine {@code verify.sh} always emits, so the now-fail-CLOSED emitter-soundness rules (a complete name list per test, and a failing-name list
     * for a failing run) are satisfied by every genuinely-good fixture. Tests that deliberately emit an INCOMPLETE set use {@link #resultWithNames}/{@link #resultWithFails}.
     */
    private static SandboxExecResult result(int tests, int failures, int errors, int exit) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < tests; i++) {
            names.add(i < DEFAULT_BOUND_NAMES.length ? DEFAULT_BOUND_NAMES[i] : "hyperionTest" + i);
        }
        // A run with any failure/error is modelled as failing EVERY test (the canonical failing template), so all bound tests fail on it and the per-test soundness gate is
        // satisfied.
        List<String> failed = (failures + errors) > 0 ? names : List.of();
        return resultWithFailsAndCounts(tests, failures, errors, exit, names, failed);
    }

    /** Builds the emission body with explicit failure/error counters (used by {@link #result}, which models a failing run as failing every test). */
    private static SandboxExecResult resultWithFailsAndCounts(int tests, int failures, int errors, int exit, List<String> allNames, List<String> failedNames) {
        StringBuilder body = new StringBuilder("build log...\n");
        for (String name : allNames) {
            body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        for (String name : failedNames) {
            body.append("HYPERION_TESTFAIL ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=").append(tests).append(" failures=").append(failures).append(" errors=").append(errors)
                .append(" skipped=0 exit=").append(exit).append('\n');
        return new SandboxExecResult(exit, body.toString(), "", false);
    }

    /** Like {@link #result} but also emits the per-test {@code HYPERION_TESTNAME} lines verify.sh prints, so the [task]-binding resolution check is exercised. */
    private static SandboxExecResult resultWithNames(int tests, int failures, int errors, int exit, String... testNames) {
        StringBuilder body = new StringBuilder("build log...\n");
        for (String name : testNames) {
            body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=").append(tests).append(" failures=").append(failures).append(" errors=").append(errors)
                .append(" skipped=0 exit=").append(exit).append('\n');
        return new SandboxExecResult(exit, body.toString(), "", false);
    }

    /**
     * Like {@link #resultWithNames} but also emits, for the named tests in {@code failedNames}, the {@code HYPERION_TESTFAIL} line verify.sh prints for every {@code <testcase>}
     * carrying a {@code <failure>}/{@code <error>}. This drives the strict per-test soundness gate (every [task]-bound test the solution passes must FAIL on the template). The
     * failure/error counters are set from {@code failedNames}.
     */
    private static SandboxExecResult resultWithFails(int tests, int exit, List<String> allNames, List<String> failedNames) {
        StringBuilder body = new StringBuilder("build log...\n");
        for (String name : allNames) {
            body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        for (String name : failedNames) {
            body.append("HYPERION_TESTFAIL ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=").append(tests).append(" failures=").append(failedNames.size()).append(" errors=0 skipped=0 exit=")
                .append(exit).append('\n');
        return new SandboxExecResult(exit, body.toString(), "", false);
    }

    private static final String PROBLEM_STATEMENT_WITH_TASK = "# Sort\n[task][Sort an array](sortsUnsortedArray,sortsArrayWithDuplicates)\n";

    private static VerificationResult verify(SandboxExecResult solution, SandboxExecResult template) {
        return verify(solution, template, PROBLEM_STATEMENT_WITH_TASK);
    }

    private static VerificationResult verify(SandboxExecResult solution, SandboxExecResult template, String problemStatement) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise());
    }

    /** Runs the 9-arg verify with the authoritative auto-seeded structural test names, so the W1 structural-binding exemption is exercised with (and without) that set. */
    private static VerificationResult verifyWithSeededStructural(SandboxExecResult solution, SandboxExecResult template, String problemStatement, Set<String> seededStructural) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(),
                seededStructural);
    }

    /**
     * A sandbox returning DIFFERENT output for the agent's {@code /workspace/verify.sh} (a forged stub) versus the verifier-owned {@code /opt/hyperion/verify.sh} (the pristine
     * script), and recording every exec so a test can assert the verifier ran the PRISTINE path and never the agent's copy.
     */
    private static final class PathDispatchingSandbox implements InteractiveSandbox {

        private final SandboxExecResult forgedSolution;

        private final SandboxExecResult forgedTemplate;

        private final SandboxExecResult pristineSolution;

        private final SandboxExecResult pristineTemplate;

        private final String problemStatement;

        private final List<String> execCommands = new ArrayList<>();

        private PathDispatchingSandbox(SandboxExecResult forgedSolution, SandboxExecResult forgedTemplate, SandboxExecResult pristineSolution, SandboxExecResult pristineTemplate,
                String problemStatement) {
            this.forgedSolution = forgedSolution;
            this.forgedTemplate = forgedTemplate;
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
            boolean pristine = joined.contains(SandboxBuildCommandService.PRISTINE_VERIFY_PATH);
            boolean workspace = joined.contains("/workspace/verify.sh");
            if (!pristine && !workspace) {
                // The pristine-script mkdir, or any other non-build command.
                return new SandboxExecResult(0, "", "", false);
            }
            // The invocation is "sh <path> solution '<nonce>'" / "... template '<nonce>'"; match the assignment token regardless of the trailing nonce argument.
            boolean solution = joined.contains(" solution");
            if (pristine) {
                return solution ? pristineSolution : pristineTemplate;
            }
            return solution ? forgedSolution : forgedTemplate;
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
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    @Test
    void shouldRejectWhenForgedWorkspaceScriptEchoesPerfectMarkersButPristineFindsNoTests() {
        // The agent forged /workspace/verify.sh to echo a perfect passing-solution/failing-template marker set with no real tests; the pristine script the verifier runs finds no
        // reports (tests=0 both runs), so the verdict must be a rejection graded by the pristine script.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "t3", "t4", "t5");
        SandboxExecResult forgedSolution = resultWithFails(5, 0, names, List.of());
        SandboxExecResult forgedTemplate = resultWithFails(5, 1, names, names);
        SandboxExecResult pristineSolution = result(0, 0, 0, 0);
        SandboxExecResult pristineTemplate = result(0, 0, 0, 0);
        PathDispatchingSandbox sandbox = new PathDispatchingSandbox(forgedSolution, forgedTemplate, pristineSolution, pristineTemplate, PROBLEM_STATEMENT_WITH_TASK);

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise());

        assertThat(result.accepted()).as("a forged /workspace/verify.sh must not produce an accepted verdict").isFalse();
        assertThat(result.testCount()).isZero();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No tests were detected"));
        // Prove the verifier ran the PRISTINE script and never the agent's forged /workspace copy.
        assertThat(sandbox.execCommands).anyMatch(c -> c.contains(SandboxBuildCommandService.PRISTINE_VERIFY_PATH + " solution"));
        assertThat(sandbox.execCommands).noneMatch(c -> c.contains("/workspace/verify.sh"));
    }

    @Test
    void shouldAcceptViaPristineScriptWhenTheRealBuildIsGood() {
        // The mirror: when the pristine script reports a genuinely-good build, the verdict is accepted, so the re-seed path does not break the happy case.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        PathDispatchingSandbox sandbox = new PathDispatchingSandbox(result(0, 0, 0, 0), result(0, 0, 0, 0), resultWithFails(2, 0, names, List.of()),
                resultWithFails(2, 1, names, names), PROBLEM_STATEMENT_WITH_TASK);
        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise());
        assertThat(result.accepted()).isTrue();
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
     * copy the verbatim {@code HYPERION_TESTNAME} name into the {@code [task]} binding. With the old {@code [^)]*} capture the binding {@code (testBubbleSort())} was truncated at
     * the first {@code )} to {@code testBubbleSort(}, resolved against no real test, and the exercise was wrongly rejected as having an unresolved binding. The paren-aware capture
     * plus the {@code ()}-stripping normalisation must now resolve the paren form against the {@code testBubbleSort()} the runner reported, so the exercise is accepted.
     */
    @Test
    void shouldAcceptWhenTaskBindingTestNamesCarryParentheses() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort(),testMergeSort())\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        SandboxExecResult solution = resultWithFails(2, 0, names, List.of());
        SandboxExecResult template = resultWithFails(2, 1, names, names);
        VerificationResult result = verify(solution, template, problemStatement);
        assertThat(result.reasons()).as("paren-bearing [task] bindings must resolve, not be flagged as unresolved").noneMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.accepted()).isTrue();
        assertThat(result.testCount()).isEqualTo(2);
    }

    /** The no-parentheses binding form (the prompt example: {@code (sortsAscendingArray)}) must keep resolving against a {@code sortsAscendingArray()} the runner reported. */
    @Test
    void shouldAcceptWhenTaskBindingOmitsParenthesesButTestNameHasThem() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort,testMergeSort)\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        SandboxExecResult solution = resultWithFails(2, 0, names, List.of());
        SandboxExecResult template = resultWithFails(2, 1, names, names);
        VerificationResult result = verify(solution, template, problemStatement);
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.accepted()).isTrue();
    }

    /** Runs the full 8-arg verify with the integrity-gate inputs, so the harness-immutability and solution-leak gates are exercised alongside the differential oracle. */
    private static VerificationResult verifyWithFiles(SandboxExecResult solution, SandboxExecResult template, Map<String, String> seedTests, Map<String, String> producedTests,
            Map<String, String> producedTemplate, Map<String, String> producedSolution) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", new ProgrammingExercise(), seedTests, producedTests,
                producedTemplate, producedSolution, Set.of());
    }

    private static final String SOLUTION_BODY = "module Exercise (factorial) where\n\nfactorial :: Integer -> Integer\nfactorial 0 = 1\nfactorial n = n * factorial (n - 1)\n";

    private static final String SEED_CABAL = "library solution\n  hs-source-dirs: ${solutionWorkingDirectory}/src\n  exposed-modules: Exercise\n";

    @Test
    void integrityGates_acceptWhenHarnessUnchangedAndNoLeak() {
        // A genuinely-good exercise: the build oracle passes, the harness equals the seed (modulo the placeholder substitution the pipeline applies), and the template is a stub.
        var seedTests = Map.of("test.cabal", SEED_CABAL);
        var producedTests = Map.of("test.cabal", SEED_CABAL.replace("${solutionWorkingDirectory}", "assignment"));
        var producedTemplate = Map.of("src/Exercise.hs", "factorial _ = error \"todo: implement factorial here\"\n");
        var producedSolution = Map.of("src/Exercise.hs", SOLUTION_BODY);
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), seedTests, producedTests, producedTemplate, producedSolution);
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void integrityGates_rejectWhenHarnessBuildLayoutTampered() {
        // The Haskell defect: the agent rewrote the solution library's hs-source-dirs to assignment/solution/src — where production does NOT lay the solution out — so the build
        // passes in the sandbox but breaks in CI. The build oracle is happy; the harness gate rejects.
        var seedTests = Map.of("test.cabal", SEED_CABAL);
        var producedTests = Map.of("test.cabal", SEED_CABAL.replace("${solutionWorkingDirectory}/src", "assignment/solution/src"));
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), seedTests, producedTests, Map.of(), Map.of());
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("tests/test.cabal") && r.contains("harness is graded"));
    }

    @Test
    void integrityGates_rejectWhenTemplateLeaksSolutionToANonGradedPath() {
        // The graded src/ holds a proper stub (so the build oracle is happy: template fails), but the agent also copied the solution implementation into a non-graded template file
        // that ships the answer to students. The leak gate rejects.
        var producedTemplate = Map.of("src/Exercise.hs", "factorial _ = error \"todo: implement the factorial function here\"\n", "doc/reference_solution.hs", SOLUTION_BODY);
        var producedSolution = Map.of("src/Exercise.hs", SOLUTION_BODY);
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), Map.of(), Map.of(), producedTemplate, producedSolution);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("template leaks the reference solution"));
    }

    @Test
    void integrityGates_failOpenWhenNoFilesProvided() {
        // The 4-arg verify (and any caller without snapshots) must behave exactly as before: the gates contribute nothing.
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), Map.of(), Map.of(), Map.of(), Map.of());
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldAcceptWhenCatch2TestCaseCountsMatchEvenThoughAssertionCountsDoNot() {
        // Catch2 (C++) regression: verify.sh now reports the test count as the number of <testcase> elements (3 here), so the solution and the failing template carry the SAME
        // count
        // even though Catch2's tests="N" assertion attribute disagreed (14 vs 7) because REQUIRE is fatal and the template aborts each case early. With equal test-case counts and
        // a
        // failing template, the differential oracle accepts. (Before the fix the solution reported tests=14 and the template tests=7, tripping the "different number of tests"
        // gate.)
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
        // failure counts, not the exit code: a template that compiled, ran the same tests, and failed enough of them is valid even with exit=0.
        VerificationResult result = verify(result(14, 0, 0, 0), result(14, 14, 0, 0));
        assertThat(result.accepted()).isTrue();
        assertThat(result.templateFailed()).isTrue();
    }

    @Test
    void shouldAcceptWhenTemplateFailsWithErrors() {
        // A template returning null can make tests error (e.g. NPE) rather than assert-fail; that is still a valid "template does not work".
        VerificationResult result = verify(result(4, 0, 0, 0), result(4, 0, 4, 1));
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectWhenTemplateDoesNotCompile() {
        // No reports written -> tests=0 against the template -> it did not compile.
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
    void shouldRejectWhenBuildHelperEmittedNoSummary() {
        // The script was killed or the workspace was not seeded; no marker -> treated as a failed build with no tests.
        SandboxExecResult noMarker = new SandboxExecResult(137, "killed", "", false);
        VerificationResult result = verify(noMarker, noMarker);
        assertThat(result.accepted()).isFalse();
        assertThat(result.solutionPassed()).isFalse();
    }

    @Test
    void shouldRejectWhenSolutionTimesOut() {
        SandboxExecResult timeout = new SandboxExecResult(124, "", "", true);
        VerificationResult result = verify(timeout, result(5, 5, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("timed out"));
    }

    @Test
    void shouldRejectWhenTemplateFailsTooFewTests() {
        // A template that fails only 1 of 6 tests is nearly complete; it must fail at least half to be a meaningful starting point.
        VerificationResult result = verify(result(6, 0, 0, 0), result(6, 1, 0, 1));
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
        // The default problem statement binds sortsUnsortedArray and sortsArrayWithDuplicates; the build reports exactly those names (with () to exercise normalisation), and the
        // template fails both (the per-test soundness gate needs the failing-name list).
        List<String> names = List.of("sortsUnsortedArray()", "sortsArrayWithDuplicates()");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names));
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectWhenTaskBindingReferencesDisplayNameInsteadOfMethodName() {
        // The classic defect: the [task] names are @DisplayName / prose text, while the real test method names differ — the task would bind to nothing in Artemis.
        String problemStatement = "# Sort\n[task][Sort an unsorted array](Sort an unsorted array)\n[task][Sort with duplicates](Sort with duplicates)\n";
        VerificationResult result = verify(resultWithNames(2, 0, 0, 0, "sortsUnsortedArray", "sortsArrayWithDuplicates"),
                resultWithNames(2, 2, 0, 1, "sortsUnsortedArray", "sortsArrayWithDuplicates"), problemStatement);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test"));
    }

    @Test
    void shouldRejectWhenSolutionRanTestsButEmittedNoTestNames() {
        // Defect B, rule (a), now fail-CLOSED. The verifier runs its OWN pristine verify.sh, which always emits a HYPERION_TESTNAME per <testcase>. A result line claiming tests=2
        // with ZERO names is therefore evidence of a broken/forged emitter (e.g. a stub verify.sh that echoes only the summary), which would silently disable the
        // binding-resolution
        // and per-test-soundness gates. We must REJECT, not skip. (Previously this was fail-open and the exercise was wrongly accepted.)
        VerificationResult result = verify(resultWithNames(2, 0, 0, 1), resultWithNames(2, 2, 0, 1));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("only recorded 0 test name"));
    }

    @Test
    void shouldRejectWhenFewerTestNamesThanTestsAreEmitted() {
        // Defect B, rule (a): tests=2 but only one TESTNAME line. An incomplete name list cannot be trusted to validate the bindings, so the pristine-emitter contract is violated
        // and we REJECT rather than skip the check. (Previously fail-open.)
        String problemStatement = "# Sort\n[task][Sort](onlyKnownTest)\n[task][Other](someOtherTest)\n";
        VerificationResult result = verify(resultWithNames(2, 0, 0, 1, "onlyKnownTest"), resultWithNames(2, 2, 0, 1, "onlyKnownTest"), problemStatement);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("only recorded 1 test name"));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Strict per-test soundness gate (Defect 2): every [task]-bound test the SOLUTION passes must FAIL on the template.
    // A graded test the template accidentally satisfies (e.g. fibonacci(0)==0 for a `return 0` stub, or pop()->undefined
    // for an empty-stack assertion) hands the student a free point and must be rejected even if the count gate passes.
    // ----------------------------------------------------------------------------------------------------------------

    @Test
    void shouldAcceptWhenEveryTaskBoundTestFailsOnTemplate() {
        // The whole suite is [task]-bound; the template reports every one of them failed. Sound -> accepted.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        VerificationResult result = verify(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names));
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectWhenTaskBoundTestPassesOnTemplateRustFibonacciZero() {
        // Rust: template `fibonacci(_n)->0` makes test_fibonacci_of_0 (asserts fib(0)==0) PASS on the template, the other six fail. The count gate ("fail at least half") is
        // satisfied,
        // but the strict per-test gate rejects: a graded test must not earn the student a free point.
        List<String> all = List.of("test_factorial_of_0", "test_factorial_of_5", "test_factorial_of_20", "test_fibonacci_of_0", "test_fibonacci_of_1", "test_fibonacci_of_10",
                "test_fibonacci_of_50");
        List<String> failedOnTemplate = List.of("test_factorial_of_5", "test_factorial_of_20", "test_fibonacci_of_1", "test_fibonacci_of_10", "test_fibonacci_of_50");
        // factorial_of_0 (0!==1, stub returns 0) also fails; include it so only test_fibonacci_of_0 passes on the template.
        List<String> failed2 = new ArrayList<>(failedOnTemplate);
        failed2.add("test_factorial_of_0");
        String ps = "# Factorial & Fibonacci\n[task][Factorial of 0](test_factorial_of_0)\n[task][Fibonacci of 0](test_fibonacci_of_0)\n[task][Fibonacci of 10](test_fibonacci_of_10)\n";
        VerificationResult result = verify(resultWithFails(7, 0, all, List.of()), resultWithFails(7, 1, all, failed2), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("test_fibonacci_of_0"));
    }

    @Test
    void shouldRejectWhenTaskBoundTestPassesOnTemplateTsPopUndefined() {
        // TypeScript: template `pop()->undefined` makes pop_on_empty_returns_undefined PASS on the template; the other three fail. Rejected for the same reason.
        List<String> all = List.of("Stack_push_pop_cycle", "Stack_peek_returns_top_without_removal", "Stack_isEmpty_behaviour", "Stack_pop_on_empty_returns_undefined");
        List<String> failedOnTemplate = List.of("Stack_push_pop_cycle", "Stack_peek_returns_top_without_removal", "Stack_isEmpty_behaviour");
        String ps = "# Stack\n[task][Push/pop](Stack_push_pop_cycle)\n[task][Pop on empty returns undefined](Stack_pop_on_empty_returns_undefined)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("Stack_pop_on_empty_returns_undefined"));
    }

    @Test
    void shouldAcceptWhenUnboundHarnessTestPassesOnTemplateButAllTaskBoundTestsFail() {
        // C++-style: a non-gradable harness testcase (TestConfigure/CompileStack) legitimately passes on BOTH solution and template (the template compiles); it is NOT
        // [task]-bound.
        // Every [task]-bound behavioural test fails on the template. The gate must look ONLY at [task]-bound names, so this is accepted.
        List<String> all = List.of("CompileStack", "stack_initially_empty", "stack_push_top", "stack_pop");
        // The template fails the three behavioural tests but the harness CompileStack passes on both.
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
        // reverse_empty_string test (reverse_string("") -> "" for a stub returning ""). The [task]-only gate is satisfied (both bound tests fail on the template), but production
        // grades the unbound test too, so the bare template would score >0%. The production-parity gate must REJECT.
        List<String> all = List.of("reverse_non_empty", "reverse_empty_string", "is_palindrome_true", "is_palindrome_false");
        // The template fails three behaviour tests but the unbound reverse_empty_string PASSES on it (placeholder returns "").
        List<String> failedOnTemplate = List.of("reverse_non_empty", "is_palindrome_true", "is_palindrome_false");
        String ps = "# Strings\n[task][Reverse](reverse_non_empty)\n[task][Palindrome](is_palindrome_true)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("an unbound test passing on the template would give a bare-template student >0% in production").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("reverse_empty_string"));
    }

    @Test
    void shouldAcceptWhenOnlyBuildConfigureGatePassesOnTemplateAndEveryBehaviourTestFails() {
        // The legitimate C++ case: the harness emits non-behavioural build gates (TestConfigure runs CMake, CompileSort compiles the target) that pass on BOTH the solution and the
        // template (a same-signature placeholder template compiles by design). EVERY behaviour test fails on the template. The production-parity gate must EXEMPT the build gates
        // by
        // name and ACCEPT — they carry no exercise behaviour, so a student earns no behaviour point from them.
        List<String> all = List.of("TestConfigure", "CompileSort", "sorts_ascending", "sorts_with_duplicates", "stable_on_equal_keys");
        List<String> failedOnTemplate = List.of("sorts_ascending", "sorts_with_duplicates", "stable_on_equal_keys");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\n[task][Duplicates](sorts_with_duplicates)\n[task][Stable](stable_on_equal_keys)\n";
        VerificationResult result = verify(resultWithFails(5, 0, all, List.of()), resultWithFails(5, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("build/configure gates legitimately pass on both; every behaviour test fails on the template").isTrue();
    }

    @Test
    void shouldAcceptWhenFrameworkPrefixedBuildGatePassesOnTemplate() {
        // The REAL C++ harness reports the build gates WITH the framework suite prefix: `GBS-Tester-1.36.TestConfigure` / `GBS-Tester-1.36.CompileSort`. The earlier whole-name
        // build-gate match failed on the prefix, wrongly REJECTING a valid C++ exercise (the authentic sweep's only C++ failure). The gate word is the final dot-segment, so the
        // exemption must look there too. Behaviour tests carry the same suite prefix and must STILL be required to fail on the template.
        List<String> all = List.of("GBS-Tester-1.36.TestConfigure", "GBS-Tester-1.36.CompileSort", "sort-test.empty_initial", "sort-test.push_top");
        List<String> failedOnTemplate = List.of("sort-test.empty_initial", "sort-test.push_top");
        String ps = "# Stack\n[task][Empty](sort-test.empty_initial)\n[task][Push/top](sort-test.push_top)\n";
        VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).as("a framework-prefixed build gate (GBS-Tester-1.36.TestConfigure/CompileSort) is exempted by its final segment").isTrue();
    }

    @Test
    void shouldRejectWhenUnboundBehaviourPassesEvenThoughABuildGateAlsoPasses() {
        // Guard against over-trusting the allowlist: a real behaviour test must NOT be exempted just because another test is a build gate. Here CompileSort (gate) passes on both
        // AND an unbound behaviour test (peek_returns_top) also passes on the template — the latter must still be rejected.
        List<String> all = List.of("CompileSort", "push_grows", "peek_returns_top");
        List<String> failedOnTemplate = List.of("push_grows");
        String ps = "# Stack\n[task][Push](push_grows)\n";
        VerificationResult result = verify(resultWithFails(3, 0, all, List.of()), resultWithFails(3, 1, all, failedOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("peek_returns_top"));
    }

    @Test
    void rejects_whenABoundStructuralTestPassesOnTemplate_rubyStackStructure() {
        // Ruby reality (documented tension): the structural test test_stack_structure only checks method existence/arity, which the template (it DEFINES push/pop/peek/empty? with
        // the
        // right arity, bodies raising NotImplementedError) satisfies — so it PASSES on the template while the six behavioural tests error. Because that structural test IS
        // [task]-bound,
        // the strict per-test gate (correctly, by its own rule) flags it: a bound test that passes on the template is a free graded point. This pins that behaviour; the fix at the
        // exercise level is to NOT bind the structural test as a graded [task] (or to make it assert behaviour the template cannot meet).
        List<String> all = List.of("test_new_stack_is_empty", "test_push_makes_not_empty", "test_peek_returns_last_without_removing", "test_pop_returns_last_and_removes",
                "test_pop_on_empty_raises", "test_peek_on_empty_raises", "test_stack_structure");
        List<String> behaviouralFailOnTemplate = List.of("test_new_stack_is_empty", "test_push_makes_not_empty", "test_peek_returns_last_without_removing",
                "test_pop_returns_last_and_removes", "test_pop_on_empty_raises", "test_peek_on_empty_raises");
        String ps = "# Stack\n[task][Empty](test_new_stack_is_empty)\n[task][Structure](test_stack_structure)\n";
        VerificationResult result = verify(resultWithFails(7, 0, all, List.of()), resultWithFails(7, 1, all, behaviouralFailOnTemplate), ps);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("test_stack_structure"));
    }

    @Test
    void shouldRejectWhenTemplateReportsFailuresButEmitsNoFailNames() {
        // Defect B, rule (b), now fail-CLOSED. The template reports 2 failures (count gate would be happy) but emits ZERO HYPERION_TESTFAIL lines. The pristine verify.sh always
        // emits a fail line per failing <testcase>, so a missing fail set means we cannot confirm WHICH tests failed — and therefore cannot prove the [task]-bound tests fail on
        // the
        // template. We must REJECT, not skip. (Previously fail-open and wrongly accepted.)
        VerificationResult result = verify(resultWithNames(2, 0, 0, 0, "sortsUnsortedArray", "sortsArrayWithDuplicates"),
                resultWithNames(2, 2, 0, 1, "sortsUnsortedArray", "sortsArrayWithDuplicates"));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("could not record WHICH tests failed"));
    }

    @Test
    void shouldRejectWhenRepositorySeededNonEmptyExtractsEmpty() {
        // Defect B, rule (c), now fail-CLOSED. A read-back error (signalled via the extraction-failed set) is distinct from a genuinely empty repo: an empty produced map silently
        // disables the harness/leak gates, so accepting on that doubt would wave an unverified exercise through. The differential build is otherwise perfect here; the extraction
        // failure alone must force a rejection.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        VerificationResult result = newVerifier().verify(
                new ScriptedSandbox(resultWithFails(2, 0, names, List.of()), resultWithFails(2, 1, names, names), PROBLEM_STATEMENT_WITH_TASK), "s", new ProgrammingExercise(),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of("tests"));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("could not be read back for verification") && r.contains("tests"));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Defect F2 (CRITICAL): the per-test markers are read from the build's combined stdout, which also carries the
    // output of the AGENT'S TESTS. A test that prints "HYPERION_TESTFAIL <name>" / "HYPERION_RESULT …" to stdout could
    // forge the failing-template signal for a graded test that actually PASSES on the template — handing the student a
    // free point while the oracle accepts. The fix stamps a fresh per-run nonce onto every marker the PRISTINE verify.sh
    // emits; only nonce-bearing lines are honored, so an unsigned line the agent's test printed is ignored.
    // ----------------------------------------------------------------------------------------------------------------

    /** Builds a template run whose REAL (nonce-stamped) markers show {@code luckyName} PASSING on the template, but which also carries FORGED, UNSIGNED marker lines. */
    private static SandboxExecResult forgedTemplateRun(List<String> allNames, List<String> reallyFailed, String forgedLuckyName) {
        StringBuilder body = new StringBuilder("running the agent's tests...\n");
        // The agent's test prints UNSIGNED forgeries to stdout, trying to make the lucky test look failed and to inflate the failure count.
        body.append("HYPERION_TESTFAIL ").append(forgedLuckyName).append('\n');
        body.append("HYPERION_RESULT tests=").append(allNames.size()).append(" failures=").append(allNames.size()).append(" errors=0 skipped=0 exit=1\n");
        // The genuine, nonce-stamped emission from the pristine script: every name listed, only the truly-failing ones flagged (luckyName is NOT among them).
        for (String name : allNames) {
            body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        for (String name : reallyFailed) {
            body.append("HYPERION_TESTFAIL ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=").append(allNames.size()).append(" failures=").append(reallyFailed.size())
                .append(" errors=0 skipped=0 exit=1\n");
        return new SandboxExecResult(1, body.toString(), "", false);
    }

    @Test
    void shouldRejectWhenAgentForgesUnsignedTestFailMarkerForAPassingOnTemplateTest() {
        // A too-lucky template: the graded test peek_returns_top actually PASSES on the template (free point). The agent's test prints an UNSIGNED "HYPERION_TESTFAIL
        // peek_returns_top" to stdout to forge the failing signal. Because that line lacks the per-run nonce, the verifier ignores it, sees peek_returns_top still passing on the
        // template, and REJECTS. (Without the nonce, the forged line would have flipped this to accepted — a free-points exercise shipped.)
        List<String> all = List.of("push_grows", "peek_returns_top");
        List<String> reallyFailedOnTemplate = List.of("push_grows");
        String ps = "# Stack\n[task][Push](push_grows)\n[task][Peek](peek_returns_top)\n";
        SandboxExecResult solution = resultWithFails(2, 0, all, List.of());
        SandboxExecResult template = forgedTemplateRun(all, reallyFailedOnTemplate, "peek_returns_top");
        VerificationResult result = verify(solution, template, ps);
        assertThat(result.accepted()).as("an unsigned forged HYPERION_TESTFAIL must not flip a passing-on-template test to failed").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("PASS on the template") && r.contains("peek_returns_top"));
    }

    @Test
    void shouldIgnoreForgedUnsignedResultLineWhenCheckingTemplateMustFail() {
        // The aggregate count gate ("template must fail at least half"): a template that passes ALL tests prints a forged unsigned "HYPERION_RESULT tests=4 failures=4" to look
        // like
        // a fully-failing template. The unsigned line is ignored; the genuine nonce-stamped result (failures=0) wins, so the template is seen to PASS and is rejected.
        List<String> all = List.of("a", "b", "c", "d");
        String ps = "# X\n[task][A](a)\n";
        SandboxExecResult solution = resultWithFails(4, 0, all, List.of());
        // The genuine run: template passes everything (failures=0). The forged unsigned result claiming failures=4 is printed AFTER the genuine summary, so a nonce-agnostic
        // "last HYPERION_RESULT wins" parser would have picked up the forgery and seen a fully-failing template. The nonce anchor must make the parser ignore the unsigned line and
        // keep the genuine failures=0.
        StringBuilder body = new StringBuilder();
        for (String name : all) {
            body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
        }
        body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=4 failures=0 errors=0 skipped=0 exit=0\n");
        body.append("HYPERION_RESULT tests=4 failures=4 errors=0 skipped=0 exit=1\n");
        SandboxExecResult template = new SandboxExecResult(0, body.toString(), "", false);
        VerificationResult result = verify(solution, template, ps);
        assertThat(result.accepted()).as("a forged unsigned HYPERION_RESULT must not satisfy the template-must-fail gate").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("passes the tests"));
    }

    /**
     * The static-code-analysis parity gate (Defect D2). When SCA is enabled with a GRADED, positively-penalised category, a reference solution whose build produces a finding in
     * that category would be docked by production ({@code calculateTotalPenalty}) and so cannot grade 100% — the oracle must REJECT it (it used to accept, blind to the SCA
     * report).
     * When SCA cannot dock the solution (SCA disabled, {@code maxPenalty == 0}, only FEEDBACK/INACTIVE categories, or an info-only finding in a non-graded category), the gate
     * stays
     * silent and the verdict is unchanged.
     */
    @Nested
    class StaticCodeAnalysisParityGate {

        /** A solution build that passes all tests AND emits the given HYPERION_SCA findings; paired with a normal failing template. */
        private SandboxExecResult solutionWithScaFindings(String... findings) {
            List<String> names = List.of(DEFAULT_BOUND_NAMES);
            StringBuilder body = new StringBuilder("build log...\n");
            for (String name : names) {
                body.append("HYPERION_TESTNAME ").append(TEST_NONCE).append(' ').append(name).append('\n');
            }
            for (String finding : findings) {
                body.append("HYPERION_SCA ").append(TEST_NONCE).append(' ').append(finding).append('\n');
            }
            body.append("HYPERION_RESULT ").append(TEST_NONCE).append(" tests=2 failures=0 errors=0 skipped=0 exit=0\n");
            return new SandboxExecResult(0, body.toString(), "", false);
        }

        private SandboxExecResult failingTemplate() {
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

        /** Builds a SCA-enabled Java exercise with an id and the given persisted categories, plus a verifier wired to a stub repository returning those categories. */
        private VerificationResult verifyScaExercise(Integer maxPenalty, Boolean scaEnabled, Set<StaticCodeAnalysisCategory> categories, SandboxExecResult solution,
                SandboxExecResult template) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(4242L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setStaticCodeAnalysisEnabled(scaEnabled);
            exercise.setMaxStaticCodeAnalysisPenalty(maxPenalty);

            var repo = mock(StaticCodeAnalysisCategoryRepository.class);
            when(repo.findByExerciseId(4242L)).thenReturn(categories);
            var verifier = new AuthoritativeVerificationService(buildCommandFactory(), () -> TEST_NONCE, Optional.of(repo));
            return verifier.verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", exercise);
        }

        @Test
        void shouldRejectWhenScaEnabledAndSolutionHasGradedViolation() {
            // "Code Style" is GRADED with a positive penalty; the SpotBugs STYLE finding maps to it (StaticCodeAnalysisConfigurer Java default), so production would dock the
            // score.
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaFindings("SPOTBUGS|STYLE"), failingTemplate());
            assertThat(result.accepted()).as("a solution with a graded SCA violation must be rejected").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("static-code-analysis findings that production would penalise"));
        }

        @Test
        void shouldAcceptWhenScaEnabledButSolutionIsScaClean() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            // No HYPERION_SCA lines: the solution build produced no findings.
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaFindings(), failingTemplate());
            assertThat(result.accepted()).as("a clean solution is still accepted when SCA is graded").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenScaDisabledEvenIfFindingsLeakIntoOutput() {
            // SCA disabled: even if a HYPERION_SCA line appeared (it would not in production, since verify.sh emits no SCA section), the gate must not parse or reject on it.
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(50, false, categories, solutionWithScaFindings("SPOTBUGS|STYLE"), failingTemplate());
            assertThat(result.accepted()).as("SCA disabled => no SCA rejection").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenMaxPenaltyIsZeroSoScaCannotAffectTheScore() {
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(0, true, categories, solutionWithScaFindings("SPOTBUGS|STYLE"), failingTemplate());
            assertThat(result.accepted()).as("maxStaticCodeAnalysisPenalty == 0 => SCA penalty is disabled => no rejection").isTrue();
        }

        @Test
        void shouldAcceptWhenFindingIsInANonGradedCategory() {
            // The finding maps to "Code Style"; but only "Security" is GRADED here. Production would not penalise a Code Style finding, so the oracle must not reject (no
            // over-rejection on non-graded findings).
            var categories = Set.of(category("Code Style", CategoryState.FEEDBACK, 0.2), category("Security", CategoryState.GRADED, 2.5));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaFindings("SPOTBUGS|STYLE"), failingTemplate());
            assertThat(result.accepted()).as("a finding in a non-graded category must not be rejected").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        @Test
        void shouldAcceptWhenGradedCategoryHasZeroPenalty() {
            // A GRADED category with penalty 0 contributes 0 points (size * 0). Production deducts nothing, so the oracle must not reject.
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.0));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaFindings("SPOTBUGS|STYLE"), failingTemplate());
            assertThat(result.accepted()).as("a graded category with zero penalty deducts nothing => no rejection").isTrue();
        }

        @Test
        void shouldRejectUnknownCategoryFindingWhenTheToolHasAGradedCategory() {
            // A SARIF/GCC-style finding with the * sentinel category: conservatively penalising iff the producing tool (here SPOTBUGS via the Code Style mapping) has any graded,
            // positively-penalised category. This is the documented conservative path for tools whose category is not derived in POSIX.
            var categories = Set.of(category("Code Style", CategoryState.GRADED, 0.2));
            VerificationResult result = verifyScaExercise(50, true, categories, solutionWithScaFindings("SPOTBUGS|*"), failingTemplate());
            assertThat(result.accepted()).as("an undetermined-category finding is conservatively rejected when the tool has a graded category").isFalse();
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // W1 — auto-seeded structural-test binding exemption. The Java structural-oracle seeder injects Ares structural tests
    // (testClass[X], testMethods[X], …) into the test repo AFTER the agent submits, and the scaffold readme teaches the
    // agent the [task][…](testClass[X]) convention — so the agent writes structural bindings up front that the conservative
    // seeder may decline to materialise, leaving them resolving to nothing and forcing wasted retries (58–70 turns). A
    // structural-SHAPED binding must NOT be required to resolve, while the differential (solution passes / template fails)
    // stays fully enforced for every REAL test regardless of its name shape — so the exemption cannot be abused to evade
    // grading on a real behaviour test the agent named structurally.
    // ----------------------------------------------------------------------------------------------------------------

    @Nested
    class StructuralBindingExemption {

        @Test
        void shouldAcceptWhenBehaviourTestsBoundButAutoSeededStructuralTestsAreNot() {
            // The exact W1 thrash: the agent bound its two behaviour tests; the seeder then injected testClass[Sorter]/testMethods[Sorter] (failing on the template, since the
            // template omits the class). The agent never bound those structural tests. This must be ACCEPTED — and the structural tests still failed on the template (they ARE in
            // the differential). Previously this was the kind of run that, when the agent ALSO copied the scaffold's structural-binding convention, forced a retry.
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]", "testMethods[Sorter]");
            // Every test (behaviour AND structural) fails on the template: the structural ones because the class is absent, the behaviour ones because the stub is wrong.
            VerificationResult result = verify(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, all));
            assertThat(result.accepted()).as("auto-seeded structural tests need no [task] binding; they remain in the differential").isTrue();
        }

        @Test
        void shouldAcceptWhenStructuralTaskBindingResolvesToNothingBecauseSeederDeclined() {
            // The from-scratch trap, no authoritative set supplied: the agent copied the scaffold convention and wrote [task][…](testClass[Helper]) for a class the conservative
            // seeder ultimately did NOT enforce (Helper exists in both repos / is non-public), so testClass[Helper] resolves to NO real test. Before the fix this tripped
            // unresolvedTaskBindings -> bindingsResolve=false -> forced retry. Now a structural-SHAPED binding is exempt from resolution: a dead cosmetic task that backs no real
            // test is harmless. Every real behaviour test still fails on the template.
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Helper structure](testClass[Helper],testMethods[Helper])\n";
            VerificationResult result = verify(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, all), ps);
            assertThat(result.reasons()).as("a structural-shaped binding must not be reported as unresolved").noneMatch(r -> r.contains("match no actual test"));
            assertThat(result.accepted()).isTrue();
        }

        @Test
        void resolvesStructuralBinding_whenItIsInTheAuthoritativeSeededSet_evenIfExtractionMomentarilyLagsBindingValue() {
            // The authoritative-set tightening: testClass[Sorter] is in the seeder's own seeded set, so the binding resolves directly via that authority. It is also a real,
            // template-failing test here, so the differential is satisfied. Accepted.
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]", "testMethods[Sorter]");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Create Sorter](testClass[Sorter],testMethods[Sorter])\n";
            VerificationResult result = verifyWithSeededStructural(resultWithFails(4, 0, all, List.of()), resultWithFails(4, 1, all, all), ps,
                    Set.of("testClass[Sorter]", "testMethods[Sorter]"));
            assertThat(result.accepted()).isTrue();
        }

        @Test
        void stillRejects_whenAREALBehaviourTestIsLeftUnboundAndDanglingBinding() {
            // The exemption is ONLY for the structural shape: a [task] that binds a NON-structural name to a non-existent test is still a genuine dangling binding and must be
            // rejected. (Guards against the exemption being widened to all unresolved bindings.)
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Mystery](aDisplayNameNotAMethodName)\n";
            VerificationResult result = verify(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, all), ps);
            assertThat(result.accepted()).isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test") && r.contains("aDisplayNameNotAMethodName"));
        }

        @Test
        void forgeryResistance_aRealBehaviourTestNamedStructurallyThatPassesOnTemplate_isStillRejected() {
            // THE FORGERY ATTEMPT. The agent names a REAL behaviour test testClass[Evil] (mimicking the structural shape) hoping the W1 exemption lets it dodge the differential.
            // It
            // does NOT: the structural-shape exemption only relaxes binding RESOLUTION, never the differential. testClass[Evil] PASSES on the template (a free point), so the
            // production-parity gate (which keys on REAL test names, not binding shape) rejects it. The agent gains nothing from the structural disguise.
            List<String> all = List.of("realBehaviour", "testClass[Evil]");
            // realBehaviour fails on the template (good); testClass[Evil] PASSES on the template (the forged free point).
            List<String> failedOnTemplate = List.of("realBehaviour");
            String ps = "# X\n[task][Real](realBehaviour)\n[task][Disguised](testClass[Evil])\n";
            VerificationResult result = verify(resultWithFails(2, 0, all, List.of()), resultWithFails(2, 1, all, failedOnTemplate), ps);
            assertThat(result.accepted()).as("a structurally-named real test that passes on the template must still be rejected by the differential").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("testClass[Evil]"));
        }

        @Test
        void differentialStillEnforced_whenAnAutoSeededStructuralTestPassesOnTemplate() {
            // A structural test must remain part of the differential: if testClass[Sorter] PASSES on the template (the template did NOT actually omit the class — a mis-seed or a
            // template that kept the signature), the production-parity gate rejects it as a free point. The exemption is purely about [task] binding resolution, not the
            // pass/fail differential.
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]");
            // testClass[Sorter] PASSES on the template (not in the failed set); the two behaviour tests fail.
            List<String> failedOnTemplate = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            VerificationResult result = verifyWithSeededStructural(resultWithFails(3, 0, all, List.of()), resultWithFails(3, 1, all, failedOnTemplate), PROBLEM_STATEMENT_WITH_TASK,
                    Set.of("testClass[Sorter]"));
            assertThat(result.accepted()).as("a seeded structural test that passes on the template is a free point and must be rejected").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("production grades EVERY discovered test") && r.contains("testClass[Sorter]"));
        }
    }
}
