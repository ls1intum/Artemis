package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationRequest;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The attempt loop of one generation or adaptation run: author a candidate, verify it mechanically, review it semantically, and — while budget remains — schedule exactly one
 * scoped repair before trying again.
 * <p>
 * A plain per-run object rather than a Spring bean: every field below is loop-carried state of a single run, and the whole point of this type is that the state lives together
 * with the rules that read it. {@link GenerationOrchestrationService} keeps the bean identity, the sandbox lifecycle around the loop, and the outcome resolution; it constructs
 * one instance per run once the sandbox is seeded and the agent tools exist.
 */
class GenerationAttemptLoop {

    private static final Logger log = LoggerFactory.getLogger(GenerationAttemptLoop.class);

    /** Initial candidate plus at most three mechanical repairs. */
    static final int MAX_MECHANICAL_ATTEMPTS = 4;

    record Dependencies(GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner, DifferentialVerificationService verifier,
            StructuralOracleSeedingService structuralOracleSeeder, SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService,
            StagedGenerationRunner stagedGenerationRunner, AgentTranscriptWriter transcriptWriter, boolean stagedGenerationEnabled, int maxTurns, int maxGenerationAttempts,
            int maxSemanticRepairs) {
    }

    /**
     * The seed of one run: the sandbox session, the seeded workspace, the baselines the gates compare against, the briefs, and the agent tools. All of it is decided before the
     * first attempt and none of it changes between attempts.
     */
    record RunContext(ProgrammingExercise exercise, GenerationMode mode, String jobId, InteractiveSandbox sandbox, String sessionId,
            GenerationWorkspaceService.WorkspaceSeed workspaceSeed, Map<String, String> testsSeedSnapshot, Map<String, String> placeholderReplacements,
            Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement, Set<String> baselineGradedTestNames, String sourceBrief,
            boolean specStageApplies, String systemPrompt, String firstPrompt, SandboxAgentTools baseTools, Object tools, BooleanSupplier cancelled,
            @Nullable GenerationProgressSink progress, Consumer<ChatResponse> usageSink) {
    }

    private record CandidateArtifacts(Map<RepositoryType, Map<String, String>> candidateFiles, Set<String> extractionFailed, VerificationRequest verificationRequest,
            @Nullable String testPlanJson) {
    }

    record CandidateSnapshot(AgentLoopResult loopResult, VerificationResult verification, Map<RepositoryType, Map<String, String>> producedFiles, String problemStatement,
            SpecFidelityReport reviewReport, @Nullable String specDocument, @Nullable String testPlanJson) {
    }

    record ExtractedCandidate(AgentLoopResult loopResult, Map<RepositoryType, Map<String, String>> producedFiles, String problemStatement) {
    }

    private enum LoopStep {
        NEXT_ATTEMPT, STOP
    }

    private final GenerationOrchestrationService service;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final DifferentialVerificationService verifier;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    private final SpecFidelityCriticService specFidelityCritic;

    private final GenerationJobService jobService;

    private final StagedGenerationRunner stagedGenerationRunner;

    private final AgentTranscriptWriter transcriptWriter;

    private final int maxTurns;

    private final int maxGenerationAttempts;

    private final ProgrammingExercise exercise;

    private final GenerationMode mode;

    private final String jobId;

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    private final GenerationWorkspaceService.WorkspaceSeed workspaceSeed;

    private final Map<String, String> testsSeedSnapshot;

    private final Map<String, String> placeholderReplacements;

    private final Map<RepositoryType, Map<String, String>> baselineRepositoryFiles;

    @Nullable
    private final String baselineProblemStatement;

    private final Set<String> baselineGradedTestNames;

    private final String reviewBrief;

    private final String authoringBrief;

    private final String systemPrompt;

    private final SandboxAgentTools baseTools;

    private final Object tools;

    private final BooleanSupplier cancelled;

    @Nullable
    private final GenerationProgressSink progress;

    private final Consumer<ChatResponse> usageSink;

    // Java/GENERATE is the only contract StagedGenerationRunner supports (see its javadoc); every other mode and language uses the single, open-ended agent-loop call.
    private final boolean useStagedGeneration;

    // The SPEC stage runs only when the instructor gave no real statement: an existing non-trivial statement IS the specification, and a competing SPEC.md would at best
    // duplicate it and at worst drift from it.
    private final boolean specStageApplies;

    // The gate-approved SPEC.md snapshot, frozen by the runner's spec gate: instructor-visible immediately, fed to the critic's grounding, and appended to every repair prompt so
    // scope-cutting under repair pressure faces the contract it is cutting.
    private final AtomicReference<String> specSnapshot = new AtomicReference<>();

    private String currentPrompt;

    @Nullable
    private AgentLoopResult loopResult;

    // One logical conversation spans the whole run: the first attempt hands its conversation out and every repair attempt continues it, instead of starting blind and re-reading
    // the workspace it just produced. Null when the first attempt produced no carryable conversation (FRESH staged context, or the unstaged path).
    @Nullable
    private List<Message> carriedConversation;

    private int totalAgentTurns;

    @Nullable
    private VerificationResult verification;

    // Overwritten each attempt, so the outcome carries the last (accepted or exhausted) attempt's tree. Persist reuses this rather than re-reading the sandbox, since verification
    // already extracted it for the integrity gates.
    private final Map<RepositoryType, Map<String, String>> producedFilesByType = new EnumMap<>(RepositoryType.class);

    private String producedProblemStatement = "";

    private SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();

    @Nullable
    private VerificationRequest lastRejectedVerificationRequest;

    // The repair phase's own state machine: the semantic repair budget, the surface fairness state, the once-per-run adoption and re-review opportunities, and the per-round
    // finding accounting. Held rather than inherited because none of it reads the sandbox, the verifier, or a model — only the review reports this loop hands it.
    private final RepairRoundScheduler repairScheduler;

    private int mechanicalCorrectionsAfterRepairRemaining = 1;

    private int mechanicalAttemptsBeforeAnyRepair;

    // The candidate as it stood before the semantic repair now in flight; it remains the review authority until that repair completes its own review.
    @Nullable
    private CandidateSnapshot candidateBeforeCurrentRepair;

    @Nullable
    private SemanticRepairBatch pendingSemanticRepair;

    @Nullable
    private SemanticRepairBatch lastSemanticRepair;

    // The checkpoints the owning service falls back to when the run ends abnormally: the last candidate that built and graded, and the last tree that was read back at all.
    @Nullable
    private CandidateSnapshot lastMechanicallyVerifiedCandidate;

    @Nullable
    private ExtractedCandidate lastExtractedCandidate;

    // Instrumentation only; nothing below is read by a scheduling decision, a gate, or the verdict.

    // Why this loop stopped, recorded at the exit that takes it. Written once per run: the run's only reader is the owning service, which stamps it onto the outcome so the
    // terminal event carries it. Prose in a log line cannot answer "budget or convergence"; this can.
    @Nullable
    private TerminationReason terminationReason;

    GenerationAttemptLoop(GenerationOrchestrationService service, Dependencies dependencies, RunContext context) {
        this.service = service;
        this.workspace = dependencies.workspace();
        this.agentLoopRunner = dependencies.agentLoopRunner();
        this.verifier = dependencies.verifier();
        this.structuralOracleSeeder = dependencies.structuralOracleSeeder();
        this.specFidelityCritic = dependencies.specFidelityCritic();
        this.jobService = dependencies.jobService();
        this.stagedGenerationRunner = dependencies.stagedGenerationRunner();
        this.transcriptWriter = dependencies.transcriptWriter();
        this.maxTurns = dependencies.maxTurns();
        this.maxGenerationAttempts = dependencies.maxGenerationAttempts();
        this.exercise = context.exercise();
        this.mode = context.mode();
        this.jobId = context.jobId();
        this.sandbox = context.sandbox();
        this.sessionId = context.sessionId();
        this.workspaceSeed = context.workspaceSeed();
        this.testsSeedSnapshot = context.testsSeedSnapshot();
        this.placeholderReplacements = context.placeholderReplacements();
        this.baselineRepositoryFiles = context.baselineRepositoryFiles();
        this.baselineProblemStatement = context.baselineProblemStatement();
        this.baselineGradedTestNames = context.baselineGradedTestNames();
        this.reviewBrief = context.sourceBrief();
        this.authoringBrief = GenerationOrchestrationService.renderAuthoringBrief(context.sourceBrief());
        this.systemPrompt = context.systemPrompt();
        this.baseTools = context.baseTools();
        this.tools = context.tools();
        this.cancelled = context.cancelled();
        this.progress = context.progress();
        this.usageSink = context.usageSink();
        this.useStagedGeneration = dependencies.stagedGenerationEnabled() && context.mode() == GenerationMode.GENERATE
                && context.exercise().getProgrammingLanguage() == ProgrammingLanguage.JAVA;
        this.specStageApplies = context.specStageApplies();
        this.currentPrompt = context.firstPrompt();
        // An adaptation gets a single semantic round whatever the configured generation budget is: its scope is one requested change, not an open authoring task.
        this.repairScheduler = new RepairRoundScheduler(context.mode() == GenerationMode.GENERATE ? dependencies.maxSemanticRepairs() : 1);
    }

    /**
     * Runs attempts until the candidate is accepted, a budget is exhausted, or the run ends early.
     * <p>
     * On mechanical rejection, the verifier's reasons are fed back and the attempt is retried within a bounded budget. The verifier enforces rules the agent's own verify.sh
     * cannot show — the template must fail a meaningful fraction, the problem statement must bind tasks — so a "builds, but not quite right" candidate is repaired here rather
     * than reaching an instructor.
     * <p>
     * The loop deliberately holds no wall-clock ceiling of its own; its bounds are the attempt count and, per attempt, the turn budget. Wall-clock is owned entirely by
     * {@code cancelled}, which carries the job deadline configured as {@code artemis.hyperion.agent.max-job-duration}: it is polled before every model turn, and a run it stops
     * still keeps (and saves) the last verified candidate. A private ceiling here could only duplicate that or silently contradict an operator who changed it.
     *
     * @return the outcome when the run ended inside the loop (cancelled, errored, or a preserved checkpoint), or {@code null} when the loop ran to completion and the caller
     *         resolves the outcome from the final state
     */
    @Nullable
    GenerationOutcome run() {
        for (int attempt = 1; attempt <= maxGenerationAttempts; attempt++) {
            runAttempt(attempt);
            GenerationOutcome stoppedOutcome = outcomeIfAgentStopped();
            if (stoppedOutcome != null) {
                return stoppedOutcome;
            }

            // Seeded structural names are authoritative enough to resolve task bindings, but the verifier still requires every seeded grading check to appear in the student
            // checklist, so a first-attempt omission comes back to the agent as a repair.
            Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);
            if (cancelled.getAsBoolean()) {
                return cancelledOutcome(cancelledResult(loopResult));
            }

            emit("Checking the exercise builds and grades (attempt " + attempt + " of " + maxGenerationAttempts + ")");
            workspace.cleanTransientBuildOutputs(sandbox, sessionId);
            CandidateArtifacts artifacts = captureArtifacts(seededStructuralTestNames);
            if (cancelled.getAsBoolean()) {
                return cancelledOutcome(cancelledResult(loopResult));
            }
            if (lastRejectedVerificationRequest != null && lastRejectedVerificationRequest.equals(artifacts.verificationRequest())) {
                emit("The agent resubmitted the unchanged rejected candidate; stopping without repeating the same verification.");
                terminationReason = TerminationReason.UNCHANGED_CANDIDATE_RESUBMITTED;
                break;
            }
            // Snapshot the approved SPEC.md before verification: each restore below resets the tmpfs workspace, and without re-seeding it the contract would be silently gone
            // for every later repair attempt and for the outcome's spec capture.
            String specDocumentSnapshot = GenerationOrchestrationService.readSpecDocument(sandbox, sessionId);
            verification = verifyCandidate(artifacts, specDocumentSnapshot);
            emit(verification.report());
            if (!verification.mechanicallyVerified() && artifacts.extractionFailed().isEmpty()) {
                lastRejectedVerificationRequest = artifacts.verificationRequest();
            }
            if (verification.mechanicallyVerified()) {
                checkpointMechanicallyVerifiedCandidate(artifacts, specDocumentSnapshot);
            }
            if (cancelled.getAsBoolean()) {
                return cancelledOutcome(cancelledResult(loopResult));
            }

            // Reviewing a candidate that cannot build or grade would spend provider quota on findings against artifacts the next attempt must replace anyway.
            if (verification.mechanicallyVerified()) {
                GenerationOutcome cancelledDuringReview = reviewCandidate(attempt, artifacts, specDocumentSnapshot);
                if (cancelledDuringReview != null) {
                    return cancelledDuringReview;
                }
            }
            else {
                specFidelityReport = SpecFidelityReport.empty();
            }

            // A validated witness is advisory, so nothing above blocks and the loop would otherwise stop with the witness never offered to the agent. One adoption round is
            // granted instead: once per generation and only on an otherwise finished candidate, so a witness can never drive repeated rewrites.
            // GENERATE only: an adaptation gets a single semantic round, and spending it on optional tests rather than on a defect would be a poor trade. The once-per-run and
            // budget guards live in the scheduler.
            boolean adoptWitnesses = verification.mechanicallyVerified() && !specFidelityReport.hasBlockingFindings() && attempt < maxGenerationAttempts
                    && mode == GenerationMode.GENERATE && repairScheduler.witnessAdoption(specFidelityReport).isPresent();
            if (verification.mechanicallyVerified() && !specFidelityReport.hasBlockingFindings() && !adoptWitnesses) {
                terminationReason = TerminationReason.CONVERGED;
                break;
            }
            if (attempt == maxGenerationAttempts) {
                terminationReason = TerminationReason.ATTEMPT_CAP_REACHED;
                break;
            }
            if (!verification.mechanicallyVerified() && repairScheduler.roundsStarted() == 0 && ++mechanicalAttemptsBeforeAnyRepair >= MAX_MECHANICAL_ATTEMPTS) {
                emit("The bounded mechanical repair phase is exhausted; keeping the current candidate for instructor review.");
                terminationReason = TerminationReason.MECHANICAL_REPAIR_EXHAUSTED;
                break;
            }
            if (verification.mechanicallyVerified()) {
                if (applySemanticRepair(attempt, adoptWitnesses, artifacts, specDocumentSnapshot) == LoopStep.STOP) {
                    break;
                }
                continue;
            }
            if (repairScheduler.roundsStarted() > 0 && mechanicalCorrectionsAfterRepairRemaining == 0) {
                emit("The one mechanical correction after semantic repair was not enough; preserving the last mechanically verified candidate instead of starting "
                        + "another open-ended repair cycle.");
                terminationReason = TerminationReason.POST_REPAIR_CORRECTION_EXHAUSTED;
                break;
            }
            if (repairScheduler.roundsStarted() > 0) {
                mechanicalCorrectionsAfterRepairRemaining--;
                emit("The semantic repair broke mechanical verification; allowing one narrow mechanical correction without reopening semantic scope.");
            }
            emit("Verification rejected the exercise; asking the agent to fix the issues and try again.");
            currentPrompt = mechanicalRejectionPrompt(attempt);
        }
        if (terminationReason == null) {
            // Reachable only when the derived attempt cap is non-positive, so the body never ran and no exit above was taken. Recorded rather than left absent — a termination
            // reason that can be missing is missing exactly when a campaign needs it — but logged, because a silent default here would hide a real regression in the exits above.
            log.warn("Exercise {} generation loop ended without recording a termination reason at any exit; attributing it to the attempt cap", exercise.getId());
            terminationReason = TerminationReason.ATTEMPT_CAP_REACHED;
        }
        return null;
    }

    /**
     * Runs one authoring attempt, either as the staged first attempt or as a single agent-loop call, and carries the conversation and turn count forward.
     * <p>
     * Staged authoring applies to the FIRST attempt only: a retry carries a targeted repair prompt (verification reasons, critic findings) plus the frozen spec contract, and
     * re-running the spec stage against such a prompt fails its gate by construction. Repairs therefore run the single loop, which suits surgical fixes on an existing workspace.
     */
    private void runAttempt(int attempt) {
        boolean stagedAttempt = useStagedGeneration && attempt == 1;
        if (stagedAttempt) {
            StagedGenerationRunner.StagedRunOutcome stagedOutcome = stagedGenerationRunner.run(exercise, baseTools, tools, currentPrompt, reviewBrief, testsSeedSnapshot, sandbox,
                    sessionId, cancelled, usageSink, progress, () -> structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise), specStageApplies, spec -> {
                        specSnapshot.set(spec);
                        jobService.recordSpecDocument(exercise.getId(), jobId, spec);
                    });
            loopResult = stagedOutcome.result();
            carriedConversation = stagedOutcome.conversation();
        }
        else {
            SemanticRepairBatch repairBatchForAttempt = pendingSemanticRepair;
            pendingSemanticRepair = null;
            if (repairBatchForAttempt != null) {
                lastSemanticRepair = repairBatchForAttempt;
                baseTools.enterRepairScope(repairBatchForAttempt.writableRoots());
            }
            try {
                AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, carriedConversation, currentPrompt, tools, maxTurns, cancelled, usageSink,
                        progress);
                loopResult = session.result();
                carriedConversation = session.conversation();
                transcriptWriter.write(exercise.getId(), "attempt-" + attempt + (attempt == 1 ? "-single-loop-" : "-repair-") + loopResult.status().name().toLowerCase(Locale.ROOT),
                        carriedConversation);
            }
            finally {
                baseTools.exitRepairScope();
            }
        }
        totalAgentTurns += loopResult.turns();
        if (stagedAttempt) {
            // Unstage the shared tools instance: a later repair attempt reuses it through the single-loop path, which must see no current stage rather than whichever stage the
            // staged run last entered, or that stage's check would dispatch from the repair loop's verify/submit.
            baseTools.exitStagedGeneration();
        }
        log.info("Exercise generation attempt {} took {} turn(s); {} turn(s) total so far", attempt, loopResult.turns(), totalAgentTurns);
    }

    /**
     * The outcome to return when the attempt just run ended the whole session, or {@code null} when the run continues. Cancellation is only polled between turns, so the final
     * check here honours a cancel that would otherwise cost minutes of verification build.
     */
    @Nullable
    private GenerationOutcome outcomeIfAgentStopped() {
        if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
            return cancelledOutcome(loopResult);
        }
        if (loopResult.status() == AgentLoopResult.Status.ERROR) {
            terminationReason = TerminationReason.AGENT_ERROR;
            if (lastMechanicallyVerifiedCandidate != null) {
                return service.preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
            }
            Map<RepositoryType, Map<String, String>> erroredFiles = GenerationOrchestrationService.changedCapturedRepositoryFiles(baselineRepositoryFiles,
                    service.captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
            String erroredStatement = workspace.extractProblemStatement(sandbox, sessionId).trim();
            boolean statementChanged = !Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), erroredStatement);
            if (statementChanged || !erroredFiles.isEmpty()) {
                return new GenerationOutcome(loopResult, null, sessionId, service, sandbox, erroredFiles, erroredStatement,
                        SpecFidelityReport.qualityReviewUnavailable("The agent stopped before verification; the partial candidate requires manual review."),
                        workspaceSeed.repositoryHeads(), GenerationOrchestrationService.readSpecDocument(sandbox, sessionId),
                        GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
            }
            service.destroyQuietly(sandbox, sessionId);
            return GenerationOutcome.error(loopResult);
        }
        if (cancelled.getAsBoolean()) {
            return cancelledOutcome(cancelledResult(loopResult));
        }
        return null;
    }

    /**
     * Reads the produced repositories and problem statement back for the sandbox-free integrity gates and derives this attempt's verification request. The extraction-failed
     * flag lets the verifier fail closed on a read-back error, which it could not otherwise tell apart from a genuinely empty repository.
     */
    private CandidateArtifacts captureArtifacts(Set<String> seededStructuralTestNames) {
        GenerationWorkspaceService.RepositoryExtraction producedTests = workspace.extractRepository(sandbox, sessionId, RepositoryType.TESTS,
                workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.TESTS, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
        GenerationWorkspaceService.RepositoryExtraction producedTemplate = workspace.extractRepository(sandbox, sessionId, RepositoryType.TEMPLATE,
                workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.TEMPLATE, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
        GenerationWorkspaceService.RepositoryExtraction producedSolution = workspace.extractRepository(sandbox, sessionId, RepositoryType.SOLUTION,
                workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.SOLUTION, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
        producedTests = GenerationOrchestrationService.replacePlaceholders(producedTests, placeholderReplacements);
        producedTemplate = GenerationOrchestrationService.replacePlaceholders(producedTemplate, placeholderReplacements);
        producedSolution = GenerationOrchestrationService.replacePlaceholders(producedSolution, placeholderReplacements);
        producedFilesByType.put(RepositoryType.TESTS, producedTests.files());
        producedFilesByType.put(RepositoryType.TEMPLATE, producedTemplate.files());
        producedFilesByType.put(RepositoryType.SOLUTION, producedSolution.files());
        // Persistence trims the statement before saving. Canonicalize it before verification so both stages consume the same value.
        producedProblemStatement = workspace.extractProblemStatement(sandbox, sessionId).trim();
        Set<String> extractionFailed = new LinkedHashSet<>();
        addIfExtractionFailed(extractionFailed, producedTests, RepositoryType.TESTS);
        addIfExtractionFailed(extractionFailed, producedTemplate, RepositoryType.TEMPLATE);
        addIfExtractionFailed(extractionFailed, producedSolution, RepositoryType.SOLUTION);
        Map<RepositoryType, Map<String, String>> candidateFiles = copyProducedFiles(producedFilesByType);
        if (extractionFailed.isEmpty()) {
            if (hasProducedChanges(baselineRepositoryFiles, producedFilesByType, baselineProblemStatement, producedProblemStatement)) {
                lastExtractedCandidate = new ExtractedCandidate(loopResult, candidateFiles, producedProblemStatement);
            }
        }
        // Capture the grading plan with the repositories and statement. Final verification and persistence must decide on the same plan, not independently re-read a mutable
        // workspace after the build.
        String testPlanSnapshot = GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json");
        VerificationRequest verificationRequest = new VerificationRequest(testsSeedSnapshot, baselineRepositoryFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()),
                baselineRepositoryFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), candidateFiles.getOrDefault(RepositoryType.TESTS, Map.of()),
                candidateFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()), candidateFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), Set.copyOf(extractionFailed),
                Set.copyOf(seededStructuralTestNames), baselineGradedTestNames, producedProblemStatement, testPlanSnapshot, mode == GenerationMode.ADAPT);
        return new CandidateArtifacts(candidateFiles, Set.copyOf(extractionFailed), verificationRequest, testPlanSnapshot);
    }

    /**
     * The authoritative differential pass: it re-seeds its pristine script, discards old reports, and builds from fresh temporary directories before parsing the result
     * independently. Each of its builds resets the tmpfs workspace, so the restore hook below re-materializes the candidate — including the problem statement, the frozen spec
     * and the grading plan, all of which the reset wipes.
     */
    private VerificationResult verifyCandidate(CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        Map<RepositoryType, Map<String, String>> candidateFiles = artifacts.candidateFiles();
        String candidateProblemStatement = producedProblemStatement;
        String testPlanSnapshot = artifacts.testPlanJson();
        Runnable restoreCandidate = () -> {
            sandbox.resetSession(sessionId);
            workspace.materializeRepositoryFiles(sandbox, sessionId, exercise, mode, candidateFiles, workspaceSeed.repositoryMetadata(), workspaceSeed.repositoryBinaryFiles(),
                    candidateProblemStatement, specDocumentSnapshot, testPlanSnapshot);
        };
        return verifyWithInfrastructureRetry(artifacts.verificationRequest(), restoreCandidate);
    }

    private VerificationResult verifyWithInfrastructureRetry(VerificationRequest request, Runnable restoreCandidate) {
        try {
            return verifier.verify(sandbox, sessionId, exercise, request, restoreCandidate);
        }
        catch (DifferentialVerificationService.VerificationInfrastructureException exception) {
            if (cancelled.getAsBoolean() || !exception.isRetryableInSameSession()) {
                throw exception;
            }
            log.warn("Exercise verification infrastructure failed once for exercise {} ({}); retrying the same candidate without another provider call", exercise.getId(),
                    exception.getClass().getSimpleName());
            emit("The verification infrastructure failed; retrying the same exercise without asking the AI to regenerate it.");
            return verifier.verify(sandbox, sessionId, exercise, request, restoreCandidate);
        }
    }

    /**
     * The first verified candidate is useful even if review is interrupted. A semantic repair is not promoted until its review completes; cancellation or an exception at that
     * boundary must preserve the last mechanically verified AND reviewed checkpoint.
     */
    private void checkpointMechanicallyVerifiedCandidate(CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        CandidateSnapshot mechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the mechanically verified candidate received its full-artifact review."),
                specDocumentSnapshot, artifacts.testPlanJson());
        if (candidateBeforeCurrentRepair == null) {
            lastMechanicallyVerifiedCandidate = mechanicallyVerifiedCandidate;
        }
    }

    /** Returns an outcome only when a cancellation arrived during the review; {@code null} means the run continues. */
    @Nullable
    private GenerationOutcome reviewCandidate(int attempt, CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        @Nullable
        String adaptationChanges = mode == GenerationMode.ADAPT
                ? GenerationOrchestrationService.renderAdaptationChanges(baselineProblemStatement, producedProblemStatement, baselineRepositoryFiles, producedFilesByType)
                : null;
        String repairDelta = mode == GenerationMode.GENERATE && candidateBeforeCurrentRepair != null
                ? GenerationOrchestrationService.renderGenerationRepairChanges(candidateBeforeCurrentRepair.problemStatement(), producedProblemStatement,
                        candidateBeforeCurrentRepair.producedFiles(), producedFilesByType, candidateBeforeCurrentRepair.testPlanJson(), artifacts.testPlanJson())
                : null;
        // The checkpointed candidate, not the mutable field, is the review authority once a repair is in flight: a mechanical rejection clears the field so the narrow
        // mechanical-correction prompt cannot reopen semantic scope, and the previous verdict would be lost with it.
        SpecFidelityReport previousReview = candidateBeforeCurrentRepair == null ? specFidelityReport : candidateBeforeCurrentRepair.reviewReport();
        specFidelityReport = runSpecFidelityCritic(producedProblemStatement, exercise.getProgrammingLanguage(), adaptationChanges, repairDelta, previousReview,
                effectiveSpecReviewContext(specSnapshot.get(), specDocumentSnapshot), artifacts.testPlanJson());
        // Skipped while anything still blocks: a repair round is coming that will rewrite the very artifacts a witness is derived from and validated against, so one authored
        // now could stop passing before it is ever offered.
        if (!specFidelityReport.hasBlockingFindings()) {
            specFidelityReport = adoptContractWitnesses(specFidelityReport, specDocumentSnapshot);
        }
        lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement, specFidelityReport,
                specDocumentSnapshot, artifacts.testPlanJson());
        recordReviewRound(attempt);
        if (cancelled.getAsBoolean()) {
            CandidateSnapshot safeCheckpoint = candidateBeforeCurrentRepair == null ? lastMechanicallyVerifiedCandidate : candidateBeforeCurrentRepair;
            terminationReason = TerminationReason.CANCELLED;
            return service.preserveCandidate(safeCheckpoint, sandbox, sessionId, workspaceSeed);
        }
        return null;
    }

    /**
     * Hands this review to the round accounting and puts the resulting counts on the round's progress event.
     *
     * @param attempt the authoring attempt whose candidate was just reviewed
     */
    private void recordReviewRound(int attempt) {
        ExerciseGenerationRepairRoundDTO round = repairScheduler.recordReviewRound(specFidelityReport, attempt);
        log.info("Exercise {} quality review round {} (attempt {}): {} blocking, {} advisory; {} carried over, {} drained, {} fresh", exercise.getId(), round.round(), attempt,
                round.blocking(), round.advisory(), round.carriedOver(), round.drained(), round.fresh());
        emitRound(RepairRoundScheduler.roundMessage(round), round);
    }

    /**
     * Schedules the next scoped semantic repair on a mechanically verified candidate.
     *
     * @return {@link LoopStep#NEXT_ATTEMPT} once a repair prompt is prepared, or {@link LoopStep#STOP} to keep the current candidate — because the budget is spent, the reviewer
     *         never returned a verdict, or no blocking finding maps to a repairable surface
     */
    private LoopStep applySemanticRepair(int attempt, boolean adoptWitnesses, CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        if (repairScheduler.budgetExhausted()) {
            emit("The bounded semantic repair phase is exhausted; keeping the latest mechanically verified candidate and its current review findings.");
            terminationReason = TerminationReason.REPAIR_BUDGET_EXHAUSTED;
            return LoopStep.STOP;
        }
        boolean reviewUnavailable = RepairRoundScheduler.hasReviewUnavailableFinding(specFidelityReport);
        Optional<SemanticRepairBatch> repairBatch = adoptWitnesses ? repairScheduler.witnessAdoption(specFidelityReport) : repairScheduler.nextRepairBatch(specFidelityReport);
        if (adoptWitnesses) {
            repairScheduler.markWitnessAdoptionAttempted();
        }
        if (reviewUnavailable && repairBatch.isEmpty()) {
            // One re-review is attempted before giving up, at most once per run; see RepairRoundScheduler#claimReviewRetry for why the work is not allowed to fail open here.
            if (repairScheduler.claimReviewRetry()) {
                log.info("Exercise {}: the quality review did not complete; re-reviewing once before ending the repair phase", exercise.getId());
                emit("The quality review did not complete; reviewing the exercise once more.");
                String retryAdaptationChanges = mode == GenerationMode.ADAPT
                        ? GenerationOrchestrationService.renderAdaptationChanges(baselineProblemStatement, producedProblemStatement, baselineRepositoryFiles, producedFilesByType)
                        : null;
                // No previous report is carried in: the point of the retry is a clean verdict on this candidate, not a continuation of the failed one.
                specFidelityReport = runSpecFidelityCritic(producedProblemStatement, exercise.getProgrammingLanguage(), retryAdaptationChanges, null, null,
                        effectiveSpecReviewContext(specSnapshot.get(), specDocumentSnapshot), artifacts.testPlanJson());
                lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                        specFidelityReport, specDocumentSnapshot, artifacts.testPlanJson());
                recordReviewRound(attempt);
                repairBatch = repairScheduler.nextRepairBatch(specFidelityReport);
            }
            if (repairBatch.isEmpty()) {
                terminationReason = RepairRoundScheduler.reasonForUnschedulableReport(specFidelityReport);
                return LoopStep.STOP;
            }
        }
        if (repairBatch.isEmpty()) {
            // A blocking finding that maps to no repair surface ends the loop with budget still unspent; without this record, that is indistinguishable from an exhausted budget.
            log.info("Exercise {} stopped repairing after {}/{} rounds with no schedulable surface; unrepaired findings {}", exercise.getId(), repairScheduler.roundsStarted(),
                    repairScheduler.roundLimit(), specFidelityReport.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                            .collect(Collectors.groupingBy(SpecFidelityReport.Finding::kind, Collectors.counting())));
            terminationReason = RepairRoundScheduler.reasonForUnschedulableReport(specFidelityReport);
            return LoopStep.STOP;
        }
        candidateBeforeCurrentRepair = lastMechanicallyVerifiedCandidate;
        pendingSemanticRepair = repairBatch.get();
        if (adoptWitnesses) {
            repairScheduler.recordAdoptionRound();
        }
        else {
            repairScheduler.recordRepairRound(pendingSemanticRepair.surface());
        }
        // Both what the critic raised and what this attempt was given to repair: without the pair, a weakness that was found but never scheduled cannot be told apart from one
        // that was never found.
        log.info("Exercise {} semantic repair {}/{} on surface {}: critic findings {}; repairing {}", exercise.getId(), repairScheduler.roundsStarted(),
                repairScheduler.roundLimit(), pendingSemanticRepair.surface(),
                specFidelityReport.findings().stream().collect(Collectors.groupingBy(SpecFidelityReport.Finding::kind, Collectors.counting())),
                pendingSemanticRepair.report().findings().stream().map(SpecFidelityReport.Finding::kind).distinct().toList());
        emit(adoptWitnesses ? "The exercise is verified; offering the AI the contract tests an independent reviewer prepared for it."
                : "Mechanical verification passed, but the exercise review found requirements or quality issues; asking the AI to correct them.");
        currentPrompt = adoptWitnesses ? witnessAdoptionPrompt(attempt, specSnapshot.get(), repairBatch.get()) : semanticRepairPrompt(attempt, repairBatch.get());
        return LoopStep.NEXT_ATTEMPT;
    }

    /**
     * Adds any contract witness the reference solution actually satisfied to the report, as advisory findings the agent can adopt. The executable counterpart to the oracle
     * review, which reasons about wrong implementations but never runs one, so its {@code killed} flag is only the reviewing model's own claim. A witness is offered only once
     * it has passed against the reference solution, so a mistaken one never reaches the agent, and any failure along the way leaves the report exactly as it was.
     */
    private SpecFidelityReport adoptContractWitnesses(SpecFidelityReport report, @Nullable String specDocumentSnapshot) {
        Map<String, String> testsFiles = producedFilesByType.getOrDefault(RepositoryType.TESTS, Map.of());
        if (specDocumentSnapshot == null || specDocumentSnapshot.isBlank() || testsFiles.isEmpty() || cancelled.getAsBoolean()) {
            return report;
        }
        try {
            List<ContractWitness> candidates = specFidelityCritic.authorContractWitnesses(specDocumentSnapshot, renderArtifactSources(testsFiles),
                    renderArtifactSources(producedFilesByType.getOrDefault(RepositoryType.SOLUTION, Map.of())), usageSink, cancelled);
            List<ContractWitness> validated = verifier.validateContractWitnesses(sandbox, sessionId, exercise, testsFiles, candidates);
            if (validated.isEmpty()) {
                return report;
            }
            List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
            validated.forEach(witness -> combined.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE,
                    "Rule " + witness.ruleId() + " has an executable witness the reference solution already passes",
                    "Add this test to the graded suite, or state why it is redundant with an existing assertion. It was authored from rule " + witness.ruleId()
                            + " of the approved specification by a reviewer independent of the authoring loop, and it has been run against the reference solution, which passes it:\n"
                            + witness.code())));
            emit("Adding " + validated.size() + (validated.size() == 1 ? " contract witness" : " contract witnesses") + " the reference solution already passes.");
            return new SpecFidelityReport(List.copyOf(combined));
        }
        catch (RuntimeException e) {
            log.warn("Contract witnesses could not be produced for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
            return report;
        }
    }

    private static String renderArtifactSources(Map<String, String> files) {
        return files.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> "// " + entry.getKey() + "\n" + entry.getValue()).collect(Collectors.joining("\n\n"));
    }

    private SpecFidelityReport runSpecFidelityCritic(String problemStatement, @Nullable ProgrammingLanguage language, @Nullable String adaptationChanges,
            @Nullable String repairDelta, @Nullable SpecFidelityReport previousReport, @Nullable String specSnapshotForReview, @Nullable String testPlanSnapshot) {
        try {
            List<String> testNames = GenerationOrchestrationService.extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = adaptationChanges == null
                    ? specFidelityCritic.critique(reviewBrief, problemStatement, testNames, producedFilesByType, usageSink, cancelled, previousReport, specSnapshotForReview,
                            repairDelta, testPlanSnapshot)
                    : specFidelityCritic.critiqueAdaptation(reviewBrief, problemStatement, testNames, adaptationChanges, producedFilesByType, usageSink, cancelled, previousReport);
            if (adaptationChanges != null && adaptationChanges.contains(GenerationOrchestrationService.CHANGE_SUMMARY_TRUNCATED)) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(SpecFidelityReport.adaptationScopeUnavailable("The bounded change summary was truncated, so not every changed line could be reviewed.").findings());
                report = new SpecFidelityReport(List.copyOf(combined));
            }
            // Messageless assertions remain advisory and share the same retry/review channel.
            List<SpecFidelityReport.Finding> messageless = specFidelityCritic.detectMessagelessAssertions(language,
                    producedFilesByType.getOrDefault(RepositoryType.TESTS, Map.of()));
            if (!messageless.isEmpty()) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(messageless);
                report = new SpecFidelityReport(combined);
            }
            report = reclassifyUngradeableTechniqueFindings(report, specSnapshotForReview);
            // Same channel, same advisory weight: a technique the exercise requires but cannot grade is something the instructor must know before releasing it.
            List<SpecFidelityReport.Finding> techniqueRules = specFidelityCritic.detectUnenforceableTechniqueRules(specSnapshotForReview);
            if (!techniqueRules.isEmpty()) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(techniqueRules);
                report = new SpecFidelityReport(combined);
            }
            if (report.hasFindings()) {
                long blockingCount = report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).count();
                long advisoryCount = report.findings().size() - blockingCount;
                String counts = blockingCount > 0 && advisoryCount > 0 ? blockingCount + " blocking and " + advisoryCount + " advisory"
                        : blockingCount > 0 ? blockingCount + " blocking" : advisoryCount + " advisory";
                String gaps = report.findings().size() == 1 ? " exercise-quality gap" : " exercise-quality gaps";
                emit("The review found " + counts + gaps + (blockingCount > 0 ? " that require instructor attention." : "."));
            }
            return report;
        }
        catch (RuntimeException e) {
            log.warn("Spec-fidelity critic could not run for exercise ({})", e.getClass().getSimpleName());
            return adaptationChanges == null ? SpecFidelityReport.qualityReviewUnavailable("The full-artifact reviewer failed before it could assess the candidate.")
                    : SpecFidelityReport.adaptationScopeUnavailable("The adaptation-scope reviewer failed before it could assess every changed line.");
        }
    }

    /**
     * Downgrades a repairable finding that in fact demands an ungradeable implementation technique.
     * <p>
     * {@code WEAK_TEST_ORACLE} and {@code UNCOVERED_REQUIREMENT} map to the oracle repair surface, so the loop schedules them and asks the agent to write a discriminating test.
     * When the "requirement" is that the implementation be recursive or use a stream pipeline, no such test exists, and the only way to appear to write one is to assert on the
     * student's source text. The finding is real and worth telling the instructor — it just cannot be repaired, so it must not hold a repair round.
     */
    private static SpecFidelityReport reclassifyUngradeableTechniqueFindings(SpecFidelityReport report, @Nullable String specSnapshot) {
        // Provenance first: unless the frozen contract actually mandates a technique, no finding is downgraded. This is what keeps the reclassification honest — it can only
        // fire on exercises that carry the defect, so a misread finding on any other exercise costs nothing.
        if (ExerciseIntegrityGate.techniqueMandatesInRules(specSnapshot).isEmpty()) {
            return report;
        }
        if (report.findings().stream().noneMatch(GenerationAttemptLoop::demandsUngradeableTechnique)) {
            return report;
        }
        List<SpecFidelityReport.Finding> reclassified = report.findings().stream()
                .map(finding -> demandsUngradeableTechnique(finding)
                        ? new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, finding.requirement(),
                                "No assertion through the public API can observe this, so it cannot be repaired by strengthening the tests: " + finding.detail())
                        : finding)
                .toList();
        return new SpecFidelityReport(reclassified);
    }

    /**
     * Whether a finding asks the tests to grade how the code is written. Read the finding's own prose, not a specification rule: for a weak-oracle finding the requirement is
     * the surviving mutant's description ("an iterative implementation using an explicit stack"), which is written in the critic's voice and does not contain "must".
     */
    private static boolean demandsUngradeableTechnique(SpecFidelityReport.Finding finding) {
        return (finding.kind() == SpecFidelityReport.Kind.WEAK_TEST_ORACLE || finding.kind() == SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT)
                && ExerciseIntegrityGate.describesTechniqueRatherThanBehaviour(finding.requirement() + " " + finding.detail());
    }

    /** Frames the prompt built after {@code completedAttempt}, which drives the attempt after it. */
    private String attemptFraming(int completedAttempt) {
        int upcomingAttempt = completedAttempt + 1;
        boolean finalAttempt = upcomingAttempt >= maxGenerationAttempts;
        return "Repair attempt " + upcomingAttempt + " of " + maxGenerationAttempts
                + (finalAttempt ? " — this is the FINAL attempt; prioritise the blocking findings (especially any repeated from earlier reviews) over cosmetic ones. " : ". ");
    }

    /**
     * The prompt for the one witness-adoption round. Framed as an offer rather than a defect list, because the candidate already passed every gate. Declining is explicitly
     * allowed so the agent is not pushed into restating a case its suite already makes: a redundant test is a cost, not a win.
     */
    private String witnessAdoptionPrompt(int completedAttempt, @Nullable String specSnapshotForPrompt, SemanticRepairBatch batch) {
        return attemptFraming(completedAttempt)
                + "Your previous attempt is fully verified and accepted; nothing is broken. An independent reviewer derived the tests below from the "
                + "approved specification and the server has already run each one against your reference solution, which passes them. Add each test to the graded suite unless an "
                + "existing assertion already distinguishes exactly the same wrong implementation, in which case leave the suite as it is and say which test covers it. Change "
                + "nothing else: the solution, template, statement and every existing test stay as they are. Then call the structured `verify` tool, and call submit when it "
                + "reports MECHANICAL PRECHECK: PASS.\n\nThe instructor source requirements are:\n" + authoringBrief + specContractSection(specSnapshotForPrompt)
                + specFidelityCritic.renderForRetryPrompt(batch.report());
    }

    private String semanticRepairPrompt(int completedAttempt, SemanticRepairBatch batch) {
        String scopeGuidance = mode == GenerationMode.ADAPT ? " Preserve all content outside the requested adaptation." : "";
        return attemptFraming(completedAttempt) + "Your previous attempt passed mechanical verification, but the automated full-artifact review found review blockers."
                + scopeGuidance
                + " Preserve the mechanically correct work: do not restart or rewrite unrelated files. Begin with only the artifact(s) explicitly implicated by each finding's evidence. "
                + batch.guidance()
                + "After that smallest edit, call the structured `verify` tool; expand the repair surface only if its report identifies a concrete cross-artifact inconsistency caused by the edit. "
                + "Keep every unaffected requirement, API, test, and example. The template is expected to fail behavioural and structural tests at approved TODOs and absent "
                + "student-creates types—never make those tests pass merely because a raw template build exits non-zero. `verify`, not a raw build exit code, is the acceptance verdict. "
                + "Call submit when it reports MECHANICAL PRECHECK: PASS.\n\nThe instructor " + "source requirements are:\n" + authoringBrief
                + specContractSection(specSnapshot.get()) + specFidelityCritic.renderForRetryPrompt(batch.report());
    }

    private String mechanicalRejectionPrompt(int completedAttempt) {
        String semanticCorrectionGuidance = repairScheduler.roundsStarted() > 0 && lastSemanticRepair != null ? "\n\nThis rejection followed a "
                + lastSemanticRepair.surface().name().toLowerCase(Locale.ROOT)
                + " quality repair. Before changing production code, audit the new assertion against the frozen contract. If the assertion invents behavior the contract does not "
                + "require, fix or remove the unsupported assertion first. " + lastSemanticRepair.guidance() : "";
        return attemptFraming(completedAttempt) + "Your previous attempt was rejected by the differential verifier:\n" + verification.report()
                + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, call the structured `verify` tool, then submit when it reports "
                + "MECHANICAL PRECHECK: PASS. If a reason names a forbidden, duplicate, or abandoned path, delete it; replacing it with a "
                + "placeholder does not remove the violation. Make the smallest coherent repair, leave unrelated files unchanged, and preserve the source requirements below.\n\n"
                + authoringBrief + specContractSection(specSnapshot.get()) + semanticCorrectionGuidance + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
    }

    /**
     * The frozen, gate-approved specification appended to every repair prompt, so a repair under verification pressure faces the behavioural contract it might otherwise
     * silently cut. Empty when no spec was captured, either because the stage was skipped or because the run was not staged.
     */
    private static String specContractSection(@Nullable String specSnapshot) {
        if (specSnapshot == null || specSnapshot.isBlank()) {
            return "";
        }
        return "\n\nTHE SPECIFICATION (frozen at the spec gate — the read-only behavioural contract; repair downstream artifacts against it):\n" + specSnapshot.strip();
    }

    /** The frozen contract is what the semantic critic reviews against; the live workspace copy is a fallback only when no specification gate ran. */
    private static String effectiveSpecReviewContext(@Nullable String approvedSpec, @Nullable String liveSpec) {
        String approved = approvedSpec == null ? "" : approvedSpec.strip();
        String live = liveSpec == null ? "" : liveSpec.strip();
        if (approved.isEmpty()) {
            return live;
        }
        return approved;
    }

    private GenerationOutcome cancelledOutcome(AgentLoopResult cancelledResult) {
        // Every caller of this method is a cooperative stop, so the reason belongs here rather than repeated at each of them.
        terminationReason = TerminationReason.CANCELLED;
        if (lastMechanicallyVerifiedCandidate != null) {
            return service.preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
        }
        return service.stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement, cancelledResult);
    }

    private static AgentLoopResult cancelledResult(AgentLoopResult lastResult) {
        return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, lastResult.turns(), lastResult.finalMessage());
    }

    private static Map<RepositoryType, Map<String, String>> copyProducedFiles(Map<RepositoryType, Map<String, String>> producedFiles) {
        Map<RepositoryType, Map<String, String>> copy = new EnumMap<>(RepositoryType.class);
        producedFiles.forEach((type, files) -> copy.put(type, Map.copyOf(files)));
        return Map.copyOf(copy);
    }

    private static boolean hasProducedChanges(Map<RepositoryType, Map<String, String>> baselineFiles, Map<RepositoryType, Map<String, String>> producedFiles,
            @Nullable String baselineProblemStatement, String producedProblemStatement) {
        if (!Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), producedProblemStatement)) {
            return true;
        }
        return List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS).stream()
                .anyMatch(type -> !baselineFiles.getOrDefault(type, Map.of()).equals(producedFiles.getOrDefault(type, Map.of())));
    }

    /** Records the repository's directory as extraction-failed so the verifier can fail CLOSED on a read-back error (distinct from a genuinely empty repo). */
    private static void addIfExtractionFailed(Set<String> extractionFailed, GenerationWorkspaceService.RepositoryExtraction extraction, RepositoryType type) {
        if (extraction.extractionFailed()) {
            extractionFailed.add(GenerationWorkspaceService.directoryFor(type));
        }
    }

    private void emit(String message) {
        GenerationOrchestrationService.emit(progress, message);
    }

    /** The same progress line every other stage emits, with this round's counts attached so the persisted transcript is machine-readable without parsing the prose. */
    private void emitRound(String message, ExerciseGenerationRepairRoundDTO round) {
        if (progress != null) {
            progress.progress(message, round);
        }
    }

    @Nullable
    AgentLoopResult loopResult() {
        return loopResult;
    }

    @Nullable
    VerificationResult verification() {
        return verification;
    }

    int totalAgentTurns() {
        return totalAgentTurns;
    }

    Map<RepositoryType, Map<String, String>> producedFilesByType() {
        return producedFilesByType;
    }

    String producedProblemStatement() {
        return producedProblemStatement;
    }

    SpecFidelityReport specFidelityReport() {
        return specFidelityReport;
    }

    @Nullable
    CandidateSnapshot lastMechanicallyVerifiedCandidate() {
        return lastMechanicallyVerifiedCandidate;
    }

    @Nullable
    ExtractedCandidate lastExtractedCandidate() {
        return lastExtractedCandidate;
    }

    /**
     * @return why this loop stopped; {@code null} only while it is still running, or when it was abandoned by an exception it never caught
     */
    @Nullable
    TerminationReason terminationReason() {
        return terminationReason;
    }
}
