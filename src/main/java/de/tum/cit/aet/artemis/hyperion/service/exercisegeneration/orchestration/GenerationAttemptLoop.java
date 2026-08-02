package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAttemptSupport.addIfExtractionFailed;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAttemptSupport.cancelledResult;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAttemptSupport.copyProducedFiles;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAttemptSupport.hasProducedChanges;

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
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationRequest;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Per-run state machine that authors, verifies, reviews, and schedules one scoped repair at a time. The orchestration service owns bean and sandbox lifecycle.
 */
class GenerationAttemptLoop {

    private static final Logger log = LoggerFactory.getLogger(GenerationAttemptLoop.class);

    /** Initial candidate plus at most three mechanical repairs. */
    static final int MAX_MECHANICAL_ATTEMPTS = 4;

    private static final int MAX_TRACKED_SEMANTIC_MUTANTS = 6;

    record Dependencies(GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner, DifferentialVerificationService verifier,
            StructuralOracleSeedingService structuralOracleSeeder, SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService,
            StagedGenerationRunner stagedGenerationRunner, AgentTranscriptWriter transcriptWriter, boolean stagedGenerationEnabled, int maxTurns, int maxGenerationAttempts,
            int maxSemanticRepairs) {

        /** Returns dependencies pinned to the run's model, context, and turn settings. */
        Dependencies forSettings(@Nullable HyperionGenerationSettings settings) {
            if (settings == null) {
                return this;
            }
            AgentLoopRunner profileRunner = agentLoopRunner.forSettings(settings);
            SpecFidelityCriticService profileCritic = specFidelityCritic.forSettings(settings);
            StagedGenerationRunner profileStagedRunner = stagedGenerationRunner.forSettings(settings, profileRunner, profileCritic);
            return new Dependencies(workspace, profileRunner, verifier, structuralOracleSeeder, profileCritic, jobService, profileStagedRunner, transcriptWriter,
                    settings.stagedGeneration(), settings.maxTurns(), maxGenerationAttempts, maxSemanticRepairs);
        }
    }

    record RunContext(ProgrammingExercise exercise, GenerationMode mode, String jobId, InteractiveSandbox sandbox, String sessionId,
            GenerationWorkspaceService.WorkspaceSeed workspaceSeed, Map<String, String> testsSeedSnapshot, Map<String, String> placeholderReplacements,
            Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement, Set<String> baselineGradedTestNames, String sourceBrief,
            boolean specStageApplies, boolean conceptSelectionApplies, String systemPrompt, String firstPrompt, SandboxAgentTools baseTools, Object tools,
            BooleanSupplier cancelled, @Nullable GenerationProgressSink progress, Consumer<ChatResponse> usageSink) {
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

    // GENERATE always compiles a reviewed internal contract. An authoritative statement fixes that contract's content but does not make its operational seams implicit.
    private final boolean specStageApplies;

    // Concept invention is distinct from contract compilation: an authoritative statement already fixes the concept, while a brief-only run still needs selection.
    private final boolean conceptSelectionApplies;

    // The gate-approved SPEC.md snapshot, frozen by the runner's spec gate: instructor-visible immediately, fed to the critic's grounding, and appended to every repair prompt so
    // scope-cutting under repair pressure faces the contract it is cutting.
    private final AtomicReference<String> specSnapshot = new AtomicReference<>();

    private String currentPrompt;

    @Nullable
    private AgentLoopResult loopResult;

    @Nullable
    private TerminationReason stagedTerminationReason;

    @Nullable
    private List<Message> carriedConversation;

    private int totalAgentTurns;

    @Nullable
    private VerificationResult verification;

    private final Map<RepositoryType, Map<String, String>> producedFilesByType = new EnumMap<>(RepositoryType.class);

    private String producedProblemStatement = "";

    private SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();

    private List<String> unresolvedSpecificationFindings = List.of();

    private List<SemanticMutant> semanticMutantsAwaitingKill = List.of();

    private List<SemanticMutant> semanticMutantsPendingRepair = List.of();

    private List<SemanticMutant> semanticMutantsAwaitingRecheck = List.of();

    private List<SemanticMutant> semanticMutantsPendingSpecApproval = List.of();

    private List<SemanticMutant.Exclusion> semanticMutantHistory = List.of();

    private SeededStructuralTests seededStructuralTests = SeededStructuralTests.EMPTY;

    private List<ContractWitness> referenceWitnessesAwaitingPass = List.of();

    private List<ContractWitness> referenceWitnessesAwaitingAdjudication = List.of();

    private List<ContractWitness> positiveWitnessesAwaitingAdjudication = List.of();

    // Independently approved reference-pass/starter-fail witnesses remain available across unrelated repair surfaces. A full-artifact re-review must not erase their evidence.
    private List<ContractWitness> contractWitnessesPendingAdoption = List.of();

    @Nullable
    private VerificationRequest lastRejectedVerificationRequest;

    private final RepairRoundScheduler repairScheduler;

    private int mechanicalCorrectionsAfterRepairRemaining = 1;

    private int mechanicalAttemptsBeforeAnyRepair;

    @Nullable
    private CandidateSnapshot candidateBeforeCurrentRepair;

    @Nullable
    private SemanticRepairBatch pendingSemanticRepair;

    @Nullable
    private SemanticRepairBatch lastSemanticRepair;

    @Nullable
    private CandidateSnapshot lastMechanicallyVerifiedCandidate;

    @Nullable
    private ExtractedCandidate lastExtractedCandidate;

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
        this.conceptSelectionApplies = context.conceptSelectionApplies();
        this.currentPrompt = context.firstPrompt();
        // An adaptation gets a single semantic round whatever the configured generation budget is: its scope is one requested change, not an open authoring task.
        this.repairScheduler = new RepairRoundScheduler(context.mode() == GenerationMode.GENERATE ? dependencies.maxSemanticRepairs() : 1);
    }

    /** Runs bounded authoring, verification, review, and repair attempts. */
    @Nullable
    GenerationOutcome run() {
        for (int attempt = 1; attempt <= maxGenerationAttempts; attempt++) {
            runAttempt(attempt);
            GenerationOutcome stoppedOutcome = outcomeIfAgentStopped();
            if (stoppedOutcome != null) {
                return stoppedOutcome;
            }

            // Seeded structural names remain authoritative task bindings throughout repair.
            seededStructuralTests = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);
            if (cancelled.getAsBoolean()) {
                return cancelledOutcome(cancelledResult(loopResult));
            }

            emit("Checking the exercise builds and grades (attempt " + attempt + " of " + maxGenerationAttempts + ")");
            workspace.cleanTransientBuildOutputs(sandbox, sessionId);
            CandidateArtifacts artifacts = captureArtifacts(seededStructuralTests);
            if (cancelled.getAsBoolean()) {
                return cancelledOutcome(cancelledResult(loopResult));
            }
            if (lastRejectedVerificationRequest != null && lastRejectedVerificationRequest.equals(artifacts.verificationRequest())) {
                emit("The agent resubmitted the unchanged rejected candidate; stopping without repeating the same verification.");
                terminationReason = TerminationReason.UNCHANGED_CANDIDATE_RESUBMITTED;
                break;
            }
            // Snapshot the approved SPEC.md because each verification restore resets and must re-seed the tmpfs workspace.
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
                if (terminationReason == TerminationReason.REPAIR_DID_NOT_IMPROVE) {
                    break;
                }
            }
            else {
                specFidelityReport = SpecFidelityReport.empty();
            }

            // A GENERATE run may spend one otherwise-idle round offering advisory witnesses. An instructor-only blocker must not discard unrelated environment-proven coverage,
            // but a failed or unapproved specification cannot authorize new grading.
            boolean hasSchedulableRepair = repairScheduler.nextRepairBatch(specFidelityReport).isPresent();
            boolean adoptWitnesses = verification.mechanicallyVerified() && !hasSchedulableRepair && !RepairRoundScheduler.hasInstrumentUnavailableFinding(specFidelityReport)
                    && unresolvedSpecificationFindings.isEmpty() && attempt < maxGenerationAttempts && mode == GenerationMode.GENERATE
                    && repairScheduler.witnessAdoption(specFidelityReport).isPresent();
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
            // Reachable only when the derived attempt cap is non-positive; record and log it rather than leaving a campaign's termination reason absent.
            log.warn("Exercise {} generation loop ended without recording a termination reason at any exit; attributing it to the attempt cap", exercise.getId());
            terminationReason = TerminationReason.ATTEMPT_CAP_REACHED;
        }
        return null;
    }

    /** Runs staged authoring for the first attempt and a scoped agent loop for later repairs. */
    private void runAttempt(int attempt) {
        // Pushed before any work, for the same reason the agent loop pushes turns: an attempt abandoned at a gate must still be counted, and a count derived from the outcome the
        // caller returns is missing exactly for the runs an administrator wants to see.
        recordAttempt();
        boolean stagedAttempt = useStagedGeneration && attempt == 1;
        if (stagedAttempt) {
            StagedGenerationRunner.StagedRunOutcome stagedOutcome = stagedGenerationRunner.run(exercise, baseTools, tools, currentPrompt, reviewBrief, testsSeedSnapshot, sandbox,
                    sessionId, cancelled, usageSink, progress, () -> structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise), specStageApplies,
                    conceptSelectionApplies, spec -> {
                        specSnapshot.set(spec);
                        jobService.recordSpecDocument(exercise.getId(), jobId, spec);
                    });
            loopResult = stagedOutcome.result();
            stagedTerminationReason = stagedOutcome.terminationReason();
            carriedConversation = stagedOutcome.conversation();
            unresolvedSpecificationFindings = stagedOutcome.unresolvedSpecificationFindings();
        }
        else {
            SemanticRepairBatch repairBatchForAttempt = pendingSemanticRepair;
            pendingSemanticRepair = null;
            if (repairBatchForAttempt != null) {
                lastSemanticRepair = repairBatchForAttempt;
                baseTools.enterRepairScope(repairBatchForAttempt.writableRoots());
            }
            try {
                // Repairs start from durable workspace/server state; replaying a failed trajectory buries current evidence and grows quadratically. Checkpoints keep the audit.
                AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, null, currentPrompt, tools, maxTurns, cancelled, usageSink, progress);
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
            // A later single-loop repair must not inherit the staged run's final dispatch scope.
            baseTools.exitStagedGeneration();
        }
        log.info("Exercise generation attempt {} took {} turn(s); {} turn(s) total so far", attempt, loopResult.turns(), totalAgentTurns);
    }

    private void recordAttempt() {
        if (usageSink instanceof ProviderUsageSink providerUsageSink) {
            providerUsageSink.recordAttempt();
        }
    }

    /** Returns the terminal attempt outcome, polling cancellation before the expensive verification build; {@code null} means continue. */
    @Nullable
    private GenerationOutcome outcomeIfAgentStopped() {
        if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
            return cancelledOutcome(loopResult);
        }
        if (loopResult.status() == AgentLoopResult.Status.ERROR) {
            terminationReason = stagedTerminationReason == null ? TerminationReason.AGENT_ERROR : stagedTerminationReason;
            if (terminationReason == TerminationReason.NO_ADMISSIBLE_CONCEPT) {
                service.destroyQuietly(sandbox, sessionId);
                return GenerationOutcome.error(loopResult, "No generated exercise concept satisfied the instructor brief and learning-fit review.");
            }
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

    /** Reads all candidate artifacts back and preserves extraction failures so verification can distinguish them from empty repositories. */
    private CandidateArtifacts captureArtifacts(SeededStructuralTests seededStructuralTests) {
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
        // Capture the grading plan with the repositories and statement so verification and persistence decide on the same immutable candidate.
        String testPlanSnapshot = GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json");
        VerificationRequest verificationRequest = new VerificationRequest(testsSeedSnapshot, baselineRepositoryFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()),
                baselineRepositoryFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), candidateFiles.getOrDefault(RepositoryType.TESTS, Map.of()),
                candidateFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()), candidateFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), Set.copyOf(extractionFailed),
                seededStructuralTests, baselineGradedTestNames, producedProblemStatement, testPlanSnapshot, mode == GenerationMode.ADAPT);
        return new CandidateArtifacts(candidateFiles, Set.copyOf(extractionFailed), verificationRequest, testPlanSnapshot);
    }

    /** Runs the authoritative differential build from fresh directories, restoring the candidate after every sandbox reset. */
    private VerificationResult verifyCandidate(CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        Map<RepositoryType, Map<String, String>> candidateFiles = artifacts.candidateFiles();
        String candidateProblemStatement = producedProblemStatement;
        String testPlanSnapshot = artifacts.testPlanJson();
        Runnable restoreCandidate = () -> {
            sandbox.resetSession(sessionId);
            workspace.materializeRepositoryFiles(sandbox, sessionId, exercise, mode, candidateFiles, workspaceSeed.repositoryMetadata(), workspaceSeed.repositoryBinaryFiles(),
                    candidateProblemStatement, specDocumentSnapshot, testPlanSnapshot);
        };
        VerificationResult result = verifyWithInfrastructureRetry(artifacts.verificationRequest(), restoreCandidate);
        if (!result.mechanicallyVerified() || semanticMutantsAwaitingKill.isEmpty()) {
            return result;
        }
        Map<String, String> currentSolution = candidateFiles.getOrDefault(RepositoryType.SOLUTION, Map.of());
        List<SemanticMutant> applicableMutants = semanticMutantsAwaitingKill.stream()
                .filter(mutant -> mutant.originalSolutionSource().equals(currentSolution.get(mutant.solutionPath()))).toList();
        int staleMutants = semanticMutantsAwaitingKill.size() - applicableMutants.size();
        if (staleMutants > 0) {
            emit("Retired " + staleMutants
                    + " semantic mutant acceptance check(s) because a separate repair changed their reference source; the executable review will author fresh mutants against the current solution.");
        }
        if (applicableMutants.isEmpty()) {
            semanticMutantsAwaitingKill = List.of();
            return result;
        }
        List<SemanticMutantOutcome> outcomes = verifier.checkSemanticMutants(sandbox, sessionId, exercise, currentSolution, seededStructuralTests, applicableMutants,
                restoreCandidate);
        GenerationReviewSupport.SemanticMutantRecheck recheck = GenerationReviewSupport.semanticMutantRecheck(outcomes);
        semanticMutantsAwaitingKill = recheck.unresolvedMutants();
        if (semanticMutantsAwaitingKill.isEmpty()) {
            return result;
        }
        List<String> reasons = new ArrayList<>(result.reasons());
        reasons.addAll(recheck.failureReasons());
        return new VerificationResult(false, result.solutionPassed(), result.templateFailed(), result.testCount(), List.copyOf(reasons), result.templateFailureEvidence());
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

    /** Checkpoints the first verified candidate; later semantic repairs are promoted only after their review completes. */
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
        if (candidateBeforeCurrentRepair != null && candidateBeforeCurrentRepair.producedFiles().equals(producedFilesByType)
                && candidateBeforeCurrentRepair.problemStatement().equals(producedProblemStatement)
                && Objects.equals(candidateBeforeCurrentRepair.specDocument(), specDocumentSnapshot)
                && Objects.equals(candidateBeforeCurrentRepair.testPlanJson(), artifacts.testPlanJson())) {
            specFidelityReport = candidateBeforeCurrentRepair.reviewReport();
            lastMechanicallyVerifiedCandidate = candidateBeforeCurrentRepair;
            candidateBeforeCurrentRepair = null;
            emit("The semantic repair made no artifact changes; retaining the previous environment evidence and review instead of re-rolling a verdict on the same candidate.");
            return null;
        }
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
                GenerationReviewSupport.effectiveSpecReviewContext(specSnapshot.get(), specDocumentSnapshot), artifacts.testPlanJson());
        specFidelityReport = GenerationReviewSupport.preserveSpecificationReviewState(specFidelityReport, unresolvedSpecificationFindings);
        specFidelityReport = adoptExecutableCounterexamples(specFidelityReport, artifacts, specDocumentSnapshot);
        recordReviewRound(attempt);
        if (candidateBeforeCurrentRepair != null && !RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(specFidelityReport)
                && !RepairRoundScheduler.repairImproved(candidateBeforeCurrentRepair.reviewReport(), specFidelityReport)) {
            CandidateSnapshot retained = candidateBeforeCurrentRepair;
            candidateBeforeCurrentRepair = null;
            lastMechanicallyVerifiedCandidate = retained;
            terminationReason = TerminationReason.REPAIR_DID_NOT_IMPROVE;
            emit("The repair did not reduce the reviewed blockers without introducing another one; retaining the previous reviewed checkpoint.");
            return service.preserveCandidate(retained, sandbox, sessionId, workspaceSeed);
        }
        promoteReviewedCandidate(artifacts, specDocumentSnapshot);
        if (cancelled.getAsBoolean()) {
            terminationReason = TerminationReason.CANCELLED;
            return service.preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
        }
        return null;
    }

    /**
     * Promotes the candidate only after its review and executable probes have completed. Clearing the repair predecessor here is part of the invariant: a later deadline must
     * preserve the candidate that was just verified and reviewed, while {@link #applySemanticRepair} records it again if another repair actually starts.
     */
    private void promoteReviewedCandidate(CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        if (candidateBeforeCurrentRepair != null && RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(specFidelityReport)) {
            emit("The repaired candidate passed mechanical verification, but its primary quality review did not complete; retaining the previously reviewed checkpoint ("
                    + candidateBeforeCurrentRepair.verification().testCount() + " tests).");
            return;
        }
        lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement, specFidelityReport,
                specDocumentSnapshot, artifacts.testPlanJson());
        candidateBeforeCurrentRepair = null;
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
        boolean reviewUnavailable = RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(specFidelityReport);
        Optional<SemanticRepairBatch> repairBatch = adoptWitnesses ? repairScheduler.witnessAdoption(specFidelityReport) : repairScheduler.nextRepairBatch(specFidelityReport);
        if (adoptWitnesses) {
            repairScheduler.markWitnessAdoptionAttempted();
        }
        if (reviewUnavailable && repairBatch.isEmpty() && !semanticMutantsAwaitingRecheck.isEmpty()) {
            emit("Executable semantic evidence remains inconclusive; keeping the last fully reviewed candidate for instructor review.");
            terminationReason = TerminationReason.REVIEW_UNAVAILABLE;
            return LoopStep.STOP;
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
                        GenerationReviewSupport.effectiveSpecReviewContext(specSnapshot.get(), specDocumentSnapshot), artifacts.testPlanJson());
                specFidelityReport = preserveExecutableEvidenceState(GenerationReviewSupport.preserveSpecificationReviewState(specFidelityReport, unresolvedSpecificationFindings));
                promoteReviewedCandidate(artifacts, specDocumentSnapshot);
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
        boolean repairsExecutableOracle = pendingSemanticRepair.report().findings().stream()
                .anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        if (repairsExecutableOracle) {
            semanticMutantsAwaitingKill = semanticMutantsPendingRepair;
            semanticMutantsPendingRepair = List.of();
        }
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

    /** Converts model-proposed mutants and witnesses into evidence only after environment execution; infrastructure failures infer nothing. */
    private SpecFidelityReport adoptExecutableCounterexamples(SpecFidelityReport report, CandidateArtifacts artifacts, @Nullable String specDocumentSnapshot) {
        Map<String, String> testsFiles = producedFilesByType.getOrDefault(RepositoryType.TESTS, Map.of());
        if (specDocumentSnapshot == null || specDocumentSnapshot.isBlank() || testsFiles.isEmpty() || cancelled.getAsBoolean()) {
            return preserveExecutableEvidenceState(report);
        }
        try {
            Map<String, String> solutionFiles = producedFilesByType.getOrDefault(RepositoryType.SOLUTION, Map.of());
            Map<RepositoryType, Map<String, String>> candidateFiles = copyProducedFiles(producedFilesByType);
            String candidateProblemStatement = producedProblemStatement;
            Runnable restoreCandidate = () -> {
                sandbox.resetSession(sessionId);
                workspace.materializeRepositoryFiles(sandbox, sessionId, exercise, mode, candidateFiles, workspaceSeed.repositoryMetadata(), workspaceSeed.repositoryBinaryFiles(),
                        candidateProblemStatement, specDocumentSnapshot, artifacts.testPlanJson());
            };
            List<SemanticMutant> priorUnresolvedMutants = java.util.stream.Stream
                    .of(semanticMutantsPendingRepair, semanticMutantsAwaitingRecheck, semanticMutantsPendingSpecApproval).flatMap(List::stream).distinct()
                    .limit(MAX_TRACKED_SEMANTIC_MUTANTS).toList();
            List<SemanticMutant> applicablePendingMutants = priorUnresolvedMutants.stream()
                    .filter(mutant -> mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath()))).toList();
            int stalePendingMutants = priorUnresolvedMutants.size() - applicablePendingMutants.size();
            if (stalePendingMutants > 0) {
                emit("Retired " + stalePendingMutants
                        + " pending semantic mutant(s) because the reference source changed; the executable reviewer will propose replacements against the current solution.");
            }
            List<SemanticMutantOutcome> pendingMutantOutcomes = applicablePendingMutants.isEmpty() ? List.of()
                    : verifier.checkSemanticMutants(sandbox, sessionId, exercise, solutionFiles, seededStructuralTests, applicablePendingMutants, restoreCandidate);
            int freshCapacity = MAX_TRACKED_SEMANTIC_MUTANTS
                    - (int) pendingMutantOutcomes.stream().filter(outcome -> outcome.disposition() != Disposition.KILLED_BY_GRADED_SUITE).count();
            List<SemanticMutant> freshMutantCandidates = freshCapacity == 0 ? List.of()
                    : specFidelityCritic
                            .authorSemanticMutants(specDocumentSnapshot, solutionFiles,
                                    GenerationReviewSupport.withPriorSemanticMutants(report.findings(), applicablePendingMutants), semanticMutantHistory, usageSink, cancelled)
                            .stream().filter(candidate -> !applicablePendingMutants.contains(candidate)).limit(freshCapacity).toList();
            List<SemanticMutantOutcome> freshMutantOutcomes = freshMutantCandidates.isEmpty() ? List.of()
                    : verifier.evaluateSemanticMutants(sandbox, sessionId, exercise, testsFiles, solutionFiles, seededStructuralTests, freshMutantCandidates, restoreCandidate);
            semanticMutantHistory = GenerationReviewSupport.rememberExecutedMutants(semanticMutantHistory, freshMutantOutcomes);
            List<SemanticMutantOutcome> mutantOutcomes = java.util.stream.Stream.concat(pendingMutantOutcomes.stream(), freshMutantOutcomes.stream()).toList();
            List<SemanticMutant> validatedMutants = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE)
                    .map(SemanticMutantOutcome::mutant).distinct().toList();
            List<SemanticMutant> inconclusivePendingMutants = pendingMutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE)
                    .map(SemanticMutantOutcome::mutant).toList();
            boolean specificationApproved = unresolvedSpecificationFindings.isEmpty();
            semanticMutantsPendingRepair = specificationApproved ? validatedMutants : List.of();
            // Preserve inconclusive rechecks as historical unavailable evidence; never upgrade them to current survivors while specification approval is pending.
            semanticMutantsAwaitingRecheck = inconclusivePendingMutants;
            semanticMutantsPendingSpecApproval = specificationApproved ? List.of() : validatedMutants;

            Set<String> pendingWitnessNames = referenceWitnessesAwaitingPass.stream().map(ContractWitness::testName).collect(Collectors.toSet());
            Set<String> pendingReferenceAdjudicationNames = referenceWitnessesAwaitingAdjudication.stream().map(ContractWitness::testName).collect(Collectors.toSet());
            Set<String> pendingAdoptionAdjudicationNames = positiveWitnessesAwaitingAdjudication.stream().map(ContractWitness::testName).collect(Collectors.toSet());
            Set<String> approvedAdoptionNames = contractWitnessesPendingAdoption.stream().map(ContractWitness::testName).collect(Collectors.toSet());
            List<ContractWitness> candidates = ReferenceWitnessEvidence.candidates(referenceWitnessesAwaitingPass, referenceWitnessesAwaitingAdjudication,
                    positiveWitnessesAwaitingAdjudication, contractWitnessesPendingAdoption, specFidelityCritic.authorContractWitnesses(specDocumentSnapshot,
                            GenerationReviewSupport.renderArtifactSources(testsFiles), GenerationReviewSupport.renderArtifactSources(solutionFiles), usageSink, cancelled));
            Set<String> mutatedRules = validatedMutants.stream().map(SemanticMutant::ruleId).collect(Collectors.toUnmodifiableSet());
            List<ContractWitnessOutcome> witnessOutcomes = verifier.evaluateContractWitnesses(sandbox, sessionId, exercise, testsFiles, seededStructuralTests, candidates,
                    restoreCandidate);
            List<ContractWitnessOutcome> proposedAdjudications = ReferenceWitnessEvidence.adjudicationCandidates(freshMutantOutcomes, witnessOutcomes, pendingWitnessNames,
                    approvedAdoptionNames, mutatedRules);
            List<ContractWitnessOutcome> adjudicationCandidates = specificationApproved ? proposedAdjudications : List.of();
            SpecFidelityCriticService.ReferenceWitnessReview referenceReview = adjudicationCandidates
                    .isEmpty()
                            ? SpecFidelityCriticService.ReferenceWitnessReview.empty()
                            : Optional.ofNullable(specFidelityCritic.adjudicateReferenceWitnesses(specDocumentSnapshot,
                                    GenerationReviewSupport.renderArtifactSources(solutionFiles), adjudicationCandidates, usageSink, cancelled))
                                    .orElseGet(SpecFidelityCriticService.ReferenceWitnessReview::empty);
            Set<String> alreadySourceApproved = java.util.stream.Stream.concat(pendingWitnessNames.stream(), approvedAdoptionNames.stream())
                    .collect(Collectors.toUnmodifiableSet());
            contractWitnessesPendingAdoption = java.util.stream.Stream
                    .concat(ReferenceWitnessEvidence.approvedForAdoption(witnessOutcomes, alreadySourceApproved).stream(), referenceReview.adoptableWitnesses().stream()).distinct()
                    .toList();
            ReferenceWitnessEvidence.State witnessState = ReferenceWitnessEvidence.reconcile(proposedAdjudications, witnessOutcomes, pendingWitnessNames,
                    pendingReferenceAdjudicationNames, pendingAdoptionAdjudicationNames, referenceReview);
            referenceWitnessesAwaitingPass = witnessState.awaitingPass();
            referenceWitnessesAwaitingAdjudication = witnessState.awaitingReferenceAdjudication();
            positiveWitnessesAwaitingAdjudication = witnessState.awaitingAdoptionAdjudication();
            emit(new GenerationReviewSupport.ExecutableProbeSummary(mutantOutcomes, witnessOutcomes, contractWitnessesPendingAdoption.size(), referenceWitnessesAwaitingPass.size(),
                    referenceWitnessesAwaitingAdjudication.size(), positiveWitnessesAwaitingAdjudication.size()).render());
            List<SpecFidelityReport.Finding> combined = new ArrayList<>(SemanticEvidenceReconciler.reconcile(report, mutantOutcomes));
            combined.addAll(referenceReview.findings());
            contractWitnessesPendingAdoption.stream()
                    .filter(witness -> combined.stream()
                            .noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE
                                    && (finding.requirement() + "\n" + finding.detail()).contains(witness.testName())))
                    .map(GenerationReviewSupport::approvedContractWitnessAvailable).forEach(combined::add);
            GenerationReviewSupport.addReferenceUnavailability(combined, witnessState.omittedReferenceAdjudication().size(), witnessState.pendingPassInconclusive().size(),
                    witnessState.referenceAdjudicationInconclusive().size());
            inconclusivePendingMutants.forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantRecheckUnavailable(mutant)));
            witnessState.stillFailing().forEach(witness -> combined.add(GenerationReviewSupport.referenceDefectStillFailing(witness)));
            validatedMutants.forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantFinding(mutant, specificationApproved)));
            positiveWitnessesAwaitingAdjudication.stream().map(GenerationReviewSupport::positiveWitnessAdjudicationUnavailable).filter(finding -> !combined.contains(finding))
                    .forEach(combined::add);
            return preserveReferenceWitnessState(new SpecFidelityReport(List.copyOf(combined)));
        }
        catch (DifferentialVerificationService.VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException e) {
            log.warn("Executable semantic probes were unavailable for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
            emit("Executable semantic probes were unavailable; the verified candidate is preserved, but no mutation or witness evidence was inferred from that failure.");
            return preserveExecutableEvidenceState(report);
        }
    }

    private SpecFidelityReport preserveExecutableEvidenceState(SpecFidelityReport report) {
        SpecFidelityReport preserved = GenerationReviewSupport.preserveSemanticMutantState(preserveReferenceWitnessState(report), semanticMutantsPendingRepair,
                semanticMutantsAwaitingRecheck);
        return GenerationReviewSupport.preservePendingSpecApprovalMutants(preserved, semanticMutantsPendingSpecApproval);
    }

    private SpecFidelityReport preserveReferenceWitnessState(SpecFidelityReport report) {
        SpecFidelityReport preserved = GenerationReviewSupport.preserveReferenceWitnessState(report, referenceWitnessesAwaitingPass, referenceWitnessesAwaitingAdjudication);
        return GenerationReviewSupport.preservePositiveWitnessState(preserved, positiveWitnessesAwaitingAdjudication);
    }

    private SpecFidelityReport runSpecFidelityCritic(String problemStatement, @Nullable ProgrammingLanguage language, @Nullable String adaptationChanges,
            @Nullable String repairDelta, @Nullable SpecFidelityReport previousReport, @Nullable String specSnapshotForReview, @Nullable String testPlanSnapshot) {
        try {
            List<String> testNames = GenerationOrchestrationService.extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = adaptationChanges == null
                    ? specFidelityCritic.critique(reviewBrief, problemStatement, testNames, producedFilesByType, usageSink, cancelled, previousReport, specSnapshotForReview,
                            repairDelta, testPlanSnapshot, verification.templateFailureEvidence())
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
            report = GenerationReviewSupport.reclassifyUngradeableTechniqueFindings(report, specSnapshotForReview);
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
                + "nothing else: the solution, template, statement and every existing test stay as they are. When you add a test, add its exact method name to test-plan.json with "
                + "the same approved seam, weight, and visibility as the witness it strengthens. Then call the structured `verify` tool, and call submit when it "
                + "reports MECHANICAL PRECHECK: PASS.\n\nThe instructor source requirements are:\n" + authoringBrief
                + GenerationReviewSupport.specContractSection(specSnapshotForPrompt) + specFidelityCritic.renderForRetryPrompt(batch.report());
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
                + "If you add, rename, or remove a behavioral test, update test-plan.json in the same edit so it maps every exact test method name. "
                + "Call submit when it reports MECHANICAL PRECHECK: PASS.\n\nThe instructor " + "source requirements are:\n" + authoringBrief
                + GenerationReviewSupport.specContractSection(specSnapshot.get()) + specFidelityCritic.renderForRetryPrompt(batch.report());
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
                + authoringBrief + GenerationReviewSupport.specContractSection(specSnapshot.get()) + semanticCorrectionGuidance
                + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
    }

    private GenerationOutcome cancelledOutcome(AgentLoopResult cancelledResult) {
        // Every caller of this method is a cooperative stop, so the reason belongs here rather than repeated at each of them.
        terminationReason = TerminationReason.CANCELLED;
        if (lastMechanicallyVerifiedCandidate != null) {
            return service.preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
        }
        return service.stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement, cancelledResult);
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
