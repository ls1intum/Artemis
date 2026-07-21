package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the mechanical per-stage gates, in particular the "compiled" definition fix ({@link SingleBuildResult#compiled()}): {@code verify.sh} exits non-zero both for a
 * genuine compile failure and for failing tests, so the SOLUTION/TEMPLATE gates must distinguish "did not compile" (an infrastructure-level problem) from "compiled but a test
 * fails" (an authoring-quality problem the differential is supposed to catch), and must never punish a template for correctly failing its behavioural tests. The differential
 * itself ({@link DifferentialVerificationService}) is mocked throughout; its own build-and-parse behaviour is covered by {@code DifferentialVerificationServiceTest}.
 */
class StageCheckServiceTest {

    private static final String VALID_DESIGN_DOCUMENT = """
            ## Classes
            | name | role |
            |------|------|
            | Foo  | given |

            ## Public API
            - Foo#bar()

            ## Tasks
            | task | partitions |
            |------|------------|
            | bar  | typical    |

            ## Diagram
            no — single-class exercise
            """;

    /** Serves canned {@code cat}/{@code diff} output; every other command succeeds with empty output. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private String designMarkdown = VALID_DESIGN_DOCUMENT;

        private String problemStatement = "# Title\n\nDo the thing.";

        /** {@code diff -rq} exit code; 1 means the trees differ (the expected, healthy case). */
        private int diffExitCode = 1;

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            if (command.length >= 2 && "cat".equals(command[0])) {
                String path = command[1];
                if (path.endsWith("DESIGN.md")) {
                    return designMarkdown == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, designMarkdown, "", false);
                }
                if (path.endsWith("problem-statement.md")) {
                    return problemStatement == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, problemStatement, "", false);
                }
            }
            if (command.length >= 1 && "diff".equals(command[0])) {
                return new SandboxExecResult(diffExitCode, "", "", false);
            }
            return new SandboxExecResult(0, "", "", false);
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

    private DifferentialVerificationService verifier;

    private StageCheckService service;

    private FakeSandbox sandbox;

    private ProgrammingExercise exercise;

    private static SingleBuildResult compiled() {
        return new SingleBuildResult(0, 0, 0, List.of(), "");
    }

    private static SingleBuildResult compileFailure(String buildOutput) {
        return new SingleBuildResult(1, 0, 0, List.of(), buildOutput);
    }

    private static SingleBuildResult testsRan(int exitCode, int testsRun, int failures, List<String> failedTestNames) {
        return new SingleBuildResult(exitCode, testsRun, failures, failedTestNames, "");
    }

    @BeforeEach
    void setUp() {
        verifier = mock(DifferentialVerificationService.class);
        service = new StageCheckService(verifier);
        sandbox = new FakeSandbox();
        exercise = new ProgrammingExercise();
    }

    private StageCheckResult check(GenerationStage stage) {
        return check(stage, null);
    }

    private StageCheckResult check(GenerationStage stage, AgentVerifyReport lastTestsReport) {
        return service.check(stage, sandbox, "s", exercise, Map.of(), lastTestsReport);
    }

    @Nested
    class Design {

        @Test
        void passes_whenAllRequiredSectionsArePresent() {
            StageCheckResult result = check(GenerationStage.DESIGN);

            assertThat(result.passed()).isTrue();
            assertThat(result.report()).isNull();
        }

        @Test
        void fails_whenRequiredSectionsAreMissing() {
            sandbox.designMarkdown = "no headings here";

            StageCheckResult result = check(GenerationStage.DESIGN);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("DESIGN.md is missing required section(s)").contains("## Classes", "## Public API", "## Tasks");
        }

        @Test
        void fails_whenFileIsMissingOrEmpty() {
            sandbox.designMarkdown = null;

            StageCheckResult result = check(GenerationStage.DESIGN);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("DESIGN.md is missing or empty");
        }
    }

    @Nested
    class Solution {

        @Test
        void passes_whenTheBuildExitsCleanlyWithNoTestsYet() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compiled());

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void passes_whenEveryTestPasses() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(testsRan(0, 5, 0, List.of()));

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void fails_asACompileError_whenNoTestsRanAndTheExitCodeIsNonZero() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution")))
                    .thenReturn(compileFailure("Compiling...\n[ERROR] Foo.java:[12,5] cannot find symbol\n[ERROR] Foo.java:[20,1] ';' expected\nBUILD FAILURE"));

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("The reference solution does not compile").contains("cannot find symbol").contains("';' expected")
                    .doesNotContain("Compiling...", "BUILD FAILURE");
        }

        @Test
        void fails_byNamingFailingTests_whenTestsRanButSomeFail_notAsACompileError() {
            // The compiled-definition fix: a non-zero exit with tests > 0 is a failing-test outcome, not a compile failure.
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(testsRan(1, 5, 2, List.of("testSortsDescending", "testHandlesEmpty")));

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("must pass every test").contains("testSortsDescending", "testHandlesEmpty").doesNotContain("does not compile");
        }

        @Test
        void fails_gracefully_whenTheBuildInfrastructureThrows() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenThrow(new RuntimeException("sandbox unreachable"));

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("Could not run the reference solution compile check").contains("sandbox unreachable");
        }
    }

    @Nested
    class Template {

        @BeforeEach
        void defaultsToADifferingTree() {
            sandbox.diffExitCode = 1; // solution/template differ, the healthy default
        }

        @Test
        void passes_andReportsCorrectFailures_whenItCompilesAndFailsItsTests() {
            // The compiled-definition fix: the template correctly failing its behavioural tests must PASS, not be misread as a compile failure.
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1", "t2", "t3", "t4", "t5")));

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).containsIgnoringCase("template correctly failing").contains("5");
        }

        @Test
        void passes_silently_whenItCompilesWithNoTestsYet() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compiled());

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void fails_asACompileError_whenNoTestsRanAndTheExitCodeIsNonZero() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compileFailure("[ERROR] Bar.java:[3,1] class, interface, or enum expected"));

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("The template does not compile").contains("class, interface, or enum expected");
        }

        @Test
        void fails_whenTheTemplateIsByteIdenticalToTheSolution_evenThoughItCompiles() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.diffExitCode = 0; // byte-identical

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("byte-identical to the solution");
        }

        @Test
        void fails_gracefully_whenTheBuildInfrastructureThrows() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenThrow(new RuntimeException("sandbox unreachable"));

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("Could not run the template compile check").contains("sandbox unreachable");
        }
    }

    @Nested
    class Tests {

        private AgentVerifyReport report(boolean solutionPassed, boolean templateFailed) {
            return new AgentVerifyReport(5, solutionPassed, solutionPassed ? List.of() : List.of("testFoo"), 5, true, templateFailed, List.of(), List.of("testFoo"), List.of(),
                    List.of(), solutionPassed && templateFailed, solutionPassed && templateFailed ? List.of() : List.of("some blocking reason"));
        }

        @Test
        void passes_andCarriesTheReport_whenSolutionPassesAndTemplateFails() {
            AgentVerifyReport report = report(true, true);
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report);

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isTrue();
            assertThat(result.report()).isSameAs(report);
        }

        @Test
        void fails_butStillCarriesTheReport_whenTheDifferentialDoesNotHold() {
            AgentVerifyReport report = report(false, true);
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report);

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("do not yet satisfy the differential requirement");
            assertThat(result.report()).isSameAs(report);
        }

        @Test
        void fails_gracefully_whenTheSelfCheckThrows() {
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenThrow(new RuntimeException("build agent lost"));

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.report()).isNull();
            assertThat(result.observation()).contains("Could not run the differential self-check").contains("build agent lost");
        }
    }

    @Nested
    class Statement {

        @Test
        void fails_whenTheStatementIsMissingOrEmpty() {
            sandbox.problemStatement = "  ";

            StageCheckResult result = check(GenerationStage.STATEMENT);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("problem-statement.md is missing or empty");
        }

        @Test
        void passes_whenNonEmptyAndNoTestsReportIsAvailableYet() {
            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void passes_whenEveryTaskBindingResolvesAgainstTheTestsReportsExactNames() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending,testSortsDescending)\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsDescending"), List.of(),
                    List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void fails_whenADiagramTestsColorNameMatchesNoRealTest() {
            // A dead testsColor link renders an element that can never turn green — same resolution standard as a [task] binding.
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending)\n@startuml\nclass A {\n  <color:testsColor(testGhost)>+sort()</color>\n}\n@enduml\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(1, true, List.of(), 1, true, true, List.of(), List.of("testSortsAscending"), List.of(), List.of(), true,
                    List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("testsColor").contains("testGhost");
        }

        @Test
        void fails_whenPlantUmlDirectivesLeakOutsideTheDiagramBlock() {
            // Observed live: 'hide empty fields' after @enduml renders as stray statement text.
            sandbox.problemStatement = "# Title\n@startuml\nclass A\n@enduml\nhide empty fields\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("OUTSIDE the @startuml");
        }

        @Test
        void passes_whenPlantUmlDirectivesSitInsideTheDiagramBlock() {
            sandbox.problemStatement = "# Title\n@startuml\nclass A\nhide empty fields\nhide empty methods\n@enduml\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void passes_whenDiagramTestsColorNamesResolve_includingParenthesisedAndStructuralForms() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending)\n@startuml\nclass A {\n  <color:testsColor(testSortsAscending())>+sort()</color>\n}\n"
                    + "A -up-|> B #testsColor(testClass[A])\n@enduml\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(1, true, List.of(), 1, true, true, List.of(), List.of("testSortsAscending", "testClass[A]"), List.of(),
                    List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void fails_whenATaskBindingReferencesANameThatMatchesNoRealTest() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending,testDoesNotExist)\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(1, true, List.of(), 1, true, true, List.of(), List.of("testSortsAscending"), List.of(), List.of(), true,
                    List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("reference names that match no actual test").contains("testDoesNotExist").contains("testSortsAscending"); // the exact-names
                                                                                                                                                                // hint, so the
                                                                                                                                                                // agent can copy
                                                                                                                                                                // correctly
        }
    }
}
