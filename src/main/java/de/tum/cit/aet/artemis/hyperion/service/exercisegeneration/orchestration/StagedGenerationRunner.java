package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Runs one Java/{@code GENERATE} agent attempt as five enforced stages — specification, solution, template, tests, statement — each with its own system prompt
 * ({@link AgentSystemPromptService#buildStage}), its own bounded turn budget, and a mechanical gate that must pass before the next stage starts. This replaces one
 * {@code agentLoopRunner.run(...)} call in {@link GenerationOrchestrationService#generate}; everything before and after that call site (workspace seeding, mechanical
 * verification, spec-fidelity review, the outer repair-attempt loop) is unchanged and treats the aggregated {@link AgentLoopResult} this class returns exactly like a
 * single-call result.
 * <p>
 * Stage file tools enforce monotonic write scope: a stage may correct its own artifact or an earlier dependency, but cannot pre-author a later artifact before that artifact's
 * instructions and gate. There is no re-entry into an *earlier* stage — a gate failure that exhausts its stage's own re-entry budget (see below) stops the whole run immediately
 * and hands the aggregated result (with the gate report appended) back to the existing outer attempt loop, which already knows how to turn a rejected candidate into a repair
 * prompt for the next attempt.
 * <p>
 * Conversation continuity across stages is controlled by {@code artemis.hyperion.agent.staged-context} ({@link StagedContext}, default {@code CONTINUOUS}): CONTINUOUS
 * carries one logical conversation across all five stages via {@link AgentLoopRunner#runSession} (the model keeps everything it learned in earlier stages instead of
 * starting blind every time), while FRESH starts a brand-new conversation per stage via {@link AgentLoopRunner#run} exactly as this class originally worked. Either way,
 * on a stage's first gate failure the stage gets one re-entry (same stage, gate feedback fed back in) if the shared pool still has at least {@link #MIN_STAGE_BUDGET}
 * turns and the run has not yet spent its total re-entry budget ({@link #MAX_TOTAL_REENTRIES} across the whole run); a second failure at the same stage stops the run as
 * described above.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StagedGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(StagedGenerationRunner.class);

    /**
     * One conceptual rewrite and one focused follow-up are enough to respond to qualitative review without turning specification authoring into an open-ended patch loop.
     */
    private static final int MAX_SEMANTIC_SPEC_REFINEMENTS = 2;

    /** A complete SPEC rewrite plus verification fits in five turns; the two allowed refinements exactly consume the pool's ten-turn headroom. */
    private static final int SEMANTIC_SPEC_REFINEMENT_BUDGET = 5;

    private static final List<GenerationStage> STAGE_ORDER = List.of(GenerationStage.SPEC, GenerationStage.SOLUTION, GenerationStage.TEMPLATE, GenerationStage.TESTS,
            GenerationStage.STATEMENT);

    /**
     * Base per-stage turn budget, in {@link #STAGE_ORDER} order; sums to 68, leaving headroom under {@link #POOL_HARD_CAP} for rollover. SPEC runs no builds but now carries the
     * whole plan (rules, worked examples, design, testing strategy), so it gets more room than the old thin spec+design pair combined minus their hand-off overhead.
     */
    private static final int[] STAGE_BASE_BUDGETS = { 7, 22, 8, 24, 7 };

    /**
     * Hard ceiling on turns spent across all five stages combined. A stage (or re-entry) is only started while at least {@link #MIN_STAGE_BUDGET} turns remain, so the cap holds.
     */
    private static final int POOL_HARD_CAP = 78;

    /** The smallest turn budget a stage can usefully run with; below this remaining pool, no further stage or re-entry is started. */
    private static final int MIN_STAGE_BUDGET = 3;

    /** At most this many stage re-entries (see {@link #run}) are granted across the whole run, regardless of how many stages fail their gate on the first attempt. */
    private static final int MAX_TOTAL_REENTRIES = 2;

    private static final List<String> STAGE_PROGRESS_LABELS = List.of("Stage 1/5: specifying the exercise", "Stage 2/5: implementing the reference solution",
            "Stage 3/5: building the student template", "Stage 4/5: authoring the tests", "Stage 5/5: writing the problem statement");

    /** Once the run has spent this long, no further stage is started; final (post-loop) verification decides the outcome of whatever was produced. */
    private static final Duration WALL_CLOCK_BUDGET = Duration.ofMinutes(22);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Bound on the gate-failure progress line (the full report still goes to the info log and the returned final message). */
    private static final int MAX_GATE_PROGRESS_CHARS = 140;

    private final AgentLoopRunner agentLoopRunner;

    private final AgentSystemPromptService systemPromptService;

    private final StageCheckService stageCheckService;

    private final ApprovedSpecRegistry approvedSpecs;

    @Nullable
    private final SpecFidelityCriticService specificationReviewer;

    private final AgentTranscriptWriter transcriptWriter;

    private final StagedContext stagedContext;

    /** Test hook so a wall-clock test can advance time deterministically instead of sleeping; production always uses the real clock. */
    private Supplier<Instant> clock = Instant::now;

    /**
     * The conversation-carry strategy for the enforced stage sequence (see {@link #run}).
     */
    enum StagedContext {

        /**
         * Every stage (and re-entry) continues one logical conversation via {@link AgentLoopRunner#runSession}: the model keeps everything it learned in earlier stages, and
         * each stage's user prompt is slimmed down accordingly (no re-injected SPEC.md/workspace layout).
         */
        CONTINUOUS,

        /** Every stage (and re-entry) starts a fresh conversation via {@link AgentLoopRunner#run}, rebuilding the full stage prompt (SPEC.md, workspace layout) each time. */
        FRESH;

        static StagedContext parse(String value) {
            try {
                return StagedContext.valueOf(value.strip().toUpperCase(Locale.ROOT));
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("artemis.hyperion.agent.staged-context must be CONTINUOUS or FRESH, got: '" + value + "'");
            }
        }
    }

    // @Autowired disambiguates from the package-private test constructor; with two constructors and no annotation Spring cannot instantiate the bean.
    @Autowired
    public StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer,
            @Value("${artemis.hyperion.agent.staged-context:CONTINUOUS}") String stagedContext) {
        this.agentLoopRunner = agentLoopRunner;
        this.systemPromptService = systemPromptService;
        this.stageCheckService = stageCheckService;
        this.transcriptWriter = transcriptWriter;
        this.approvedSpecs = approvedSpecs;
        this.specificationReviewer = specificationReviewer;
        this.stagedContext = StagedContext.parse(stagedContext);
    }

    /** Test constructor: an isolated registry, so a staged run under test publishes its approved specification without a Spring context. */
    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, new ApprovedSpecRegistry(), null, stagedContext);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, null, stagedContext);
    }

    /**
     * The staged run's aggregated loop result together with the conversation it produced ({@code null} under {@link StagedContext#FRESH}), so the outer repair-attempt loop can
     * continue the same logical conversation instead of starting each repair blind.
     */
    public record StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation) {
    }

    /**
     * Runs the enforced stages in order, honouring a shared turn-budget pool, a wall-clock ceiling, and cooperative cancellation between stages.
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
    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook) {
        return run(exercise, baseTools, tools, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, true, null);
    }

    /**
     * Like {@link #run(ProgrammingExercise, SandboxAgentTools, Object, String, Map, InteractiveSandbox, String, BooleanSupplier, Consumer, Consumer, Supplier)}, with control
     * over the SPEC stage.
     *
     * @param exercise           the exercise being generated (Java/{@code GENERATE} only; the caller decides applicability)
     * @param baseTools          the shared, stateful {@link SandboxAgentTools} instance whose {@code enterStage} is called before every stage
     * @param tools              the tools object exposed to the model this turn (may be a decorator wrapping {@code baseTools})
     * @param briefPrompt        the instructor brief / outer-attempt repair prompt, injected fresh into every stage's user prompt
     * @param seedTestsFiles     the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param sandbox            the open sandbox session
     * @param sessionId          the sandbox session id
     * @param cancelled          polled between stages (and inside each stage's own agent loop)
     * @param usageSink          receives token usage for every model call; may be {@code null}
     * @param progress           receives one short progress line per stage; may be {@code null}
     * @param structuralSeedHook invoked once, best-effort, after the TEMPLATE gate passes, to seed Java structural tests before the TESTS stage starts
     * @param specStageApplies   whether to run the SPEC stage; {@code false} when the instructor already provided a non-trivial problem statement — that statement IS the
     *                               specification, and the model must not overwrite it with a restatement
     * @param specSink           receives the gate-approved SPEC.md snapshot right after the spec gate passes (early instructor observability and the orchestrator's frozen copy
     *                               for the critic and repair prompts); may be {@code null}
     * @return one aggregated {@link AgentLoopResult} plus the carried conversation, exactly as the shorter overload
     */
    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook, boolean specStageApplies, @Nullable Consumer<String> specSink) {
        return run(exercise, baseTools, tools, briefPrompt, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, specStageApplies,
                specSink);
    }

    /**
     * Runs the staged workflow with the raw source brief kept separate from authoring context for the pre-freeze semantic review.
     *
     * @param exercise           exercise being generated
     * @param baseTools          shared stateful agent tools
     * @param tools              tools exposed to the model
     * @param briefPrompt        current authoring context, which may include repair feedback
     * @param sourceBrief        raw instructor brief used only as review authority
     * @param seedTestsFiles     tests repository before generation
     * @param sandbox            open sandbox
     * @param sessionId          sandbox session identifier
     * @param cancelled          cooperative cancellation signal
     * @param usageSink          optional token-usage sink
     * @param progress           optional progress sink
     * @param structuralSeedHook best-effort structural-test seeding hook
     * @param specStageApplies   whether this run creates a specification
     * @param specSink           optional approved-specification sink
     * @return aggregated staged result and carried conversation
     */
    public StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, String sourceBrief, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook, boolean specStageApplies, @Nullable Consumer<String> specSink) {
        Instant startedAt = clock.get();
        boolean continuous = stagedContext == StagedContext.CONTINUOUS;
        int remainingPool = POOL_HARD_CAP;
        int rollover = 0;
        int totalTurns = 0;
        String lastFinalMessage = "";
        AgentLoopResult.Status lastStatus = AgentLoopResult.Status.COMPLETED;
        String lastVerifyReport = null;
        AgentVerifyReport lastTestsReport = null;
        // CONTINUOUS carries one logical conversation across every stage (and re-entry) via AgentLoopRunner#runSession; FRESH never populates this (stays null forever), so
        // every stage starts a brand-new conversation via the plain run() call, exactly as before this feature existed.
        List<Message> conversation = null;
        List<Message> archivedConversation = new ArrayList<>();
        int reentriesRemaining = MAX_TOTAL_REENTRIES;
        int semanticSpecRefinementsUsed = 0;
        boolean semanticSpecCorrectionUsed = false;
        String semanticSpecFeedback = null;
        boolean freshSemanticSpecAttempt = false;

        for (int index = 0; index < STAGE_ORDER.size(); index++) {
            GenerationStage stage = STAGE_ORDER.get(index);
            if (stage == GenerationStage.SPEC && !specStageApplies) {
                // The instructor's existing statement is the specification; writing a competing SPEC.md would at best duplicate it and at worst drift from it.
                continue;
            }
            if (cancelled.getAsBoolean()) {
                return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
            }
            if (Duration.between(startedAt, clock.get()).compareTo(WALL_CLOCK_BUDGET) > 0) {
                log.info("Staged generation wall-clock budget exceeded before stage {} for exercise {}; stopping with {} stage(s) completed", stage, exercise.getId(), index);
                break;
            }
            if (remainingPool < MIN_STAGE_BUDGET) {
                // The pool is the hard turn ceiling: starting another stage with the floor budget would silently exceed it, so stop here and let post-loop verification decide.
                log.info("Staged generation turn pool exhausted before stage {} for exercise {}; stopping with {} stage(s) completed", stage, exercise.getId(), index);
                break;
            }

            int allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], rollover, remainingPool);
            emit(progress, STAGE_PROGRESS_LABELS.get(index));
            baseTools.enterStage(stage);
            String systemPrompt = systemPromptService.buildStage(exercise, stage);

            String gateFeedback = null;
            int stageReentriesUsed = 0;
            boolean stagePassed = false;
            while (!stagePassed) {
                AgentLoopResult result;
                if (freshSemanticSpecAttempt) {
                    String userPrompt = freshSemanticSpecPrompt(sourceBrief, gateFeedback);
                    if (continuous) {
                        AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, null, userPrompt, tools, allocation, cancelled, usageSink, progress);
                        result = session.result();
                        conversation = session.conversation();
                    }
                    else {
                        result = agentLoopRunner.run(systemPrompt, userPrompt, tools, allocation, cancelled, usageSink, progress);
                    }
                    freshSemanticSpecAttempt = false;
                }
                else if (continuous && gateFeedback != null) {
                    // Re-entry, CONTINUOUS: the carried conversation already has everything up to the failed attempt; just hand back the gate report as the next turn.
                    String retryUserPrompt = "The previous attempt at this stage did not pass its gate. Address this feedback, then continue:\n\n" + gateFeedback;
                    AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, conversation, retryUserPrompt, tools, allocation, cancelled, usageSink,
                            progress);
                    result = session.result();
                    conversation = session.conversation();
                }
                else {
                    String userPrompt = buildStagePrompt(stage, briefPrompt, sandbox, sessionId, lastVerifyReport, continuous, gateFeedback);
                    if (continuous) {
                        AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, conversation, userPrompt, tools, allocation, cancelled, usageSink,
                                progress);
                        result = session.result();
                        conversation = session.conversation();
                    }
                    else {
                        result = agentLoopRunner.run(systemPrompt, userPrompt, tools, allocation, cancelled, usageSink, progress);
                    }
                }
                totalTurns += result.turns();
                lastFinalMessage = result.finalMessage();
                remainingPool = Math.max(0, remainingPool - result.turns());
                rollover = Math.max(0, allocation - result.turns());

                if (result.status() == AgentLoopResult.Status.ERROR || result.status() == AgentLoopResult.Status.CANCELLED) {
                    return finish(exercise, result.status(), totalTurns, lastFinalMessage, archivedConversation, conversation);
                }
                lastStatus = result.status();

                if (stage == GenerationStage.SPEC) {
                    materializeReturnedSpecification(baseTools, sandbox, sessionId, result.finalMessage());
                }
                GateEvaluation gateEvaluation = evaluateGate(stage, baseTools, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport);
                StageCheckResult gate = gateEvaluation.result();
                if (stage != GenerationStage.SPEC || !gate.passed()) {
                    emit(progress, gateProgressLabel(index, stage, gate, gateEvaluation.reused()));
                }
                if (stage == GenerationStage.TESTS) {
                    lastVerifyReport = gate.observation();
                    lastTestsReport = gate.report();
                    // The agent's own in-loop TESTS-stage checks may have set this already; setting it again from the runner's own official gate result (fresh or reused) covers
                    // the case where the gate never went through the tools at all (a reused cache, or a TESTS turn that never called verify/submit), so STATEMENT is never blind.
                    baseTools.recordLastTestsReport(gate.report());
                }

                if (gate.passed()) {
                    if (stage == GenerationStage.SPEC) {
                        String specSnapshot = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
                        if (!specSnapshot.isBlank()) {
                            if (specificationReviewer != null) {
                                emit(progress, "Reviewing the specification against the instructor brief");
                                SpecFidelityCriticService.SpecificationReview review = specificationReviewer.reviewSpecification(sourceBrief, specSnapshot, usageSink, cancelled);
                                if (cancelled.getAsBoolean()) {
                                    return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
                                }
                                if (!review.complete()) {
                                    String reviewFailure = "Specification fidelity review was unavailable, so generation stopped before freezing an unchecked contract. Retry "
                                            + "generation; no downstream artifacts were produced from this specification.";
                                    log.warn("Specification review was unavailable for exercise {}; stopping before contract approval", exercise.getId());
                                    emit(progress, reviewFailure);
                                    return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, reviewFailure), archivedConversation,
                                            conversation);
                                }
                                else if (!review.accepted()) {
                                    String reviewFeedback = review.feedback();
                                    log.info("Specification review rejected the candidate for exercise {}: {}", exercise.getId(), reviewFeedback);
                                    if (semanticSpecRefinementsUsed >= MAX_SEMANTIC_SPEC_REFINEMENTS || remainingPool < MIN_STAGE_BUDGET) {
                                        return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, reviewFeedback), archivedConversation,
                                                conversation);
                                    }
                                    semanticSpecRefinementsUsed++;
                                    semanticSpecCorrectionUsed = false;
                                    semanticSpecFeedback = reviewFeedback;
                                    gateFeedback = semanticSpecRefinementPrompt(reviewFeedback);
                                    if (review.conceptualReworkRequired()) {
                                        // The rejected plan is not useful context for a conceptual replacement. Remove both anchors: the carried conversation and the rejected
                                        // workspace artifact. Keep the old conversation only for the audit transcript, never as model context. The next bounded attempt receives
                                        // the raw brief and property-level diagnosis, but no reviewer-authored design.
                                        if (conversation != null) {
                                            archivedConversation.addAll(conversation);
                                        }
                                        conversation = null;
                                        baseTools.writeFile("SPEC.md", "");
                                        freshSemanticSpecAttempt = true;
                                    }
                                    emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": refining the specification after brief-fidelity review");
                                    allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, remainingPool);
                                    continue;
                                }
                            }
                            // Publish the APPROVED specification before anything downstream runs. From here on it is read-only; later stages repair executable artifacts
                            // against this exact contract rather than weakening or expanding it under compile pressure.
                            approvedSpecs.approve(sessionId, specSnapshot);
                            if (specSink != null) {
                                specSink.accept(specSnapshot);
                            }
                            emit(progress, gateProgressLabel(index, stage, gate, gateEvaluation.reused()));
                        }
                    }
                    stagePassed = true;
                    break;
                }

                log.info("Staged generation gate failed at stage {} for exercise {}: {}", stage, exercise.getId(), gate.observation());
                // A semantic refinement starts after a known mechanical pass, so it owns one bounded completion/correction independent of the ordinary SPEC retries, which may
                // already have been spent materialising and normalising the document. Continue the same revision with both reports; raising the generic retry count would make
                // every failure path longer without fixing this distinct lifecycle.
                boolean canCorrectSemanticSpec = stage == GenerationStage.SPEC && semanticSpecRefinementsUsed > 0 && !semanticSpecCorrectionUsed && semanticSpecFeedback != null
                        && remainingPool >= MIN_STAGE_BUDGET;
                if (canCorrectSemanticSpec) {
                    semanticSpecCorrectionUsed = true;
                    gateFeedback = semanticSpecCorrectionPrompt(semanticSpecFeedback, gate.observation());
                    emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": completing the specification refinement after its consistency check");
                    allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, remainingPool);
                    continue;
                }
                // SPEC gets two private retries that do NOT draw from the shared re-entry budget: one can be consumed merely materializing SPEC.md when the model returned its
                // contents as prose, leaving one real gate-guided refinement before the contract is frozen. Both remain bounded by the global turn and wall-clock budgets.
                boolean ordinaryRetryPhase = stage != GenerationStage.SPEC || semanticSpecRefinementsUsed == 0;
                boolean privateSpecRetry = ordinaryRetryPhase && stage == GenerationStage.SPEC && stageReentriesUsed < 2;
                boolean stageCanReenter = ordinaryRetryPhase && (privateSpecRetry || stageReentriesUsed == 0 && reentriesRemaining > 0);
                if (!stageCanReenter || remainingPool < MIN_STAGE_BUDGET) {
                    // The SPEC gate is the contract checkpoint. A generic repair can safely continue after later gates because the authoritative verifier repeats their checks,
                    // but it cannot reconstruct a specification that was never approved. Fail only that case closed; otherwise preserve the existing bounded repair path.
                    AgentLoopResult.Status exitStatus = stage == GenerationStage.SPEC ? AgentLoopResult.Status.ERROR : lastStatus;
                    return finish(exercise, exitStatus, totalTurns, appendGateReport(lastFinalMessage, gate.observation()), archivedConversation, conversation);
                }
                // Cooperative cancellation between the failed attempt and its re-entry (the outer for-loop already checked before this stage's first attempt).
                if (cancelled.getAsBoolean()) {
                    return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
                }
                stageReentriesUsed++;
                if (stage != GenerationStage.SPEC) {
                    reentriesRemaining--;
                }
                gateFeedback = gate.observation();
                emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": retrying after gate feedback");
                allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], 0, remainingPool);
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
        return finish(exercise, lastStatus, totalTurns, lastFinalMessage, archivedConversation, conversation);
    }

    private static String semanticSpecRefinementPrompt(String reviewFeedback) {
        return reviewFeedback
                + """


                        Re-evaluate the cited defects against the instructor brief before editing. Distinguish a LOCAL defect (for example one wrong worked result or an isolated
                        inconsistency) from a CONCEPTUAL rejection of the chosen domain, learning fit, or central student work.

                        For a conceptual rejection, the current plan is not an asset to preserve. Return to the brief and replace the hollow domain or behaviour, then rewrite all of
                        SPEC.md coherently in one write_file call. Before writing, privately compare three replacement domain-and-behaviour concepts: identify the real domain constraint
                        that makes their variants differ and the non-routine reasoning left to students. Reject concepts where variants are merely independently assigned constants,
                        multipliers, or thresholds over one scalar input. Renaming the same textbook problem, adding themed adjectives, adding another trivial strategy, or inventing
                        an arbitrary selector/validation rule is not a conceptual repair. Choose one domain-motivated interaction that leaves the requested level of reasoning after
                        routine Strategy wiring and prescribed transcription are subtracted.

                        For a local defect, preserve unaffected domain terms, public identifiers, ownership, and accepted design choices and make the smallest coherent repair. Reviewer
                        feedback deliberately supplies no replacement theme, identifier, API, or formula; derive the repair independently from the brief. In either case keep every required
                        section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no decision valid. Resolve every cited defect, then call the structured
                        verify tool before finishing.
                        """;
    }

    private static String freshSemanticSpecPrompt(String sourceBrief, @Nullable String reviewFeedback) {
        return """
                Create a fresh specification from the instructor brief below. The previous concept was rejected at the learning-fit review and has been discarded; do not recover,
                patch, rename, or reskin it. Independently compare three domain-and-behaviour concepts as directed by the stage instructions, then write one complete new SPEC.md.
                The reviewer supplies only a diagnosis, never a replacement design. Resolve that diagnosis without adding unrelated complexity, and call the structured verify tool
                before finishing.

                INSTRUCTOR BRIEF:
                """ + sourceBrief.strip() + "\n\nREVIEW DIAGNOSIS:\n"
                + (reviewFeedback == null ? "The previous concept did not satisfy the requested learning fit." : reviewFeedback);
    }

    private static String semanticSpecCorrectionPrompt(String reviewFeedback, String mechanicalFeedback) {
        return """
                The semantic revision is incomplete and does not yet pass SPEC.md's mechanical consistency check. Continue the SAME bounded revision; do not merely patch the
                parser-visible symptom or open unrelated design choices. Re-read the whole current file, finish every original semantic repair coherently, and use one full
                write_file rewrite if incremental edits have left mixed vocabulary or identifiers. Preserve unaffected domain, API, ownership, examples, and grading intent.
                Keep every required section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no decision valid. Call the structured verify
                tool and finish only after it passes.

                ORIGINAL SEMANTIC REVIEW:
                """ + reviewFeedback + "\n\nMECHANICAL DEFECT IN THE CURRENT REVISION:\n" + mechanicalFeedback;
    }

    /** Builds the outcome on every exit path and writes the session transcript (best-effort, no-op unless a transcript directory is configured). */
    private StagedRunOutcome finish(ProgrammingExercise exercise, AgentLoopResult.Status status, int totalTurns, String finalMessage, List<Message> archivedConversation,
            @Nullable List<Message> conversation) {
        List<Message> transcriptConversation = new ArrayList<>(archivedConversation);
        if (conversation != null) {
            transcriptConversation.addAll(conversation);
        }
        transcriptWriter.write(exercise.getId(), "attempt-1-staged-" + status.name().toLowerCase(Locale.ROOT), transcriptConversation);
        return new StagedRunOutcome(new AgentLoopResult(status, totalTurns, finalMessage), conversation);
    }

    /**
     * Builds the post-gate progress line: pass/fail in the same voice as {@link #STAGE_PROGRESS_LABELS}, bounding a failure's report to its first line, ~140 chars. A passing
     * gate that reused the tools' cached check (see {@link #evaluateGate}) instead of re-running it carries a suffix so the transcript stays honest about why it was instant.
     */
    private String gateProgressLabel(int index, GenerationStage stage, StageCheckResult gate, boolean reused) {
        String prefix = "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": " + stage.displayName().toLowerCase(Locale.ROOT) + " gate ";
        if (!gate.passed()) {
            return prefix + "failed: " + firstLineBounded(gate.observation(), MAX_GATE_PROGRESS_CHARS);
        }
        return reused ? prefix + "passed (reused in-stage check)" : prefix + "passed";
    }

    /** Extracts the first line of {@code text}, bounded to {@code maxChars} code points (an ellipsis marks truncation). Used to keep a gate-failure progress line short. */
    private static String firstLineBounded(@Nullable String text, int maxChars) {
        if (text == null) {
            return "";
        }
        // A header line ending in ':' carries no information on its own (observed live: "solution gate failed: The reference solution does not compile:" with
        // nothing after the colon) — fold the first content line in so the instructor-visible event actually says what went wrong.
        String[] lines = text.strip().split("\n");
        String firstLine = lines[0].strip();
        if (firstLine.endsWith(":")) {
            for (int i = 1; i < lines.length; i++) {
                String candidate = lines[i].strip();
                if (!candidate.isEmpty()) {
                    firstLine = firstLine + " " + candidate;
                    break;
                }
            }
        }
        if (firstLine.codePointCount(0, firstLine.length()) <= maxChars) {
            return firstLine;
        }
        int end = firstLine.offsetByCodePoints(0, maxChars);
        return firstLine.substring(0, end) + "…";
    }

    /**
     * Allocates one stage's turn budget from the shared pool: its base plus whatever unspent rollover carried forward, capped by the remaining pool. The floor only applies
     * while the caller guarantees {@code remainingPool >= MIN_STAGE_BUDGET} (both the stage loop and the re-entry path check that before allocating), so the pool cap is hard.
     */
    static int allocateStageBudget(int base, int rollover, int remainingPool) {
        return Math.max(MIN_STAGE_BUDGET, Math.min(base + rollover, remainingPool));
    }

    /** One gate evaluation's outcome, together with whether it came from {@link SandboxAgentTools#reuseCachedPassingCheck} instead of a fresh {@link StageCheckService} call. */
    private record GateEvaluation(StageCheckResult result, boolean reused) {
    }

    /**
     * Evaluates one stage's exit gate: reuses the tools' cached passing check when nothing has changed since it ran (see {@link SandboxAgentTools#reuseCachedPassingCheck}) so a
     * stage the agent already verified clean does not pay for a redundant check, and otherwise delegates to {@link StageCheckService}. This runner owns only stage sequencing,
     * turn budgets, re-entry, and this cache consultation (see the class javadoc); it does not itself decide whether a stage's artifact passed.
     */
    private GateEvaluation evaluateGate(GenerationStage stage, SandboxAgentTools baseTools, InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise,
            Map<String, String> seedTestsFiles, @Nullable AgentVerifyReport lastTestsReport) {
        Optional<StageCheckResult> reused = baseTools.reuseCachedPassingCheck(stage);
        if (reused.isPresent()) {
            return new GateEvaluation(reused.get(), true);
        }
        return new GateEvaluation(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport), false);
    }

    /**
     * Builds a stage's user prompt. In {@code FRESH} mode this re-injects the current SPEC.md and workspace layout, because that stage's conversation starts empty; in
     * {@code continuous} mode that re-injection is dropped (the carried conversation already has it, or — for the very first stage — there is nothing to inject yet),
     * keeping only the brief, a short stage-instructions reference, and any gate feedback.
     *
     * @param continuous    whether this stage runs under the {@link StagedContext#CONTINUOUS} strategy
     * @param retryFeedback the previous failed attempt's gate report, folded into a {@code FRESH}-mode retry prompt ({@code null} on a first attempt, and always {@code null}
     *                          under {@code continuous} — a continuous retry hands the feedback back as its own turn instead, see {@link #run})
     */
    private String buildStagePrompt(GenerationStage stage, String briefPrompt, InteractiveSandbox sandbox, String sessionId, @Nullable String lastVerifyReport, boolean continuous,
            @Nullable String retryFeedback) {
        StringBuilder prompt = new StringBuilder(briefPrompt);
        if (continuous) {
            prompt.append("\n\nContinue this session for the ").append(stage.displayName())
                    .append(" stage: follow that stage's instructions in the system prompt above. The specification and workspace state from earlier stages are already in this "
                            + "conversation; re-read a file only if you need to confirm its exact current contents.");
        }
        else {
            String specDocument = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
            if (!specDocument.isBlank()) {
                prompt.append("\n\n=== CURRENT SPEC.md ===\n").append(specDocument.strip()).append("\n=== END SPEC.md ===");
            }
            String layout = execRead(sandbox, sessionId, "sh", "-c", "find " + GenerationWorkspaceService.WORKSPACE + " -maxdepth 3 -type f -not -path '*/target/*' | head -80");
            if (!layout.isBlank()) {
                prompt.append("\n\n=== CURRENT WORKSPACE LAYOUT ===\n").append(layout.strip()).append("\n=== END WORKSPACE LAYOUT ===");
            }
        }
        if ((stage == GenerationStage.TESTS || stage == GenerationStage.STATEMENT) && lastVerifyReport != null && !lastVerifyReport.isBlank()) {
            prompt.append("\n\n=== MOST RECENT VERIFICATION REPORT ===\n").append(lastVerifyReport).append("\n=== END VERIFICATION REPORT ===");
        }
        if (retryFeedback != null && !retryFeedback.isBlank()) {
            prompt.append("\n\n=== GATE FEEDBACK FROM THE PREVIOUS ATTEMPT AT THIS STAGE ===\n").append(retryFeedback).append("\n=== END GATE FEEDBACK ===");
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

    /**
     * Models occasionally return the complete requested specification in their final text instead of invoking {@code write_file}. Preserve that authored content verbatim and
     * still subject it to the normal mechanical and semantic gates rather than spending a full retry merely moving the same bytes into the workspace.
     */
    private void materializeReturnedSpecification(SandboxAgentTools baseTools, InteractiveSandbox sandbox, String sessionId, @Nullable String finalMessage) {
        if (!execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md").isBlank() || finalMessage == null) {
            return;
        }
        int heading = finalMessage.indexOf("# SPEC.md");
        if (heading < 0) {
            return;
        }
        String specification = finalMessage.substring(heading).strip();
        if (specification.endsWith("```")) {
            specification = specification.substring(0, specification.length() - 3).stripTrailing();
        }
        String result = baseTools.writeFile("SPEC.md", specification);
        if (result != null && !result.startsWith("ERROR")) {
            log.info("Materialized SPEC.md from the agent's returned artifact before applying the normal specification gates");
        }
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
}
