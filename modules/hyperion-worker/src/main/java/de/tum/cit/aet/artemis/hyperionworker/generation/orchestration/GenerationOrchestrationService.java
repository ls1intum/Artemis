package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress.FileChange;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentActivitySink;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.FileChangeEmittingAgentTools;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperionworker.generation.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.SandboxExecResult;

/**
 * Top-level driver of agentic exercise generation and adaptation. Owns one Hyperion sandbox: create a sandbox, seed it with the exercise's components, run the agent loop, then
 * run the differential verifier. The verdict and produced files are returned to the caller, which decides whether to persist. The session container is always destroyed, even on
 * failure.
 * <p>
 * The attempt loop itself — authoring, verification, review and the scoped repair rounds between them — lives in {@link GenerationAttemptLoop}, one instance per run. This class
 * keeps what surrounds it: the sandbox lifecycle, the diagnostic capture paths, and the run's final outcome.
 */
public class GenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrationService.class);

    private static final long VERIFY_WORKSPACE_MAX_FILE_BYTES = 30L * 1024 * 1024;

    private static final long VERIFY_WORKSPACE_MAX_TOTAL_BYTES = 30L * 1024 * 1024;

    private static final int MAX_ADAPTATION_CHANGE_CHARS = 24_000;

    static final String CHANGE_SUMMARY_TRUNCATED = "\n... [change summary truncated]\n";

    private final InteractiveSandbox interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentSystemPromptService systemPromptService;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    private final DifferentialVerificationService verifier;

    /** The per-session specification the spec gate approved; dropped when the session is destroyed so the registry never outlives its runs. */
    private final ApprovedSpecRegistry approvedSpecs;

    private final StageCheckService stageCheckService;

    private final GenerationAttemptLoop.Dependencies attemptLoopDependencies;

    /**
     * The teardown gate of every sandbox session this node currently owns, keyed by session id. Every destroy route — the node-local cancel hook, this class's terminal
     * {@link #destroyQuietly} — resolves the session's gate here, so cancellation cannot destroy a sandbox during an active capture. The run's finally block removes the gate
     * after all capture paths have finished.
     */
    private final Map<String, SandboxSessionLifecycle> sessionLifecycles = new ConcurrentHashMap<>();

    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    public GenerationOrchestrationService(InteractiveSandbox interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            DifferentialVerificationService verifier, AgentSystemPromptService systemPromptService, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, int maxTurns, int maxSemanticRepairs, StagedGenerationRunner stagedGenerationRunner, boolean stagedGenerationEnabled,
            StageCheckService stageCheckService, AgentTranscriptWriter transcriptWriter, ApprovedSpecRegistry approvedSpecs) {
        if (maxTurns <= 0 || maxSemanticRepairs < 1 || maxSemanticRepairs > 12) {
            throw new IllegalArgumentException("Invalid generation attempt limits");
        }
        this.interactiveSandbox = interactiveSandbox;
        this.workspace = workspace;
        this.verifier = verifier;
        this.systemPromptService = systemPromptService;
        this.structuralOracleSeeder = structuralOracleSeeder;
        this.stageCheckService = stageCheckService;
        this.approvedSpecs = approvedSpecs;
        int maxGenerationAttempts = GenerationAttemptLoop.MAX_MECHANICAL_ATTEMPTS + maxSemanticRepairs + 1;
        this.attemptLoopDependencies = new GenerationAttemptLoop.Dependencies(workspace, agentLoopRunner, verifier, structuralOracleSeeder, specFidelityCritic,
                stagedGenerationRunner, transcriptWriter, stagedGenerationEnabled, maxTurns, maxGenerationAttempts, maxSemanticRepairs);
    }

    /**
     * Runs the authoring/repair policy and closes its worker-local sandbox before returning immutable artifacts.
     *
     * @param exercise            immutable authoring facts
     * @param seed                repository files captured by core
     * @param userPrompt          instructor generation or adaptation request
     * @param jobId               execution's job identity
     * @param mode                generation or adaptation
     * @param cancelled           combined cancellation and budget stop condition
     * @param progress            instructor activity sink
     * @param fileChangeSink      bounded file previews
     * @param usageSink           provider accounting sink
     * @param originalSourceBrief primary requirements retained across adaptations
     * @param settings            resolved effort profile
     * @param specObserver        specification checkpoint sink
     * @param checkpointObserver  verified candidate checkpoint sink
     * @return captured artifacts and their verification and review outcome
     */
    public GenerationOutcome generate(GenerationInput exercise, WorkspaceSnapshot seed, String userPrompt, String jobId, Mode mode, BooleanSupplier cancelled,
            @Nullable GenerationProgressSink progress, @Nullable Consumer<FileChange> fileChangeSink, Consumer<ChatResponse> usageSink, @Nullable String originalSourceBrief,
            HyperionGenerationSettings settings, Consumer<String> specObserver, Consumer<GenerationOutcome> checkpointObserver) {
        GenerationAttemptLoop.Dependencies runDependencies = attemptLoopDependencies.forSettings(settings);
        // Snapshot the pre-adapt graded test names so the verifier can reject a destructive total wipe (an adapt that retains none of them = a from-scratch regeneration mislabeled
        // as an adapt). Empty for GENERATE, which leaves the total-wipe gate inert.
        Set<String> baselineGradedTestNames = mode == Mode.ADAPT ? exercise.baselineGradedTestNames() : Set.of();
        String baselineProblemStatement = exercise.problemStatement();
        // The client seeds every new exercise with the default template readme, so only a real instructor statement may steer the brief, the workspace seed, or skip the SPEC
        // stage. Otherwise that template's contents become "requirements to preserve" everywhere at once.
        boolean generatedFromSourceBrief = mode == Mode.GENERATE && originalSourceBrief != null && !originalSourceBrief.isBlank();
        boolean statementAuthoritative = mode == Mode.ADAPT || !generatedFromSourceBrief && systemPromptService.isAuthoritativeProblemStatement(exercise);
        String sourceBrief = generatedFromSourceBrief ? originalSourceBrief.strip() : renderReviewBrief(mode, userPrompt, statementAuthoritative ? baselineProblemStatement : null);
        Consumer<ChatResponse> effectiveUsageSink = usageSink;
        InteractiveSandbox sandbox = interactiveSandbox;
        String sessionId = null;
        GenerationWorkspaceService.WorkspaceSeed workspaceSeed = null;
        Map<String, String> placeholderReplacements = Map.of();
        Map<RepositoryRole, Map<String, String>> baselineRepositoryFiles = Map.of();
        AuthoringStageCapture capture = null;
        GenerationAttemptLoop attemptLoop = null;
        try {
            if (cancelled.getAsBoolean()) {
                return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            emit(progress, "Setting up the build environment");
            sessionId = sandbox.createSession();
            String activeSessionId = sessionId;
            SandboxSessionLifecycle lifecycle = registerSessionLifecycle(sandbox, activeSessionId);
            emit(progress, mode == Mode.GENERATE ? "Preparing a clean exercise workspace" : "Loading the existing exercise");
            // Snapshot the seeded tests-repo harness so the verifier can reject later tampering against this exact baseline.
            workspaceSeed = workspace.seedWorkspace(sandbox, sessionId, exercise, mode, seed, statementAuthoritative);
            placeholderReplacements = Map.of();
            Map<String, String> testsSeedSnapshot = replacePlaceholders(workspaceSeed.testsSeedSnapshot(), placeholderReplacements);
            structuralOracleSeeder.captureBaseline(sessionId, workspaceSeed.testsSeedSnapshot());
            baselineRepositoryFiles = replacePlaceholdersByRepository(workspaceSeed.repositoryTextFiles(), placeholderReplacements);
            capture = new AuthoringStageCapture(this, workspace, sandbox, sessionId, lifecycle, workspaceSeed, placeholderReplacements, baselineRepositoryFiles,
                    baselineProblemStatement);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, capture, new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }

            emit(progress, "Checking the build environment");
            Optional<String> buildEnvironmentFailure = checkBuildEnvironment(sandbox, sessionId, exercise);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, capture, new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            if (buildEnvironmentFailure.isPresent()) {
                return GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 0, ""), buildEnvironmentFailure.get())
                        .withTermination(TerminationReason.ENVIRONMENT_UNAVAILABLE);
            }

            String systemPrompt = systemPromptService.build(exercise, mode);
            // The agent's `verify` tool runs the same differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); the post-loop
            // verification inside the attempt loop stays the mechanical-verification decision.
            SandboxAgentTools baseTools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise, testsSeedSnapshot, mode == Mode.ADAPT, stageCheckService);
            baseTools.configureStructuralOracleRefresh(() -> structuralOracleSeeder.seedIfStructuralDiff(sandbox, activeSessionId, exercise));
            // The decorator emits path/action metadata for the instructor's live activity view, never file content. It re-exposes the same @Tool surface, so the model sees an
            // identical tool set either way.
            Object tools = fileChangeSink != null ? new FileChangeEmittingAgentTools(baseTools, fileChangeSink, AgentActivitySink.trackerOf(progress)) : baseTools;

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (an empty probe leaves the prompt unchanged) and first-attempt only: retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), renderAuthoringBrief(sourceBrief, mode));

            attemptLoop = new GenerationAttemptLoop(this, runDependencies,
                    new GenerationAttemptLoop.RunContext(exercise, mode, jobId, sandbox, sessionId, workspaceSeed, testsSeedSnapshot, placeholderReplacements,
                            baselineRepositoryFiles, baselineProblemStatement, baselineGradedTestNames, sourceBrief, mode == Mode.GENERATE, !statementAuthoritative, systemPrompt,
                            firstPrompt, baseTools, tools, cancelled, progress, effectiveUsageSink, capture, specObserver, checkpointObserver));
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
            return new GenerationOutcome(attemptLoop.loopResult(), attemptLoop.verification(), attemptLoop.producedFilesByType(), attemptLoop.producedProblemStatement(),
                    attemptLoop.specFidelityReport(), readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"))
                    .withTermination(attemptLoop.terminationReason());
        }
        catch (RuntimeException e) {
            GenerationAttemptLoop.CandidateSnapshot verifiedCheckpoint = attemptLoop == null ? null : attemptLoop.lastMechanicallyVerifiedCandidate();
            GenerationAttemptLoop.ExtractedCandidate extractedCheckpoint = attemptLoop == null ? null : attemptLoop.lastExtractedCandidate();
            // A build interrupted by the cancel hook surfaces as a throw; report it as a clean cancellation.
            if (cancelled.getAsBoolean()) {
                if (verifiedCheckpoint != null && workspaceSeed != null) {
                    return preserveCandidate(verifiedCheckpoint, sandbox, sessionId, workspaceSeed).withTermination(TerminationReason.CANCELLED);
                }
                return stopOrPreserve(sandbox, sessionId, capture, new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "")).withTermination(TerminationReason.CANCELLED);
            }
            if (verifiedCheckpoint != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while repairing a mechanically verified candidate for exercise {}; preserving the verified checkpoint ({})", exercise.id(),
                        e.getClass().getSimpleName(), e);
                return new GenerationOutcome(verifiedCheckpoint.loopResult(), verifiedCheckpoint.verification(), verifiedCheckpoint.producedFiles(),
                        verifiedCheckpoint.problemStatement(), verifiedCheckpoint.reviewReport(), verifiedCheckpoint.specDocument(), verifiedCheckpoint.testPlanJson())
                        .withTermination(TerminationReason.RUN_FAILED);
            }
            if (extractedCheckpoint != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while verifying an extracted candidate for exercise {}; preserving the captured work ({})", exercise.id(),
                        e.getClass().getSimpleName(), e);
                AgentLoopResult stopped = AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Generation stopped before verification completed.");
                return new GenerationOutcome(stopped, null, extractedCheckpoint.producedFiles(), extractedCheckpoint.problemStatement(),
                        SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the captured candidate could be fully verified."),
                        readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json")).withTermination(TerminationReason.RUN_FAILED);
            }
            GenerationOutcome diagnosticError = captureUnexpectedFailure(capture);
            if (diagnosticError != null) {
                log.warn("Exercise generation failed after producing diagnostic artifacts for exercise {} ({})", exercise.id(), e.getClass().getSimpleName(), e);
                return diagnosticError.withTermination(TerminationReason.RUN_FAILED);
            }
            log.error("Exercise generation failed for exercise {} ({})", exercise.id(), e.getClass().getSimpleName(), e);
            throw e;
        }
        finally {
            destroyQuietly(sandbox, sessionId);
        }
    }

    GenerationOutcome preserveCandidate(GenerationAttemptLoop.CandidateSnapshot candidate, InteractiveSandbox sandbox, String sessionId,
            GenerationWorkspaceService.WorkspaceSeed workspaceSeed) {
        return new GenerationOutcome(candidate.loopResult(), candidate.verification(), candidate.producedFiles(), candidate.problemStatement(), candidate.reviewReport(),
                candidate.specDocument(), candidate.testPlanJson());
    }

    GenerationOutcome stopOrPreserve(InteractiveSandbox sandbox, @Nullable String sessionId, @Nullable AuthoringStageCapture capture, AgentLoopResult cancelledResult) {
        GenerationOutcome diagnosticOutcome = captureUnexpectedFailure(capture);
        if (diagnosticOutcome != null) {
            return diagnosticOutcome;
        }
        return GenerationOutcome.cancelled(cancelledResult);
    }

    private @Nullable GenerationOutcome captureUnexpectedFailure(@Nullable AuthoringStageCapture capture) {
        // Null before the workspace was seeded: there is no session content to read back and nothing to fall back to either.
        return capture == null ? null
                : capture.partialOutcome(AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Generation stopped unexpectedly before verification completed."),
                        "Generation stopped before the candidate could be fully verified.");
    }

    Map<RepositoryRole, Map<String, String>> captureRepositoryFiles(InteractiveSandbox sandbox, String sessionId, GenerationWorkspaceService.WorkspaceSeed workspaceSeed,
            Map<String, String> placeholderReplacements) {
        Map<RepositoryRole, Map<String, String>> captured = new EnumMap<>(RepositoryRole.class);
        for (RepositoryRole type : List.of(RepositoryRole.SOLUTION, RepositoryRole.TEMPLATE, RepositoryRole.TESTS)) {
            GenerationWorkspaceService.RepositoryExtraction extraction = workspace.extractRepository(sandbox, sessionId, type,
                    workspaceSeed.repositoryMetadata().getOrDefault(type, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
            if (!extraction.extractionFailed()) {
                captured.put(type, replacePlaceholders(extraction, placeholderReplacements).files());
            }
        }
        return Map.copyOf(captured);
    }

    static Map<RepositoryRole, Map<String, String>> changedCapturedRepositoryFiles(Map<RepositoryRole, Map<String, String>> baselineFiles,
            Map<RepositoryRole, Map<String, String>> capturedFiles) {
        Map<RepositoryRole, Map<String, String>> changed = new EnumMap<>(RepositoryRole.class);
        capturedFiles.forEach((type, files) -> {
            if (!baselineFiles.getOrDefault(type, Map.of()).equals(files)) {
                changed.put(type, files);
            }
        });
        return Map.copyOf(changed);
    }

    static String renderReviewBrief(Mode mode, String runInstruction, @Nullable String startingProblemStatement) {
        if (startingProblemStatement == null || startingProblemStatement.isBlank()) {
            return runInstruction;
        }
        String instructionRole = mode == Mode.ADAPT ? "authoritative adaptation request" : "authoritative for requested changes";
        return "RUN INSTRUCTION (" + instructionRole + "):\n" + runInstruction
                + "\n\nSTARTING PROBLEM STATEMENT (preserve every requirement where the run instruction is silent):\n" + startingProblemStatement.strip();
    }

    /**
     * Renders the instructor's brief as the first user prompt. GENERATE frames it as the source requirements to design a minimal API from; ADAPT frames it as a change request
     * against an exercise that already exists, so it must not invite the agent to re-derive the API or shrink what the request does not mention.
     */
    static String renderAuthoringBrief(String sourceBrief, Mode mode) {
        if (mode == Mode.ADAPT) {
            return "ADAPTATION REQUEST (authoritative; everything this request does not mention is preserved as it is):\n" + sourceBrief
                    + "\n\nDo not add graded purity, immutability, thread-safety, exception, or architecture requirements unless the request explicitly asks for them. "
                    + "Keep the statement, starter, solution, tests, examples, and task bindings consistent.";
        }
        return "PRIMARY SOURCE REQUIREMENTS (authoritative; preserve every explicit requirement):\n" + sourceBrief
                + "\n\nChoose only the minimal API and behavior needed to implement these requirements. Do not add graded purity, immutability, thread-safety, exception, or architecture "
                + "requirements unless the source explicitly requests them. Keep the statement, starter, solution, tests, examples, and task bindings consistent.";
    }

    /** Matches an Artemis {@code [task][Title](testA,testB)} binding, capturing the comma-separated test-name list. */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task]\\[[^]]*]\\(([^)]*)\\)");

    static String renderAdaptationChanges(@Nullable String baselineProblemStatement, String producedProblemStatement, Map<RepositoryRole, Map<String, String>> baselineFiles,
            Map<RepositoryRole, Map<String, String>> producedFiles) {
        StringBuilder changes = new StringBuilder();
        appendChangedFile(changes, "problem-statement.md", baselineProblemStatement, producedProblemStatement);
        for (RepositoryRole type : List.of(RepositoryRole.SOLUTION, RepositoryRole.TEMPLATE, RepositoryRole.TESTS)) {
            Map<String, String> before = baselineFiles.getOrDefault(type, Map.of());
            Map<String, String> after = producedFiles.getOrDefault(type, Map.of());
            Set<String> paths = new TreeSet<>(before.keySet());
            paths.addAll(after.keySet());
            for (String path : paths) {
                appendChangedFile(changes, GenerationWorkspaceService.directoryFor(type) + "/" + path, before.get(path), after.get(path));
            }
        }
        return changes.toString();
    }

    static String renderGenerationRepairChanges(@Nullable String baselineProblemStatement, String producedProblemStatement, Map<RepositoryRole, Map<String, String>> baselineFiles,
            Map<RepositoryRole, Map<String, String>> producedFiles, @Nullable String baselineTestPlan, @Nullable String producedTestPlan) {
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

    private static Map<RepositoryRole, Map<String, String>> replacePlaceholdersByRepository(Map<RepositoryRole, Map<String, String>> filesByRepository,
            Map<String, String> replacements) {
        Map<RepositoryRole, Map<String, String>> normalized = new EnumMap<>(RepositoryRole.class);
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

    GenerationWorkspaceService workspace() {
        return workspace;
    }

    private Optional<String> checkBuildEnvironment(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise) {
        try {
            workspace.stageBuildReadinessFixture(sandbox, sessionId, exercise);
            return verifier.checkBuildEnvironment(sandbox, sessionId, exercise);
        }
        catch (RuntimeException exception) {
            log.warn("Could not prepare the sandbox build-environment readiness probe for exercise {} ({}): {}", exercise.id(), exception.getClass().getSimpleName(),
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
            SandboxExecResult result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/" + fileName);
            return result != null && result.isSuccess() ? result.stdout() : null;
        }
        catch (RuntimeException e) {
            log.debug("Could not read {} after generation for diagnostics: {}", fileName, e.getMessage());
            return null;
        }
    }

    /** Opens the single teardown gate for a freshly created session, so cancel hook, terminal close, and error paths all destroy it through the same route. */
    private SandboxSessionLifecycle registerSessionLifecycle(InteractiveSandbox sandbox, String sessionId) {
        SandboxSessionLifecycle lifecycle = new SandboxSessionLifecycle(sessionId, () -> destroySessionNow(sandbox, sessionId));
        sessionLifecycles.put(sessionId, lifecycle);
        if (cancellationRequested.get()) {
            lifecycle.requestDestroy();
        }
        return lifecycle;
    }

    public void requestCancel() {
        cancellationRequested.set(true);
        sessionLifecycles.values().forEach(SandboxSessionLifecycle::requestDestroy);
    }

    void destroyQuietly(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
        SandboxSessionLifecycle lifecycle = sessionId == null ? null : sessionLifecycles.get(sessionId);
        if (lifecycle != null) {
            // Destroys now, or as soon as an in-flight capture releases the session. Never twice, however many routes ask.
            lifecycle.requestDestroy();
            // Only the run's finally block removes the gate, after every capture path has finished. Cancellation leaves it registered until then.
            sessionLifecycles.remove(sessionId, lifecycle);
            return;
        }
        destroySessionNow(sandbox, sessionId);
    }

    private void destroySessionNow(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
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
