package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContextDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationConfigurationValidator;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentActivitySink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.FileChangeEmittingAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Top-level driver of agentic exercise generation and adaptation. Owns one Hyperion sandbox: create a sandbox, seed it with the exercise's components, run the agent loop, then
 * run the differential verifier. The verdict and produced files are returned to the caller, which decides whether to persist. The session container is always destroyed, even on
 * failure.
 * <p>
 * The attempt loop itself — authoring, verification, review and the scoped repair rounds between them — lives in {@link GenerationAttemptLoop}, one instance per run. This class
 * keeps what surrounds it: the sandbox lifecycle, the diagnostic capture paths, and the run's final outcome.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrationService.class);

    private static final long VERIFY_WORKSPACE_MAX_FILE_BYTES = 30L * 1024 * 1024;

    private static final long VERIFY_WORKSPACE_MAX_TOTAL_BYTES = 30L * 1024 * 1024;

    private static final int MAX_ADAPTATION_CHANGE_CHARS = 24_000;

    static final String CHANGE_SUMMARY_TRUNCATED = "\n... [change summary truncated]\n";

    // Optional so a core-only node (where no build agent is co-located to host the sandbox) still starts; absence is reported only when a run is attempted.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentSystemPromptService systemPromptService;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // Holds the node-local cancel hook that destroys the sandbox session, so a cancellation during a long build interrupts promptly rather than at the next between-turn poll.
    private final GenerationJobService jobService;

    // Source of the pre-adapt graded test names (the adapt total-wipe gate's baseline). Optional because it is a core-profile repository, absent on a build-agent-only node; when
    // absent the baseline is empty and the total-wipe gate stays inert (fail-open), consistent with every other doubt-on-read-back gate.
    private final Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository;

    private final DifferentialVerificationService verifier;

    /** The per-session specification the spec gate approved; dropped when the session is destroyed so the registry never outlives its runs. */
    private final ApprovedSpecRegistry approvedSpecs;

    private final StageCheckService stageCheckService;

    private final GenerationAttemptLoop.Dependencies attemptLoopDependencies;

    // Required: with the package-private test constructor also present, Spring cannot pick an injection constructor without it.
    @Autowired
    public GenerationOrchestrationService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            DifferentialVerificationService verifier, AgentSystemPromptService systemPromptService, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService, Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository,
            HyperionAgentProperties agentProperties, @Value("${artemis.hyperion.agent.max-semantic-repairs:6}") int maxSemanticRepairs,
            StagedGenerationRunner stagedGenerationRunner, StageCheckService stageCheckService, AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs) {
        this(interactiveSandbox, workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder, specFidelityCritic, jobService, testCaseRepository,
                agentProperties.getMaxTurns(), maxSemanticRepairs, stagedGenerationRunner, agentProperties.isStagedGeneration(), stageCheckService, transcriptWriter,
                approvedSpecs);
    }

    GenerationOrchestrationService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            DifferentialVerificationService verifier, AgentSystemPromptService systemPromptService, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService, Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository, int maxTurns,
            int maxSemanticRepairs, StagedGenerationRunner stagedGenerationRunner, boolean stagedGenerationEnabled, StageCheckService stageCheckService,
            AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs) {
        // maxTurns bounds each attempt, not the whole run.
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-turns must be positive");
        }
        this.interactiveSandbox = interactiveSandbox;
        this.workspace = workspace;
        this.verifier = verifier;
        this.systemPromptService = systemPromptService;
        this.structuralOracleSeeder = structuralOracleSeeder;
        this.jobService = jobService;
        this.testCaseRepository = testCaseRepository;
        // Reject rather than substitute the default: silently running 6 where an operator wrote 20 makes the setting look applied and only shows up as a run that stops repairing
        // earlier than the deployment was tuned for. HyperionGenerationConfigurationValidator applies the same rule eagerly at startup, so this is the second line of defence.
        HyperionGenerationConfigurationValidator.validateMaxSemanticRepairs(maxSemanticRepairs);
        int effectiveMaxSemanticRepairs = maxSemanticRepairs;
        // The attempt ceiling: the mechanical repair phase, one attempt per semantic repair the budget allows, and one more for the narrow mechanical correction the loop grants
        // when a semantic repair breaks the build — an attempt neither of the other two terms pays for, so without it the last configured repair round is unreachable. Derived
        // from the semantic budget so raising that budget can never leave rounds unreachable; the configured job deadline remains the real bound.
        int maxGenerationAttempts = GenerationAttemptLoop.MAX_MECHANICAL_ATTEMPTS + effectiveMaxSemanticRepairs + 1;
        this.stageCheckService = stageCheckService;
        this.approvedSpecs = approvedSpecs;
        this.attemptLoopDependencies = new GenerationAttemptLoop.Dependencies(workspace, agentLoopRunner, verifier, structuralOracleSeeder, specFidelityCritic, jobService,
                stagedGenerationRunner, transcriptWriter, stagedGenerationEnabled, maxTurns, maxGenerationAttempts, effectiveMaxSemanticRepairs);
    }

    private InteractiveSandbox requireSandbox() {
        return interactiveSandbox.orElseThrow(
                () -> new IllegalStateException("No interactive sandbox is available on this node. Agentic exercise generation requires either a co-located build agent or a "
                        + "reachable build agent in the cluster to host the sandbox container."));
    }

    public GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled,
            @Nullable GenerationProgressSink progress, @Nullable Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink, @Nullable Consumer<ChatResponse> usageSink) {
        return generate(exercise, user, userPrompt, jobId, mode, cancelled, progress, fileChangeSink, usageSink, null, null);
    }

    GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled,
            @Nullable GenerationProgressSink progress, @Nullable Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable String originalSourceBrief) {
        return generate(exercise, user, userPrompt, jobId, mode, cancelled, progress, fileChangeSink, usageSink, originalSourceBrief, null);
    }

    /**
     * Runs one generation/adaptation session, streaming a file change to {@code fileChangeSink} on every successful {@code write_file}/{@code edit_file} so the triggering
     * instructor's editor can show which files the agent changes.
     *
     * @param exercise            the exercise to generate or adapt (its repositories must already be scaffolded)
     * @param user                the instructor performing the generation, recorded with the LLM token-usage trace
     * @param userPrompt          the instruction for this run (a generation brief, or the feedback to address)
     * @param jobId               the job id, used to register a node-local cancel hook
     * @param mode                the explicit run intent (generate vs. adapt)
     * @param cancelled           polled cooperatively; if it returns {@code true} the session is aborted
     * @param progress            receives short human-readable progress lines for the live transcript; may be {@code null}
     * @param fileChangeSink      receives a file change on every successful write for live streaming; {@code null} disables file-change streaming
     * @param usageSink           receives token usage for every model call; {@code null} uses the default persisted run sink
     * @param originalSourceBrief the raw instructor brief when this run was started from one, kept separate from {@code userPrompt} so the review authority is not the
     *                                repair-framed prompt; {@code null} otherwise
     * @param settings            the resolved effort profile of this run, or {@code null} to run the deployment default
     * @return the outcome including the verification verdict and the produced files
     */
    GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled,
            @Nullable GenerationProgressSink progress, @Nullable Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable String originalSourceBrief, @Nullable HyperionGenerationSettings settings) {
        GenerationAttemptLoop.Dependencies runDependencies = attemptLoopDependencies.forSettings(settings);
        // Snapshot the pre-adapt graded test names so the verifier can reject a destructive total wipe (an adapt that retains none of them = a from-scratch regeneration mislabeled
        // as an adapt). Empty for GENERATE, which leaves the total-wipe gate inert.
        Set<String> baselineGradedTestNames = mode == GenerationMode.ADAPT ? captureBaselineGradedTestNames(exercise) : Set.of();
        String baselineProblemStatement = exercise.getProblemStatement();
        // The client seeds every new exercise with the default template readme, so only a real instructor statement may steer the brief, the workspace seed, or skip the SPEC
        // stage. Otherwise that template's contents become "requirements to preserve" everywhere at once.
        boolean generatedFromSourceBrief = mode == GenerationMode.GENERATE && originalSourceBrief != null && !originalSourceBrief.isBlank();
        boolean statementAuthoritative = mode == GenerationMode.ADAPT || !generatedFromSourceBrief && systemPromptService.isAuthoritativeProblemStatement(exercise);
        String sourceBrief = generatedFromSourceBrief ? originalSourceBrief.strip() : renderReviewBrief(mode, userPrompt, statementAuthoritative ? baselineProblemStatement : null);
        Long courseId = courseIdOf(exercise);
        Consumer<ChatResponse> effectiveUsageSink = usageSink != null ? usageSink : jobService.tokenUsageSink(courseId, exercise.getId(), user.getId(), jobId);
        InteractiveSandbox sandbox = requireSandbox();
        String sessionId = null;
        GenerationWorkspaceService.WorkspaceSeed workspaceSeed = null;
        Map<String, String> placeholderReplacements = Map.of();
        Map<RepositoryType, Map<String, String>> baselineRepositoryFiles = Map.of();
        GenerationAttemptLoop attemptLoop = null;
        boolean checkpointRunStarted = false;
        SandboxSessionSpecDTO sessionSpec = workspace.sessionSpec(exercise,
                new SandboxSessionContextDTO(jobId, exercise.getId(), exercise.getTitle(), courseId, user.getLogin(), mode.name()));
        try {
            if (cancelled.getAsBoolean()) {
                return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            emit(progress, "Setting up the build environment");
            sessionId = sandbox.createSession(sessionSpec);
            String activeSessionId = sessionId;
            jobService.registerCancelHook(jobId, () -> sandbox.destroySession(activeSessionId));

            emit(progress, mode == GenerationMode.GENERATE ? "Preparing a clean exercise workspace" : "Loading the existing exercise");
            // Snapshot the seeded tests-repo harness so the verifier can reject later tampering against this exact baseline.
            workspaceSeed = workspace.seedWorkspace(sandbox, sessionId, exercise, mode, statementAuthoritative);
            placeholderReplacements = ciPlaceholderReplacements(exercise);
            Map<String, String> testsSeedSnapshot = replacePlaceholders(workspaceSeed.testsSeedSnapshot(), placeholderReplacements);
            structuralOracleSeeder.captureBaseline(sessionId, workspaceSeed.testsSeedSnapshot());
            baselineRepositoryFiles = replacePlaceholdersByRepository(workspaceSeed.repositoryTextFiles(), placeholderReplacements);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }

            emit(progress, "Checking the build environment");
            Optional<String> buildEnvironmentFailure = checkBuildEnvironment(sandbox, sessionId, exercise);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            if (buildEnvironmentFailure.isPresent()) {
                destroyQuietly(sandbox, sessionId);
                return GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 0, ""), buildEnvironmentFailure.get())
                        .withTermination(TerminationReason.ENVIRONMENT_UNAVAILABLE);
            }

            String systemPrompt = systemPromptService.build(exercise, mode);
            // The agent's `verify` tool runs the same differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); the post-loop
            // verification inside the attempt loop stays the mechanical-verification decision.
            SandboxAgentTools baseTools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise, testsSeedSnapshot, mode == GenerationMode.ADAPT, stageCheckService);
            baseTools.configureStructuralOracleRefresh(() -> structuralOracleSeeder.seedIfStructuralDiff(sandbox, activeSessionId, exercise));
            // The decorator emits path/action metadata for the instructor's live activity view, never file content. It re-exposes the same @Tool surface, so the model sees an
            // identical tool set either way.
            Object tools = fileChangeSink != null ? new FileChangeEmittingAgentTools(baseTools, fileChangeSink, AgentActivitySink.trackerOf(progress)) : baseTools;
            runDependencies.agentLoopRunner().beginCheckpointRun(jobId, exercise, baseTools, approvedSpecs);
            checkpointRunStarted = true;

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (an empty probe leaves the prompt unchanged) and first-attempt only: retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), renderAuthoringBrief(sourceBrief));

            attemptLoop = new GenerationAttemptLoop(this, runDependencies,
                    new GenerationAttemptLoop.RunContext(exercise, mode, jobId, sandbox, sessionId, workspaceSeed, testsSeedSnapshot, placeholderReplacements,
                            baselineRepositoryFiles, baselineProblemStatement, baselineGradedTestNames, sourceBrief, mode == GenerationMode.GENERATE, !statementAuthoritative,
                            systemPrompt, firstPrompt, baseTools, tools, cancelled, progress, effectiveUsageSink));
            GenerationOutcome decidedInLoop = attemptLoop.run();
            if (decidedInLoop != null) {
                return decidedInLoop.withTermination(attemptLoop.terminationReason());
            }

            // A semantic repair can accidentally break a candidate that already built and graded correctly. Never discard that more useful checkpoint in favour of a later
            // mechanically broken tree; return the last buildable candidate and its unresolved review findings.
            boolean currentCandidateRejected = attemptLoop.verification() == null || !attemptLoop.verification().mechanicallyVerified()
                    || attemptLoop.terminationReason() == TerminationReason.REVIEW_UNAVAILABLE
                            && RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(attemptLoop.specFidelityReport());
            if (currentCandidateRejected && attemptLoop.lastMechanicallyVerifiedCandidate() != null) {
                return preserveCandidate(attemptLoop.lastMechanicallyVerifiedCandidate(), sandbox, sessionId, workspaceSeed).withTermination(attemptLoop.terminationReason());
            }
            return new GenerationOutcome(attemptLoop.loopResult(), attemptLoop.verification(), sessionId, this, sandbox, attemptLoop.producedFilesByType(),
                    attemptLoop.producedProblemStatement(), attemptLoop.specFidelityReport(), workspaceSeed.repositoryHeads(), readSpecDocument(sandbox, sessionId),
                    readWorkspaceRootFile(sandbox, sessionId, "test-plan.json")).withTermination(attemptLoop.terminationReason());
        }
        catch (RuntimeException e) {
            GenerationAttemptLoop.CandidateSnapshot verifiedCheckpoint = attemptLoop == null ? null : attemptLoop.lastMechanicallyVerifiedCandidate();
            GenerationAttemptLoop.ExtractedCandidate extractedCheckpoint = attemptLoop == null ? null : attemptLoop.lastExtractedCandidate();
            // A build interrupted by the cancel hook surfaces as a throw; report it as a clean cancellation.
            if (cancelled.getAsBoolean()) {
                if (verifiedCheckpoint != null && workspaceSeed != null) {
                    return preserveCandidate(verifiedCheckpoint, sandbox, sessionId, workspaceSeed).withTermination(TerminationReason.CANCELLED);
                }
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            if (verifiedCheckpoint != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while repairing a mechanically verified candidate for exercise {}; preserving the verified checkpoint ({})", exercise.getId(),
                        e.getClass().getSimpleName(), e);
                return new GenerationOutcome(verifiedCheckpoint.loopResult(), verifiedCheckpoint.verification(), sessionId, this, sandbox, verifiedCheckpoint.producedFiles(),
                        verifiedCheckpoint.problemStatement(), verifiedCheckpoint.reviewReport(), workspaceSeed.repositoryHeads(), verifiedCheckpoint.specDocument(),
                        verifiedCheckpoint.testPlanJson()).withTermination(TerminationReason.RUN_FAILED);
            }
            if (extractedCheckpoint != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while verifying an extracted candidate for exercise {}; preserving the captured work ({})", exercise.getId(),
                        e.getClass().getSimpleName(), e);
                AgentLoopResult stopped = AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Generation stopped before verification completed.");
                return new GenerationOutcome(stopped, null, sessionId, this, sandbox, extractedCheckpoint.producedFiles(), extractedCheckpoint.problemStatement(),
                        SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the captured candidate could be fully verified."), workspaceSeed.repositoryHeads(),
                        readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json")).withTermination(TerminationReason.RUN_FAILED);
            }
            GenerationOutcome diagnosticError = captureUnexpectedFailure(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles,
                    baselineProblemStatement);
            if (diagnosticError != null) {
                log.warn("Exercise generation failed after producing diagnostic artifacts for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName(), e);
                return diagnosticError.withTermination(TerminationReason.RUN_FAILED);
            }
            // No usable outcome exists for the caller to close.
            destroyQuietly(sandbox, sessionId);
            log.error("Exercise generation failed for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName(), e);
            throw e;
        }
        finally {
            if (checkpointRunStarted) {
                runDependencies.agentLoopRunner().endCheckpointRun();
            }
            jobService.deregisterCancelHook(jobId);
        }
    }

    GenerationOutcome preserveCandidate(GenerationAttemptLoop.CandidateSnapshot candidate, InteractiveSandbox sandbox, String sessionId,
            GenerationWorkspaceService.WorkspaceSeed workspaceSeed) {
        return new GenerationOutcome(candidate.loopResult(), candidate.verification(), sessionId, this, sandbox, candidate.producedFiles(), candidate.problemStatement(),
                candidate.reviewReport(), workspaceSeed.repositoryHeads(), candidate.specDocument(), candidate.testPlanJson());
    }

    GenerationOutcome stopOrPreserve(InteractiveSandbox sandbox, @Nullable String sessionId, GenerationWorkspaceService.@Nullable WorkspaceSeed workspaceSeed,
            Map<String, String> placeholderReplacements, Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement,
            AgentLoopResult cancelledResult) {
        GenerationOutcome diagnosticOutcome = captureUnexpectedFailure(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles,
                baselineProblemStatement);
        if (diagnosticOutcome != null) {
            return diagnosticOutcome;
        }
        destroyQuietly(sandbox, sessionId);
        return GenerationOutcome.cancelled(cancelledResult);
    }

    private @Nullable GenerationOutcome captureUnexpectedFailure(InteractiveSandbox sandbox, @Nullable String sessionId,
            GenerationWorkspaceService.@Nullable WorkspaceSeed workspaceSeed, Map<String, String> placeholderReplacements,
            Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement) {
        if (sessionId == null || workspaceSeed == null) {
            return null;
        }
        try {
            workspace.cleanTransientBuildOutputs(sandbox, sessionId);
        }
        catch (RuntimeException cleanupFailure) {
            log.debug("Could not clean transient outputs before diagnostic capture: {}", cleanupFailure.getMessage());
        }
        Map<RepositoryType, Map<String, String>> files = Map.of();
        try {
            files = changedCapturedRepositoryFiles(baselineRepositoryFiles, captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
        }
        catch (RuntimeException extractionFailure) {
            log.debug("Could not extract every repository after generation failed: {}", extractionFailure.getMessage());
        }
        String statement;
        try {
            statement = workspace.extractProblemStatement(sandbox, sessionId).trim();
        }
        catch (RuntimeException extractionFailure) {
            statement = baselineProblemStatement == null ? "" : baselineProblemStatement.trim();
        }
        boolean statementChanged = !Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), statement);
        if (!statementChanged && files.isEmpty()) {
            return null;
        }
        AgentLoopResult loopResult = AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Generation stopped unexpectedly before verification completed.");
        return new GenerationOutcome(loopResult, null, sessionId, this, sandbox, files, statement,
                SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the candidate could be fully verified."), workspaceSeed.repositoryHeads(),
                readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
    }

    Map<RepositoryType, Map<String, String>> captureRepositoryFiles(InteractiveSandbox sandbox, String sessionId, GenerationWorkspaceService.WorkspaceSeed workspaceSeed,
            Map<String, String> placeholderReplacements) {
        Map<RepositoryType, Map<String, String>> captured = new EnumMap<>(RepositoryType.class);
        for (RepositoryType type : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            GenerationWorkspaceService.RepositoryExtraction extraction = workspace.extractRepository(sandbox, sessionId, type,
                    workspaceSeed.repositoryMetadata().getOrDefault(type, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
            if (!extraction.extractionFailed()) {
                captured.put(type, replacePlaceholders(extraction, placeholderReplacements).files());
            }
        }
        return Map.copyOf(captured);
    }

    static Map<RepositoryType, Map<String, String>> changedCapturedRepositoryFiles(Map<RepositoryType, Map<String, String>> baselineFiles,
            Map<RepositoryType, Map<String, String>> capturedFiles) {
        Map<RepositoryType, Map<String, String>> changed = new EnumMap<>(RepositoryType.class);
        capturedFiles.forEach((type, files) -> {
            if (!baselineFiles.getOrDefault(type, Map.of()).equals(files)) {
                changed.put(type, files);
            }
        });
        return Map.copyOf(changed);
    }

    static String renderReviewBrief(GenerationMode mode, String runInstruction, @Nullable String startingProblemStatement) {
        if (startingProblemStatement == null || startingProblemStatement.isBlank()) {
            return runInstruction;
        }
        String instructionRole = mode == GenerationMode.ADAPT ? "authoritative adaptation request" : "authoritative for requested changes";
        return "RUN INSTRUCTION (" + instructionRole + "):\n" + runInstruction
                + "\n\nSTARTING PROBLEM STATEMENT (preserve every requirement where the run instruction is silent):\n" + startingProblemStatement.strip();
    }

    static String renderAuthoringBrief(String sourceBrief) {
        return "PRIMARY SOURCE REQUIREMENTS (authoritative; preserve every explicit requirement):\n" + sourceBrief
                + "\n\nChoose only the minimal API and behavior needed to implement these requirements. Do not add graded purity, immutability, thread-safety, exception, or architecture "
                + "requirements unless the source explicitly requests them. Keep the statement, starter, solution, tests, examples, and task bindings consistent.";
    }

    /** Matches an Artemis {@code [task][Title](testA,testB)} binding, capturing the comma-separated test-name list. */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task]\\[[^]]*]\\(([^)]*)\\)");

    static String renderAdaptationChanges(@Nullable String baselineProblemStatement, String producedProblemStatement, Map<RepositoryType, Map<String, String>> baselineFiles,
            Map<RepositoryType, Map<String, String>> producedFiles) {
        StringBuilder changes = new StringBuilder();
        appendChangedFile(changes, "problem-statement.md", baselineProblemStatement, producedProblemStatement);
        for (RepositoryType type : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            Map<String, String> before = baselineFiles.getOrDefault(type, Map.of());
            Map<String, String> after = producedFiles.getOrDefault(type, Map.of());
            Set<String> paths = new TreeSet<>(before.keySet());
            paths.addAll(after.keySet());
            for (String path : paths) {
                appendChangedFile(changes, type.getName() + "/" + path, before.get(path), after.get(path));
            }
        }
        return changes.toString();
    }

    static String renderGenerationRepairChanges(@Nullable String baselineProblemStatement, String producedProblemStatement, Map<RepositoryType, Map<String, String>> baselineFiles,
            Map<RepositoryType, Map<String, String>> producedFiles, @Nullable String baselineTestPlan, @Nullable String producedTestPlan) {
        StringBuilder changes = new StringBuilder(renderAdaptationChanges(baselineProblemStatement, producedProblemStatement, baselineFiles, producedFiles));
        appendChangedFile(changes, "test-plan.json", baselineTestPlan, producedTestPlan);
        return changes.toString();
    }

    private static void appendChangedFile(StringBuilder changes, String path, @Nullable String before, @Nullable String after) {
        if (changes.length() >= MAX_ADAPTATION_CHANGE_CHARS || Objects.equals(before == null ? "" : before, after == null ? "" : after)) {
            return;
        }
        appendCapped(changes, "\n--- " + path + "\n");
        RawText oldText = new RawText((before == null ? "" : before).getBytes(StandardCharsets.UTF_8));
        RawText newText = new RawText((after == null ? "" : after).getBytes(StandardCharsets.UTF_8));
        for (Edit edit : DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS).diff(RawTextComparator.DEFAULT, oldText, newText)) {
            appendCapped(changes, "@@ -" + (edit.getBeginA() + 1) + " +" + (edit.getBeginB() + 1) + " @@\n");
            for (int line = edit.getBeginA(); line < edit.getEndA(); line++) {
                appendCapped(changes, "- " + oldText.getString(line) + "\n");
            }
            for (int line = edit.getBeginB(); line < edit.getEndB(); line++) {
                appendCapped(changes, "+ " + newText.getString(line) + "\n");
            }
        }
    }

    private static void appendCapped(StringBuilder target, String value) {
        if (target.indexOf(CHANGE_SUMMARY_TRUNCATED) >= 0) {
            return;
        }
        int contentLimit = MAX_ADAPTATION_CHANGE_CHARS - CHANGE_SUMMARY_TRUNCATED.length();
        int remaining = contentLimit - target.length();
        if (value.length() <= remaining) {
            target.append(value);
        }
        else {
            if (remaining > 0) {
                target.append(value, 0, remaining);
            }
            target.append(CHANGE_SUMMARY_TRUNCATED);
        }
    }

    static List<String> extractTaskBoundTestNames(String problemStatement) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = TASK_BINDING.matcher(problemStatement);
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String name = raw.trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    static String prependWorkspaceLayout(String layout, String userPrompt) {
        if (layout == null || layout.isBlank()) {
            return userPrompt;
        }
        return "=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\n" + layout.strip() + "\n=== END INITIAL WORKSPACE ===\n\n" + userPrompt;
    }

    /**
     * A conservative superset of the graded coverage the adapt total-wipe gate protects: every persisted case rather than the active/weighted subset, so the baseline is never
     * under-reported. Empty — leaving the gate inert — when no authoritative baseline is available, so a missing baseline can never fabricate a rejection.
     */
    private Set<String> captureBaselineGradedTestNames(ProgrammingExercise exercise) {
        if (testCaseRepository.isEmpty() || exercise.getId() == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (ProgrammingExerciseTestCase testCase : testCaseRepository.get().findByExerciseId(exercise.getId())) {
            String name = testCase.getTestName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    @Nullable
    private static Long courseIdOf(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course == null ? null : course.getId();
    }

    static GenerationWorkspaceService.RepositoryExtraction replacePlaceholders(GenerationWorkspaceService.RepositoryExtraction extraction, Map<String, String> replacements) {
        return new GenerationWorkspaceService.RepositoryExtraction(replacePlaceholders(extraction.files(), replacements), extraction.extractionFailed());
    }

    private static Map<String, String> replacePlaceholders(Map<String, String> files, Map<String, String> replacements) {
        Map<String, String> normalized = new LinkedHashMap<>();
        long totalBytes = 0;
        for (Map.Entry<String, String> file : files.entrySet()) {
            String content = replacePlaceholders(file.getValue(), replacements);
            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            totalBytes += bytes;
            if (totalBytes > VERIFY_WORKSPACE_MAX_TOTAL_BYTES) {
                throw new IllegalStateException("Normalized generated repository content exceeds " + VERIFY_WORKSPACE_MAX_TOTAL_BYTES + " bytes");
            }
            normalized.put(file.getKey(), content);
        }
        return normalized;
    }

    private static Map<RepositoryType, Map<String, String>> replacePlaceholdersByRepository(Map<RepositoryType, Map<String, String>> filesByRepository,
            Map<String, String> replacements) {
        Map<RepositoryType, Map<String, String>> normalized = new EnumMap<>(RepositoryType.class);
        filesByRepository.forEach((type, files) -> normalized.put(type, replacePlaceholders(files, replacements)));
        return Map.copyOf(normalized);
    }

    private static String replacePlaceholders(String content, Map<String, String> replacements) {
        long normalizedBytes = content.getBytes(StandardCharsets.UTF_8).length;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            int occurrences = 0;
            int index = 0;
            while ((index = content.indexOf(replacement.getKey(), index)) >= 0) {
                occurrences++;
                index += replacement.getKey().length();
            }
            normalizedBytes += (long) occurrences * (replacement.getValue().getBytes(StandardCharsets.UTF_8).length - replacement.getKey().getBytes(StandardCharsets.UTF_8).length);
            if (normalizedBytes > VERIFY_WORKSPACE_MAX_FILE_BYTES) {
                throw new IllegalStateException("Normalized generated file exceeds " + VERIFY_WORKSPACE_MAX_FILE_BYTES + " bytes");
            }
        }
        String normalized = content;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            normalized = normalized.replace(replacement.getKey(), replacement.getValue());
        }
        return normalized;
    }

    private static Map<String, String> ciPlaceholderReplacements(ProgrammingExercise exercise) {
        var buildConfig = exercise.getBuildConfig();
        String assignment = buildConfig == null ? null : buildConfig.getAssignmentCheckoutPath();
        assignment = assignment == null || assignment.isBlank() ? Constants.ASSIGNMENT_REPO_NAME : stripLeadingSlash(assignment);
        String tests = buildConfig == null ? null : buildConfig.getTestCheckoutPath();
        tests = tests == null || tests.isBlank() ? Constants.TEST_REPO_NAME : stripLeadingSlash(tests);
        String solution = buildConfig == null ? null : buildConfig.getSolutionCheckoutPath();
        solution = solution == null || solution.isBlank() ? Constants.SOLUTION_REPO_NAME : stripLeadingSlash(solution);
        String assignmentParent = exercise.getProgrammingLanguage() == ProgrammingLanguage.PYTHON ? assignment.replace('/', '.') : assignment;
        return Map.of("${studentWorkingDirectory}", "/" + assignment + "/src", "${studentParentWorkingDirectoryName}", assignmentParent, "${solutionWorkingDirectory}", solution,
                "${testWorkingDirectory}", tests);
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    GenerationWorkspaceService workspace() {
        return workspace;
    }

    private Optional<String> checkBuildEnvironment(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        try {
            workspace.stageBuildReadinessFixture(sandbox, sessionId, exercise);
            return verifier.checkBuildEnvironment(sandbox, sessionId, exercise);
        }
        catch (RuntimeException exception) {
            log.warn("Could not prepare the sandbox build-environment readiness probe for exercise {} ({}): {}", exercise.getId(), exception.getClass().getSimpleName(),
                    DifferentialVerificationService.boundedReadinessDiagnostic(exception.getMessage()));
            return Optional.of("The sandbox build environment could not be prepared before authoring began. Fix the build image or sandbox runtime; the authoring agent was not "
                    + "started.");
        }
    }

    /**
     * Best-effort, read-once capture of the workspace's {@code SPEC.md} for {@link GenerationOutcome#specDocument()}; {@code null} when the file was never written or
     * could not be read (e.g. the sandbox session no longer exists). Never persisted into any repository.
     */
    @Nullable
    static String readSpecDocument(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
        return readWorkspaceRootFile(sandbox, sessionId, "SPEC.md");
    }

    @Nullable
    static String readWorkspaceRootFile(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId, String fileName) {
        if (sandbox == null || sessionId == null) {
            return null;
        }
        try {
            SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/" + fileName);
            return result != null && result.isSuccess() ? result.stdout() : null;
        }
        catch (RuntimeException e) {
            log.debug("Could not read {} after generation for diagnostics: {}", fileName, e.getMessage());
            return null;
        }
    }

    void destroyQuietly(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
        structuralOracleSeeder.forget(sessionId);
        approvedSpecs.forget(sessionId);
        if (sandbox != null && sessionId != null) {
            try {
                sandbox.destroySession(sessionId);
            }
            catch (RuntimeException e) {
                log.warn("Failed to destroy sandbox session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    static void emit(@Nullable Consumer<String> progress, String message) {
        AgentActivitySink.emit(progress, message);
    }
}
