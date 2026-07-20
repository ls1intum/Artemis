package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Runs one Java/{@code GENERATE} agent attempt as five enforced stages — design, solution, template, tests, statement — each with its own system prompt
 * ({@link AgentSystemPromptService#buildStage}), its own bounded turn budget, and a mechanical gate that must pass before the next stage starts. This replaces one
 * {@code agentLoopRunner.run(...)} call in {@link GenerationOrchestrationService#generate}; everything before and after that call site (workspace seeding, mechanical
 * verification, spec-fidelity review, the outer repair-attempt loop) is unchanged and treats the aggregated {@link AgentLoopResult} this class returns exactly like a
 * single-call result.
 * <p>
 * v1 (advisor-approved): no write scoping between stages (the tools still expose the whole workspace) and no re-entry into an earlier stage — a failed gate stops the whole
 * run immediately and hands the aggregated result (with the gate report appended) back to the existing outer attempt loop, which already knows how to turn a rejected
 * candidate into a repair prompt for the next attempt.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StagedGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(StagedGenerationRunner.class);

    private static final List<GenerationStage> STAGE_ORDER = List.of(GenerationStage.DESIGN, GenerationStage.SOLUTION, GenerationStage.TEMPLATE, GenerationStage.TESTS,
            GenerationStage.STATEMENT);

    /** Base per-stage turn budget, in {@link #STAGE_ORDER} order; sums to 66, leaving headroom under {@link #POOL_HARD_CAP} for rollover. */
    private static final int[] STAGE_BASE_BUDGETS = { 5, 22, 8, 24, 7 };

    /** Hard ceiling on turns spent across all five stages combined, regardless of rollover. */
    private static final int POOL_HARD_CAP = 78;

    /** Every stage gets at least this many turns, even when the pool is nearly exhausted (the floor wins over the remaining-pool cap). */
    private static final int MIN_STAGE_BUDGET = 3;

    private static final List<String> STAGE_PROGRESS_LABELS = List.of("Stage 1/5: designing the exercise", "Stage 2/5: implementing the reference solution",
            "Stage 3/5: building the student template", "Stage 4/5: authoring the tests", "Stage 5/5: writing the problem statement");

    /** Once the run has spent this long, no further stage is started; final (post-loop) verification decides the outcome of whatever was produced. */
    private static final Duration WALL_CLOCK_BUDGET = Duration.ofMinutes(22);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration COMPILE_TIMEOUT = Duration.ofMinutes(5);

    private static final int MAX_GATE_OUTPUT_CHARS = 4_000;

    private static final List<String> REQUIRED_DESIGN_HEADINGS = List.of("## Classes", "## Public API", "## Tasks");

    private final AgentLoopRunner agentLoopRunner;

    private final AgentSystemPromptService systemPromptService;

    private final DifferentialVerificationService verifier;

    /** Test hook so a wall-clock test can advance time deterministically instead of sleeping; production always uses the real clock. */
    private Supplier<Instant> clock = Instant::now;

    public StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, DifferentialVerificationService verifier) {
        this.agentLoopRunner = agentLoopRunner;
        this.systemPromptService = systemPromptService;
        this.verifier = verifier;
    }

    /**
     * Runs the five enforced stages in order, honouring a shared turn-budget pool, a wall-clock ceiling, and cooperative cancellation between stages.
     *
     * @param exercise           the exercise being generated (Java/{@code GENERATE} only; the caller decides applicability)
     * @param baseTools          the shared, stateful {@link SandboxAgentTools} instance whose {@code enterStage} is called before every stage; never re-created per stage
     * @param tools              the tools object exposed to the model this turn (may be a decorator wrapping {@code baseTools})
     * @param briefPrompt        the instructor brief / outer-attempt repair prompt, injected fresh into every stage's user prompt
     * @param seedTestsFiles     the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param sandbox            the open sandbox session
     * @param sessionId          the sandbox session id
     * @param cancelled          polled between stages (and inside each stage's own agent loop)
     * @param usageSink          receives token usage for every model call; may be {@code null}
     * @param progress           receives one short progress line per stage; may be {@code null}
     * @param structuralSeedHook invoked once, best-effort, after the TEMPLATE gate passes, to seed Java structural tests before the TESTS stage starts (the orchestrator's
     *                               own post-loop seeding call is unaffected and stays the source of truth for the final candidate)
     * @return one aggregated {@link AgentLoopResult}: summed turns, the first {@code ERROR}/{@code CANCELLED} status encountered or else the last stage's status, and the
     *         last stage's final message (with the failing gate's report appended, if a gate failed)
     */
    public AgentLoopResult run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook) {
        Instant startedAt = clock.get();
        int remainingPool = POOL_HARD_CAP;
        int rollover = 0;
        int totalTurns = 0;
        String lastFinalMessage = "";
        AgentLoopResult.Status lastStatus = AgentLoopResult.Status.COMPLETED;
        String lastVerifyReport = null;

        for (int index = 0; index < STAGE_ORDER.size(); index++) {
            GenerationStage stage = STAGE_ORDER.get(index);
            if (cancelled.getAsBoolean()) {
                return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage);
            }
            if (Duration.between(startedAt, clock.get()).compareTo(WALL_CLOCK_BUDGET) > 0) {
                log.info("Staged generation wall-clock budget exceeded before stage {} for exercise {}; stopping with {} stage(s) completed", stage, exercise.getId(), index);
                break;
            }

            int allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], rollover, remainingPool);
            emit(progress, STAGE_PROGRESS_LABELS.get(index));
            baseTools.enterStage(stage);
            String systemPrompt = systemPromptService.buildStage(exercise, stage);
            String userPrompt = buildStagePrompt(stage, briefPrompt, sandbox, sessionId, lastVerifyReport);

            AgentLoopResult result = agentLoopRunner.run(systemPrompt, userPrompt, tools, allocation, cancelled, usageSink, progress);
            totalTurns += result.turns();
            lastFinalMessage = result.finalMessage();
            remainingPool = Math.max(0, remainingPool - result.turns());
            rollover = Math.max(0, allocation - result.turns());

            if (result.status() == AgentLoopResult.Status.ERROR || result.status() == AgentLoopResult.Status.CANCELLED) {
                return new AgentLoopResult(result.status(), totalTurns, lastFinalMessage);
            }
            lastStatus = result.status();

            GateResult gate = evaluateGate(stage, sandbox, sessionId, exercise, seedTestsFiles);
            if (stage == GenerationStage.TESTS) {
                lastVerifyReport = gate.report();
            }
            if (!gate.passed()) {
                log.info("Staged generation gate failed at stage {} for exercise {}", stage, exercise.getId());
                return new AgentLoopResult(lastStatus, totalTurns, appendGateReport(lastFinalMessage, gate.report()));
            }

            if (stage == GenerationStage.TEMPLATE) {
                try {
                    structuralSeedHook.get();
                }
                catch (RuntimeException e) {
                    log.debug("Pre-TESTS structural oracle seeding failed (best-effort; the orchestrator's post-loop seeding is unaffected): {}", e.getMessage());
                }
            }
        }
        return new AgentLoopResult(lastStatus, totalTurns, lastFinalMessage);
    }

    /**
     * Allocates one stage's turn budget from the shared pool: its base plus whatever unspent rollover carried forward, capped by the remaining pool, but never below the
     * minimum floor even if that means slightly exceeding the remaining pool.
     */
    static int allocateStageBudget(int base, int rollover, int remainingPool) {
        return Math.max(MIN_STAGE_BUDGET, Math.min(base + rollover, remainingPool));
    }

    private GateResult evaluateGate(GenerationStage stage, InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles) {
        return switch (stage) {
            case DESIGN -> checkDesignGate(sandbox, sessionId);
            case SOLUTION -> checkCompileGate(sandbox, sessionId, "solution", "reference solution");
            case TEMPLATE -> checkTemplateGate(sandbox, sessionId);
            case TESTS -> checkTestsGate(sandbox, sessionId, exercise, seedTestsFiles);
            case STATEMENT -> checkStatementGate(sandbox, sessionId);
        };
    }

    private GateResult checkDesignGate(InteractiveSandbox sandbox, String sessionId) {
        String designDocument = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/DESIGN.md");
        if (designDocument.isBlank()) {
            return GateResult.failed(
                    "DESIGN.md is missing or empty. Write /workspace/DESIGN.md with the required '## Classes', '## Public API', and '## Tasks' sections before continuing.");
        }
        List<String> missing = REQUIRED_DESIGN_HEADINGS.stream().filter(heading -> !designDocument.contains(heading)).toList();
        if (!missing.isEmpty()) {
            return GateResult.failed("DESIGN.md is missing required section(s): " + missing + ". Add them before continuing.");
        }
        return GateResult.passed("");
    }

    private GateResult checkCompileGate(InteractiveSandbox sandbox, String sessionId, String repositoryDirectory, String label) {
        SandboxExecResult result;
        try {
            result = sandbox.exec(sessionId, COMPILE_TIMEOUT, "sh", "-c",
                    "cd " + GenerationWorkspaceService.WORKSPACE + "/" + repositoryDirectory + " && mvn -B -q -DskipTests compile");
        }
        catch (RuntimeException e) {
            return GateResult.failed("Could not run the " + label + " compile check: " + e.getMessage());
        }
        if (result.timedOut()) {
            return GateResult.failed("The " + label + " compile check timed out.");
        }
        if (!result.isSuccess()) {
            return GateResult.failed("The " + label + " does not compile:\n" + boundedOutput(result.combinedOutput()));
        }
        return GateResult.passed("");
    }

    private GateResult checkTemplateGate(InteractiveSandbox sandbox, String sessionId) {
        GateResult compile = checkCompileGate(sandbox, sessionId, "template", "template");
        if (!compile.passed()) {
            return compile;
        }
        try {
            SandboxExecResult diff = sandbox.exec(sessionId, COMPILE_TIMEOUT, "diff", "-rq", GenerationWorkspaceService.WORKSPACE + "/solution",
                    GenerationWorkspaceService.WORKSPACE + "/template");
            if (!diff.timedOut() && diff.exitCode() == 0) {
                return GateResult.failed("The template is byte-identical to the solution (a degenerate copy). Remove the student work DESIGN.md marks stubbed or absent from "
                        + "the template so it still compiles but no longer matches the solution.");
            }
        }
        catch (RuntimeException e) {
            // Advisory only: a tooling failure here must not block an otherwise sound template.
            log.debug("Degenerate-copy check could not run (fail-open): {}", e.getMessage());
        }
        return GateResult.passed("");
    }

    private GateResult checkTestsGate(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles) {
        AgentVerifyReport report;
        try {
            report = verifier.selfCheck(sandbox, sessionId, exercise, seedTestsFiles, false);
        }
        catch (RuntimeException e) {
            return GateResult.failed("Could not run the differential self-check: " + e.getMessage());
        }
        String observation = report.toObservation();
        if (report.solutionPassed() && report.templateFailed()) {
            return GateResult.passed(observation);
        }
        return GateResult.failed("The tests do not yet satisfy the differential requirement (the solution must pass every test, the template must fail every "
                + "task-bound behavioural test):\n" + observation);
    }

    private GateResult checkStatementGate(InteractiveSandbox sandbox, String sessionId) {
        String statement = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        if (statement.isBlank()) {
            return GateResult.failed("problem-statement.md is missing or empty. Write the student-facing problem statement before submitting.");
        }
        return GateResult.passed("");
    }

    private String buildStagePrompt(GenerationStage stage, String briefPrompt, InteractiveSandbox sandbox, String sessionId, @Nullable String lastVerifyReport) {
        StringBuilder prompt = new StringBuilder(briefPrompt);
        String designDocument = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/DESIGN.md");
        if (!designDocument.isBlank()) {
            prompt.append("\n\n=== CURRENT DESIGN.md ===\n").append(designDocument.strip()).append("\n=== END DESIGN.md ===");
        }
        String layout = execRead(sandbox, sessionId, "sh", "-c", "find " + GenerationWorkspaceService.WORKSPACE + " -maxdepth 3 -type f -not -path '*/target/*' | head -80");
        if (!layout.isBlank()) {
            prompt.append("\n\n=== CURRENT WORKSPACE LAYOUT ===\n").append(layout.strip()).append("\n=== END WORKSPACE LAYOUT ===");
        }
        if ((stage == GenerationStage.TESTS || stage == GenerationStage.STATEMENT) && lastVerifyReport != null && !lastVerifyReport.isBlank()) {
            prompt.append("\n\n=== MOST RECENT VERIFICATION REPORT ===\n").append(lastVerifyReport).append("\n=== END VERIFICATION REPORT ===");
        }
        return prompt.toString();
    }

    private String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }

    private static String boundedOutput(@Nullable String output) {
        if (output == null) {
            return "";
        }
        return output.length() <= MAX_GATE_OUTPUT_CHARS ? output : output.substring(output.length() - MAX_GATE_OUTPUT_CHARS);
    }

    private static String appendGateReport(String finalMessage, String gateReport) {
        if (gateReport == null || gateReport.isBlank()) {
            return finalMessage;
        }
        return finalMessage == null || finalMessage.isBlank() ? gateReport : finalMessage + "\n\n" + gateReport;
    }

    private static void emit(@Nullable Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    /** Test hook: inject a deterministic clock so the wall-clock guard can be exercised without sleeping. */
    void setClockForTests(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** One stage gate's outcome: whether it passed, and its report text (a failure reason, or — for TESTS — the self-check observation carried into the next stage). */
    private record GateResult(boolean passed, String report) {

        static GateResult passed(String report) {
            return new GateResult(true, report);
        }

        static GateResult failed(String report) {
            return new GateResult(false, report);
        }
    }
}
