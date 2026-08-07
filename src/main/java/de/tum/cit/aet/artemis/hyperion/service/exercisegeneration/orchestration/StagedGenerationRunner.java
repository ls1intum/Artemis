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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GeneratedTestPlan;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Runs one Java/{@code GENERATE} agent attempt in three enforced phases — specification, coherent executable build, and student-facing statement — each with its own system prompt
 * ({@link AgentSystemPromptService#buildStage}), its own bounded turn budget, and a mechanical gate that must pass before the next stage starts. It stands in for a single
 * {@code agentLoopRunner.run(...)} call: the aggregated {@link AgentLoopResult} it returns is consumed exactly like a single-call result, so workspace seeding, mechanical
 * verification, spec-fidelity review, and the outer repair-attempt loop are unaffected by the staging.
 * <p>
 * Phase file tools enforce write scope: specification is frozen after approval; the executable builder owns solution, template, tests, and grading plan together; the final
 * projection owns only the statement. There is no re-entry into an <em>earlier</em> phase — a gate failure that exhausts its own re-entry budget stops the whole run immediately
 * and hands the aggregated result (with the gate report appended) back to the outer attempt loop.
 * <p>
 * {@code artemis.hyperion.agent.staged-context} ({@link StagedContext}, default {@code CONTINUOUS}) controls conversation continuity across stages. Either way, a stage's first
 * gate failure buys one re-entry (same stage, gate feedback fed back in) while the shared pool still holds at least {@link #MIN_STAGE_BUDGET} turns and the run has not spent its
 * {@link #MAX_TOTAL_REENTRIES} whole-run re-entry budget; a second failure at the same stage stops the run.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StagedGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(StagedGenerationRunner.class);

    private static final int MAX_SEMANTIC_SPEC_REFINEMENTS = 3;

    private static final int SEMANTIC_SPEC_REFINEMENT_BUDGET = 5;

    /**
     * One planning checkpoint, one coherent executable build, one student-facing projection. Solution, template, tests, and grading plan share a stage so the builder can complete
     * risk-chosen seams vertically instead of handing four mutually dependent artifacts down a waterfall.
     */
    private static final List<GenerationStage> STAGE_ORDER = List.of(GenerationStage.SPEC, GenerationStage.TESTS, GenerationStage.STATEMENT);

    private static final int[] STAGE_BASE_BUDGETS = { 7, 54, 7 };

    /**
     * Hard ceiling on authoring-agent turns spent across all generation phases. A stage (or re-entry) is only started while at least {@link #MIN_STAGE_BUDGET} turns remain, so
     * the cap holds. The context-isolated concept selector is bounded separately (two one-turn candidate batches per selection, at most four selections).
     */
    private static final int POOL_HARD_CAP = 83;

    private static final int STATEMENT_TURN_RESERVE = STAGE_BASE_BUDGETS[2];

    private static final int MIN_STAGE_BUDGET = 3;

    private static final String CONCEPT_REPLACEMENT_FEEDBACK = """
            After the previous concept was instantiated and reviewed, its central interaction could not satisfy the requested learning objective, difficulty, domain grounding,
            feasibility, or proportionality without replacement. Generate a genuinely different central interaction whose substance belongs to learner-owned work after
            prescribed transcription and routine mechanics are removed. Do not compensate with more types, validation, exceptions, or arbitrary edge cases.
            """;

    private static final int MAX_TOTAL_REENTRIES = 2;

    private static final List<String> STAGE_PROGRESS_LABELS = List.of("Phase 1/3: specifying the exercise",
            "Phase 2/3: building executable learning increments across solution, template, and tests", "Phase 3/3: polishing the problem statement");

    /**
     * How much of the configured job deadline ({@code artemis.hyperion.agent.max-job-duration}) is held back for everything that runs after this authoring phase. The deadline
     * cancels mid-flight without preserving unverified work, so what this must guarantee is reaching the first differential verification pass — artifact capture plus the pristine
     * solution and template builds — which is what turns "the deadline fired and nothing was produced" into a checkpoint the deadline branch may keep and save. Calibrated against
     * Java Maven builds, the only shape this phase runs for. Repair rounds and the fidelity review share the remainder but need no guarantee; persistence and CI synchronization
     * are excluded because the task service cancels the deadline timer before them.
     */
    private static final Duration POST_AUTHORING_RESERVE = Duration.ofMinutes(8);

    /** Mirrors the shipped {@code artemis.hyperion.agent.max-job-duration} default; used only by the test constructors, which have no property source to read it from. */
    private static final Duration DEFAULT_MAX_JOB_DURATION = Duration.ofMinutes(30);

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

    /** Derived from the configured job deadline; see {@link #authoringBudget(Duration)}. Never a private ceiling that could outrank an operator who changed that deadline. */
    private final Duration authoringBudget;

    /** Test hook so a wall-clock test can advance time deterministically instead of sleeping; production always uses the real clock. */
    private Supplier<Instant> clock;

    enum StagedContext {

        /**
         * Executable stages (and their re-entries) continue one logical conversation via {@link AgentLoopRunner#runSession}. The terminal statement starts fresh so build logs and
         * intermediate debugging do not compete with the student-facing projection.
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

    // Required: with the package-private test constructors also present, Spring cannot pick an injection constructor without it.
    @Autowired
    public StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer, ExerciseConceptSelector conceptSelector,
            HyperionAgentProperties agentProperties) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, specificationReviewer, conceptSelector, agentProperties.getStagedContext(),
                agentProperties.getMaxJobDuration());
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, @Nullable SpecFidelityCriticService specificationReviewer,
            @Nullable ExerciseConceptSelector conceptSelector, String stagedContext, Duration maxJobDuration) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, specificationReviewer, conceptSelector, StagedContext.parse(stagedContext),
                maxJobDuration, Instant::now);
    }

    private StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, @Nullable SpecFidelityCriticService specificationReviewer,
            @Nullable ExerciseConceptSelector conceptSelector, StagedContext stagedContext, Duration maxJobDuration, Supplier<Instant> clock) {
        this.agentLoopRunner = agentLoopRunner;
        this.systemPromptService = systemPromptService;
        this.stageCheckService = stageCheckService;
        this.transcriptWriter = transcriptWriter;
        this.approvedSpecs = approvedSpecs;
        this.specificationReviewer = specificationReviewer;
        this.conceptSelector = conceptSelector;
        this.stagedContext = stagedContext;
        this.authoringBudget = authoringBudget(maxJobDuration);
        this.clock = clock;
    }

    StagedGenerationRunner forSettings(@Nullable HyperionGenerationSettings settings, AgentLoopRunner profileRunner, SpecFidelityCriticService profileReviewer) {
        if (settings == null) {
            return this;
        }
        ExerciseConceptSelector profileConceptSelector = conceptSelector == null ? null : new ExerciseConceptSelector(profileRunner, profileReviewer);
        SpecFidelityCriticService profileSpecificationReviewer = specificationReviewer == null ? null : profileReviewer;
        return new StagedGenerationRunner(profileRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, profileSpecificationReviewer,
                profileConceptSelector, StagedContext.parse(settings.stagedContext()), settings.maxJobDuration(), clock);
    }

    /** Leaves time for differential verification without consuming more than half of a short job deadline. */
    static Duration authoringBudget(Duration maxJobDuration) {
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-job-duration must be positive");
        }
        Duration reserve = POST_AUTHORING_RESERVE.compareTo(maxJobDuration.dividedBy(2)) <= 0 ? POST_AUTHORING_RESERVE : maxJobDuration.dividedBy(2);
        return maxJobDuration.minus(reserve);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, new ApprovedSpecRegistry(), null, null, stagedContext, DEFAULT_MAX_JOB_DURATION);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, null, null, stagedContext, DEFAULT_MAX_JOB_DURATION);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer, String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, specificationReviewer, null, stagedContext, DEFAULT_MAX_JOB_DURATION);
    }

    StagedGenerationRunner(AgentLoopRunner agentLoopRunner, AgentSystemPromptService systemPromptService, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs, SpecFidelityCriticService specificationReviewer, ExerciseConceptSelector conceptSelector,
            String stagedContext) {
        this(agentLoopRunner, systemPromptService, stageCheckService, transcriptWriter, approvedSpecs, specificationReviewer, conceptSelector, stagedContext,
                DEFAULT_MAX_JOB_DURATION);
    }

    /**
     * The staged run's aggregated loop result together with the conversation it produced ({@code null} under {@link StagedContext#FRESH}), so the outer repair-attempt loop can
     * continue the same logical conversation instead of starting each repair blind.
     */
    public record StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings,
            @Nullable TerminationReason terminationReason, List<String> unresolvedConceptFindings) {

        public StagedRunOutcome {
            unresolvedSpecificationFindings = List.copyOf(unresolvedSpecificationFindings);
            unresolvedConceptFindings = List.copyOf(unresolvedConceptFindings);
        }

        public StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings,
                @Nullable TerminationReason terminationReason) {
            this(result, conversation, unresolvedSpecificationFindings, terminationReason, List.of());
        }

        public StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation) {
            this(result, conversation, List.of(), null, List.of());
        }

        public StagedRunOutcome(AgentLoopResult result, @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings) {
            this(result, conversation, unresolvedSpecificationFindings, null, List.of());
        }

        /** Returns a copy carrying the objections raised against the concept this outcome was built from; an empty list leaves the outcome unchanged. */
        StagedRunOutcome withConceptFindings(List<String> conceptFindings) {
            if (conceptFindings.isEmpty()) {
                return this;
            }
            return new StagedRunOutcome(result, conversation, unresolvedSpecificationFindings, terminationReason, conceptFindings);
        }
    }

    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<SeededStructuralTests> structuralSeedHook) {
        return run(exercise, baseTools, tools, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, true, null);
    }

    StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<SeededStructuralTests> structuralSeedHook, boolean specStageApplies, @Nullable Consumer<String> specSink) {
        return run(exercise, baseTools, tools, briefPrompt, briefPrompt, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, specStageApplies,
                specStageApplies, specSink);
    }

    public StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, String sourceBrief, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<SeededStructuralTests> structuralSeedHook, boolean specStageApplies, @Nullable Consumer<String> specSink) {
        return run(exercise, baseTools, tools, briefPrompt, sourceBrief, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress, structuralSeedHook, specStageApplies,
                specStageApplies, specSink);
    }

    /**
     * Runs the enforced stages in order, honouring a shared turn-budget pool, the wall-clock budget derived from the configured job deadline (see {@link #authoringBudget}), and
     * cooperative cancellation between stages. The raw source brief is kept separate from the authoring context so the pre-freeze semantic review has an untainted authority.
     * Every shorter overload delegates here.
     *
     * @param exercise                the exercise being generated (Java/{@code GENERATE} only; the caller decides applicability)
     * @param baseTools               the shared, stateful tools instance whose {@code enterStage} is called before every stage; never re-created per stage
     * @param tools                   the tools object exposed to the model this turn (may be a decorator wrapping {@code baseTools})
     * @param briefPrompt             the current authoring context — the instructor brief or an outer-attempt repair prompt — injected fresh into every stage's user prompt
     * @param sourceBrief             the raw instructor brief, used as review authority and as the clean student-statement authoring context
     * @param seedTestsFiles          the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param sandbox                 the open sandbox session
     * @param sessionId               the sandbox session id
     * @param cancelled               polled between stages (and inside each stage's own agent loop)
     * @param usageSink               receives token usage for every model call; may be {@code null}
     * @param progress                receives one short progress line per stage; may be {@code null}
     * @param structuralSeedHook      refreshes generated Java structural tests during executable-build verification; the orchestrator's post-loop call remains the final source of
     *                                    truth
     * @param specStageApplies        whether to compile and review an internal executable specification
     * @param conceptSelectionApplies whether the model must invent a concept first; false when an authoritative statement already fixes it
     * @param specSink                receives the gate-approved SPEC.md snapshot right after the spec gate passes, which is also the orchestrator's frozen copy for the critic and
     *                                    repair prompts; may be {@code null}
     * @return one aggregated {@link AgentLoopResult} — summed turns, the first {@code ERROR}/{@code CANCELLED} status encountered or else the last stage's status, and the last
     *         stage's final message (with the failing gate's report appended, if a gate failed) — together with the carried conversation
     */
    public StagedRunOutcome run(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, String sourceBrief, Map<String, String> seedTestsFiles,
            InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress,
            Supplier<SeededStructuralTests> structuralSeedHook, boolean specStageApplies, boolean conceptSelectionApplies, @Nullable Consumer<String> specSink) {
        // Owned here rather than threaded through the stage machine below, so that objections raised against a concept the run proceeded with anyway leave through every exit:
        // gate failure, wall clock, cancellation, or a clean finish.
        List<String> conceptFindings = new ArrayList<>();
        StagedRunOutcome outcome = runStages(exercise, baseTools, tools, briefPrompt, sourceBrief, seedTestsFiles, sandbox, sessionId, cancelled, usageSink, progress,
                structuralSeedHook, specStageApplies, conceptSelectionApplies, specSink, conceptFindings);
        return conceptFindings.isEmpty() ? outcome : outcome.withConceptFindings(conceptFindings);
    }

    private StagedRunOutcome runStages(ProgrammingExercise exercise, SandboxAgentTools baseTools, Object tools, String briefPrompt, String sourceBrief,
            Map<String, String> seedTestsFiles, InteractiveSandbox sandbox, String sessionId, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable Consumer<String> progress, Supplier<SeededStructuralTests> structuralSeedHook, boolean specStageApplies, boolean conceptSelectionApplies,
            @Nullable Consumer<String> specSink, List<String> conceptFindings) {
        Instant startedAt = clock.get();
        boolean continuous = stagedContext == StagedContext.CONTINUOUS;
        int remainingPool = POOL_HARD_CAP;
        int rollover = 0;
        int totalTurns = 0;
        String lastFinalMessage = "";
        AgentLoopResult.Status lastStatus = AgentLoopResult.Status.COMPLETED;
        AgentVerifyReport lastTestsReport = null;
        // Stays null for the whole run under FRESH, which is what makes every stage there start a brand-new conversation via the plain run() call.
        List<Message> conversation = null;
        List<Message> archivedConversation = new ArrayList<>();
        int reentriesRemaining = MAX_TOTAL_REENTRIES;
        int semanticSpecRefinementsUsed = 0;
        // The best specification this concept has produced, and how many findings it drew. Refinement is not monotonic, so a later draft can be worse than an earlier one and the
        // loop keeps the best rather than the most recent. Reset whenever the concept is replaced: a specification written for a rejected concept must never come back.
        String bestSpecSnapshot = null;
        List<String> bestSpecFindings = List.of();
        int bestSpecFindingCount = Integer.MAX_VALUE;
        List<String> unresolvedSpecificationFindings = List.of();
        String semanticSpecFeedback = null;
        SpecFidelityCriticService.SpecificationReview previousSpecificationReview = null;
        String previousRejectedLearningFitDirection = null;
        boolean freshSemanticSpecAttempt = false;
        String selectedConcept = null;
        int specificationReviewNumber = 0;
        baseTools.configureStructuralOracleRefresh(structuralSeedHook);

        if (conceptSelectionApplies && conceptSelector != null) {
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
                ExerciseConceptSelector.ConceptFallback fallback = selection.fallback();
                if (fallback == null) {
                    String failure = "No exercise concept passed the brief and learning-fit review, and the review named no candidate to fall back to. No specification or "
                            + "repository artifacts were produced." + (selection.feedback().isBlank() ? "" : "\n" + selection.feedback());
                    emit(progress, failure);
                    return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, failure, archivedConversation, conversation, List.of(),
                            TerminationReason.NO_ADMISSIBLE_CONCEPT);
                }
                selectedConcept = fallback.concept();
                conceptFindings.addAll(conceptAdmissionNotes(fallback));
                emit(progress, "No exercise concept was admitted outright. Continuing with candidate " + fallback.candidate()
                        + ", the one the review rejected least, and attaching every objection for instructor review.");
                log.info("Exercise {}: no concept was admitted; proceeding with candidate {} ({} failed selection axes) and {} finding(s) attached", exercise.getId(),
                        fallback.candidate(), fallback.failedRequiredAxes(), conceptFindings.size());
            }
            else {
                selectedConcept = selection.selectedConcept();
            }
        }

        for (int index = 0; index < STAGE_ORDER.size(); index++) {
            GenerationStage stage = STAGE_ORDER.get(index);
            if (stage == GenerationStage.SPEC && !specStageApplies) {
                // Non-generation callers do not compile a new authoring contract.
                continue;
            }
            if (cancelled.getAsBoolean()) {
                return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
            }
            if (wallClockExceeded(startedAt)) {
                log.info("Staged generation wall-clock budget of {} exceeded before stage {} for exercise {}; stopping with {} stage(s) completed", authoringBudget, stage,
                        exercise.getId(), index);
                emit(progress, "Phase " + (index + 1) + "/" + STAGE_ORDER.size() + " was skipped because the authoring time budget was exhausted.");
                break;
            }
            if (allocatablePool(stage, remainingPool) < MIN_STAGE_BUDGET) {
                // The pool is the hard turn ceiling: starting another stage with the floor budget would silently exceed it, so stop here and let post-loop verification decide.
                log.info("Staged generation turn pool exhausted before stage {} for exercise {}; stopping with {} stage(s) completed", stage, exercise.getId(), index);
                emit(progress, "Phase " + (index + 1) + "/" + STAGE_ORDER.size() + " was skipped because the shared agent-turn budget was exhausted.");
                break;
            }
            if (continuous && stage == GenerationStage.STATEMENT && conversation != null) {
                archivedConversation.addAll(conversation);
                conversation = null;
            }

            int allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], rollover, allocatablePool(stage, remainingPool));
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
                if (continuous && gateFeedback != null) {
                    // A failed gate starts a new attempt rather than another turn in the trajectory that produced the failure: keep the old trajectory for diagnostics, but give
                    // the model the current artifacts plus the gate report instead of its own stale assumptions and tool history.
                    if (conversation != null) {
                        archivedConversation.addAll(conversation);
                        conversation = null;
                    }
                    baseTools.enterStage(stage);
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
                else {
                    String stageBriefPrompt = switch (stage) {
                        case SPEC -> specPromptWithSelectedConcept(briefPrompt, selectedConcept);
                        // Statement authoring starts from the raw instructor authority plus the typed SPEC/visible-test handoff below, not the turn-0 workspace listing, which is
                        // useful only while building executable artifacts.
                        case STATEMENT -> sourceBrief;
                        default -> briefPrompt;
                    };
                    String userPrompt = buildStagePrompt(stage, stageBriefPrompt, sandbox, sessionId, continuous && conversation != null, baseTools.seededStructuralTestNames(),
                            gateFeedback);
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
                    // A gate that never went through the tools (a reused cache, or a TESTS turn that never called verify/submit) would leave STATEMENT blind, so the official
                    // gate result is always written back even though the agent's in-loop checks may have recorded it already.
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
                            SpecFidelityCriticService.SpecificationReview review;
                            if (previousSpecificationReview != null) {
                                review = specificationReviewer.reviewSpecification(sourceBrief, selectedConcept, specSnapshot, previousSpecificationReview, usageSink, cancelled);
                            }
                            else if (selectedConcept == null) {
                                review = specificationReviewer.reviewSpecification(sourceBrief, specSnapshot, usageSink, cancelled);
                            }
                            else {
                                review = specificationReviewer.reviewSpecification(sourceBrief, selectedConcept, specSnapshot, usageSink, cancelled);
                            }
                            previousSpecificationReview = review;
                            transcriptWriter.writeAudit(exercise.getId(), "spec-review-" + ++specificationReviewNumber, specificationReviewAudit(review));
                            if (cancelled.getAsBoolean()) {
                                return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
                            }
                            if (review.complete() && review.findings().size() < bestSpecFindingCount) {
                                bestSpecFindingCount = review.findings().size();
                                bestSpecSnapshot = specSnapshot;
                                bestSpecFindings = review.findings();
                            }
                            if (!review.complete()) {
                                // Fail open on the subjective axis: a qualitative reviewer that cannot return a well-formed verdict must never discard a specification that
                                // already passed the deterministic mechanical gate. The current draft is unmeasured on this path, so a strictly better measured draft already in
                                // hand is restored rather than frozen over.
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
                                String reviewAdvisory;
                                if (bestSpecSnapshot != null && bestSpecSnapshot.equals(specSnapshot)) {
                                    unresolvedSpecificationFindings = bestSpecFindings;
                                    reviewAdvisory = bestSpecFindings.isEmpty()
                                            ? "The latest specification review was inconclusive; continuing with the previously accepted, mechanically checked specification."
                                            : "The latest specification review was inconclusive; continuing with the strongest measured specification and attaching its remaining "
                                                    + "concerns for instructor review.";
                                }
                                else {
                                    unresolvedSpecificationFindings = unresolvedInconclusiveReviewFindings(review);
                                    reviewAdvisory = "The specification quality review was inconclusive; continuing with the mechanically checked specification and attaching that "
                                            + "uncertainty for instructor review.";
                                }
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
                                if (conceptSelectionApplies && (review.conceptualReworkRequired() || repeatedLearningFitFailure)) {
                                    // The selected concept itself failed the pre-freeze review, so re-enter the context-separated discovery boundary rather than let the SPEC
                                    // agent privately replace its own plan. A repeated learning-fit direction also triggers reselection: rewriting a second time against an
                                    // unchanged qualitative proxy optimizes the proxy instead of the concept.
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
                                        ExerciseConceptSelector.ConceptFallback replacementFallback = replacement.fallback();
                                        if (!replacement.accepted() && replacementFallback == null) {
                                            String failure = replacement.complete()
                                                    ? "No replacement exercise concept passed the learning-fit review, and the review named no candidate to fall back to."
                                                    : "Replacement concept discovery did not produce a reviewable decision.";
                                            return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns,
                                                    appendGateReport(lastFinalMessage, failure + "\n" + replacement.feedback()), archivedConversation, conversation, List.of(),
                                                    replacement.complete() ? TerminationReason.NO_ADMISSIBLE_CONCEPT : null);
                                        }
                                        if (replacement.accepted()) {
                                            selectedConcept = replacement.selectedConcept();
                                            // The rejected concept's objections described a design this run no longer builds, so carrying them would mislead the instructor.
                                            conceptFindings.clear();
                                        }
                                        else {
                                            selectedConcept = replacementFallback.concept();
                                            conceptFindings.clear();
                                            conceptFindings.addAll(conceptAdmissionNotes(replacementFallback));
                                            emit(progress, "No replacement concept was admitted outright. Continuing with candidate " + replacementFallback.candidate()
                                                    + ", the one the review rejected least, and attaching every objection for instructor review.");
                                            log.info("Exercise {}: no replacement concept was admitted; proceeding with candidate {} ({} failed selection axes)", exercise.getId(),
                                                    replacementFallback.candidate(), replacementFallback.failedRequiredAxes());
                                        }
                                    }
                                    previousRejectedLearningFitDirection = null;
                                    // The concept is being replaced, so every specification measured so far described a concept the reviewer rejected.
                                    bestSpecSnapshot = null;
                                    bestSpecFindingCount = Integer.MAX_VALUE;
                                    bestSpecFindings = List.of();
                                    previousSpecificationReview = null;
                                    // Neither rejected candidate text nor quote-rich SPEC feedback enters the fresh discovery/SPEC contexts: the independent reviewer assesses
                                    // the replacement from scratch against the raw brief.
                                    gateFeedback = null;
                                    semanticSpecFeedback = null;
                                    freshSemanticSpecAttempt = true;
                                }
                                emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": refining the specification after brief-fidelity review");
                                allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, allocatablePool(stage, remainingPool));
                                continue;
                            }
                            else if (!review.accepted()) {
                                // Refinement budget exhausted — fail open rather than discard a mechanically valid specification: freeze it and surface the remaining findings as
                                // an advisory for instructor review. Objective gates downstream (compile/tests/oracle) stay fail-closed.
                                log.info("Specification review still had findings for exercise {} after exhausting the refinement budget; freezing with advisory: {}",
                                        exercise.getId(), review.feedback());
                                if (bestSpecSnapshot != null && bestSpecFindingCount < review.findings().size() && !bestSpecSnapshot.equals(specSnapshot)) {
                                    // A later refinement left the contract worse than one this concept already reached, so restore the best draft before it becomes read-only.
                                    String restore = baseTools.writeFile("SPEC.md", bestSpecSnapshot);
                                    if (restore != null && !restore.startsWith("ERROR")) {
                                        log.info("Restored the best reviewed specification for exercise {} ({} findings) over the final refinement ({} findings)", exercise.getId(),
                                                bestSpecFindingCount, review.findings().size());
                                        specSnapshot = bestSpecSnapshot;
                                        unresolvedSpecificationFindings = bestSpecFindings;
                                        emit(progress, "Keeping the strongest reviewed specification this concept produced.");
                                    }
                                    else {
                                        log.warn("Could not restore the best reviewed specification for exercise {}: {}", exercise.getId(), restore);
                                    }
                                }
                                if (unresolvedSpecificationFindings.isEmpty()) {
                                    unresolvedSpecificationFindings = review.findings();
                                }
                                emit(progress, "Continuing with the reviewed specification; remaining concerns are attached for instructor review.");
                            }
                        }
                        // Publish the APPROVED specification before anything downstream runs. From here on it is read-only: later stages repair executable artifacts against this
                        // exact contract rather than weakening or expanding it under compile pressure.
                        approvedSpecs.approve(sessionId, specSnapshot);
                        if (specSink != null) {
                            specSink.accept(specSnapshot);
                        }
                        // SPEC approval is the provenance checkpoint: the audit transcript keeps the complete authoring conversation, but downstream stages see only the
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
                    if (semanticSpecRefinementsUsed >= MAX_SEMANTIC_SPEC_REFINEMENTS || allocatablePool(stage, remainingPool) < MIN_STAGE_BUDGET) {
                        return finish(exercise, AgentLoopResult.Status.ERROR, totalTurns, appendGateReport(lastFinalMessage, gate.observation()), archivedConversation,
                                conversation);
                    }
                    semanticSpecRefinementsUsed++;
                    gateFeedback = semanticSpecFeedback == null ? gate.observation() : semanticSpecCorrectionPrompt(semanticSpecFeedback, gate.observation());
                    semanticSpecFeedback = null;
                    emit(progress, "Phase " + (index + 1) + "/" + STAGE_ORDER.size() + ": refining the specification after its review or consistency check");
                    allocation = allocateStageBudget(SEMANTIC_SPEC_REFINEMENT_BUDGET, 0, allocatablePool(stage, remainingPool));
                    continue;
                }
                boolean stageCanReenter = stageReentriesUsed == 0 && reentriesRemaining > 0;
                if (!stageCanReenter || allocatablePool(stage, remainingPool) < MIN_STAGE_BUDGET) {
                    // A failed TESTS gate still lets STATEMENT run: it can be authored from the approved SPEC and the tests that did execute, and the outer verifier then gives
                    // one repair context both artifacts instead of making a missing statement compete with the original test defect. SPEC has no safe downstream authority and
                    // STATEMENT has no later stage, so those still stop here.
                    if (stage == GenerationStage.TESTS) {
                        lastFinalMessage = appendGateReport(lastFinalMessage, gate.observation());
                        emit(progress, "The executable-build gate is still failing; preserving the reserved statement phase before authoritative repair.");
                        break;
                    }
                    return finish(exercise, stage == GenerationStage.SPEC ? AgentLoopResult.Status.ERROR : lastStatus, totalTurns,
                            appendGateReport(lastFinalMessage, gate.observation()), archivedConversation, conversation);
                }
                // Cooperative cancellation between the failed attempt and its re-entry (the outer for-loop already checked before this stage's first attempt).
                if (cancelled.getAsBoolean()) {
                    return finish(exercise, AgentLoopResult.Status.CANCELLED, totalTurns, lastFinalMessage, archivedConversation, conversation);
                }
                stageReentriesUsed++;
                reentriesRemaining--;
                gateFeedback = gate.observation();
                emit(progress, "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": retrying after gate feedback");
                allocation = allocateStageBudget(STAGE_BASE_BUDGETS[index], 0, allocatablePool(stage, remainingPool));
            }

        }
        return finish(exercise, lastStatus, totalTurns, lastFinalMessage, archivedConversation, conversation, unresolvedSpecificationFindings);
    }

    /**
     * Renders one instructor-facing note per objection the concept review raised, led by a note naming the candidate the run actually proceeded with.
     * <p>
     * Findings are carried verbatim, never summarized, so that the broad selection review ({@code "Candidate N: ..."}) and the focused admission audit
     * ({@code "Selected concept failed focused admission: ..."}) stay distinguishable, and so a reviewer can still see the objections raised against candidates this run did not
     * build.
     */
    private static List<String> conceptAdmissionNotes(ExerciseConceptSelector.ConceptFallback fallback) {
        List<String> notes = new ArrayList<>();
        notes.add("The concept review admitted no candidate. This exercise was built from candidate " + fallback.candidate()
                + ", which the review rejected least, so the design below is a draft the review objected to rather than one it approved.");
        fallback.findings().stream().filter(finding -> !finding.isBlank()).map(String::strip).forEach(notes::add);
        return List.copyOf(notes);
    }

    private static List<String> unresolvedInconclusiveReviewFindings(SpecFidelityCriticService.SpecificationReview review) {
        if (review.riskHistory().isEmpty()) {
            return List.of("The automated specification quality review was inconclusive, so the mechanically checked contract requires instructor review.");
        }
        return review.riskHistory().stream().map(finding -> "Unresolved specification-review hypothesis from grounded evidence: " + finding).toList();
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
        return finish(exercise, status, totalTurns, finalMessage, archivedConversation, conversation, List.of());
    }

    private StagedRunOutcome finish(ProgrammingExercise exercise, AgentLoopResult.Status status, int totalTurns, String finalMessage, List<Message> archivedConversation,
            @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings) {
        return finish(exercise, status, totalTurns, finalMessage, archivedConversation, conversation, unresolvedSpecificationFindings, null, List.of());
    }

    private StagedRunOutcome finish(ProgrammingExercise exercise, AgentLoopResult.Status status, int totalTurns, String finalMessage, List<Message> archivedConversation,
            @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings, @Nullable TerminationReason terminationReason) {
        return finish(exercise, status, totalTurns, finalMessage, archivedConversation, conversation, unresolvedSpecificationFindings, terminationReason, List.of());
    }

    private StagedRunOutcome finish(ProgrammingExercise exercise, AgentLoopResult.Status status, int totalTurns, String finalMessage, List<Message> archivedConversation,
            @Nullable List<Message> conversation, List<String> unresolvedSpecificationFindings, @Nullable TerminationReason terminationReason,
            List<String> unresolvedConceptFindings) {
        List<Message> transcriptConversation = new ArrayList<>(archivedConversation);
        if (conversation != null) {
            transcriptConversation.addAll(conversation);
        }
        transcriptWriter.write(exercise.getId(), "attempt-1-staged-" + status.name().toLowerCase(Locale.ROOT), transcriptConversation);
        return new StagedRunOutcome(new AgentLoopResult(status, totalTurns, finalMessage), conversation, unresolvedSpecificationFindings, terminationReason,
                unresolvedConceptFindings);
    }

    /** A gate that reused the tools' cached check instead of re-running it says so, to keep the transcript honest about why it was instant. */
    private String gateProgressLabel(int index, GenerationStage stage, StageCheckResult gate, boolean reused) {
        String prefix = "Stage " + (index + 1) + "/" + STAGE_ORDER.size() + ": " + stage.displayName().toLowerCase(Locale.ROOT) + " gate ";
        if (!gate.passed()) {
            return prefix + "failed: " + firstLineBounded(gate.observation(), MAX_GATE_PROGRESS_CHARS);
        }
        return reused ? prefix + "passed (reused in-stage check)" : prefix + "passed";
    }

    private static String firstLineBounded(@Nullable String text, int maxChars) {
        if (text == null) {
            return "";
        }
        // A header line ending in ':' carries no information on its own, so fold the first content line in and the instructor-visible event actually says what went wrong.
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

    static int allocatablePool(GenerationStage stage, int remainingPool) {
        return stage == GenerationStage.STATEMENT ? remainingPool : Math.max(0, remainingPool - STATEMENT_TURN_RESERVE);
    }

    private record GateEvaluation(StageCheckResult result, boolean reused) {
    }

    /**
     * Evaluates one stage's exit gate: reuses the tools' cached passing check when nothing has changed since it ran (see {@link SandboxAgentTools#reuseCachedPassingCheck}), and
     * otherwise delegates to {@link StageCheckService}. This runner never decides itself whether a stage's artifact passed; it owns only stage sequencing, turn budgets, re-entry,
     * and this cache consultation.
     */
    private GateEvaluation evaluateGate(GenerationStage stage, SandboxAgentTools baseTools, InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise,
            Map<String, String> seedTestsFiles, @Nullable AgentVerifyReport lastTestsReport, Supplier<SeededStructuralTests> structuralSeedHook) {
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
            return new GateEvaluation(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTests()), false);
        }
        Optional<StageCheckResult> reused = baseTools.reuseCachedPassingCheck(stage);
        if (reused.isPresent()) {
            return new GateEvaluation(reused.get(), true);
        }
        return new GateEvaluation(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTests()), false);
    }

    /**
     * Builds a stage's user prompt. When no conversation is carried this re-injects the current SPEC.md and workspace layout; a carried conversation already has that context.
     * {@code retryFeedback} is the previous failed attempt's gate report, folded into a fresh retry prompt.
     */
    private String buildStagePrompt(GenerationStage stage, String briefPrompt, InteractiveSandbox sandbox, String sessionId, boolean carriesConversation,
            Set<String> seededStructuralTestNames, @Nullable String retryFeedback) {
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
            String handoff = statementHandoff(sandbox, sessionId, seededStructuralTestNames);
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
     * Projects the accepted grading plan and server-authored structural oracle into the only facts statement authoring needs. Raw build output and TESTS-stage instructions are
     * excluded: they are debugging context, not student-facing contract evidence.
     */
    private String statementHandoff(InteractiveSandbox sandbox, String sessionId, Set<String> seededStructuralTestNames) {
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (planJson.isBlank()) {
            return "";
        }
        try {
            GeneratedTestPlan plan = GeneratedTestPlan.parse(planJson);
            StringBuilder handoff = new StringBuilder("=== ACCEPTED STATEMENT HANDOFF ===\n");
            handoff.append("Use the exact lowercase singular Artemis task syntax `[task][Student-facing title](exactTestName)`. Bind every visible test below exactly once on "
                    + "the one task for its specification seam; a task may list multiple names separated by commas. Any testsColor links must use only these same exact test "
                    + "method names. Never use `[tasks]`, `[Task]`, display names, or hidden test names. Write each task marker as plain Markdown on its own line, without inline "
                    + "backticks or a fenced code block.\nVisible tests grouped by specification seam:\n");
            plan.visibleEntries().stream()
                    .collect(Collectors.groupingBy(GeneratedTestPlan.Entry::seam, LinkedHashMap::new,
                            Collectors.mapping(GeneratedTestPlan.Entry::name, Collectors.toCollection(ArrayList::new))))
                    .forEach((seam, names) -> handoff.append("- ").append(seam).append(": ").append(String.join(", ", names)).append("\n"));
            if (!seededStructuralTestNames.isEmpty()) {
                handoff.append("Server-seeded structural checks grouped by owner type (all are visible and must also be bound exactly once):\n");
                structuralTestsByOwner(seededStructuralTestNames)
                        .forEach((owner, names) -> handoff.append("- ").append(owner).append(": ").append(String.join(", ", names)).append("\n"));
                handoff.append("Add each structural name to the existing task whose work creates or declares that owner type/API; do not create one task per structural check. "
                        + "If several behavioral seams share the owner, attach the checks to the task that introduces the type/API and never duplicate them. These checks may "
                        + "carry zero score when behavioral evidence exists, but they are still visible Artemis progress checks for required student-created structure.\n");
            }
            if (!plan.hiddenEntries().isEmpty()) {
                handoff.append(plan.hiddenEntries().size()).append(
                        " hidden behavioral test(s) are intentionally omitted from this handoff. Bind only the visible names above; do not inspect or reveal hidden names.\n");
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

    /** Groups authoritative Ares names by the type inside their brackets while retaining every name even if a future provider uses another shape. */
    private static Map<String, List<String>> structuralTestsByOwner(Set<String> seededStructuralTestNames) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        seededStructuralTestNames.stream().sorted().forEach(name -> {
            int openBracket = name.indexOf('[');
            int closeBracket = name.lastIndexOf(']');
            String owner = openBracket >= 0 && closeBracket > openBracket + 1 ? name.substring(openBracket + 1, closeBracket) : "Other structural checks";
            grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(name);
        });
        return grouped;
    }

    private String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }

    private boolean wallClockExceeded(Instant startedAt) {
        return Duration.between(startedAt, clock.get()).compareTo(authoringBudget) > 0;
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

    void setClockForTests(Supplier<Instant> clock) {
        this.clock = clock;
    }
}
