package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Unit tests for the mechanical per-stage gates, in particular the "compiled" definition fix ({@link SingleBuildResult#compiled()}): {@code verify.sh} exits non-zero both for a
 * genuine compile failure and for failing tests, so the SOLUTION/TEMPLATE gates must distinguish "did not compile" (an infrastructure-level problem) from "compiled but a test
 * fails" (an authoring-quality problem the differential is supposed to catch), and must never punish a template for correctly failing its behavioural tests. The differential
 * itself ({@link DifferentialVerificationService}) is mocked throughout; its own build-and-parse behaviour is covered by {@code DifferentialVerificationServiceTest}.
 */
class StageCheckServiceTest {

    /** Serves canned {@code cat}/{@code diff} output; every other command succeeds with empty output. */
    private static final class FakeSandbox implements InteractiveSandbox {

        /** Defaults to a gate-valid specification: every later stage re-checks SPEC.md, so a broken default would mask each stage's own logic. */
        private String spec = specWithDesign("| Calculator | computes | stubbed |\n");

        private String testPlanJson;

        private String problemStatement = "# Title\n\nDo the thing.";

        /**
         * Output of the type-declaration probe (find/grep), keyed by whether the probed repo path contains "solution" or "template". The probe is TYPE-AWARE: it answers only
         * for the type actually being probed, because the template gate now asks two opposite questions of the same helper — "is this student-created type absent?" and "is
         * this supplied/stubbed scaffold type present?". A payload that answered every probe identically would make one of the two questions meaningless.
         */
        private String solutionFindOutput = "";

        private String templateFindOutput = "";

        /** Whether the template ships the specification's supplied/stubbed scaffold. False models the empty starter repository the scaffold gate must reject. */
        private boolean templateShipsScaffold = true;

        /**
         * The specification the workspace was actually built against. Files on disk do not change when the live SPEC.md is edited after approval, so the probe must model the
         * approved snapshot — otherwise a downgraded live copy would appear to move a declaration between repositories.
         */
        private java.util.function.Supplier<String> builtAgainstSpec = () -> spec;

        /** Stable work markers found in the template; the default matches the default specification's sole S1 seam. */
        private String templateTodoOutput = "TODO S1:";

        private Map<String, String> templateRepositoryFiles = Map.of();

        /** {@code diff -rq} exit code; 1 means the trees differ (the expected, healthy case). */
        private int diffExitCode = 1;

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        /**
         * Answers a single type-declaration probe. The explicitly configured payload wins when it names the probed type (how a test models a leaked or present declaration);
         * otherwise a healthy repository is modelled: the template ships the specification's supplied/stubbed scaffold, and the solution ships everything.
         */
        private String declarationProbe(String[] command, boolean solutionRepo) {
            String type = probedType(command);
            String payload = solutionRepo ? solutionFindOutput : templateFindOutput;
            if (type == null) {
                return payload;
            }
            if (payload.contains(type)) {
                return payload;
            }
            if (solutionRepo) {
                return "";
            }
            String effective = builtAgainstSpec.get();
            boolean scaffoldType = StageCheckService.specScaffoldTypes(effective == null ? "" : effective).contains(type);
            return templateShipsScaffold && scaffoldType ? "/workspace/template/src/" + type + ".java" : "";
        }

        /** The bare type name a {@code find -name Type.*} or declaration {@code grep} is asking about. */
        private static String probedType(String[] command) {
            for (int index = 0; index < command.length - 1; index++) {
                if ("-name".equals(command[index]) && command[index + 1].endsWith(".*")) {
                    return command[index + 1].substring(0, command[index + 1].length() - 2);
                }
            }
            for (String argument : command) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\|record\\|trait\\|struct\\|protocol\\)\\[\\[:space:]]\\+(\\w+)").matcher(argument);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            return null;
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
                return new SandboxExecResult(0, declarationProbe(command, command[1].contains("solution")), "", false);
            }
            if (command.length >= 2 && "grep".equals(command[0])) {
                if (java.util.Arrays.stream(command).anyMatch(argument -> argument.contains("TODO"))) {
                    return new SandboxExecResult(0, templateTodoOutput, "", false);
                }
                boolean solutionRepo = java.util.Arrays.stream(command).anyMatch(argument -> argument.contains("/solution"));
                return new SandboxExecResult(0, declarationProbe(command, solutionRepo), "", false);
            }
            return new SandboxExecResult(0, "", "", false);
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            return templateRepositoryFiles.isEmpty() ? null
                    : ReportTarFixtures.tar("template", templateRepositoryFiles.entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8))));
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    /** A complete, gate-valid SPEC.md whose '## Design' table carries the given data rows — every stage now re-checks the spec, so a partial fixture would fail that check. */
    private static String specWithDesign(String designRows) {
        String ownerType = designRows.split("\\|")[1].strip();
        return """
                # Exercise

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
                """ + designRows + """

                ## Public API
                `%s`: `int calculate(int input)`

                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |------|------------|------------|--------|----------------|
                | S1 | %s | typical; zero | 3 | no |

                ## Diagram
                no — single-class exercise
                """.formatted(ownerType, ownerType);
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

    private ApprovedSpecRegistry approvedSpecs;

    @BeforeEach
    void setUp() {
        verifier = mock(DifferentialVerificationService.class);
        approvedSpecs = new ApprovedSpecRegistry();
        service = new StageCheckService(verifier, approvedSpecs);
        sandbox = new FakeSandbox();
        sandbox.builtAgainstSpec = () -> approvedSpecs.approved("s").orElseGet(() -> sandbox.spec);
        exercise = new ProgrammingExercise();
    }

    private StageCheckResult check(GenerationStage stage) {
        return check(stage, null);
    }

    private StageCheckResult check(GenerationStage stage, AgentVerifyReport lastTestsReport) {
        return service.check(stage, sandbox, "s", exercise, Map.of(), lastTestsReport, Set.of());
    }

    @Test
    void validateArtifactWrite_rejectsAStudentCreatedTypeDeclarationInTheTemplate() {
        approvedSpecs.approve("s", specWithDesign("| FuelStrategy | designed by students | student-creates |\n"));

        assertThat(service.validateArtifactWrite("s", "template/src/FuelStrategy.java", "public interface FuelStrategy { double compute(double input); }"))
                .hasValueSatisfying(message -> assertThat(message).contains("FuelStrategy").contains("Do not restore or pre-create"));
        assertThat(service.validateArtifactWrite("s", "solution/src/FuelStrategy.java", "public interface FuelStrategy {}")).isEmpty();
        assertThat(service.validateArtifactWrite("s", "template/src/Spacecraft.java", "public class Spacecraft { // TODO S1: create strategy\n}")).isEmpty();
        assertThat(service.validateArtifactWrite("s", "SPEC.md", sandbox.spec)).hasValueSatisfying(message -> assertThat(message).contains("read-only"));
    }

    @Test
    void restoreApprovedSpecAfterCommand_restoresAnOutOfBandShellMutation() {
        approvedSpecs.approve("s", specWithDesign("| FuelStrategy | designed by students | student-creates |\n"));
        sandbox.spec = "tampered";

        assertThat(service.restoreApprovedSpecAfterCommand(sandbox, "s"))
                .hasValueSatisfying(message -> assertThat(message).contains("changed read-only SPEC.md").contains("restored the approved specification"));
    }

    @Test
    void approvedOwnershipViolationAfterCommand_detectsAnOutOfBandTemplateArtifact() {
        approvedSpecs.approve("s", specWithDesign("| FuelStrategy | designed by students | student-creates |\n"));
        sandbox.templateFindOutput = "/workspace/template/src/FuelStrategy.java";

        assertThat(service.approvedOwnershipViolationAfterCommand(sandbox, "s"))
                .hasValueSatisfying(message -> assertThat(message).contains("FuelStrategy.java").contains("reflection").contains("delete_file/edit_file"));
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
            sandbox.spec = specWithDesign("| RewardStrategy | strategy | student-creates |\n| LoyaltyAccount | context | stubbed |\n");

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("solution contains no file for them").contains("RewardStrategy");
        }

        @Test
        void passes_andConfirmsPresence_whenTheStudentCreatedTypeExistsInTheSolution() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(testsRan(0, 5, 0, List.of()));
            sandbox.spec = specWithDesign("| RewardStrategy | strategy | student-creates |\n| LoyaltyAccount | context | stubbed |\n");
            sandbox.solutionFindOutput = "/workspace/solution/src/de/tum/RewardStrategy.java";

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("contains every student-created type").contains("RewardStrategy");
        }
    }

    @Nested
    class Template {

        private static final String SPEC_WITH_STUDENT_CREATED_TYPE = specWithDesign("""
                | RewardStrategy | strategy interface | student-creates |
                | LoyaltyAccount | context | stubbed |
                """).replace("| S1 | RewardStrategy | typical; zero | 3 | no |", """
                | S1 | RewardStrategy | typical; zero | 3 | no |
                | S2 | LoyaltyAccount | delegation and replacement | 3 | no |""");

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
        void doesNotGuessTodoAssociationWhenTheRepositorySnapshotIsUnavailable() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compiled());
            sandbox.templateTodoOutput = "TODO S9:";

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void doesNotApplyJavaTodoOwnershipRulesToAnotherLanguage() {
            exercise.setProgrammingLanguage(ProgrammingLanguage.C);
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compiled());
            sandbox.templateRepositoryFiles = Map.of("src/main.c", "int main(void) { return 0; }");

            assertThat(check(GenerationStage.TEMPLATE).passed()).isTrue();
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
            assertThat(result.observation()).contains("must NOT contain").contains("RewardStrategy.java").contains("changing SPEC.md now cannot");
        }

        @Test
        void fails_whenTheTemplateShipsNoneOfTheSuppliedScaffoldTypes() {
            // Observed live: a generated exercise marked every type student-created, shipped an EMPTY template, and still scored the pipeline's best grade — an empty template
            // compiles (no sources, exit 0) and "fails every test" (nothing runs), so the differential is satisfied by the degenerate candidate it should reject.
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = SPEC_WITH_STUDENT_CREATED_TYPE;
            sandbox.templateShipsScaffold = false;

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("missing type(s)").contains("LoyaltyAccount").contains("only");
        }

        @Test
        void fails_andNamesTheType_whenOnlyPartOfTheJavaScaffoldIsMissing() {
            // Observed live: a generics exercise declared two policy types 'given', shipped a template without them, and burned its whole repair budget on "cannot find symbol"
            // while the tests compiled against the template — nothing ever named the absent type. Java declares each type in a file named after it, so the probe can be exact.
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = specWithDesign("| Stack | student work | student-creates |\n| OverflowPolicy | supplied policy | given |\n| Support | supplied helper | given |\n");
            // The template ships Support but not OverflowPolicy: a partial scaffold the wholly-missing check would wave through.
            sandbox.templateFindOutput = "/workspace/template/src/Support.java";
            sandbox.templateShipsScaffold = false;

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("OverflowPolicy").doesNotContain("Support");
        }

        @Test
        void passes_andPositivelyConfirmsTheAbsence_whenStudentCreatedTypesAreOmitted() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = SPEC_WITH_STUDENT_CREATED_TYPE;

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Confirmed absent from the template").contains("RewardStrategy");
        }

        @Test
        void rejectsAMisleadingStudentCreatedBreadcrumbBeforeTheTestsAndStatementStages() {
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.spec = SPEC_WITH_STUDENT_CREATED_TYPE;
            sandbox.templateRepositoryFiles = Map.of("src/LoyaltyAccount.java", "class LoyaltyAccount { // TODO S1: create RewardStrategy\n// TODO S2: delegate to it\n}");

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("student-created", "S1", "LoyaltyAccount.java");
        }
    }

    @Nested
    class Tests {

        /** A gate-valid spec whose Testing Strategy declares NO hidden variant, so the plan checks below are not also judged on hidden-variant follow-through. */
        private static final String SPEC_WITHOUT_HIDDEN_VARIANTS = specWithDesign("| Calculator | computes | stubbed |\n")
                .replace("- compute seam: typical and zero partitions; weight 3.", "- compute seam: typical and zero partitions; weight 3; no hidden variant needed.");

        @BeforeEach
        void specDeclaresNoHiddenVariants() {
            sandbox.spec = SPEC_WITHOUT_HIDDEN_VARIANTS;
        }

        private AgentVerifyReport report(boolean solutionPassed, boolean templateFailed) {
            return new AgentVerifyReport(5, solutionPassed, solutionPassed ? List.of() : List.of("testFoo"), 5, true, templateFailed, List.of(), List.of("testFoo"), List.of(),
                    List.of(), solutionPassed && templateFailed, solutionPassed && templateFailed ? List.of() : List.of("some blocking reason"));
        }

        @Test
        void passes_andCarriesTheReport_whenSolutionPassesAndTemplateFails_andTheGradingPlanIsValid() {
            AgentVerifyReport report = report(true, true);
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isTrue();
            assertThat(result.report()).isSameAs(report);
            assertThat(result.observation()).contains("Grading plan accepted").contains("0 hidden until the due date");
        }

        @Test
        void fails_whenTheDifferentialPassesButTheGradingPlanIsMissing() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("test-plan.json is missing").contains("testFoo");
        }

        @Test
        void fails_withTheParsersActionableMessage_whenTheGradingPlanIsInvalid() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":7,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("test-plan.json is invalid").contains("tiers must be between 1 and 3");
        }

        @Test
        void fails_whenTheGradingPlanNamesATestThatDoesNotExist() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testGhost\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("names tests the verifier did not run").contains("testGhost").contains("testFoo");
        }

        @Test
        void fails_whenTheSpecDeclaresHiddenVariantsButThePlanHidesNothing() {
            // The spec's own Testing Strategy is the plan's contract: silently shipping every test visible throws away the overfit resistance the spec promised.
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |",
                    "| S1 | Calculator | typical; zero | 3 | yes |");
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("requires AFTER_DUE_DATE variants").contains("S1");
        }

        @Test
        void fails_whenOnlySomeSeamsWithHiddenVariantsHaveHiddenPlanEntries() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |", """
                    | S1 | Calculator | ordinary values | 3 | yes |
                    | S2 | Calculator | boundary values | 2 | yes |
                    """);
            AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("ordinary", "boundary"), List.of(), List.of(), true, List.of());
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"ordinary\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"},"
                    + "{\"name\":\"boundary\",\"seam\":\"S2\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("S2").doesNotContain("S1, S2");
        }

        @Test
        void rejectsUnplannedGradableTests_soNoneBypassTheApprovedPlan() {
            AgentVerifyReport report = new AgentVerifyReport(5, true, List.of(), 5, true, true, List.of(), List.of("testFoo", "testBar"), List.of(), List.of(), true, List.of());
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("omits verified gradable test", "testBar");
        }

        @Test
        void rejectsWeightDriftAndUnexpectedHiddenTests() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";
            assertThat(check(GenerationStage.TESTS).observation()).contains("weights do not match", "S1 requires 3");

            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";
            assertThat(check(GenerationStage.TESTS).observation()).contains("says no hidden variant", "S1");
        }

        @Test
        void rejectsAHiddenOnlySeamBecauseStudentsNeedVisibleEvidence() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |",
                    "| S1 | Calculator | typical; zero | 3 | yes |");
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";

            assertThat(check(GenerationStage.TESTS).observation()).contains("no ALWAYS-visible test", "S1", "formative visible evidence");
        }

        @Test
        void rejectsAfterDueDateVisibilityWhenTheExerciseHasNoDueDate() {
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |",
                    "| S1 | Calculator | typical; zero | 3 | yes |");
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";

            assertThat(check(GenerationStage.TESTS).observation()).contains("has no due date", "hidden indefinitely");
        }

        @Test
        void reportsTheDifferentialFailureFirst_neverTheMissingPlan_whenBothAreWrong() {
            // Feedback-priority contract: a failing differential is the real problem; plan noise on top of it would bury the actionable signal.
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(false, true));

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("TESTS-stage checks").doesNotContain("test-plan.json");
        }

        @Test
        void rejectsACompleteBuildPairWhenAnotherTestArtifactGateFailsBeforeReadingThePlan() {
            AgentVerifyReport report = new AgentVerifyReport(5, true, List.of(), 5, true, true, List.of(), List.of("testFoo"), List.of(), List.of(), false,
                    List.of("The Java tests do not use @WhitelistPath."));
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("@WhitelistPath").doesNotContain("test-plan.json is missing");
        }

        @Test
        void fails_whenAPlanEntryHasNoSeamOrNamesASeamTheSpecNeverDeclared() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report(true, true));
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";

            assertThat(check(GenerationStage.TESTS).observation()).contains("no seam").contains("S1");

            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S2\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";

            assertThat(check(GenerationStage.TESTS).observation()).contains("uses seam(s) the approved Testing Strategy never declared").contains("S2").contains("S1");
        }

        @Test
        void fails_butStillCarriesTheReport_whenTheDifferentialDoesNotHold() {
            AgentVerifyReport report = report(false, true);
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("do not yet satisfy the TESTS-stage checks");
            assertThat(result.report()).isSameAs(report);
        }

        @Test
        void differentialFailure_explainsHowToTestApprovedStudentCreatedTypesWithoutRestoringThem() {
            sandbox.spec = specWithDesign("| FuelStrategy | designed by students | student-creates |\n");
            approvedSpecs.approve("s", sandbox.spec);
            AgentVerifyReport report = report(true, false);
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenReturn(report);

            StageCheckResult result = check(GenerationStage.TESTS);

            assertThat(result.observation()).contains("approved student-created types are [FuelStrategy]").contains("Do not add their declarations").contains("Class.forName")
                    .contains("dynamic proxy");
        }

        @Test
        void fails_gracefully_whenTheSelfCheckThrows() {
            when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), anySet())).thenThrow(new RuntimeException("build agent lost"));

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
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending,testSortsDescending)\nImplement ascending and descending sorting.\n";
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
        void fails_whenATaskBindsATestTheGradingPlanHidesUntilTheDueDate() {
            // Binding a hidden test renders a checkbox that can never turn green before the deadline AND names the overfit probe in the student's checklist.
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending,testSortsAscending_hidden)\n";
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testSortsAscending\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"},"
                    + "{\"name\":\"testSortsAscending_hidden\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsAscending_hidden"),
                    List.of(), List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("hides until the due date").contains("testSortsAscending_hidden").contains("must stay unbound");
        }

        @Test
        void fails_whenAHiddenTestNameIsAdvertisedInProseEvenThoughItIsUnbound() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending)\nHidden tests: `testSortsAscending_hidden`.\n";
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testSortsAscending\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"},"
                    + "{\"name\":\"testSortsAscending_hidden\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsAscending_hidden"),
                    List.of(), List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("prose, or appendices").contains("testSortsAscending_hidden");
        }

        @Test
        void passes_whenOnlyVisibleTestsAreBound_andTheHiddenOneIsLeftUnbound() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending)\nImplement the sorting strategy.\n";
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testSortsAscending\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"},"
                    + "{\"name\":\"testSortsAscending_hidden\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsAscending_hidden"),
                    List.of(), List.of(), true, List.of());

            assertThat(check(GenerationStage.STATEMENT, lastTestsReport).passed()).isTrue();
        }

        @Test
        void fails_whenVisibleTestsFromOneSeamAreSplitAcrossTasks() {
            sandbox.problemStatement = "# Title\n[task][Ascending](testSortsAscending)\n[task][Descending](testSortsDescending)\n";
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testSortsAscending\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"},"
                    + "{\"name\":\"testSortsDescending\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";
            AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsDescending"), List.of(),
                    List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, report);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("one task per student-work seam").contains("S1").contains("split");
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
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("## Diagram\nno — single-class exercise",
                    "## Diagram\nYes – strategies collaborate with the context");
            sandbox.problemStatement = "# T\nImplement the strategy.\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no @startuml diagram");
        }

        @Test
        void fails_whenTheBoldDiagramDecisionSaysYesButTheStatementHasNone() {
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("## Diagram\nno — single-class exercise",
                    "## Diagram\n**Yes** – strategies collaborate with the context");
            sandbox.problemStatement = "# T\nImplement the strategy.\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no @startuml diagram");
        }

        @Test
        void doesNotTreatYesterdayOrYesSlashNoAsADiagramPromise() {
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("## Diagram\nno — single-class exercise",
                    "## Diagram\nYesterday we considered one\n\n## Later\nyes");
            sandbox.problemStatement = "# T\nExplain the result.\n";
            assertThat(check(GenerationStage.STATEMENT, null).passed()).isTrue();

            sandbox.spec = sandbox.spec.replace("Yesterday we considered one", "yes/no undecided");
            assertThat(check(GenerationStage.STATEMENT, null).passed()).isTrue();
        }

        @Test
        void fails_whenTaskBindingsHaveNoInstructionsBetweenThem() {
            sandbox.problemStatement = "# Title\n[task][First](testA)\n[task][Second](testB)\n";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testA", "testB"), List.of(), List.of(), true,
                    List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no student-facing instruction", "[task][First]", "[task][Second]");
        }

        @Test
        void passes_whenPlantUmlDirectivesSitInsideTheDiagramBlock() {
            sandbox.problemStatement = "# Title\n@startuml\nclass A\nhide empty fields\nhide empty methods\n@enduml\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void passes_whenDiagramTestsColorNamesResolve_includingParenthesisedAndStructuralForms() {
            sandbox.problemStatement = "# Title\n[task][Sort](testSortsAscending)\nImplement the sort operation.\n@startuml\nclass A {\n  <color:testsColor(testSortsAscending())>+sort()</color>\n}\n"
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

        @Test
        void unresolvedBindingFeedback_doesNotOfferAHiddenTestAsAReplacement() {
            sandbox.problemStatement = "# Title\n[task][Sort](testDoesNotExist)\n";
            sandbox.testPlanJson = "{\"tests\":[{\"name\":\"testSortsAscending\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                    + "{\"name\":\"testSortsAscending_hidden\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
            AgentVerifyReport lastTestsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testSortsAscending", "testSortsAscending_hidden"),
                    List.of(), List.of(), true, List.of());

            StageCheckResult result = check(GenerationStage.STATEMENT, lastTestsReport);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("testDoesNotExist", "testSortsAscending").doesNotContain("testSortsAscending_hidden");
        }
    }

    @Nested
    class ApprovedSpecification {

        private static final String APPROVED = specWithDesign("| RewardStrategy | strategy interface | student-creates |\n| LoyaltyAccount | context | stubbed |\n");

        private static final String DOWNGRADED = specWithDesign("| RewardStrategy | strategy interface | stubbed |\n| LoyaltyAccount | context | stubbed |\n");

        @BeforeEach
        void templateCompilesAndDiffers() {
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(testsRan(1, 5, 5, List.of("t1")));
            sandbox.diffExitCode = 1;
        }

        @Test
        void ignoresTheLiveDowngradeAndChecksTheTemplateAgainstTheApprovedSnapshot() {
            approvedSpecs.approve("s", APPROVED);
            sandbox.spec = DOWNGRADED;

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Confirmed absent from the template").contains("RewardStrategy");
        }

        @Test
        void stillEnforcesOmissionAfterTheLiveSpecDropsTheType() {
            // The live copy is irrelevant after approval; the frozen copy still enforces omission.
            approvedSpecs.approve("s", APPROVED);
            sandbox.spec = DOWNGRADED;
            sandbox.templateFindOutput = "/workspace/template/src/de/tum/RewardStrategy.java";

            StageCheckResult result = check(GenerationStage.TEMPLATE);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("must NOT contain").contains("RewardStrategy.java");
        }

        @Test
        void keepsTheApprovedDiagramPromise_afterTheLiveSpecRevokesIt() {
            approvedSpecs.approve("s",
                    specWithDesign("| Calculator | computes | stubbed |\n").replace("## Diagram\nno — single-class exercise", "## Diagram\nYes – several collaborating types"));
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n");
            sandbox.problemStatement = "# T\nImplement it.\n";

            StageCheckResult result = check(GenerationStage.STATEMENT, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no @startuml diagram");
        }
    }

    @Nested
    class HiddenVariantDeclaration {

        @Test
        void readsTheDecisionFromTheTablesLastCell() {
            assertThat(StageCheckService.specDeclaresHiddenVariants(specWithDesign("| Calculator | computes | stubbed |\n"))).isFalse();
            assertThat(StageCheckService.specDeclaresHiddenVariants(specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |",
                    "| S1 | Calculator | typical; zero | 3 | yes |"))).isTrue();
        }

        @Test
        void aSectionThatDeclinesHiddenVariantsInProseIsNotReadAsDeclaringThem() {
            // The prose heuristic this replaced fired on exactly this text: the words "hidden" and "after the due date" appear while the decision is NO. A false trigger here
            // forced the agent to invent hidden tests it had deliberately decided against.
            String spec = specWithDesign("| Calculator | computes | stubbed |\n").replace("| S1 | Calculator | typical; zero | 3 | no |",
                    "| S1 | Calculator | typical; zero | 3 | no |\n\n                Every test stays visible; no partition gets a hidden after-due-date variant.");

            assertThat(StageCheckService.specDeclaresHiddenVariants(spec)).isFalse();
        }

        @Test
        void aStructuralYesInAnotherColumnDoesNotCountAsAHiddenDeclaration() {
            // The old regex matched any "| yes" anywhere in the section, so an unrelated column could satisfy the hidden-variant declaration.
            String spec = specWithDesign("| Calculator | computes | stubbed |\n")
                    .replace("| Seam | Owner type | Observable responsibility | Weight | Hidden variant |", "| Seam | Owner type | Structural | Weight | Hidden variant |")
                    .replace("| S1 | Calculator | typical; zero | 3 | no |", "| S1 | Calculator | yes | 3 | no |");

            assertThat(StageCheckService.specDeclaresHiddenVariants(spec)).isFalse();
        }
    }

    @Nested
    class SpecDrift {

        @Test
        void laterStageFails_whenSpecMdWasEditedIntoAnInvalidSpecification() {
            // Observed live: a later stage appended a '## Tasks' section with [task] bindings and emptied the '## Diagram' decision, silently disarming the statement's
            // diagram-coherence check. The drift is caught where it happens instead of passing vacuously.
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compiled());
            sandbox.spec = sandbox.spec + "\n## Tasks\n[task][Do it](testDoIt)\n";

            StageCheckResult result = check(GenerationStage.SOLUTION);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("SPEC.md is no longer a valid specification").contains("must not contain [task] bindings")
                    .contains("belong in problem-statement.md");
        }

        @Test
        void laterStageRunsItsOwnCheck_whenThereIsNoSpecAtAll() {
            // The SPEC stage does not run when the instructor's own problem statement IS the specification, so a missing SPEC.md must never block a later stage.
            sandbox.spec = null;
            when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compiled());

            assertThat(check(GenerationStage.SOLUTION).passed()).isTrue();
        }
    }

    @Nested
    class Spec {

        private static final String VALID_SPEC = """
                # Exercise

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

                ## Public API
                `Calculator`: `int calculate(int input)`

                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |------|------------|------------|--------|----------------|
                | S1 | Calculator | typical and zero | 3 | yes |

                ## Diagram
                no — single-class exercise
                """;

        @Test
        void namesTheReflectionConsequenceOfStudentCreatedOwnershipWhenTheContractIsFrozen() {
            // Shift-left feedback at the decision boundary. A run that learned this only from downstream "cannot find symbol" errors cycled its entire repair budget between
            // re-adding the type to the template and having the ownership gate reject it, without anything naming the actual constraint.
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Stack | student work | student-creates |\n| OverflowPolicy | supplied policy | given |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("compile against the template too").contains("Stack").contains("Class.forName").contains("ownership gate will always reject");
        }

        /** A specification whose Testing Strategy carries exactly the seam rows given, with a rules table of the requested size. */
        private static String specWithRulesAndSeams(int rules, int seams) {
            StringBuilder ruleRows = new StringBuilder();
            for (int rule = 1; rule <= rules; rule++) {
                ruleRows.append("| R").append(rule).append(" | the calculator handles case ").append(rule).append(". |\n");
            }
            StringBuilder seamRows = new StringBuilder();
            for (int seam = 1; seam <= seams; seam++) {
                seamRows.append("| S").append(seam).append(" | Calculator | behaviour ").append(seam).append(" | 3 | no |\n");
            }
            return """
                    # Exercise

                    ## Rules
                    | ID | Rule |
                    |----|------|
                    """ + ruleRows + """

                    ## Worked Examples
                    | Rules | Input | Expected |
                    |-------|-------|----------|
                    | R1 | 2 | 4 |
                    | R1 | 3 | 9 |

                    ## Design
                    | Type | Role | Template status |
                    |------|------|-----------------|
                    | Calculator | computes | stubbed |

                    ## Public API
                    `Calculator`: `int calculate(int input)`

                    ## Testing Strategy
                    | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                    |------|------------|------------|--------|----------------|
                    """ + seamRows + """

                    ## Diagram
                    no — single-class exercise
                    """;
        }

        /** A gate-valid specification whose Rules section is exactly the rows given. */
        private static String specWithRules(String ruleRows) {
            return """
                    # Exercise

                    ## Rules
                    | ID | Rule |
                    |----|------|
                    """ + ruleRows + """

                    ## Worked Examples
                    | Rules | Input | Expected |
                    |-------|-------|----------|
                    | R1 | 2 | 4 |
                    | R1 | 3 | 9 |

                    ## Design
                    | Type | Role | Template status |
                    |------|------|-----------------|
                    | Calculator | computes | stubbed |

                    ## Public API
                    `Calculator`: `int calculate(int input)`

                    ## Testing Strategy
                    | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                    |------|------------|------------|--------|----------------|
                    | S1 | Calculator | typical and zero | 3 | no |

                    ## Diagram
                    no — single-class exercise
                    """;
        }

        @Test
        void rejectsASingleSeamThatSwallowsEveryRule() {
            // The shape the two weakest measured exercises had. One collapsed four rules into a single seam and shipped a suite rejecting two of four contract-breaking
            // implementations; six of twenty-six generated specifications took this shape despite the authoring prompt forbidding it.
            sandbox.spec = specWithRulesAndSeams(4, 1);

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("states 4 rules").contains("single seam").contains("independently actionable");
        }

        @Test
        void acceptsASingleSeamWhenTheExerciseGenuinelyHasFewRules() {
            // The floor is deliberately low: collapse is the target, not a decomposition the exercise does not need.
            sandbox.spec = specWithRulesAndSeams(3, 1);

            assertThat(check(GenerationStage.SPEC).passed()).isTrue();
        }

        @Test
        void countsRulesWrittenAsAPlainBulletedList() {
            // A rules section written as bullets is as common as a table. Requiring an explicit R-id counted it as zero rules, which left the decomposition check inert on
            // exactly the specifications that most often collapse.
            sandbox.spec = specWithRules("""
                    - The calculator handles the empty input.
                    - The calculator rejects a negative operand.
                    - The calculator rounds half up.
                    - The calculator reports overflow.
                    """);

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("states 4 rules").contains("single seam");
        }

        @Test
        void advisesOnATechniqueMandateWithoutRejectingTheSpecification() {
            // Rejecting this was measured wrong six times in eight, and a false rejection here discards a sound contract with no recourse. Said as advice on a pass it still
            // reaches the agent while the specification is editable, which is where it changes the outcome: the damage came from the seam the agent then wrote for the rule.
            sandbox.spec = specWithRules("| R1 | `sum` must be implemented recursively. |\n| R2 | `sum` returns 0 for an empty list. |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).as("advice, not a gate").isTrue();
            assertThat(result.observation()).contains("state an implementation technique").contains("do NOT give it a Testing Strategy seam")
                    .contains("read the student's source file");
        }

        @Test
        void saysNothingAboutTechniqueWhenNoRuleMandatesOne() {
            sandbox.spec = specWithRulesAndSeams(3, 1);

            assertThat(check(GenerationStage.SPEC).observation()).doesNotContain("state an implementation technique");
        }

        @Test
        void acceptsManyRulesOnceTheyAreSplitAcrossSeams() {
            sandbox.spec = specWithRulesAndSeams(7, 3);

            assertThat(check(GenerationStage.SPEC).passed()).isTrue();
        }

        @Test
        void omitsTheReflectionConsequenceWhenNoTypeIsStudentCreated() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Calculator | computes | stubbed |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).doesNotContain("Class.forName");
        }

        @Test
        void fails_whenEveryDesignRowIsStudentCreated_becauseTheTemplateWouldShipEmpty() {
            // The root cause of the empty-template defect: with no supplied or stubbed row the starter repository is empty by construction, whatever the builder does later.
            // Catching it at the contract keeps the agent from authoring a whole exercise around a design that cannot produce a teaching scaffold.
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| BaseShape | abstract base | student-creates |\n| Rectangle | concrete shape | student-creates |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("would ship empty").contains("at least one type 'given'").contains("'stubbed'");
        }

        @Test
        void passes_withRulesAndABranchingWorkedExamplesTable_andEchoesTheParsedTemplatePlan() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = VALID_SPEC;

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            // The pass observation echoes the parsed Design plan so the agent sees exactly what the later gates will enforce.
            assertThat(result.observation()).contains("Calculator=stubbed");
        }

        @Test
        void normalizesTypographicHyphensInTemplateStatusTokens() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            // A second, supplied row keeps the specification valid under the scaffold rule; the token under test is still the only student-created one.
            sandbox.spec = specWithDesign("| Calculator | computes the result | student‑creates |\n| Support | supplied helper | given |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Calculator=student-creates");
            assertThat(StageCheckService.specStudentCreatedTypes(sandbox.spec)).containsExactly("Calculator");
        }

        @Test
        void acceptsMarkdownEmphasisAroundAnOtherwiseExactTemplateStatusToken() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = specWithDesign("| Calculator | computes the result | **student‑creates** |\n| Support | supplied helper | given |\n");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("Calculator=student-creates");
            assertThat(StageCheckService.specStudentCreatedTypes(sandbox.spec)).containsExactly("Calculator");
        }

        @Test
        void fails_whenTheDesignSectionHasNoDataRows() {
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |\n", "");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("'## Design' section has no data rows");
        }

        @Test
        void rejectsAGivenJavaTypeWhosePublicApiDependsOnATypeAbsentFromTheTemplate() {
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |", """
                    | Calculator | given context | given |
                    | Policy | interchangeable policy | student-creates |\
                    """).replace("`Calculator`: `int calculate(int input)`", """
                    ### `Calculator`
                    - `public Calculator(Policy policy)`
                    - `public int calculate(int input)`

                    ### `Policy`
                    - `int calculate(int input)`\
                    """).replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | Policy | typical and zero | 3 | yes |");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("given Java types", "Calculator->Policy", "must ship complete and compile", "coherent ownership graph");
        }

        @Test
        void fails_whenADesignRowCarriesNoValidTemplateStatusToken() {
            // The old exemplar's verbose tokens ("student-creates-absent-from-template") are exactly what this catches: only the literal tokens are enforceable.
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |", "| Calculator | computes the result | student-implements-stubbed |");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no valid final Template status cell", "Calculator", "LAST cell", "Do not move the token into the Role cell");
        }

        @Test
        void fails_whenAStatusTokenAppearsBeforeAnInvalidFinalStatusCell() {
            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |", "| Calculator | student-creates | absent |");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("no valid final Template status cell", "Calculator", "LAST cell");
        }

        @Test
        void fails_whenTestingStrategySeamsAreMissingMalformedOrDuplicated() {
            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| compute | Calculator | typical and zero | 3 | no |");

            assertThat(check(GenerationStage.SPEC).observation()).contains("stable seam ID").contains("S1, S2");

            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | Calculator | typical | 3 | no |\n| S1 | Calculator | zero | 2 | no |");

            assertThat(check(GenerationStage.SPEC).observation()).contains("duplicate").contains("S1");
        }

        @Test
        void fails_whenASeamOwnerIsMissingUnknownOrGiven() {
            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | typical and zero | 3 | yes |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("Owner type", "S1");

            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | MissingType | typical and zero | 3 | yes |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("do not name a type", "S1->MissingType");

            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |", "| Calculator | computes the result | given |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("marked given", "S1->Calculator");
        }

        @Test
        void preservesJavaIdentifierUnderscoresAndAcceptsAnOptionalTrailingTablePipe() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = VALID_SPEC.replace("Calculator", "Damage_Strategy").replace("| S1 | Damage_Strategy | typical and zero | 3 | yes |",
                    "| S1 | Damage_Strategy | typical and zero | 3 | yes");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
            assertThat(result.observation()).contains("S1->Damage_Strategy(stubbed)");
            assertThat(StageCheckService.hiddenVariantSeamIds(sandbox.spec)).containsExactly("S1");
        }

        @Test
        void rejectsHiddenVariantsWhenTheExerciseHasNoDueDate() {
            sandbox.spec = VALID_SPEC;

            assertThat(check(GenerationStage.SPEC).observation()).contains("has no due date", "hidden indefinitely");
        }

        @Test
        void rejectsAMisnamedOwnerHeaderAndDuplicateDesignType() {
            sandbox.spec = VALID_SPEC.replace("| Seam | Owner type |", "| Seam | Partitions | ");
            assertThat(check(GenerationStage.SPEC).observation()).contains("second Testing Strategy column", "Owner type");

            sandbox.spec = VALID_SPEC.replace("| Calculator | computes the result | stubbed |",
                    "| Calculator | computes the result | stubbed |\n| Calculator | contradicts ownership | student-creates |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("same type more than once", "Calculator");
        }

        @Test
        void rejectsAMisnamedOrEmptyObservableResponsibility() {
            sandbox.spec = VALID_SPEC.replace("| Seam | Owner type | Observable responsibility |", "| Seam | Owner type | Partitions |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("third Testing Strategy column", "Observable responsibility");

            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | Calculator |  | 3 | yes |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("do not state an Observable responsibility", "S1");

            sandbox.spec = VALID_SPEC.replace("| S1 | Calculator | typical and zero | 3 | yes |", "| S1 | Calculator | typical and zero | core | yes |");
            assertThat(check(GenerationStage.SPEC).observation()).contains("weight tier", "1, 2, or 3", "S1");
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
                    .contains("## Public API").contains("## Testing Strategy").contains("## Diagram");
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
        void doesNotCountABlankHeaderCellOrTheSeparatorAsAWorkedExample() {
            sandbox.spec = VALID_SPEC.replace("""
                    | Rules | Input | Expected |
                    |-------|-------|----------|
                    | R1 | 2 | 4 |
                    | R1 | 3 | 9 |\
                    """, """
                    | Rules | Input | |
                    |-------|-------|---|
                    | R1 | 2 | 4 |\
                    """);

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("at least two data rows");
        }

        @Test
        void rejectsAHeadingThatOnlyStartsWithTheRequiredHeading() {
            sandbox.spec = VALID_SPEC.replace("## Design", "## Design notes");

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isFalse();
            assertThat(result.observation()).contains("missing required section", "## Design");
        }

        @Test
        void doesNotInferExampleQualityFromTheLastTableCellAlone() {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            sandbox.spec = VALID_SPEC.replace("""
                    | Rules | Input | Expected |
                    |-------|-------|----------|
                    | R1 | 2 | 4 |
                    | R1 | 3 | 9 |\
                    """, """
                    | Rules | Input | Expected order | Remaining capacity |
                    |-------|-------|----------------|--------------------|
                    | R1 | A, B | A, B | 4 |
                    | R1 | A, B | B, A | 4 |\
                    """);

            StageCheckResult result = check(GenerationStage.SPEC);

            assertThat(result.passed()).isTrue();
        }
    }

}
