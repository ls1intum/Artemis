package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the enforced staged-generation runner: stage order and context, the shared turn-budget pool's rollover/cap/floor arithmetic, per-stage mechanical gates and
 * their short-circuit-on-failure behaviour, cooperative cancellation between stages, and the wall-clock guard. A fake sandbox serves canned command output keyed by the exact
 * commands the runner issues, following the same pattern as {@code SandboxAgentToolsTest}.
 */
class StagedGenerationRunnerTest {

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
            """;

    /** Records every command issued and serves canned results keyed by the exact commands {@link StagedGenerationRunner} runs. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private String designMarkdown = VALID_DESIGN_DOCUMENT;

        private String problemStatement = "# Title\n\nDo the thing.";

        private String layout = "solution/pom.xml\ntemplate/pom.xml\ntests/pom.xml";

        private SandboxExecResult solutionCompileResult = success();

        private SandboxExecResult templateCompileResult = success();

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
                if (script.contains("/solution") && script.contains("mvn")) {
                    return solutionCompileResult;
                }
                if (script.contains("/template") && script.contains("mvn")) {
                    return templateCompileResult;
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

    private SandboxAgentTools baseTools;

    private ProgrammingExercise exercise;

    private StagedGenerationRunner runner;

    private FakeSandbox sandbox;

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    @BeforeEach
    void setUp() {
        agentLoopRunner = mock(AgentLoopRunner.class);
        systemPromptService = mock(AgentSystemPromptService.class);
        verifier = mock(DifferentialVerificationService.class);
        baseTools = mock(SandboxAgentTools.class);
        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(systemPromptService.buildStage(any(), any())).thenReturn("STAGE SYSTEM PROMPT");
        sandbox = new FakeSandbox();
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, verifier);
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
        return runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", cancelled, null, null, structuralSeedHook);
    }

    @Test
    void runsAllFiveStagesInOrder_withMatchingStageContextAndAggregatedResult() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(3, "design done"), completed(10, "solution done"),
                completed(4, "template done"), completed(12, "tests done"), completed(5, "statement done"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(3 + 10 + 4 + 12 + 5);
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
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(3, "design"), completed(10, "solution"),
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
    void designGateFailure_stopsBeforeSolutionStageAndReportsWhichSectionsAreMissing() {
        sandbox.designMarkdown = "no headings here";
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.turns()).isEqualTo(5);
        assertThat(result.finalMessage()).contains("DESIGN.md is missing required section(s)").contains("## Classes");
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void designGateFailure_missingFile_reportsMissingOrEmpty() {
        sandbox.designMarkdown = null;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("DESIGN.md is missing or empty");
    }

    @Test
    void solutionGateFailure_compileError_stopsBeforeTemplateStage() {
        sandbox.solutionCompileResult = new SandboxExecResult(1, "", "compile error: cannot find symbol", false);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design"), completed(10, "solution attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.turns()).isEqualTo(15);
        assertThat(result.finalMessage()).contains("reference solution does not compile").contains("compile error");
        verify(agentLoopRunner, times(2)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_degenerateCopy_stopsBeforeTestsStage() {
        sandbox.diffExitCode = 0; // template byte-identical to the solution
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design"), completed(10, "solution"),
                completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("byte-identical to the solution");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void templateGateFailure_doesNotCompile_stopsBeforeTestsStage() {
        sandbox.templateCompileResult = new SandboxExecResult(1, "", "compile error", false);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design"), completed(10, "solution"),
                completed(4, "template attempt"));

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("template does not compile");
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void testsGateFailure_stopsBeforeStatementStage_butStructuralSeedingAlreadyRan() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(failingReport());
        AtomicInteger structuralSeedCalls = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralSeedCalls.incrementAndGet();
            return Set.of();
        });

        assertThat(result.finalMessage()).contains("do not yet satisfy the differential requirement");
        assertThat(structuralSeedCalls.get()).isEqualTo(1);
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void statementGateFailure_emptyStatement_reportsMissingOrEmpty() {
        sandbox.problemStatement = "  ";
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(5, "design"), completed(10, "solution"),
                completed(4, "template"), completed(12, "tests"), completed(5, "statement attempt"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        assertThat(result.finalMessage()).contains("problem-statement.md is missing or empty");
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
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
        BooleanSupplier cancelled = () -> calls.incrementAndGet() > 1;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(2, "design done"));

        AgentLoopResult result = run(cancelled, Set::of);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(result.turns()).isEqualTo(2);
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
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
    void budgetPool_rolloverAndCapArithmeticAcrossAllFiveStages() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(2, "design"), completed(25, "solution"),
                completed(1, "template"), completed(24, "tests"), completed(7, "statement"));
        when(verifier.selfCheck(any(), anyString(), eq(exercise), any(), eq(false))).thenReturn(passingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, Set::of);

        ArgumentCaptor<Integer> maxTurnsCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(agentLoopRunner, times(5)).run(anyString(), anyString(), any(), maxTurnsCaptor.capture(), any(), any(), any());
        // base 5/22/8/24/7; unspent turns roll fully into the next stage's allocation, capped by the remaining 78-turn pool:
        // DESIGN 5 (uses 2, rollover 3) -> SOLUTION 22+3=25 (uses all, rollover 0) -> TEMPLATE 8+0=8 (uses 1, rollover 7)
        // -> TESTS 24+7=31 (uses 24, rollover 7) -> STATEMENT 7+7=14.
        assertThat(maxTurnsCaptor.getAllValues()).containsExactly(5, 25, 8, 31, 14);
        assertThat(result.turns()).isEqualTo(2 + 25 + 1 + 24 + 7);
    }

    @Test
    void allocateStageBudget_appliesFloorAndRemainingPoolCap() {
        assertThat(StagedGenerationRunner.allocateStageBudget(5, 0, 78)).isEqualTo(5);
        assertThat(StagedGenerationRunner.allocateStageBudget(22, 3, 73)).isEqualTo(25);
        assertThat(StagedGenerationRunner.allocateStageBudget(22, 60, 10)).as("the remaining pool caps an oversized rollover").isEqualTo(10);
        assertThat(StagedGenerationRunner.allocateStageBudget(5, 0, 1)).as("every stage gets at least the floor, even over a near-empty pool").isEqualTo(3);
    }
}
