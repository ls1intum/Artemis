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

    /** Serves canned {@code cat}/{@code diff} output; every other command succeeds with empty output. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private String spec;

        private String testPlanJson;

        private String problemStatement = "# Title\n\nDo the thing.";

        /** Output of the created-type file probe (find), keyed by whether the probed repo path contains "solution" or "template". */
        private String solutionFindOutput = "";

        private String templateFindOutput = "";

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
                if (path.endsWith("SPEC.md")) {
                    return spec == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, spec, "", false);
                }
                if (path.endsWith("test-plan.json")) {
                    return testPlanJson == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, testPlanJson, "", false);
                }
                if (path.endsWith("problem-statement.md")) {
                    return problemStatement == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, problemStatement, "", false);
                }
            }
            if (command.length >= 1 && "diff".equals(command[0])) {
                return new SandboxExecResult(diffExitCode, "", "", false);
            }
            if (command.length >= 2 && "find".equals(command[0])) {
                return new SandboxExecResult(0, command[1].contains("solution") ? solutionFindOutput : templateFindOutput, "", false);
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

        @Test
        void fails_whenTheSpecDeclaresAStudentCreatedTypeTheSolutionNeverImplements() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(testsRan(0, 5, 0, List.of()));
            sandbox.spec = "## Design\n| Type | Role | Template status |\n|--|--|--|\n| RewardStrategy | strategy | student-creates |\n";

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("solution contains no file for them").contains("RewardStrategy");
        }

        @Test
        void passes_andConfirmsPresence_whenTheStudentCreatedTypeExistsInTheSolution() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(testsRan(0, 5, 0, List.of()));
            sandbox.spec = "## Design\n| Type | Role | Template status |\n|--|--|--|\n| RewardStrategy | strategy | student-creates |\n";
            sandbox.solutionFindOutput = "/workspace/solution/src/de/tum/RewardStrategy.java";

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("contains every student-created type").contains("RewardStrategy");
        }
    }

    @Nested
    class Template {

        private static final String SPEC_WITH_STUDENT_CREATED_TYPE = """
                ## Design
                | Type | Role | Template status |
                |------|------|-----------------|
                | RewardStrategy | strategy interface | student-creates |
                | LoyaltyAccount | context | stubbed |
                """;

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

        @Test
        void fails_whenAStudentCreatedTypeStillShipsInTheTemplate() {
            // The evidence gate for the weakest live finding: three runs in a row shipped stubs where the brief demanded student-created types.
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = SPEC_WITH_STUDENT_CREATED_TYPE;
            sandbox.templateFindOutput = "/workspace/template/src/de/tum/RewardStrategy.java";

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("must NOT contain").contains("RewardStrategy.java").contains("change its status in SPEC.md to 'stubbed'");
        }

        @Test
        void passes_andPositivelyConfirmsTheAbsence_whenStudentCreatedTypesAreOmitted() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = SPEC_WITH_STUDENT_CREATED_TYPE;

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Confirmed absent from the template").contains("RewardStrategy");
        }
    }

    @Nested
    class Tests {

        private AgentVerifyReport report(boolean solutionPassed, boolean templateFailed) {
            return new AgentVerifyReport(5, solutionPassed, solutionPassed ? List.of() : List.of("testFoo"), 5, true, templateFailed, List.of(), List.of("testFoo"), List.of(),
                    List.of(), solutionPassed && templateFailed, solutionPassed && templateFailed ? List.of() : List.of("some blocking reason"));
        }

        @Test
        void passes_andCarriesTheReport_whenSolutionPassesAndTemplateFails_andTheGradingPlanIsValid() {
            AgentVerifyReport report = report(true, true);
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report);
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"weight\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isTrue();
            assertThat(result.report()).isSameAs(report);
            assertThat(result.observation()).contains("Grading plan accepted").contains("1 hidden until the due date");
        }

        @Test
        void fails_whenTheDifferentialPassesButTheGradingPlanIsMissing() {
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report(true, true));

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("test-plan.json is missing").contains("testFoo");
        }

        @Test
        void fails_withTheParsersActionableMessage_whenTheGradingPlanIsInvalid() {
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"weight\":7,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("test-plan.json is invalid").contains("weights must be between 1 and 3");
        }

        @Test
        void fails_whenTheGradingPlanNamesATestThatDoesNotExist() {
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testGhost\",\"weight\":2,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("names tests that do not exist").contains("testGhost").contains("testFoo");
        }

        @Test
        void passes_butNamesUnplannedTests_soTheDefaultGradingIsAConsciousChoice() {
            AgentVerifyReport report = new AgentVerifyReport(5, true, List.of(), 5, true, true, List.of(), List.of("testFoo", "testBar"), List.of(), List.of(), true, List.of());
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report);
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"weight\":2,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Not in the plan").contains("testBar");
        }

        @Test
        void reportsTheDifferentialFailureFirst_neverTheMissingPlan_whenBothAreWrong() {
            // Feedback-priority contract: a failing differential is the real problem; plan noise on top of it would bury the actionable signal.
            when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(report(false, true));

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("differential requirement").doesNotContain("test-plan.json");
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
        void fails_whenMultipleTaskLinesShareATitle() {
            // Observed live: 18 [task] lines split 1:1 per test with titles like "Telepathic Retrieval" repeated four times.
            sandbox.problemStatement = "# T\n[task][Telepathic](testA)\n[task][Telepathic](testB)\n[task][Crane](testC)\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(3, true, List.of(), 3, true, true, List.of(), List.of("testA", "testB", "testC"), List.of(), List.of(), true,
                    List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("share the same title").contains("Telepathic");
        }

        @Test
        void fails_whenTheStatementWritesAboutStudentsInTheThirdPerson() {
            sandbox.problemStatement = "# T\nStudents must define a strategy interface.\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("third person");
        }

        @Test
        void fails_whenTheSpecSaysDiagramYesButTheStatementHasNone() {
            sandbox.spec = "## Diagram\nYes – strategies collaborate with the context";
            sandbox.problemStatement = "# T\nImplement the strategy.\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no @startuml diagram");
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

    @Nested
    class Spec {

        private static final String VALID_SPEC = """
                # Exercise

                Archetype: calculator-with-rules

                ## Rules
                - R1: computes a result from the input.

                ## Worked Examples
                | Rules | Input | Expected |
                |-------|-------|----------|
                | R1 | 2 | 4 |
                | R1 | 3 | 9 |

                ## Design
                | Type | Role | Template status |
                |------|------|-----------------|
                | Calculator | computes the result | stubbed |

                ## Testing Strategy
                - compute seam: typical and zero partitions; weight 3; hidden variant after the due date.

                ## Diagram
                no — single-class exercise
                """;

        @Test
        void passes_withRulesAndABranchingWorkedExamplesTable_andEchoesTheParsedTemplatePlan() {
            sandbox.spec = VALID_SPEC;

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            // The pass observation echoes the parsed Design plan so the agent sees exactly what the later gates will enforce.
            assertThat(result.observation()).contains("Calculator=stubbed");
        }

        @Test
        void fails_whenTheDesignSectionHasNoDataRows() {
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |\n", "");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("'## Design' section has no data rows");
        }

        @Test
        void fails_whenADesignRowCarriesNoValidTemplateStatusToken() {
            // The old exemplar's verbose tokens ("student-creates-absent-from-template") are exactly what this catches: only the literal tokens are enforceable.
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |", "| Calculator | computes the result | student-implements-stubbed |");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no template-status token").contains("Calculator");
        }

        @Test
        void fails_whenTheSpecIsMissing() {
            sandbox.spec = null;

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("SPEC.md is missing or empty");
        }

        @Test
        void fails_whenRequiredSectionsAreMissing() {
            sandbox.spec = "# Exercise\n\nSome prose without the required sections.";

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("missing required section(s)").contains("## Rules").contains("## Worked Examples").contains("## Design")
                    .contains("## Testing Strategy").contains("## Diagram");
        }

        @Test
        void fails_whenTaskBindingsOrDiagramsAppearAtSpecTime() {
            sandbox.spec = VALID_SPEC + "\n[task][Do it](testDoIt)\n";

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("must not contain [task] bindings or PlantUML diagrams");
        }

        @Test
        void fails_whenTheWorkedExamplesTableHasFewerThanTwoDataRows() {
            sandbox.spec = VALID_SPEC.replace("| R1 | 3 | 9 |\n", "");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("at least two data rows");
        }

        @Test
        void fails_whenEveryExpectedResultIsIdentical_theLiteralGradingSignature() {
            sandbox.spec = VALID_SPEC.replace("| R1 | 3 | 9 |", "| R1 | 3 | 4 |");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("SAME expected result").contains("deepen the rules");
        }
    }

}
