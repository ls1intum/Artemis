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
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
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
 * gates issue for the file-based checks (DESIGN.md, problem-statement.md, the template/solution diff), following the same pattern as {@code SandboxAgentToolsTest}.
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
            """;

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

    /** Records every command issued and serves canned results keyed by the exact commands {@link StagedGenerationRunner} runs. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private String specMarkdown = VALID_SPEC_DOCUMENT;

        private String designMarkdown = VALID_DESIGN_DOCUMENT;

        private String problemStatement = "# Title\n\nDo the thing.";

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
                if (path.endsWith("DESIGN.md")) {
                    return designMarkdown == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, designMarkdown, "", false);
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
        stageCheckService = new StageCheckService(verifier);
        sandbox = new FakeSandbox();
        // Most of these tests pin the pre-existing single-call-per-stage behaviour (mocking agentLoopRunner.run(...)); FRESH reproduces that exactly. The CONTINUOUS-specific
        // tests below construct their own runner with "CONTINUOUS" and mock runSession(...) instead.
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), "FRESH");
    }

    private static StagedGenerationRunner newContinuousRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService) {
        return new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), "CONTINUOUS");
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
    void runsAllSixStagesInOrder_withMatchingStageContextAndAggregatedResult() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(0 + 3 + 10 + 4 + 12 + 5);
        assertThat(result.finalMessage()).isEqualTo("statement done");
        assertThat(structuralSeedCalls.get()).as("the structural-oracle seeding hook runs exactly once, after the TEMPLATE gate passes").isEqualTo(1);

        InOrder inOrder = inOrder(baseTools, systemPromptService, agentLoopRunner);
        for (GenerationStage stage : List.of(GenerationStage.DESIGN, GenerationStage.SOLUTION, GenerationStage.TEMPLATE, GenerationStage.TESTS, GenerationStage.STATEMENT)) {
            inOrder.verify(baseTools).enterStage(stage);
            inOrder.verify(systemPromptService).buildStage(exercise, stage);
            inOrder.verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        }
    }

    @Test
    void testsStageReport_isCarriedIntoTheStatementStagePromptOnly() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design"),
                completed(10, "solution"), completed(4, "template"), completed(12, "tests"), completed(5, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        run(NEVER_CANCELLED, Set::of);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(6)).run(anyString(), userPromptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> userPrompts = userPromptCaptor.getAllValues();
        assertThat(userPrompts.get(3)).as("TEMPLATE has no prior verify report to inject").doesNotContain("MOST RECENT VERIFICATION REPORT");
        assertThat(userPrompts.get(4)).as("TESTS itself has no prior self-check report yet").doesNotContain("MOST RECENT VERIFICATION REPORT");
        assertThat(userPrompts.get(5)).as("STATEMENT sees the TESTS stage's self-check observation").contains("MOST RECENT VERIFICATION REPORT")
                .contains("Solution: 5/5 tests pass.");
    }

    @Test
    void designGateFailure_stopsBeforeSolutionStageAndReportsWhichSectionsAreMissing() {
        sandbox.designMarkdown = "no headings here"; // never fixed: the gated re-entry (see below) also fails, so the run stops after its one re-entry
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        // The DESIGN gate's first failure is granted one re-entry (Mockito repeats the same canned response), which also fails, so the run stops with both attempts' turns.
        assertThat(result.turns()).isEqualTo(5 + 5);
        assertThat(result.finalMessage()).contains("DESIGN.md is missing required section(s)").contains("## Classes");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void designGateFailure_missingFile_reportsMissingOrEmpty() {
        sandbox.designMarkdown = null;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("DESIGN.md is missing or empty");
    }

    @Test
    void solutionGateFailure_compileError_stopsBeforeTemplateStage() {
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compileFailure("compile error: cannot find symbol")); // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        // SOLUTION's first gate failure is granted one re-entry (Mockito repeats the last canned response for the extra call), which also fails.
        assertThat(result.turns()).isEqualTo(5 + 10 + 10);
        assertThat(result.finalMessage()).contains("reference solution does not compile").contains("compile error");
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_degenerateCopy_stopsBeforeTestsStage() {
        sandbox.diffExitCode = 0; // template byte-identical to the solution, never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution"), completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("byte-identical to the solution");
        // TEMPLATE's first gate failure is granted one re-entry, which also fails (the mocked degenerate-copy condition never changes).
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_doesNotCompile_stopsBeforeTestsStage() {
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compileFailure("compile error")); // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution"), completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("template does not compile");
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void testsGateFailure_stopsBeforeStatementStage_butStructuralSeedingAlreadyRan() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution"), completed(4, "template"), completed(12, "tests attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(failingReport()); // never fixed
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.finalMessage()).contains("do not yet satisfy the differential requirement");
        // The structural-oracle seeding hook runs once when the TEMPLATE gate passes, regardless of how many times TESTS itself is re-entered.
        assertThat(structuralSeedCalls.get()).isEqualTo(1);
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void statementGateFailure_emptyStatement_reportsMissingOrEmpty() {
        sandbox.problemStatement = "  "; // never fixed
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution"), completed(4, "template"), completed(12, "tests"), completed(5, "statement attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("problem-statement.md is missing or empty");
        verify(agentLoopRunner, times(7)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
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
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(2, "design done"));

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
            return completed(5, "design done");
        });

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.turns()).isEqualTo(5);
        assertThat(result.finalMessage()).isEqualTo("design done");
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void budgetPool_rolloverAndCapArithmeticAcrossAllSixStages() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(2, "design"),
                completed(25, "solution"), completed(1, "template"), completed(24, "tests"), completed(7, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        ArgumentCaptor<Integer> maxTurnsCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), maxTurnsCaptor.capture(), any(), any(), any());
        // base 4/5/22/8/24/7; unspent turns roll fully into the next stage's allocation, capped by the remaining 78-turn pool:
        // SPEC 4 (uses 0, rollover 4) -> DESIGN 5+4=9 (uses 2, rollover 7) -> SOLUTION 22+7=29 (uses 25, rollover 4) -> TEMPLATE 8+4=12 (uses 1, rollover 11)
        // -> TESTS 24+11=35 (uses 24, rollover 11) -> STATEMENT 7+11=18.
        assertThat(maxTurnsCaptor.getAllValues()).containsExactly(4, 9, 29, 12, 35, 18);
        assertThat(result.turns()).isEqualTo(0 + 2 + 25 + 1 + 24 + 7);
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
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 2/6: design gate passed", "Stage 3/6: solution gate passed", "Stage 4/6: template gate passed", "Stage 5/6: tests gate passed",
                "Stage 6/6: statement gate passed");
    }

    @Test
    void gateEvaluations_emitABoundedFailureLineNamingTheFirstReportLine() {
        sandbox.designMarkdown = "no headings here";
        // Second (retry) attempt also fails, so the run stops with only one progress line per attempt to inspect.
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design attempt 1"),
                completed(3, "design attempt 2"));
        List<String> progressEvents = new ArrayList<>();

        runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of);

        assertThat(progressEvents).anyMatch(event -> event
                .equals("Stage 2/6: design gate failed: DESIGN.md is missing required section(s): [## Classes, ## Public API, ## Tasks, ## Diagram]. Add them before continuing."));
    }

    // --- Gated re-entry: one retry per stage on the first gate failure, gate feedback fed back in ---

    @Test
    void gateFailure_reEntersTheSameStageOnceThenReturnsOnASecondFailure() {
        // never fixed: both attempts fail
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenReturn(compileFailure("compile error: cannot find symbol"));
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(5, "design"),
                completed(10, "solution attempt 1"), completed(8, "solution attempt 2"));
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.finalMessage()).contains("reference solution does not compile").contains("compile error");
        // budget accounting: the aggregated turn count includes both the failed first attempt and the re-entry.
        assertThat(result.turns()).isEqualTo(5 + 10 + 8);
        assertThat(progressEvents).contains("Stage 3/6: retrying after gate feedback");
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void gateFailure_reEntryThatFixesTheGate_continuesToTheNextStageWithTheFeedbackInThePrompt() {
        sandbox.designMarkdown = "no headings here";
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(0, "spec done");
            case 2 -> completed(5, "design attempt 1"); // fails the gate: missing headings
            case 3 -> {
                sandbox.designMarkdown = VALID_DESIGN_DOCUMENT; // the retry "writes" a fix, as a real agent turn would
                yield completed(3, "design attempt 2");
            }
            case 4 -> completed(10, "solution");
            case 5 -> completed(4, "template");
            case 6 -> completed(12, "tests");
            case 7 -> completed(5, "statement");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("statement");
        assertThat(result.turns()).isEqualTo(5 + 3 + 10 + 4 + 12 + 5);
        verify(agentLoopRunner, times(7)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        assertThat(progressEvents).contains("Stage 2/6: retrying after gate feedback", "Stage 2/6: design gate passed")
                .anyMatch(event -> event.startsWith("Stage 2/6: design gate failed"));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(7)).run(anyString(), userPromptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(userPromptCaptor.getAllValues().get(2)).as("FRESH re-entry folds the gate feedback into the rebuilt stage prompt")
                .contains("GATE FEEDBACK FROM THE PREVIOUS ATTEMPT AT THIS STAGE").contains("DESIGN.md is missing required section(s)");
    }

    @Test
    void reentryCap_atMostTwoReentriesAcrossTheWholeRun() {
        sandbox.designMarkdown = "no headings here"; // DESIGN gate fails first
        AtomicReference<SingleBuildResult> solutionBuild = new AtomicReference<>(compileFailure("solution compile error")); // SOLUTION gate fails first
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenAnswer(invocation -> solutionBuild.get());
        // TEMPLATE gate fails and gets NO re-entry (cap spent)
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("template"))).thenReturn(compileFailure("template compile error"));
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(0, "spec done");
            case 2 -> completed(5, "design attempt 1");
            case 3 -> {
                sandbox.designMarkdown = VALID_DESIGN_DOCUMENT;
                yield completed(3, "design attempt 2");
            }
            case 4 -> completed(10, "solution attempt 1");
            case 5 -> {
                solutionBuild.set(compiled());
                yield completed(8, "solution attempt 2");
            }
            case 6 -> completed(4, "template attempt 1");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.finalMessage()).as("the TEMPLATE gate failure is reported directly: the run's two-reentry cap was already spent by DESIGN and SOLUTION")
                .contains("template does not compile");
        assertThat(result.turns()).isEqualTo(5 + 3 + 10 + 8 + 4);
        verify(agentLoopRunner, times(6)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    // --- CONTINUOUS staged-context strategy: one carried conversation across stages (and re-entries) ---

    @Test
    void continuousMode_carriesTheConversationAcrossStagesFromStageTwoOnwards() {
        AgentLoopRunner sessionAgentLoopRunner = mock(AgentLoopRunner.class);
        StagedGenerationRunner continuousRunner = newContinuousRunner(sessionAgentLoopRunner, systemPromptService, stageCheckService);
        List<Message> convAfterDesign = List.of(assistantText("design done"));
        List<Message> convAfterSolution = List.of(assistantText("solution done"));
        List<Message> convAfterTemplate = List.of(assistantText("template done"));
        List<Message> convAfterTests = List.of(assistantText("tests done"));
        List<Message> convAfterStatement = List.of(assistantText("statement done"));
        List<Message> convAfterSpec = List.of(assistantText("spec done"));
        when(sessionAgentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(session(completed(0, "spec done"), convAfterSpec),
                session(completed(3, "design done"), convAfterDesign), session(completed(10, "solution done"), convAfterSolution),
                session(completed(4, "template done"), convAfterTemplate), session(completed(12, "tests done"), convAfterTests),
                session(completed(5, "statement done"), convAfterStatement));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = continuousRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(0 + 3 + 10 + 4 + 12 + 5);
        verify(sessionAgentLoopRunner, never()).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> priorConversationCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionAgentLoopRunner, times(6)).runSession(anyString(), priorConversationCaptor.capture(), anyString(), any(), anyInt(), any(), any(), any());
        List<List<Message>> priorConversations = priorConversationCaptor.getAllValues();
        assertThat(priorConversations.get(0)).as("SPEC is the first stage: nothing to carry yet").isNull();
        assertThat(priorConversations.get(1)).as("DESIGN carries SPEC's returned conversation").isSameAs(convAfterSpec);
        assertThat(priorConversations.get(2)).isSameAs(convAfterDesign);
        assertThat(priorConversations.get(3)).isSameAs(convAfterSolution);
        assertThat(priorConversations.get(4)).isSameAs(convAfterTemplate);
        assertThat(priorConversations.get(5)).isSameAs(convAfterTests);
    }

    @Test
    void freshMode_neverUsesRunSessionOrCarriesAConversation() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
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
        sandbox.designMarkdown = "no headings here";
        List<Message> convAfterFailedDesign = List.of(assistantText("design attempt 1"));
        List<Message> convAfterFixedDesign = List.of(assistantText("design attempt 2"));
        AtomicInteger callCount = new AtomicInteger();
        List<Message> convAfterSpec = List.of(assistantText("spec done"));
        when(sessionAgentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return session(completed(0, "spec done"), convAfterSpec);
            }
            if (call == 2) {
                return session(completed(5, "design attempt 1"), convAfterFailedDesign);
            }
            sandbox.designMarkdown = VALID_DESIGN_DOCUMENT;
            return session(completed(3, "design attempt 2"), convAfterFixedDesign);
        });

        continuousRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> priorConversationCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(sessionAgentLoopRunner, org.mockito.Mockito.atLeast(2)).runSession(anyString(), priorConversationCaptor.capture(), userPromptCaptor.capture(), any(), anyInt(),
                any(), any(), any());
        assertThat(priorConversationCaptor.getAllValues().get(0)).isNull();
        assertThat(priorConversationCaptor.getAllValues().get(2)).as("the retry carries the failed attempt's own returned conversation").isSameAs(convAfterFailedDesign);
        assertThat(userPromptCaptor.getAllValues().get(2)).as("CONTINUOUS re-entry hands back only the gate report, not a rebuilt DESIGN.md/layout prompt")
                .startsWith("The previous attempt at this stage did not pass its gate.").contains("DESIGN.md is missing required section(s)")
                .doesNotContain("CURRENT WORKSPACE LAYOUT", "CURRENT DESIGN.md");
    }

    @Test
    void constructor_rejectsAnUnknownStagedContextValue() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), "sometimes"))
                .withMessageContaining("staged-context");
    }

    // --- Clean-skip exit gate: a stage whose check already passed in-loop, with no edits since, is reused instead of re-checked ---

    @Test
    void exitGate_reusesTheToolsCachedPassingCheck_withoutCallingTheCheckServiceAgain() {
        // A live DESIGN check would FAIL here (designMarkdown null), so the run only completes because the exit gate consulted the cache instead of re-running the check.
        sandbox.designMarkdown = null;
        when(baseTools.reuseCachedPassingCheck(GenerationStage.DESIGN)).thenReturn(Optional.of(StageCheckResult.passed("cached DESIGN observation")));
        StageCheckService spiedService = spy(stageCheckService);
        StagedGenerationRunner testRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, spiedService, new AgentTranscriptWriter(""), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = testRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 2/6: design gate passed (reused in-stage check)");
        verify(spiedService, never()).check(eq(GenerationStage.DESIGN), any(), anyString(), eq(exercise), any(), any());
    }

    @Test
    void exitGate_whenTheToolsReportDirty_reCallsTheCheckServiceAndDoesNotAddTheReusedSuffix() {
        // baseTools.reuseCachedPassingCheck(...) is unstubbed on the mock -> Optional.empty() (the default "dirty" state), so every stage's exit gate must call the live service.
        StageCheckService spiedService = spy(stageCheckService);
        StagedGenerationRunner testRunner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, spiedService, new AgentTranscriptWriter(""), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = testRunner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of).result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progressEvents).contains("Stage 2/6: design gate passed", "Stage 3/6: solution gate passed").noneMatch(event -> event.contains("reused in-stage check"));
        verify(spiedService, times(1)).check(eq(GenerationStage.DESIGN), any(), anyString(), eq(exercise), any(), any());
        verify(spiedService, times(1)).check(eq(GenerationStage.SOLUTION), any(), anyString(), eq(exercise), any(), any());
    }

    @Test
    void testsGate_recordsItsReportOnTheToolsInstanceForTheStatementStagesBindingCheck() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(baseTools).recordLastTestsReport(passingReport());
    }
    // --- SPEC stage: skip semantics, private retry, snapshot sink ---

    @Test
    void specStage_isSkippedWhenTheInstructorAlreadyProvidedTheSpecification() {
        // 5 loop calls only: DESIGN..STATEMENT. The SPEC stub is absent on purpose — a call for it would consume the design stub and break the sequence.
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(3, "design done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        List<String> progressEvents = new ArrayList<>();

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progressEvents::add, Set::of, false, null)
                .result();

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        assertThat(progressEvents).noneMatch(event -> event.contains("specifying the exercise behaviour"));
    }

    @Test
    void specGateFailure_getsAPrivateRetryThatDoesNotConsumeTheSharedReentryBudget() {
        // SPEC fails once (empty file), its private retry fixes it; DESIGN and SOLUTION then each fail once and must BOTH still get their shared re-entries.
        sandbox.specMarkdown = null;
        sandbox.designMarkdown = "no headings here";
        AtomicReference<SingleBuildResult> solutionBuild = new AtomicReference<>(compileFailure("solution compile error"));
        when(verifier.singleBuild(any(), anyString(), eq(exercise), eq("solution"))).thenAnswer(invocation -> solutionBuild.get());
        AtomicInteger callCount = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> switch (callCount.incrementAndGet()) {
            case 1 -> completed(1, "spec attempt 1");
            case 2 -> {
                sandbox.specMarkdown = VALID_SPEC_DOCUMENT;
                yield completed(1, "spec attempt 2");
            }
            case 3 -> completed(5, "design attempt 1");
            case 4 -> {
                sandbox.designMarkdown = VALID_DESIGN_DOCUMENT;
                yield completed(3, "design attempt 2");
            }
            case 5 -> completed(10, "solution attempt 1");
            case 6 -> {
                solutionBuild.set(compiled());
                yield completed(8, "solution attempt 2");
            }
            case 7 -> completed(4, "template");
            case 8 -> completed(12, "tests");
            case 9 -> completed(5, "statement");
            default -> throw new IllegalStateException("unexpected call " + callCount.get());
        });
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of).result();

        assertThat(result.status()).as("both shared re-entries were still available for DESIGN and SOLUTION because SPEC's retry was private")
                .isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(9)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void specGatePass_handsTheSnapshotToTheSpecSink() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(0, "spec done"), completed(3, "design done"),
                completed(10, "solution done"), completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicReference<String> snapshot = new AtomicReference<>();

        runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, Set::of, true, snapshot::set).result();

        assertThat(snapshot.get()).as("the sink receives the gate-approved SPEC.md verbatim").isEqualTo(VALID_SPEC_DOCUMENT);
    }

}
