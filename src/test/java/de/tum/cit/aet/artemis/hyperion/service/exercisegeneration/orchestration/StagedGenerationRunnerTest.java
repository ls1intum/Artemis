package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SingleBuildResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the enforced staged-generation runner: stage order and context, the shared turn-budget pool's rollover/cap/floor arithmetic, cooperative cancellation between
 * stages, and the wall-clock guard. Per-stage mechanical gate logic itself lives in {@link StageCheckService} and is unit-covered there
 * ({@code StageCheckServiceTest}); this suite wires a real {@link StageCheckService} around a mocked {@link DifferentialVerificationService} so the runner's own orchestration
 * (sequencing, budgets, re-entry, delegation) is exercised through the same seam production uses. A fake sandbox serves canned command output keyed by the exact commands the
 * gates issue for the file-based checks (SPEC.md, test-plan.json, problem-statement.md, the template/solution diff), following the same pattern as {@code SandboxAgentToolsTest}.
 */
class StagedGenerationRunnerTest {

    private static final String VALID_SPEC_DOCUMENT = """
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
            | Seam | Partitions | Weight | Hidden variant |
            |------|------------|--------|----------------|
            | S1 | typical and zero | 3 | no |

            ## Diagram
            no — single-class exercise
            """;

    /** A grading plan whose one name matches {@code passingReport()}'s exact test name, so the TESTS gate's plan check passes. */
    private static final String VALID_TEST_PLAN = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"weight\":2,\"visibility\":\"ALWAYS\"}]}";

    /** Records every command issued and serves canned results keyed by the exact commands {@link StagedGenerationRunner} runs. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private String specMarkdown = VALID_SPEC_DOCUMENT;

        private String testPlanJson = VALID_TEST_PLAN;

        private String problemStatement = "# Title\n\n[task][Do the thing](testFoo)\nImplement the calculator operation.";

        private String layout = "solution/pom.xml\ntemplate/pom.xml\ntests/pom.xml";

        /** {@code diff -rq} exit code; 1 means the trees differ (the expected, healthy case). */
        private int diffExitCode = 1;

        private final List<String> execLog = new ArrayList<>();

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            execLog.add(String.join(" ", command));
            if (command.length >= 2 && "cat".equals(command[0])) {
                String path = command[1];
                if (path.endsWith("SPEC.md")) {
                    return specMarkdown == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, specMarkdown, "", false);
                }
                if (path.endsWith("test-plan.json")) {
                    return testPlanJson == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, testPlanJson, "", false);
                }
                if (path.endsWith("problem-statement.md")) {
                    return problemStatement == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, problemStatement, "", false);
                }
            }
            if (command.length >= 3 && "sh".equals(command[0]) && "-c".equals(command[1])) {
                String script = command[2];
                if (script.contains("find") && script.contains("head -80")) {
                    return new SandboxExecResult(0, layout, "", false);
                }
            }
            if (command.length >= 1 && "diff".equals(command[0])) {
                return new SandboxExecResult(diffExitCode, "", "", false);
            }
            if (command.length >= 1 && "grep".equals(command[0])) {
                return new SandboxExecResult(0, "TODO S1:", "", false);
            }
            return success();
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

        private static SandboxExecResult success() {
            return new SandboxExecResult(0, "", "", false);
        }
    }

    private AgentLoopRunner agentLoopRunner;

    private AgentSystemPromptService systemPromptService;

    private DifferentialVerificationService verifier;

    private StageCheckService stageCheckService;

    private ApprovedSpecRegistry approvedSpecs;

    private SandboxAgentTools baseTools;

    private ProgrammingExercise exercise;

    private StagedGenerationRunner runner;

    private FakeSandbox sandbox;

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    /**
     * A {@link SingleBuildResult} that satisfies the "compiled" definition ({@code testsRun > 0 || exitCode == 0}) with no tests run at all — the solution/template compile
     * gates' default, healthy outcome before any test exists.
     */
    private static SingleBuildResult compiled() {
        return new SingleBuildResult(0, 0, 0, List.of(), "");
    }

    private static SingleBuildResult compileFailure(String buildOutput) {
        return new SingleBuildResult(1, 0, 0, List.of(), buildOutput);
    }

    @BeforeEach
    void setUp() {
        agentLoopRunner = mock(AgentLoopRunner.class);
        systemPromptService = mock(AgentSystemPromptService.class);
        verifier = mock(DifferentialVerificationService.class);
        baseTools = mock(SandboxAgentTools.class);
        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(systemPromptService.buildStage(any(), any())).thenReturn("STAGE SYSTEM PROMPT");
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compiled());
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compiled());
        // ONE registry shared by the runner and the gate, exactly as Spring wires them: the specification the spec gate approves is what every later gate enforces.
        approvedSpecs = new ApprovedSpecRegistry();
        stageCheckService = new StageCheckService(verifier, approvedSpecs);
        sandbox = new FakeSandbox();
        // Most of these tests pin the pre-existing single-call-per-stage behaviour (mocking agentLoopRunner.run(...)); FRESH reproduces that exactly. The CONTINUOUS-specific
        // tests below construct their own runner with "CONTINUOUS" and mock runSession(...) instead.
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "FRESH");
    }

    private StagedGenerationRunner newContinuousRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService) {
        return new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "CONTINUOUS");
    }

    private static AgentLoopRunner.AgentLoopSession session(AgentLoopResult result, List<Message> conversation) {
        return new AgentLoopRunner.AgentLoopSession(result, conversation);
    }

    private static AssistantMessage assistantText(String text) {
        return new AssistantMessage(text);
    }

    private static AgentLoopResult completed(int turns, String message) {
        return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, turns, message);
    }

    private static AgentVerifyReport passingReport() {
        return new AgentVerifyReport(5, true, List.of(), List.of(), 5, true, true, List.of(), List.of(), List.of("testFoo"), List.of(), List.of(), true, List.of());
    }

    private static AgentVerifyReport failingReport() {
        return new AgentVerifyReport(5, false, List.of("testFoo"), List.of(), 5, true, false, List.of(), List.of(), List.of("testFoo"), List.of(), List.of(), false,
                List.of("the solution does not pass"));
    }

    private AgentLoopResult run(BooleanSupplier cancelled, Supplier<Set<String>> structuralSeedHook) {
        return runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", cancelled, null, null, structuralSeedHook).result();
    }

    @Test
    void runsAllFiveStagesInOrder_withMatchingStageContextAndAggregatedResult() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(0 + 10 + 4 + 12 + 5);
        assertThat(result.finalMessage()).isEqualTo("statement done");
        assertThat(structuralSeedCalls.get()).as("the structural-oracle seeding hook runs exactly once, after the TEMPLATE gate passes").isEqualTo(1);

        InOrder inOrder = inOrder(baseTools, systemPromptService, agentLoopRunner);
        for (GenerationStage stage : List.of(GenerationStage.SOLUTION, GenerationStage.TEMPLATE, GenerationStage.TESTS, GenerationStage.STATEMENT)) {
            inOrder.verify(baseTools).enterStage(stage);
            inOrder.verify(systemPromptService).buildStage(exercise, stage);
            inOrder.verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        }
    }

    @Test
    void testsStageReport_isCarriedIntoTheStatementStagePromptOnly() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests"), completed(5, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        run(NEVER_CANCELLED, Set::of);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(5)).run(anyString(), userPromptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> userPrompts = userPromptCaptor.getAllValues();
        assertThat(userPrompts.get(2)).as("TEMPLATE has no prior verify report to inject").doesNotContain("MOST RECENT VERIFICATION REPORT");
        assertThat(userPrompts.get(3)).as("TESTS itself has no prior self-check report yet").doesNotContain("MOST RECENT VERIFICATION REPORT");
        assertThat(userPrompts.get(4)).as("STATEMENT sees the TESTS stage's self-check observation").contains("MOST RECENT VERIFICATION REPORT")
                .contains("Solution: 5/5 tests pass.");
    }

    @Test
    void solutionGateFailure_compileError_stopsBeforeTemplateStage() {
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compileFailure("compile error: cannot find symbol")); // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        // SOLUTION's first gate failure is granted one re-entry (Mockito repeats the last canned response for the extra call), which also fails.
        assertThat(result.turns()).isEqualTo(10 + 10);
        assertThat(result.finalMessage()).contains("reference solution does not compile").contains("compile error");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_degenerateCopy_stopsBeforeTestsStage() {
        sandbox.diffExitCode = 0; // template byte-identical to the solution, never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("byte-identical to the solution");
        // TEMPLATE's first gate failure is granted one re-entry, which also fails (the mocked degenerate-copy condition never changes).
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_doesNotCompile_stopsBeforeTestsStage() {
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compileFailure("compile error")); // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("template does not compile");
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void testsGateFailure_stopsBeforeStatementStage_butStructuralSeedingAlreadyRan() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(failingReport()); // never fixed
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.finalMessage()).contains("do not yet satisfy the differential requirement");
        // The structural-oracle seeding hook runs once when the TEMPLATE gate passes, regardless of how many times TESTS itself is re-entered.
        assertThat(structuralSeedCalls.get()).isEqualTo(1);
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void statementGateFailure_emptyStatement_reportsMissingOrEmpty() {
        sandbox.problemStatement = "  "; // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests"), completed(5, "statement attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("problem-statement.md is missing or empty");
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void errorStatus_stopsImmediatelyWithoutEvaluatingTheGate() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "the sandbox stopped responding"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.turns()).isEqualTo(1);
        assertThat(result.finalMessage()).isEqualTo("the sandbox stopped responding");
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void cancellationBetweenStages_stopsAndReturnsCancelledWithTurnsSoFar() {
        AtomicInteger calls = new AtomicInteger();
        BooleanSupplier cancelled = () -> calls.incrementAndGet() > 2;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(2, "solution done"));

        AgentLoopResult result = run(cancelled, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(result.turns()).isEqualTo(2);
        verify(agentLoopRunner, times(2)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void wallClockBudgetExceeded_skipsRemainingStagesAndReturnsWhatExists() {
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(startedAt);
        runner.setClockForTests(now::get);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            now.set(now.get().plus(Duration.ofMinutes(25)));
            return completed(5, "spec done");
        });

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.turns()).isEqualTo(5);
        assertThat(result.finalMessage()).isEqualTo("spec done");
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void budgetPool_rolloverAndCapArithmeticAcrossAllFiveStages() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(25, "solution"),
                completed(1, "template"), completed(24, "tests"), completed(7, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        ArgumentCaptor<Integer> maxTurnsCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), maxTurnsCaptor.capture(), any(), any(), any());
        // base 7/22/8/24/7; unspent turns roll fully into the next stage's allocation, capped by the remaining 78-turn pool:
        // SPEC 7 (uses 0, rollover 7) -> SOLUTION 22+7=29 (uses 25, rollover 4) -> TEMPLATE 8+4=12 (uses 1, rollover 11)
        // -> TESTS 24+11=35 (uses 24, rollover 11) -> STATEMENT 7+11=18.
        assertThat(maxTurnsCaptor.getAllValues()).containsExactly(7, 29, 12, 35, 18);
        assertThat(result.turns()).isEqualTo(0 + 25 + 1 + 24 + 7);
    }

    @Test
    void allocateStageBudget_appliesFloorAndRemainingPoolCap() {
        assertThat(StagedGenerationRunner.allocateStageBudget(5, 0, 78)).isEqualTo(5);
        assertThat(StagedGenerationRunner.allocateStageBudget(22, 3, 73)).isEqualTo(25);
        assertThat(StagedGenerationRunner.allocateStageBudget(22, 60, 10)).as("the remaining pool caps an oversized rollover").isEqualTo(10);
        assertThat(StagedGenerationRunner.allocateStageBudget(5, 0, 1)).as("every stage gets at least the floor, even over a near-empty pool").isEqualTo(3);
    }

    // --- Gate observability (progress events on every gate evaluation) ---

    @Test
    void gateEvaluations_emitAPassOrFailProgressEventInTheExistingLabelVoice() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 1/5: specification gate passed", "Stage 2/5: solution gate passed", "Stage 3/5: template gate passed",
                "Stage 4/5: tests gate passed", "Stage 5/5: statement gate passed");
    }

    @Test
    void gateEvaluations_emitABoundedFailureLineNamingTheFirstReportLine() {
        sandbox.problemStatement = "  "; // the STATEMENT gate fails with a single-line report; the retry attempt also fails, leaving the line to inspect
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests"), completed(5, "statement attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of);

        assertThat(progressEvents).anyMatch(
                event -> event.equals("Stage 5/5: statement gate failed: problem-statement.md is missing or empty. Write the student-facing problem statement before submitting."));
    }

    // --- Gated re-entry: one retry per stage on the first gate failure, gate feedback fed back in ---

    @Test
    void gateFailure_reEntersTheSameStageOnceThenReturnsOnASecondFailure() {
        // never fixed: both attempts fail
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compileFailure("compile error: cannot find symbol"));
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution attempt 1"),
                completed(8, "solution attempt 2"));
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).contains("reference solution does not compile").contains("compile error");
        // budget accounting: the aggregated turn count includes both the failed first attempt and the re-entry.
        assertThat(result.turns()).isEqualTo(10 + 8);
        assertThat(progressEvents).contains("Stage 2/5: retrying after gate feedback");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void specGateFailureThreeTimes_returnsErrorSoGenericVerificationCannotBypassTheUnapprovedContract() {
        sandbox.specMarkdown = null;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec attempt 1"), completed(1, "spec attempt 2"),
                completed(1, "spec attempt 3"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.finalMessage()).contains("SPEC.md is missing or empty");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, never()).singleBuild(any(), anyString(), any(), anyString());
    }

    @Test
    void gateFailure_reEntryThatFixesTheGate_continuesToTheNextStageWithTheFeedbackInThePrompt() {
        sandbox.diffExitCode = 0; // the TEMPLATE starts as a degenerate byte-identical copy of the solution
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compiled());
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(0, "spec done");
            case 2 -> completed(10, "solution");
            case 3 -> completed(4, "template attempt 1"); // fails the gate: degenerate copy
            case 4 -> {
                sandbox.diffExitCode = 1; // the retry "removes the student work", as a real agent turn would
                yield completed(3, "template attempt 2");
            }
            case 5 -> completed(12, "tests");
            case 6 -> completed(5, "statement");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("statement");
        assertThat(result.turns()).isEqualTo(10 + 4 + 3 + 12 + 5);
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        assertThat(progressEvents).contains("Stage 3/5: retrying after gate feedback", "Stage 3/5: template gate passed")
                .anyMatch(event -> event.startsWith("Stage 3/5: template gate failed"));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(6)).run(anyString(), userPromptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(userPromptCaptor.getAllValues().get(3)).as("FRESH re-entry folds the gate feedback into the rebuilt stage prompt")
                .contains("GATE FEEDBACK FROM THE PREVIOUS ATTEMPT AT THIS STAGE").contains("byte-identical to the solution");
    }

    @Test
    void reentryCap_atMostTwoReentriesAcrossTheWholeRun() {
        AtomicReference<SingleBuildResult> solutionBuild = new AtomicReference<>(compileFailure("solution compile error")); // SOLUTION gate fails first
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenAnswer(invocation -> solutionBuild.get());
        AtomicReference<SingleBuildResult> templateBuild = new AtomicReference<>(compileFailure("template compile error")); // TEMPLATE gate fails first
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenAnswer(invocation -> templateBuild.get());
        // TESTS gate fails and gets NO re-entry (cap spent)
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(failingReport());
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(0, "spec done");
            case 2 -> completed(10, "solution attempt 1");
            case 3 -> {
                solutionBuild.set(compiled());
                yield completed(8, "solution attempt 2");
            }
            case 4 -> completed(4, "template attempt 1");
            case 5 -> {
                templateBuild.set(compiled());
                yield completed(3, "template attempt 2");
            }
            case 6 -> completed(12, "tests attempt 1");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.finalMessage()).as("the TESTS gate failure is reported directly: the run's two-reentry cap was already spent by SOLUTION and TEMPLATE")
                .contains("do not yet satisfy the differential requirement");
        assertThat(result.turns()).isEqualTo(10 + 8 + 4 + 3 + 12);
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    // --- CONTINUOUS staged-context strategy: one carried conversation across stages (and re-entries) ---

    @Test
    void continuousMode_carriesTheConversationAcrossStagesFromStageTwoOnwards() {
        AgentLoopRunner sessionAgentLoopRunner = mock(AgentLoopRunner.class);
        StagedGenerationRunner continuousRunner = newContinuousRunner(sessionAgentLoopRunner, systemPromptService, stageCheckService);
        List<Message> convAfterSolution = List.of(assistantText("solution done"));
        List<Message> convAfterTemplate = List.of(assistantText("template done"));
        List<Message> convAfterTests = List.of(assistantText("tests done"));
        List<Message> convAfterStatement = List.of(assistantText("statement done"));
        List<Message> convAfterSpec = List.of(assistantText("spec done"));
        when(sessionAgentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(session(completed(0, "spec done"), convAfterSpec),
                session(completed(10, "solution done"), convAfterSolution), session(completed(4, "template done"), convAfterTemplate),
                session(completed(12, "tests done"), convAfterTests), session(completed(5, "statement done"), convAfterStatement));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = continuousRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(0 + 10 + 4 + 12 + 5);
        verify(sessionAgentLoopRunner, never()).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> priorConversationCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionAgentLoopRunner, times(5)).runSession(anyString(), priorConversationCaptor.capture(), anyString(), any(), anyInt(), any(), any(), any());
        List<List<Message>> priorConversations = priorConversationCaptor.getAllValues();
        assertThat(priorConversations.get(0)).as("SPEC is the first stage: nothing to carry yet").isNull();
        assertThat(priorConversations.get(1)).as("SOLUTION carries SPEC's returned conversation").isSameAs(convAfterSpec);
        assertThat(priorConversations.get(2)).isSameAs(convAfterSolution);
        assertThat(priorConversations.get(3)).isSameAs(convAfterTemplate);
        assertThat(priorConversations.get(4)).isSameAs(convAfterTests);
    }

    @Test
    void freshMode_neverUsesRunSessionOrCarriesAConversation() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void continuousMode_reEntry_appendsTheGateReportAsTheNextUserMessageInsteadOfARebuiltPrompt() {
        AgentLoopRunner sessionAgentLoopRunner = mock(AgentLoopRunner.class);
        StagedGenerationRunner continuousRunner = newContinuousRunner(sessionAgentLoopRunner, systemPromptService, stageCheckService);
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicReference<SingleBuildResult> solutionBuild = new AtomicReference<>(compileFailure("compile error: cannot find symbol"));
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenAnswer(invocation -> solutionBuild.get());
        List<Message> convAfterFailedSolution = List.of(assistantText("solution attempt 1"));
        List<Message> convAfterFixedSolution = List.of(assistantText("solution attempt 2"));
        AtomicInteger callCount = new AtomicInteger();
        List<Message> convAfterSpec = List.of(assistantText("spec done"));
        when(sessionAgentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return session(completed(0, "spec done"), convAfterSpec);
            }
            if (call == 2) {
                return session(completed(5, "solution attempt 1"), convAfterFailedSolution);
            }
            solutionBuild.set(compiled());
            return session(completed(3, "solution attempt 2"), convAfterFixedSolution);
        });

        continuousRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> priorConversationCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(sessionAgentLoopRunner, org.mockito.Mockito.atLeast(2)).runSession(anyString(), priorConversationCaptor.capture(), userPromptCaptor.capture(), any(), anyInt(),
                any(), any(), any());
        assertThat(priorConversationCaptor.getAllValues().get(0)).isNull();
        assertThat(priorConversationCaptor.getAllValues().get(2)).as("the retry carries the failed attempt's own returned conversation").isSameAs(convAfterFailedSolution);
        assertThat(userPromptCaptor.getAllValues().get(2)).as("CONTINUOUS re-entry hands back only the gate report, not a rebuilt SPEC.md/layout prompt")
                .startsWith("The previous attempt at this stage did not pass its gate.").contains("reference solution does not compile")
                .doesNotContain("CURRENT WORKSPACE LAYOUT", "CURRENT SPEC.md");
    }

    @Test
    void constructor_rejectsAnUnknownStagedContextValue() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "sometimes"))
                .withMessageContaining("staged-context");
    }

    // --- Clean-skip exit gate: a stage whose check already passed in-loop, with no edits since, is reused instead of re-checked ---

    @Test
    void exitGate_reusesTheToolsCachedPassingCheck_withoutCallingTheCheckServiceAgain() {
        // A live SOLUTION check would FAIL here (the build never compiles), so the run only completes because the exit gate consulted the cache instead of re-running the check.
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compileFailure("compile error"));
        when(baseTools.reuseCachedPassingCheck(GenerationStage.SOLUTION)).thenReturn(Optional.of(StageCheckResult.passed("cached SOLUTION observation")));
        StageCheckService spiedService = spy(stageCheckService);
        StagedGenerationRunner testRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, spiedService, new AgentTranscriptWriter(""), approvedSpecs, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = testRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 2/5: solution gate passed (reused in-stage check)");
        verify(spiedService, never()).check(eq(GenerationStage.SOLUTION), any(), anyString(), eq(exercise), any(), any());
    }

    @Test
    void exitGate_whenTheToolsReportDirty_reCallsTheCheckServiceAndDoesNotAddTheReusedSuffix() {
        // baseTools.reuseCachedPassingCheck(...) is unstubbed on the mock -> Optional.empty() (the default "dirty" state), so every stage's exit gate must call the live service.
        StageCheckService spiedService = spy(stageCheckService);
        StagedGenerationRunner testRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, spiedService, new AgentTranscriptWriter(""), approvedSpecs, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = testRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 1/5: specification gate passed", "Stage 2/5: solution gate passed").noneMatch(event -> event.contains("reused in-stage check"));
        verify(spiedService, times(1)).check(eq(GenerationStage.SPEC), any(), anyString(), eq(exercise), any(), any());
        verify(spiedService, times(1)).check(eq(GenerationStage.SOLUTION), any(), anyString(), eq(exercise), any(), any());
    }

    @Test
    void testsGate_recordsItsReportOnTheToolsInstanceForTheStatementStagesBindingCheck() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(baseTools).recordLastTestsReport(passingReport());
    }
    // --- SPEC stage: skip semantics, private retry, snapshot sink ---

    @Test
    void specStage_isSkippedWhenTheInstructorAlreadyProvidedTheSpecification() {
        // 4 loop calls only: SOLUTION..STATEMENT. The SPEC stub is absent on purpose — a call for it would consume the solution stub and break the sequence.
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(10, "solution done"), completed(4, "template done"),
                completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of, false, null)
                .result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        assertThat(progressEvents).noneMatch(event -> event.contains("specifying the exercise"));
    }

    @Test
    void specGateFailure_getsAPrivateRetryThatDoesNotConsumeTheSharedReentryBudget() {
        // SPEC fails once (empty file), its private retry fixes it; SOLUTION and TEMPLATE then each fail once and must BOTH still get their shared re-entries.
        sandbox.specMarkdown = null;
        AtomicReference<SingleBuildResult> solutionBuild = new AtomicReference<>(compileFailure("solution compile error"));
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenAnswer(invocation -> solutionBuild.get());
        AtomicReference<SingleBuildResult> templateBuild = new AtomicReference<>(compileFailure("template compile error"));
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenAnswer(invocation -> templateBuild.get());
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(1, "spec attempt 1");
            case 2 -> {
                sandbox.specMarkdown = VALID_SPEC_DOCUMENT;
                yield completed(1, "spec attempt 2");
            }
            case 3 -> completed(10, "solution attempt 1");
            case 4 -> {
                solutionBuild.set(compiled());
                yield completed(8, "solution attempt 2");
            }
            case 5 -> completed(4, "template attempt 1");
            case 6 -> {
                templateBuild.set(compiled());
                yield completed(3, "template attempt 2");
            }
            case 7 -> completed(12, "tests");
            case 8 -> completed(5, "statement");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.status()).as("both shared re-entries were still available for SOLUTION and TEMPLATE because SPEC's retry was private")
                .isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(8)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void specStageCanMaterializeTheArtifactThenRefineItFromMechanicalFeedback() {
        sandbox.specMarkdown = null;
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(1, "spec returned as prose instead of written");
            case 2 -> {
                sandbox.specMarkdown = VALID_SPEC_DOCUMENT.replace("| Calculator | computes the result | stubbed |", "| Calculator | computes the result | supplied |");
                yield completed(1, "spec materialized with a malformed status");
            }
            case 3 -> {
                sandbox.specMarkdown = VALID_SPEC_DOCUMENT;
                yield completed(1, "spec mechanically refined");
            }
            case 4 -> completed(2, "solution");
            case 5 -> completed(2, "template");
            case 6 -> completed(2, "tests");
            case 7 -> completed(2, "statement");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(7)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void specGatePass_handsTheSnapshotToTheSpecSink() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicReference<String> snapshot = new AtomicReference<>();

        runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of, true, snapshot::set).result();

        assertThat(snapshot.get()).as("the sink receives the gate-approved SPEC.md verbatim").isEqualTo(VALID_SPEC_DOCUMENT);
    }

    @Test
    void semanticSpecFinding_getsOneRefinementThenFreezesTheAcceptedRevisionUsingTheRawBrief() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(eq("RAW BRIEF"), anyString(), eq(null), any())).thenReturn(
                new SpecFidelityCriticService.SpecificationReview(true, List.of("The explicit ownership is missing.")),
                new SpecFidelityCriticService.SpecificationReview(true, List.of()));
        StagedGenerationRunner semanticRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs,
                reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(1, "refined spec"),
                completed(2, "solution"), completed(2, "template"), completed(2, "tests"), completed(2, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicReference<String> snapshot = new AtomicReference<>();

        AgentLoopResult result = semanticRunner.run(exercise, baseTools, baseTools, "authoring context with generated material", "RAW BRIEF", Map.of(), sandbox, "s",
                NEVER_CANCELLED, null, null, Set::of, true, snapshot::set).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(snapshot.get()).isEqualTo(VALID_SPEC_DOCUMENT);
        assertThat(approvedSpecs.approved("s")).contains(VALID_SPEC_DOCUMENT);
        verify(reviewer, times(2)).reviewSpecification(eq("RAW BRIEF"), eq(VALID_SPEC_DOCUMENT), eq(null), any());
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void secondGroundedSemanticSpecRejection_stopsWithoutFreezingTheContract() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), eq(null), any())).thenReturn(
                new SpecFidelityCriticService.SpecificationReview(true, List.of("missing ownership")),
                new SpecFidelityCriticService.SpecificationReview(true, List.of("still missing ownership")));
        StagedGenerationRunner semanticRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs,
                reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(1, "refined spec"));

        AgentLoopResult result = semanticRunner.run(exercise, baseTools, baseTools, "context", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of, true, null)
                .result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.finalMessage()).contains("still missing ownership");
        assertThat(approvedSpecs.approved("s")).isEmpty();
        verify(agentLoopRunner, times(2)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void unavailableSemanticSpecReview_stopsBeforeFreezingAnUncheckedContract() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), eq(null), any())).thenReturn(new SpecFidelityCriticService.SpecificationReview(false, List.of()));
        StagedGenerationRunner semanticRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs,
                reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"));

        AgentLoopResult result = semanticRunner.run(exercise, baseTools, baseTools, "context", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of, true, null)
                .result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.finalMessage()).contains("Specification fidelity review was unavailable");
        assertThat(approvedSpecs.approved("s")).isEmpty();
        verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void cancellationDuringSemanticSpecReview_remainsCancelledAndDoesNotFreezeTheContract() {
        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), eq(null), any())).thenAnswer(invocation -> {
            cancelled.set(true);
            return new SpecFidelityCriticService.SpecificationReview(false, List.of());
        });
        StagedGenerationRunner semanticRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs,
                reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"));

        AgentLoopResult result = semanticRunner
                .run(exercise, baseTools, baseTools, "context", "brief", Map.of(), sandbox, "s", () -> cancelled.get(), null, null, Set::of, true, null).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(approvedSpecs.approved("s")).isEmpty();
    }

}
