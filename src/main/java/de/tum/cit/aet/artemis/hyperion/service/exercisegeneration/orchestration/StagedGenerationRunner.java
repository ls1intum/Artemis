package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GeneratedTestPlan;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Runs one Java/{@code GENERATE} agent attempt in three enforced phases — specification, coherent executable build, and student-facing statement — each with its own system prompt
 * ({@link AgentSystemPromptService#buildStage}), its own bounded turn budget, and a mechanical gate that must pass before the next stage starts. This replaces one
 * {@code agentLoopRunner.run(...)} call in {@link GenerationOrchestrationService#generate}; everything before and after that call site (workspace seeding, mechanical
 * verification, spec-fidelity review, the outer repair-attempt loop) is unchanged and treats the aggregated {@link AgentLoopResult} this class returns exactly like a
 * single-call result.
 * <p>
 * Phase file tools enforce write scope: specification is frozen after approval; the executable builder owns solution, template, tests, and grading plan together; the final
 * projection owns only the statement. There is no re-entry into an *earlier* phase — a gate failure that exhausts its own re-entry budget (see below) stops the whole run
 * immediately
 * and hands the aggregated result (with the gate report appended) back to the existing outer attempt loop, which already knows how to turn a rejected candidate into a repair
 * prompt for the next attempt.
 * <p>
 * Conversation continuity across stages is controlled by {@code artemis.hyperion.agent.staged-context} ({@link StagedContext}, default {@code CONTINUOUS}): CONTINUOUS
 * checkpoints specification provenance, carries one logical conversation through the executable stages, then starts a clean statement conversation from the approved contract
 * and a typed grading handoff. FRESH starts a brand-new conversation per stage via {@link AgentLoopRunner#run}. Either way,
 * on a stage's first gate failure the stage gets one re-entry (same stage, gate feedback fed back in) if the shared pool still has at least {@link #MIN_STAGE_BUDGET}
 * turns and the run has not yet spent its total re-entry budget ({@link #MAX_TOTAL_REENTRIES} across the whole run); a second failure at the same stage stops the run as
 * described above.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StagedGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(StagedGenerationRunner.class);

    /** One shared limit for every semantic specification repair; review labels choose the repair scope but never create extra retry channels. */
    private static final int MAX_SEMANTIC_SPEC_REFINEMENTS = 3;

    /** A complete SPEC rewrite plus verification fits in five turns; every refinement still competes for the unchanged global turn and wall-clock budgets. */
    private static final int SEMANTIC_SPEC_REFINEMENT_BUDGET = 5;

    /**
     * One planning checkpoint, one coherent executable build, one student-facing projection. Solution, template, tests, and grading plan deliberately share a stage so the builder
     * can complete risk-chosen seams vertically instead of handing four mutually dependent artifacts down a waterfall.
     */
    private static final List<GenerationStage> STAGE_ORDER = List.of(GenerationStage.SPEC, GenerationStage.TESTS, GenerationStage.STATEMENT);

    /**
     * Base per-stage turn budget, in {@link #STAGE_ORDER} order; sums to 68, leaving headroom under {@link #POOL_HARD_CAP} for rollover. SPEC runs no builds but now carries the
     * whole plan (rules, worked examples, design, testing strategy), so it gets more room than the old thin spec+design pair combined minus their hand-off overhead.
     */
    private static final int[] STAGE_BASE_BUDGETS = { 7, 54, 7 };

    /**
     * Hard ceiling on authoring-agent turns spent across all generation phases. The separately context-isolated concept selector is bounded to two one-turn candidate batches
     * per selection; at most the initial selection and three specification-triggered replacements can run. A stage (or re-entry) is only started while at least
     * {@link #MIN_STAGE_BUDGET} turns remain, so this artifact-authoring cap holds.
     */
    private static final int POOL_HARD_CAP = 83;

    /** The smallest turn budget a stage can usefully run with; below this remaining pool, no further stage or re-entry is started. */
    private static final int MIN_STAGE_BUDGET = 3;

    private static final String CONCEPT_REPLACEMENT_FEEDBACK = """
            After the previous concept was instantiated and reviewed, its central interaction could not satisfy the requested learning objective, difficulty, domain grounding,
            feasibility, or proportionality without replacement. Generate a genuinely different central interaction whose substance belongs to learner-owned work after
            prescribed transcription and routine mechanics are removed. Do not compensate with more types, validation, exceptions, or arbitrary edge cases.
            """;

    /** At most this many stage re-entries (see {@link #run}) are granted across the whole run, regardless of how many stages fail their gate on the first attempt. */
    private static final int MAX_TOTAL_REENTRIES = 2;

    private static final List<String> STAGE_PROGRESS_LABELS = List.of("Phase 1/3: specifying the exercise",
            "Phase 2/3: building executable learning increments across solution, template, and tests", "Phase 3/3: polishing the problem statement");

    /** Once the run has spent this long, no further stage is started; final (post-loop) verification decides the outcome of whatever was produced. */
    private static final Duration WALL_CLOCK_BUDGET = Duration.ofMinutes(22);

    /** Bound on the gate-failure progress line (the full report still goes to the info log and the returned final message). */
    private static final int MAX_GATE_PROGRESS_CHARS = 140;

    private final AgentLoopRunner agentLoopRunner;

    private final AgentSystemPromptService systemPromptService;

    private final StageCheckService stageCheckService;

    private final ApprovedSpecRegistry approvedSpecs;

    @Nullable
    private final SpecFidelityCriticService specificationReviewer;

    @Nullable
    private final ExerciseConceptSelector conceptSelector;

    private final AgentTranscriptWriter transcriptWriter;

    private final StagedContext stagedContext;

    /** Test hook so a wall-clock test can advance time deterministically instead of sleeping; production always uses the real clock. */
    private Supplier<Instant> clock = Instant::now;

    /**
     * The conversation-carry strategy for the enforced stage sequence (see {@link #run}).
     */
    enum StagedContext {

        /**
         * Executable stages (and their re-entries) continue one logical conversation via {@link AgentLoopRunner#runSession}. The terminal statement starts fresh so build logs,
         * grading-plan authoring instructions, and intermediate debugging do not compete with the student-facing projection.
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
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer, ExerciseConceptSelector conceptSelector,
            @Value("${artemis.hyperion.agent.staged-context:CONTINUOUS}") String stagedContext) {
        this.agentLoopRunner = agentLoopRunner;
        this.systemPromptService = systemPromptService;
        this.stageCheckService = stageCheckService;
        this.transcriptWriter = transcriptWriter;
        this.approvedSpecs = approvedSpecs;
        this.specificationReviewer = specificationReviewer;
        this.conceptSelector = conceptSelector;
        this.stagedContext = StagedContext.parse(stagedContext);
    }

    /** Test constructor: an isolated registry, so a staged run under test publishes its approved specification without a Spring context. */
    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, new ApprovedSpecRegistry(), null, null, stagedContext);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, null, null, stagedContext);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, specificationReviewer, null, stagedContext);
    }

    /**
     * The staged run's aggregated loop result together with the conversation it produced ({@code null} under {@link StagedContext#FRESH}), so the outer repair-attempt loop can
     * continue the same logical conversation instead of starting each repair blind.
     */
    public record StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation) {
    }

    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook) {
        return run(exercise, baseTools, tools, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, true, null);
    }

    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<Set<String>> structuralSeedHook, boolean specStageApplies, @Nullable Consumer<String> specSink) {
        return run(exercise, baseTools, tools, briefPrompt, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, specStageApplies,
                specSink);
    }

    /**
     * Runs the enforced stages in order, honouring a shared turn-budget pool, a wall-clock ceiling, and cooperative cancellation between stages, with the raw source brief kept
     * separate from the authoring context for the pre-freeze semantic review. Every shorter overload delegates here.
     *
     * @param exercise           the exercise being generated (Java/{@code GENERATE} only; the caller decides applicability)
     * @param baseTools          the shared, stateful {@link SandboxAgentTools} instance whose {@code enterStage} is called before every stage; never re-created per stage
     * @param tools              the tools object exposed to the model this turn (may be a decorator wrapping {@code baseTools})
     * @param briefPrompt        the current authoring context — the instructor brief or an outer-attempt repair prompt — injected fresh into every stage's user prompt
     * @param sourceBrief        the raw instructor brief, used as review authority and as the clean student-statement authoring context
     * @param seedTestsFiles     the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param sandbox            the open sandbox session
     * @param sessionId          the sandbox session id
     * @param cancelled          polled between stages (and inside each stage's own agent loop)
     * @param usageSink          receives token usage for every model call; may be {@code null}
     * @param progress           receives one short progress line per stage; may be {@code null}
     * @param structuralSeedHook refreshes generated Java structural tests during executable-build verification (the orchestrator's post-loop call remains the final source of
     *                               truth)
     * @param specStageApplies   whether to run the SPEC stage; {@code false} when the instructor already provided a non-trivial problem statement — that statement IS the
     *                               specification, and the model must not overwrite it with a restatement
     * @param specSink           receives the gate-approved SPEC.md snapshot right after the spec gate passes (early instructor observability and the orchestrator's frozen copy
     *                               for the critic and repair prompts); may be {@code null}
     * @return one aggregated {@link AgentLoopResult} — summed turns, the first {@code ERROR}/{@code CANCELLED} status encountered or else the last stage's status, and the last
     *         stage's final message (with the failing gate's report appended, if a gate failed) — together with the carried conversation
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
        AgentVerifyReport lastTestsReport = null;
        // CONTINUOUS carries one logical conversation across every stage (and re-entry) via AgentLoopRunner#runSession; FRESH never populates this (stays null forever), so
        // every stage starts a brand-new conversation via the plain run() call, exactly as before this feature existed.
        List<Message> conversation = null;
        List<Message> archivedConversation = new ArrayList<>();
        int reentriesRemaining = MAX_TOTAL_REENTRIES;
        int semanticSpecRefinementsUsed = 0;
        // The best specification this concept has produced, and how many findings it drew. Refinement is not monotonic — an observed run scored its worked-example replay
        // 1/2, then 2/2, then 0/2, then 0/2, and froze the last one — so the loop keeps the best rather than trusting the most recent. Reset whenever the concept is
        // replaced: a specification written for a rejected concept must never come back.
        String bestSpecSnapshot = null;
        int bestSpecFindingCount = Integer.MAX_VALUE;
        String semanticSpecFeedback = null;
        String previousRejectedLearningFitDirection = null;
        boolean freshSemanticSpecAttempt = false;
        String selectedConcept = null;
        int specificationReviewNumber = 0;
        baseTools.configureStructuralOracleRefresh(structuralSeedHook);

        if (specStageApplies && conceptSelector != null) {
            ExerciseConceptSelector.ConceptSelection selection = conceptSelector.select(sourceBrief, cancelled, usageSink, progress);
            totalTurns += selection.turns();
            remainingPool = Math.max(0, remainingPool - selection.turns());
            archivedConversation.addAll(selection.transcript());
            transcriptWriter.writeAudit(exercise.getId(), "concept-review-1", selection.auditSummary());
            if (cancelled.getAsBoolean()) {
                return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, selection.feedback(), archivedConversation, conversation);
            }
            if (!selection.complete()) {
                emit(progress, "Concept exploration was unavailable; continuing from the instructor brief. The mandatory specification review still decides whether the exercise "
                        + "has a coherent, sufficiently deep learning design.");
            }
            else if (!selection.accepted()) {
                String failure = "No exercise concept passed the brief and learning-fit review. No specification or repository artifacts were produced."
                        + (selection.feedback().isBlank() ? "" : "\n" + selection.feedback());
                emit(progress, failure);
                return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, failure, archivedConversation, conversation);
            }
            else {
                selectedConcept = selection.selectedConcept();
            }
        }

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
            if (continuous && stage == GenerationStage.STATEMENT && conversation != null) {
                archivedConversation.addAll(conversation);
                conversation = null;
            }

            int allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], rollover, remainingPool);
            emit(progress, STAGE_PROGRESS_LABELS.get(index));
            baseTools.enterStage(stage);
            String systemPrompt = systemPromptService.buildStage(exercise, stage);

            String gateFeedback = null;
            int stageReentriesUsed = 0;
            boolean stagePassed = false;
            while (!stagePassed) {
                if (wallClockExceeded(startedAt)) {
                    String failure = "Exercise generation reached its wall-clock budget before the next " + stage + " attempt, so no further model work was started.";
                    return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, failure), archivedConversation, conversation);
                }
                AgentLoopResult result;
                if (freshSemanticSpecAttempt) {
                    String userPrompt = freshSemanticSpecPrompt(sourceBrief, selectedConcept);
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
                    String stageBriefPrompt = switch (stage) {
                        case SPEC -> specPromptWithSelectedConcept(briefPrompt, selectedConcept);
                        // Statement authoring starts from a deliberately fresh context. Give it the raw instructor authority plus the typed SPEC/visible-test handoff below,
                        // not the turn-0 workspace/reference listing that was useful only while building executable artifacts.
                        case STATEMENT -> sourceBrief;
                        default -> briefPrompt;
                    };
                    String userPrompt = buildStagePrompt(stage, stageBriefPrompt, sandbox, sessionId, continuous && conversation != null, gateFeedback);
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
                GateEvaluation gateEvaluation = evaluateGate(stage, baseTools, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, structuralSeedHook);
                StageCheckResult gate = gateEvaluation.result();
                if (stage != GenerationStage.SPEC || !gate.passed()) {
                    emit(progress, gateProgressLabel(index, stage, gate, gateEvaluation.reused()));
                }
                if (stage == GenerationStage.TESTS) {
                    lastTestsReport = gate.report();
                    // The agent's own in-loop TESTS-stage checks may have set this already; setting it again from the runner's own official gate result (fresh or reused) covers
                    // the case where the gate never went through the tools at all (a reused cache, or a TESTS turn that never called verify/submit), so STATEMENT is never blind.
                    baseTools.recordLastTestsReport(gate.report());
                }

                if (gate.passed()) {
                    if (stage == GenerationStage.SPEC) {
                        String specSnapshot = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
                        if (specSnapshot.isBlank()) {
                            String failure = "SPEC.md passed its consistency gate but could not be read back for semantic review and approval. Generation stopped before downstream "
                                    + "artifacts were produced from an unfrozen contract.";
                            return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, failure), archivedConversation, conversation);
                        }
                        if (specificationReviewer != null) {
                            if (wallClockExceeded(startedAt)) {
                                String failure = "Exercise generation reached its wall-clock budget before specification review, so the contract was not approved.";
                                return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, failure), archivedConversation, conversation);
                            }
                            emit(progress, "Reviewing the specification against the instructor brief");
                            SpecFidelityCriticService.SpecificationReview review = selectedConcept == null
                                    ? specificationReviewer.reviewSpecification(sourceBrief, specSnapshot, usageSink, cancelled)
                                    : specificationReviewer.reviewSpecification(sourceBrief, selectedConcept, specSnapshot, usageSink, cancelled);
                            transcriptWriter.writeAudit(exercise.getId(), "spec-review-" + ++specificationReviewNumber, specificationReviewAudit(review));
                            if (cancelled.getAsBoolean()) {
                                return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
                            }
                            if (review.complete() && review.findings().size() < bestSpecFindingCount) {
                                bestSpecFindingCount = review.findings().size();
                                bestSpecSnapshot = specSnapshot;
                            }
                            if (!review.complete()) {
                                // Fail open on the subjective axis. A qualitative reviewer that cannot return a well-formed verdict must never discard a specification that
                                // already passed the deterministic mechanical gate. Freeze the checked contract and let downstream mechanical verification (compile, tests,
                                // differential oracle — all still fail-closed), the post-generation artifact critic, and instructor review carry quality forward.
                                // The same non-monotonic refinement the restore below exists for: if the LAST review broke, the current draft is unmeasured, and a strictly
                                // better measured one may already be in hand. Restoring it here costs nothing and keeps the fail-open path from freezing the worse draft.
                                if (bestSpecSnapshot != null && !bestSpecSnapshot.equals(specSnapshot)) {
                                    String restoreAfterIncompleteReview = baseTools.writeFile("SPEC.md", bestSpecSnapshot);
                                    if (restoreAfterIncompleteReview != null && !restoreAfterIncompleteReview.startsWith("ERROR")) {
                                        log.info("Specification review was inconclusive for exercise {}; restoring the best measured draft ({} findings)", exercise.getId(),
                                                bestSpecFindingCount);
                                        specSnapshot = bestSpecSnapshot;
                                    }
                                    else {
                                        log.warn("Could not restore the best measured specification for exercise {}: {}", exercise.getId(), restoreAfterIncompleteReview);
                                    }
                                }
                                String reviewAdvisory = "The specification quality review was inconclusive; continuing with the mechanically checked specification. Any remaining "
                                        + "qualitative concerns are left for instructor review.";
                                log.warn("Specification review was inconclusive for exercise {}; freezing the mechanically-valid specification and continuing", exercise.getId());
                                emit(progress, reviewAdvisory);
                            }
                            else if (!review.accepted() && semanticSpecRefinementsUsed < MAX_SEMANTIC_SPEC_REFINEMENTS && remainingPool >= MIN_STAGE_BUDGET) {
                                String reviewFeedback = review.feedback();
                                log.info("Specification review rejected the candidate for exercise {}: {}", exercise.getId(), reviewFeedback);
                                semanticSpecRefinementsUsed++;
                                semanticSpecFeedback = reviewFeedback;
                                gateFeedback = semanticSpecRefinementPrompt(reviewFeedback);
                                String rejectedDirection = "SUFFICIENT".equals(review.learningFitDirection()) ? null : review.learningFitDirection();
                                boolean repeatedLearningFitFailure = rejectedDirection != null && rejectedDirection.equals(previousRejectedLearningFitDirection);
                                previousRejectedLearningFitDirection = rejectedDirection;
                                if (review.conceptualReworkRequired() || repeatedLearningFitFailure) {
                                    // The selected concept itself failed the pre-freeze review. Re-enter the same context-separated discovery boundary instead of asking
                                    // the SPEC agent to privately replace its own plan. Repeating the same learning-fit direction also triggers reselection: optimizing a
                                    // second rewrite against an unchanged qualitative proxy caused the number-puzzle escalation this boundary prevents.
                                    if (conversation != null) {
                                        archivedConversation.addAll(conversation);
                                    }
                                    conversation = null;
                                    String clearResult;
                                    try {
                                        clearResult = baseTools.writeFile("SPEC.md", "");
                                    }
                                    catch (RuntimeException e) {
                                        clearResult = "ERROR: " + e.getMessage();
                                    }
                                    if (clearResult == null || clearResult.startsWith("ERROR")) {
                                        String failure = "Could not clear the rejected specification before concept replacement. Generation stopped to prevent the rejected "
                                                + "contract from contaminating a fresh attempt." + (clearResult == null ? "" : "\n" + clearResult);
                                        return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, failure), archivedConversation,
                                                conversation);
                                    }
                                    if (conceptSelector != null) {
                                        if (wallClockExceeded(startedAt)) {
                                            String failure = "Exercise generation reached its wall-clock budget before replacement concept discovery, so no new model work "
                                                    + "was started.";
                                            return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, failure), archivedConversation,
                                                    conversation);
                                        }
                                        ExerciseConceptSelector.ConceptSelection replacement = conceptSelector.select(sourceBrief, CONCEPT_REPLACEMENT_FEEDBACK, cancelled,
                                                usageSink, progress);
                                        totalTurns += replacement.turns();
                                        remainingPool = Math.max(0, remainingPool - replacement.turns());
                                        archivedConversation.addAll(replacement.transcript());
                                        transcriptWriter.writeAudit(exercise.getId(), "concept-review-" + (semanticSpecRefinementsUsed + 1), replacement.auditSummary());
                                        if (cancelled.getAsBoolean()) {
                                            return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, replacement.feedback(), archivedConversation, conversation);
                                        }
                                        if (!replacement.accepted()) {
                                            String failure = replacement.complete() ? "No replacement exercise concept passed the learning-fit review."
                                                    : "Replacement concept discovery did not produce a reviewable decision.";
                                            return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns,
                                                    appendGateReport(lastFinalMessage, failure + "\n" + replacement.feedback()), archivedConversation, conversation);
                                        }
                                        selectedConcept = replacement.selectedConcept();
                                    }
                                    previousRejectedLearningFitDirection = null;
                                    // The concept is being replaced, so every specification measured so far described a concept the reviewer rejected.
                                    bestSpecSnapshot = null;
                                    bestSpecFindingCount = Integer.MAX_VALUE;
                                    // Neither rejected candidate text nor quote-rich SPEC feedback enters the fresh discovery/SPEC contexts. The independent reviewer
                                    // will assess the replacement from scratch against the raw brief.
                                    gateFeedback = null;
                                    semanticSpecFeedback = null;
                                    freshSemanticSpecAttempt = true;
                                }
                                emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": refining the specification after brief-fidelity review");
                                allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, remainingPool);
                                continue;
                            }
                            else if (!review.accepted()) {
                                // Refinement budget exhausted — fail open rather than discard a mechanically valid specification. Freeze it and surface the remaining findings
                                // as an advisory for instructor review instead of erroring the one-click generation. Objective gates downstream (compile/tests/oracle) stay
                                // fail-closed.
                                log.info("Specification review still had findings for exercise {} after exhausting the refinement budget; freezing with advisory: {}",
                                        exercise.getId(), review.feedback());
                                if (bestSpecSnapshot != null && bestSpecFindingCount < review.findings().size() && !bestSpecSnapshot.equals(specSnapshot)) {
                                    // A later refinement left the contract worse than one this concept already reached. Freezing the most recent draft would hand every
                                    // downstream stage the weaker contract for no reason, so restore the best one before it becomes read-only.
                                    String restore = baseTools.writeFile("SPEC.md", bestSpecSnapshot);
                                    if (restore != null && !restore.startsWith("ERROR")) {
                                        log.info("Restored the best reviewed specification for exercise {} ({} findings) over the final refinement ({} findings)", exercise.getId(),
                                                bestSpecFindingCount, review.findings().size());
                                        specSnapshot = bestSpecSnapshot;
                                        emit(progress, "Keeping the strongest reviewed specification this concept produced.");
                                    }
                                    else {
                                        log.warn("Could not restore the best reviewed specification for exercise {}: {}", exercise.getId(), restore);
                                    }
                                }
                                emit(progress, "Continuing with the reviewed specification; remaining concerns are attached for instructor review.");
                            }
                        }
                        // Publish the APPROVED specification before anything downstream runs. From here on it is read-only; later stages repair executable artifacts
                        // against this exact contract rather than weakening or expanding it under compile pressure.
                        approvedSpecs.approve(sessionId, specSnapshot);
                        if (specSink != null) {
                            specSink.accept(specSnapshot);
                        }
                        // SPEC approval is the provenance checkpoint. Keep the complete authoring conversation in the audit transcript, but downstream stages see only the
                        // instructor brief and the approved workspace contract — never rejected candidates or pre-freeze reviewer discussion as a competing authority.
                        if (conversation != null) {
                            archivedConversation.addAll(conversation);
                            conversation = null;
                        }
                        emit(progress, gateProgressLabel(index, stage, gate, gateEvaluation.reused()));
                    }
                    stagePassed = true;
                    break;
                }

                log.info("Staged generation gate failed at stage {} for exercise {}: {}", stage, exercise.getId(), gate.observation());
                if (stage == GenerationStage.SPEC) {
                    if (semanticSpecRefinementsUsed >= MAX_SEMANTIC_SPEC_REFINEMENTS || remainingPool < MIN_STAGE_BUDGET) {
                        return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, gate.observation()), archivedConversation,
                                conversation);
                    }
                    semanticSpecRefinementsUsed++;
                    gateFeedback = semanticSpecFeedback == null ? gate.observation() : semanticSpecCorrectionPrompt(semanticSpecFeedback, gate.observation());
                    semanticSpecFeedback = null;
                    emit(progress, "Phase " + (index + 1) + "/" + STAGE_ORDER.size() + ": refining the specification after its review or consistency check");
                    allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, remainingPool);
                    continue;
                }
                boolean stageCanReenter = stageReentriesUsed == 0 && reentriesRemaining > 0;
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
                reentriesRemaining--;
                gateFeedback = gate.observation();
                emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": retrying after gate feedback");
                allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], 0, remainingPool);
            }

        }
        return finish(exercise, lastStatus, totalTurns, lastFinalMessage, archivedConversation, conversation);
    }

    private static String semanticSpecRefinementPrompt(String reviewFeedback) {
        return reviewFeedback + """


                Treat the cited findings as review hypotheses, not instructions to patch isolated sentences. Re-read the whole current SPEC.md and confirm each finding
                against it. Preserve the selected concept's central situation, constraint, student-owned behavior, unaffected identifiers, ownership, and accepted semantic
                choices. Plan the smallest coherent contract revision, then replace SPEC.md with one complete `write_file` call rather than accumulating local edits. Reconcile
                every affected rule, example, policy algorithm, public API, ownership row, testing seam, and diagram decision together. Reviewer feedback deliberately supplies no
                replacement theme, identifier, API, or formula. Keep every required section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no
                decision valid. Resolve the grounded batch; if a finding is contradicted by the complete contract, preserve the correct contract and let the next fresh review
                adjudicate it. Replay every changed example through its named policy, then call the structured verify tool before finishing.
                """;
    }

    private static String specificationReviewAudit(SpecFidelityCriticService.SpecificationReview review) {
        if (!review.complete()) {
            return "The specification review did not produce a complete grounded verdict."
                    + (review.auditSummary().isBlank() ? "" : "\n\nValidation detail: " + review.auditSummary());
        }
        if (review.accepted()) {
            return "The specification review accepted the candidate with no blocking findings." + (review.auditSummary().isBlank() ? "" : "\n\n" + review.auditSummary());
        }
        return (review.auditSummary().isBlank() ? "" : review.auditSummary() + "\n\n") + review.feedback();
    }

    private static String freshSemanticSpecPrompt(String sourceBrief, @Nullable String selectedConcept) {
        return """
                Create a fresh specification from the instructor brief and newly reviewed concept below. A previous plan was discarded; it is deliberately absent from this fresh
                context. Instantiate the selected generator-authored concept coherently in one complete SPEC.md without adding unrelated complexity, then call the structured
                verify tool.

                INSTRUCTOR BRIEF:
                """ + sourceBrief.strip() + "\n\nNEWLY SELECTED GENERATOR-AUTHORED CONCEPT:\n"
                + (selectedConcept == null ? "No reviewed replacement concept is available; preserve the instructor brief exactly." : selectedConcept.strip());
    }

    private static String specPromptWithSelectedConcept(String briefPrompt, @Nullable String selectedConcept) {
        if (selectedConcept == null || selectedConcept.isBlank()) {
            return briefPrompt;
        }
        return briefPrompt + """


                SELECTED GENERATOR-AUTHORED CONCEPT (planning input, not yet an approved contract):
                ---
                """ + selectedConcept.strip() + """

                ---
                Instantiate this concept coherently in SPEC.md. The instructor brief remains authoritative, and the normal specification reviewer must still approve the result.
                """;
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
            Map<String, String> seedTestsFiles, @Nullable AgentVerifyReport lastTestsReport, Supplier<Set<String>> structuralSeedHook) {
        if (stage == GenerationStage.TESTS) {
            // The TESTS stage may repair solution/template after its in-loop check. Re-seed first and always run the official differential against that final artifact state;
            // a cached pass was computed with the pre-TESTS structural oracle and cannot prove the grading plan covers newly changed structural names.
            try {
                baseTools.refreshStructuralOracle();
            }
            catch (RuntimeException e) {
                return new GateEvaluation(StageCheckResult.failed("The server could not materialize the approved student-created structural contract: " + e.getMessage()
                        + ". Keep every approved student-created type public in the solution and absent from the template; do not edit the server-generated structural test assets."),
                        false);
            }
            return new GateEvaluation(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTestNames()), false);
        }
        Optional<StageCheckResult> reused = baseTools.reuseCachedPassingCheck(stage);
        if (reused.isPresent()) {
            return new GateEvaluation(reused.get(), true);
        }
        return new GateEvaluation(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTestNames()), false);
    }

    /**
     * Builds a stage's user prompt. When no conversation is carried this re-injects the current SPEC.md and workspace layout; a carried conversation already has that context.
     *
     * @param carriesConversation whether this call receives a prior conversation
     * @param retryFeedback       the previous failed attempt's gate report, folded into a {@code FRESH}-mode retry prompt ({@code null} on a first attempt, and always {@code null}
     *                                under {@code continuous} — a continuous retry hands the feedback back as its own turn instead, see {@link #run})
     */
    private String buildStagePrompt(GenerationStage stage, String briefPrompt, InteractiveSandbox sandbox, String sessionId, boolean carriesConversation,
            @Nullable String retryFeedback) {
        StringBuilder prompt = new StringBuilder(briefPrompt);
        if (carriesConversation) {
            prompt.append("\n\nContinue this session for the ").append(stage.displayName())
                    .append(" stage: follow that stage's instructions in the system prompt above. The specification and workspace state from earlier stages are already in this "
                            + "conversation; re-read a file only if you need to confirm its exact current contents.");
        }
        else {
            String specDocument = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
            if (!specDocument.isBlank()) {
                String label = stage == GenerationStage.STATEMENT ? "APPROVED EXERCISE CONTRACT — INTERNAL SOURCE; REWRITE IT SELF-CONTAINED AND NEVER NAME THIS SOURCE"
                        : "CURRENT SPEC.md";
                prompt.append("\n\n=== ").append(label).append(" ===\n").append(specDocument.strip()).append("\n=== END ").append(label).append(" ===");
            }
            if (stage != GenerationStage.STATEMENT) {
                String layout = execRead(sandbox, sessionId, "sh", "-c",
                        "find " + GenerationWorkspaceService.WORKSPACE + " -maxdepth 3 -type f -not -path '*/target/*' | head -80");
                if (!layout.isBlank()) {
                    prompt.append("\n\n=== CURRENT WORKSPACE LAYOUT ===\n").append(layout.strip()).append("\n=== END WORKSPACE LAYOUT ===");
                }
            }
        }
        if (stage == GenerationStage.STATEMENT) {
            String handoff = statementHandoff(sandbox, sessionId);
            if (!handoff.isBlank()) {
                prompt.append("\n\n").append(handoff);
            }
        }
        if (retryFeedback != null && !retryFeedback.isBlank()) {
            prompt.append("\n\n=== GATE FEEDBACK FROM THE PREVIOUS ATTEMPT AT THIS STAGE ===\n").append(retryFeedback).append("\n=== END GATE FEEDBACK ===");
        }
        return prompt.toString();
    }

    /**
     * Projects the accepted grading plan into the only facts statement authoring needs. Raw build output and TESTS-stage instructions are deliberately excluded: they are
     * debugging context, not student-facing contract evidence.
     */
    private String statementHandoff(InteractiveSandbox sandbox, String sessionId) {
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (planJson.isBlank()) {
            return "";
        }
        try {
            GeneratedTestPlan plan = GeneratedTestPlan.parse(planJson);
            StringBuilder handoff = new StringBuilder("=== ACCEPTED STATEMENT HANDOFF ===\n");
            handoff.append("Bind tasks and testsColor links only to these visible tests, grouped by specification seam:\n");
            plan.visibleEntries().stream()
                    .collect(Collectors.groupingBy(GeneratedTestPlan.Entry::seam, LinkedHashMap::new,
                            Collectors.mapping(GeneratedTestPlan.Entry::name, Collectors.toUnmodifiableList())))
                    .forEach((seam, names) -> handoff.append("- ").append(seam).append(": ").append(String.join(", ", names)).append("\n"));
            if (!plan.hiddenEntries().isEmpty()) {
                handoff.append(plan.hiddenEntries().size())
                        .append(" hidden test(s) are intentionally omitted from this handoff. Bind only the visible names above; do not inspect or reveal hidden names.\n");
            }
            handoff.append("Write the complete student-facing artifact with write_file(\"problem-statement.md\", ...). A prose chat response does not create the artifact.\n")
                    .append("=== END ACCEPTED STATEMENT HANDOFF ===");
            return handoff.toString();
        }
        catch (IllegalArgumentException e) {
            log.warn("Could not project the accepted test plan into the statement handoff for session {}: {}", sessionId, e.getMessage());
            return "";
        }
    }

    private String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            SandboxExecResult result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }

    private boolean wallClockExceeded(Instant startedAt) {
        return Duration.between(startedAt, clock.get()).compareTo(WALL_CLOCK_BUDGET) > 0;
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
