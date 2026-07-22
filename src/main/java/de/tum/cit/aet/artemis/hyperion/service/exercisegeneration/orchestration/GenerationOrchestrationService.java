package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.FileChangeEmittingAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationRequest;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
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
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrationService.class);

    private static final long VERIFY_WORKSPACE_MAX_FILE_BYTES = 30L * 1024 * 1024;

    private static final long VERIFY_WORKSPACE_MAX_TOTAL_BYTES = 30L * 1024 * 1024;

    private static final int MAX_ADAPTATION_CHANGE_CHARS = 24_000;

    private static final String CHANGE_SUMMARY_TRUNCATED = "\n... [change summary truncated]\n";

    /**
     * Hard cap on agent turns per attempt ({@code artemis.hyperion.agent.max-turns}); generous so slow multi-file languages finish in one attempt, still bounded against runaways.
     */
    private final int maxTurns;

    /** Initial candidate plus at most three mechanical repairs. */
    private static final int MAX_MECHANICAL_ATTEMPTS = 4;

    // Once a candidate passes mechanical verification, the full-artifact review must still have room to improve it. The two extra slots are one transactional semantic repair
    // and, only if that repair breaks the build, one narrow mechanical correction. They are not additional open-ended attempts at the initial mechanical phase.
    private static final int MAX_GENERATION_ATTEMPTS = MAX_MECHANICAL_ATTEMPTS + 2;

    /**
     * Total wall-clock ceiling for one generation run across ALL attempts. The staged first attempt has its own 22-minute guard (see StagedGenerationRunner), but repair
     * attempts previously had none — a run's time was unbounded exactly where, empirically, most of it is spent. Checked before starting each repair attempt; the current
     * candidate and verification state proceed to the normal outcome path.
     */
    private static final Duration TOTAL_WALL_CLOCK_BUDGET = Duration.ofMinutes(35);

    /** Best-effort read timeout for capturing {@code SPEC.md} once after the agent loop finishes, for {@link GenerationOutcome#specDocument()}. */
    private static final Duration SPEC_DOCUMENT_READ_TIMEOUT = Duration.ofSeconds(30);

    // Optional so a core-only node (where no build agent is co-located to host the sandbox) still starts; absence is reported only when a run is attempted.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final DifferentialVerificationService verifier;

    private final AgentSystemPromptService systemPromptService;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // Reviews brief coverage and, for adaptations, requests repair when unrelated changes cannot be ruled out.
    private final SpecFidelityCriticService specFidelityCritic;

    // Used to register a node-local cancel hook that destroys the sandbox session, so a cancellation during a long build interrupts promptly rather than at the next between-turn
    // poll.
    private final GenerationJobService jobService;

    // Source of the pre-adapt graded test names (the adapt total-wipe gate's baseline). Optional because it is a core-profile repository, absent on a build-agent-only node; when
    // absent the baseline is empty and the total-wipe gate stays inert (fail-open), consistent with every other doubt-on-read-back gate.
    private final Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository;

    // Runs one attempt as five enforced, gated stages (design/solution/template/tests/statement) instead of one open-ended agent-loop call. Gated by stagedGenerationEnabled
    // so it applies only where its Java-only, single-language contract holds (see the applicability check at the call site).
    private final StagedGenerationRunner stagedGenerationRunner;

    /** The per-session specification the spec gate approved; dropped when the session is destroyed so the registry never outlives its runs. */
    private final ApprovedSpecRegistry approvedSpecs;

    private final AgentTranscriptWriter transcriptWriter;

    private final boolean stagedGenerationEnabled;

    // Wired into SandboxAgentTools so a staged session's verify/submit tools can dispatch to the current stage's mechanical check; unused by an unstaged (legacy) session, which
    // never calls SandboxAgentTools#enterStage.
    private final StageCheckService stageCheckService;

    public GenerationOrchestrationService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            DifferentialVerificationService verifier, AgentSystemPromptService systemPromptService, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService, Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository,
            @Value("${artemis.hyperion.agent.max-turns:60}") int maxTurns, StagedGenerationRunner stagedGenerationRunner,
            @Value("${artemis.hyperion.agent.staged-generation:true}") boolean stagedGenerationEnabled, StageCheckService stageCheckService, AgentTranscriptWriter transcriptWriter,
            ApprovedSpecRegistry approvedSpecs) {
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-turns must be positive");
        }
        this.maxTurns = maxTurns;
        this.interactiveSandbox = interactiveSandbox;
        this.workspace = workspace;
        this.agentLoopRunner = agentLoopRunner;
        this.verifier = verifier;
        this.systemPromptService = systemPromptService;
        this.structuralOracleSeeder = structuralOracleSeeder;
        this.specFidelityCritic = specFidelityCritic;
        this.jobService = jobService;
        this.testCaseRepository = testCaseRepository;
        this.stagedGenerationRunner = stagedGenerationRunner;
        this.stagedGenerationEnabled = stagedGenerationEnabled;
        this.stageCheckService = stageCheckService;
        this.transcriptWriter = transcriptWriter;
        this.approvedSpecs = approvedSpecs;
    }

    private InteractiveSandbox requireSandbox() {
        return interactiveSandbox.orElseThrow(
                () -> new IllegalStateException("No interactive sandbox is available on this node. Agentic exercise generation requires either a co-located build agent or a "
                        + "reachable build agent in the cluster to host the sandbox container."));
    }

    /**
     * Runs one generation/adaptation session, streaming a file change to {@code fileChangeSink} on every successful {@code write_file}/{@code edit_file} so the
     * triggering
     * instructor's editor can show which files the agent changes.
     *
     * @param exercise       the exercise to generate or adapt (its repositories must already be scaffolded)
     * @param user           the instructor performing the generation, recorded with the LLM token-usage trace
     * @param userPrompt     the instruction for this run (a generation brief, or the feedback to address)
     * @param jobId          the job id, used to register a node-local cancel hook
     * @param mode           the explicit run intent (generate vs. adapt)
     * @param cancelled      polled cooperatively; if it returns {@code true} the session is aborted
     * @param progress       receives short human-readable progress lines for the live transcript; may be {@code null}
     * @param fileChangeSink receives a file change on every successful write for live streaming; {@code null} disables file-change streaming
     * @param usageSink      receives token usage for every model call; {@code null} uses the default persisted run sink
     * @return the outcome including the verification verdict and the produced files
     */
    public GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled,
            Consumer<String> progress, @Nullable Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink, @Nullable Consumer<ChatResponse> usageSink) {
        return generate(exercise, user, userPrompt, jobId, mode, cancelled, progress, fileChangeSink, usageSink, null);
    }

    GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled, Consumer<String> progress,
            @Nullable Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink, @Nullable Consumer<ChatResponse> usageSink, @Nullable String originalSourceBrief) {
        // Snapshot the pre-adapt graded test names so the verifier can reject a destructive total wipe (an adapt that retains none of them = a from-scratch regeneration mislabeled
        // as an adapt). Empty for GENERATE, which leaves the total-wipe gate inert.
        Set<String> baselineGradedTestNames = mode == GenerationMode.ADAPT ? captureBaselineGradedTestNames(exercise) : Set.of();
        String baselineProblemStatement = exercise.getProblemStatement();
        // The client seeds every new exercise with the default template readme; only a REAL instructor statement may steer the brief, the workspace seed, or skip the SPEC
        // stage — otherwise the classic sorting readme becomes "requirements to preserve" everywhere at once (observed live: bubble sort generated from a non-sorting brief).
        boolean generatedFromSourceBrief = mode == GenerationMode.GENERATE && originalSourceBrief != null && !originalSourceBrief.isBlank();
        boolean statementAuthoritative = mode == GenerationMode.ADAPT || !generatedFromSourceBrief && systemPromptService.isAuthoritativeProblemStatement(exercise);
        String sourceBrief = generatedFromSourceBrief ? originalSourceBrief.strip() : renderReviewBrief(mode, userPrompt, statementAuthoritative ? baselineProblemStatement : null);
        Long courseId = courseIdOf(exercise);
        Consumer<ChatResponse> effectiveUsageSink = usageSink != null ? usageSink : jobService.tokenUsageSink(courseId, exercise.getId(), user.getId());
        InteractiveSandbox sandbox = requireSandbox();
        String sessionId = null;
        GenerationWorkspaceService.WorkspaceSeed workspaceSeed = null;
        Map<String, String> placeholderReplacements = Map.of();
        Map<RepositoryType, Map<String, String>> baselineRepositoryFiles = Map.of();
        CandidateSnapshot lastMechanicallyVerifiedCandidate = null;
        ExtractedCandidate lastExtractedCandidate = null;
        SandboxSessionSpec sessionSpec = workspace.sessionSpec(exercise,
                new SandboxSessionContext(jobId, exercise.getId(), exercise.getTitle(), courseId, user.getLogin(), mode.name()));
        try {
            if (cancelled.getAsBoolean()) {
                return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
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
            baselineRepositoryFiles = replacePlaceholdersByRepository(workspaceSeed.repositoryTextFiles(), placeholderReplacements);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }

            emit(progress, "Checking the build environment");
            Optional<String> buildEnvironmentFailure = checkBuildEnvironment(sandbox, sessionId, exercise);
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }
            if (buildEnvironmentFailure.isPresent()) {
                destroyQuietly(sandbox, sessionId);
                return GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 0, ""), buildEnvironmentFailure.get());
            }
            String reviewBrief = sourceBrief;
            String authoringBrief = renderAuthoringBrief(sourceBrief);
            if (generatedFromSourceBrief && baselineProblemStatement != null && !baselineProblemStatement.isBlank()) {
                authoringBrief += "\n\nCURRENT AI-GENERATED DRAFT (non-authoritative context; it may help with presentation, but it cannot override or omit the primary source requirements):\n"
                        + baselineProblemStatement.strip();
            }

            String systemPrompt = systemPromptService.build(exercise, mode);
            // The agent's `verify` tool runs the same differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); the post-loop
            // verify(...) below stays the mechanical-verification decision.
            SandboxAgentTools baseTools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise, testsSeedSnapshot, mode == GenerationMode.ADAPT, stageCheckService);
            // Wrap the tools in the file-change decorator when a sink is supplied, so each successful write/edit/delete emits a lightweight notification (path, repository bucket,
            // action, turn) for the instructor's live activity view — not the file content itself. The decorator re-exposes the same @Tool surface (the model sees the same tools)
            // and only adds emission.
            Object tools = fileChangeSink != null ? new FileChangeEmittingAgentTools(baseTools, fileChangeSink) : baseTools;

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (empty probe leaves the prompt unchanged) and first-attempt only — retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), authoringBrief);

            // On mechanical rejection, feed the verifier's reasons back and retry up to a small bound. The verifier enforces rules the agent's own verify.sh cannot show (template
            // must fail a
            // meaningful fraction; problem statement must bind tasks), so this loop turns a "builds but not quite right" first attempt into a mechanically verified candidate that
            // persistence saves for instructor review (see GenerationPersistenceService) rather than an auto-published exercise.
            // Java/GENERATE is the only contract StagedGenerationRunner supports today (see its javadoc); ADAPT and every other language keep the original single, open-ended
            // agent-loop call unchanged. Decided once per run — the mode and language do not change across repair attempts.
            boolean useStagedGeneration = stagedGenerationEnabled && mode == GenerationMode.GENERATE && exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA;
            // The SPEC stage runs only when the instructor gave no real statement: an existing non-trivial statement IS the specification, and writing a competing SPEC.md
            // would at best duplicate it and at worst drift from it (the product's draft flow is how instructors control specs).
            boolean specStageApplies = !statementAuthoritative;
            // The gate-approved SPEC.md snapshot, frozen by the runner's spec gate: instructor-visible immediately, fed to the critic's grounding, and appended to every repair
            // prompt so scope-cutting under repair pressure faces the contract it is cutting.
            AtomicReference<String> specSnapshot = new AtomicReference<>();

            String currentPrompt = firstPrompt;
            AgentLoopResult loopResult = null;
            // One logical conversation spans the whole run: the staged first attempt hands its conversation out, and every repair attempt continues it (with compaction) instead
            // of starting blind and re-reading the workspace it just produced. Stays null when the first attempt ran FRESH staged context or the legacy path produced none.
            List<Message> carriedConversation = null;
            int totalAgentTurns = 0;
            Instant runStartedAt = Instant.now();
            VerificationResult verification = null;
            // The final attempt's produced files and problem statement ride the outcome so persist reuses them instead of re-reading the sandbox (verification already extracted
            // them for the integrity gates). Overwritten each attempt so the outcome carries the last (accepted or exhausted) attempt's tree.
            Map<RepositoryType, Map<String, String>> producedFilesByType = new EnumMap<>(RepositoryType.class);
            String producedProblemStatement = "";
            // Recomputed each attempt; the final attempt's report rides the outcome.
            SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();
            @Nullable
            VerificationRequest lastRejectedVerificationRequest = null;
            boolean semanticRepairAttempted = false;
            int semanticMechanicalCorrectionsRemaining = 1;
            int initialMechanicalAttempts = 0;
            @Nullable
            CandidateSnapshot preSemanticRepairCandidate = null;
            for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
                if (attempt > 1 && Duration.between(runStartedAt, Instant.now()).compareTo(TOTAL_WALL_CLOCK_BUDGET) > 0) {
                    emit(progress, "The generation time budget is used up; keeping the current candidate instead of starting repair attempt " + attempt + ".");
                    break;
                }
                // Staged authoring applies to the FIRST attempt only: retry attempts carry a targeted repair prompt (verification reasons / critic findings) plus the frozen
                // spec contract — re-running the spec stage against a repair brief fails its gate by construction. Repairs run the legacy single loop, which is built for
                // surgical fixes on an existing workspace.
                boolean stagedAttempt = useStagedGeneration && attempt == 1;
                if (stagedAttempt) {
                    StagedGenerationRunner.StagedRunOutcome stagedOutcome = stagedGenerationRunner.run(exercise, baseTools, tools, currentPrompt, testsSeedSnapshot, sandbox,
                            activeSessionId, cancelled, effectiveUsageSink, progress, () -> structuralOracleSeeder.seedIfStructuralDiff(sandbox, activeSessionId, exercise),
                            specStageApplies, spec -> {
                                specSnapshot.set(spec);
                                jobService.recordSpecDocument(exercise.getId(), jobId, spec);
                            });
                    loopResult = stagedOutcome.result();
                    carriedConversation = stagedOutcome.conversation();
                }
                else {
                    AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runSession(systemPrompt, carriedConversation, currentPrompt, tools, maxTurns, cancelled,
                            effectiveUsageSink, progress);
                    loopResult = session.result();
                    carriedConversation = session.conversation();
                    transcriptWriter.write(exercise.getId(),
                            "attempt-" + attempt + (attempt == 1 ? "-single-loop-" : "-repair-") + loopResult.status().name().toLowerCase(Locale.ROOT), carriedConversation);
                }
                totalAgentTurns += loopResult.turns();
                if (stagedAttempt) {
                    // Unstage the shared tools instance again: a later repair attempt (below) reuses it through the legacy single-loop path, which must see currentStage back at
                    // null rather than left at whichever stage the staged run last entered (STAGE_CHECK dispatch would otherwise leak into the repair loop's verify/submit).
                    baseTools.exitStagedGeneration();
                }
                log.info("Exercise generation attempt {} took {} turn(s); {} turn(s) total so far", attempt, loopResult.turns(), totalAgentTurns);

                if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement, loopResult);
                }
                if (loopResult.status() == AgentLoopResult.Status.ERROR) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    Map<RepositoryType, Map<String, String>> erroredFiles = changedCapturedRepositoryFiles(baselineRepositoryFiles,
                            captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
                    String erroredStatement = workspace.extractProblemStatement(sandbox, sessionId).trim();
                    boolean statementChanged = !java.util.Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), erroredStatement);
                    if (statementChanged || !erroredFiles.isEmpty()) {
                        return new GenerationOutcome(loopResult, null, sessionId, this, sandbox, erroredFiles, erroredStatement,
                                SpecFidelityReport.qualityReviewUnavailable("The agent stopped before verification; the partial candidate requires manual review."),
                                workspaceSeed.repositoryHeads(), readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
                    }
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.error(loopResult);
                }
                // The loop only polls cancellation between turns; honour a cancel that arrived during the last turn before spending minutes on the verification build.
                if (cancelled.getAsBoolean()) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }

                // Seed Java structural tests when the produced solution/template structures differ. Their authoritative names can resolve task bindings, but the verifier still
                // requires every seeded grading check to appear in the student checklist; a first-attempt omission is returned to the agent for repair.
                Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);
                if (cancelled.getAsBoolean()) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }

                emit(progress, "Checking the exercise builds and grades (attempt " + attempt + " of " + MAX_GENERATION_ATTEMPTS + ")");
                workspace.cleanTransientBuildOutputs(sandbox, sessionId);
                // Read the produced repos back for the sandbox-free integrity gates (harness immutability vs the seed snapshot, solution-leak across template/solution). The
                // extraction-failed flag lets the verifier fail closed on a read-back error, distinct from an empty repo.
                GenerationWorkspaceService.RepositoryExtraction producedTests = workspace.extractRepository(sandbox, sessionId, RepositoryType.TESTS,
                        workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.TESTS, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
                GenerationWorkspaceService.RepositoryExtraction producedTemplate = workspace.extractRepository(sandbox, sessionId, RepositoryType.TEMPLATE,
                        workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.TEMPLATE, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
                GenerationWorkspaceService.RepositoryExtraction producedSolution = workspace.extractRepository(sandbox, sessionId, RepositoryType.SOLUTION,
                        workspaceSeed.repositoryMetadata().getOrDefault(RepositoryType.SOLUTION, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
                producedTests = replacePlaceholders(producedTests, placeholderReplacements);
                producedTemplate = replacePlaceholders(producedTemplate, placeholderReplacements);
                producedSolution = replacePlaceholders(producedSolution, placeholderReplacements);
                // Capture this attempt's extraction so persist reuses it — the same full-repo read verification needs for the integrity gates, not a second sandbox round-trip.
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
                // Capture the grading plan with the repositories and statement. Final verification and persistence must decide on the same plan, not independently re-read a
                // mutable workspace after the build.
                String testPlanSnapshot = readWorkspaceRootFile(sandbox, sessionId, "test-plan.json");
                VerificationRequest verificationRequest = new VerificationRequest(testsSeedSnapshot, baselineRepositoryFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()),
                        baselineRepositoryFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), candidateFiles.getOrDefault(RepositoryType.TESTS, Map.of()),
                        candidateFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()), candidateFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()),
                        Set.copyOf(extractionFailed), Set.copyOf(seededStructuralTestNames), baselineGradedTestNames, producedProblemStatement, testPlanSnapshot,
                        mode == GenerationMode.ADAPT);
                if (cancelled.getAsBoolean()) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }
                if (lastRejectedVerificationRequest != null && lastRejectedVerificationRequest.equals(verificationRequest)) {
                    emit(progress, "The agent resubmitted the unchanged rejected candidate; stopping without repeating the same verification.");
                    break;
                }
                // The authoritative pass re-seeds its pristine script, discards old reports, and builds from fresh temporary directories before parsing the result independently.
                InteractiveSandbox activeSandbox = sandbox;
                GenerationWorkspaceService.WorkspaceSeed activeWorkspaceSeed = workspaceSeed;
                String candidateProblemStatement = producedProblemStatement;
                // Snapshot SPEC.md before verification: each restore below resets the tmpfs workspace, and without re-seeding it the specification (possibly updated by later
                // stages) would be silently gone for every later repair attempt and for the outcome's spec capture.
                String specDocumentSnapshot = readSpecDocument(sandbox, sessionId);
                // Same reason as the spec: the reset wipes test-plan.json, and a repair attempt would then save the exercise with Artemis' default grading instead of the
                // weights and hidden tests the TESTS stage decided (observed live: the plan was written and gate-approved, then lost before persistence).
                Runnable restoreCandidate = () -> {
                    activeSandbox.resetSession(activeSessionId);
                    // /workspace is a tmpfs (see GenerationWorkspaceService#materializeRepositoryFiles), so the reset wipes problem-statement.md too; re-seed it alongside the
                    // repositories or the next attempt's extraction fails with "the generated problem statement is missing".
                    workspace.materializeRepositoryFiles(activeSandbox, activeSessionId, exercise, mode, candidateFiles, activeWorkspaceSeed.repositoryMetadata(),
                            activeWorkspaceSeed.repositoryBinaryFiles(), candidateProblemStatement, specDocumentSnapshot, testPlanSnapshot);
                };
                verification = verifyWithInfrastructureRetry(sandbox, sessionId, exercise, verificationRequest, restoreCandidate, cancelled, progress);
                emit(progress, verification.report());
                if (!verification.mechanicallyVerified() && extractionFailed.isEmpty()) {
                    lastRejectedVerificationRequest = verificationRequest;
                }
                if (verification.mechanicallyVerified()) {
                    lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                            SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the mechanically verified candidate received its full-artifact review."),
                            specDocumentSnapshot, testPlanSnapshot);
                }
                if (cancelled.getAsBoolean()) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }

                // Run the expensive semantic review only after the deterministic mechanical gate passes. Reviewing a candidate that cannot build or grade wastes provider quota and
                // produces findings against artifacts the next attempt must replace anyway.
                if (verification.mechanicallyVerified()) {
                    @Nullable
                    String adaptationChanges = mode == GenerationMode.ADAPT
                            ? renderAdaptationChanges(baselineProblemStatement, producedProblemStatement, baselineRepositoryFiles, producedFilesByType)
                            : null;
                    // specFidelityReport still holds the previous attempt's report at this point (SpecFidelityReport.empty() on attempt 1 or after a mechanical rejection);
                    // threading it through gives the critic continuity across repair attempts instead of re-rolling a fresh review each time.
                    specFidelityReport = runSpecFidelityCritic(reviewBrief, producedProblemStatement, exercise.getProgrammingLanguage(), producedFilesByType, adaptationChanges,
                            effectiveUsageSink, cancelled, progress, specFidelityReport, effectiveSpecReviewContext(specSnapshot.get(), specDocumentSnapshot));
                    lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                            specFidelityReport, specDocumentSnapshot, testPlanSnapshot);
                    if (cancelled.getAsBoolean()) {
                        return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                    }
                }
                else {
                    specFidelityReport = SpecFidelityReport.empty();
                }

                if (verification.mechanicallyVerified() && !specFidelityReport.hasBlockingFindings()) {
                    break;
                }
                if (attempt == MAX_GENERATION_ATTEMPTS) {
                    break;
                }
                if (!verification.mechanicallyVerified() && !semanticRepairAttempted && ++initialMechanicalAttempts >= MAX_MECHANICAL_ATTEMPTS) {
                    emit(progress, "The bounded mechanical repair phase is exhausted; keeping the current candidate for instructor review.");
                    break;
                }
                if (verification.mechanicallyVerified()) {
                    if (semanticRepairAttempted) {
                        emit(progress,
                                "The bounded semantic repair still has review blockers; rolling back the whole repair transaction instead of keeping a partially successful or regressed edit.");
                        return preserveCandidate(java.util.Objects.requireNonNull(preSemanticRepairCandidate), sandbox, sessionId, workspaceSeed);
                    }
                    boolean reviewUnavailable = specFidelityReport.findings().stream()
                            .anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE
                                    || finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
                    boolean hasActionableReviewFinding = specFidelityReport.findings().stream()
                            .anyMatch(finding -> finding.isBlocking() && finding.kind() != SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE
                                    && finding.kind() != SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
                    if (reviewUnavailable && !hasActionableReviewFinding) {
                        break;
                    }
                    preSemanticRepairCandidate = lastMechanicallyVerifiedCandidate;
                    semanticRepairAttempted = true;
                    emit(progress, "Mechanical verification passed, but the exercise review found requirements or quality issues; asking the AI to correct them.");
                    String scopeGuidance = mode == GenerationMode.ADAPT ? " Preserve all content outside the requested adaptation." : "";
                    currentPrompt = attemptFraming(attempt) + "Your previous attempt passed mechanical verification, but the automated full-artifact review found review blockers."
                            + scopeGuidance
                            + " Preserve the mechanically correct work: do not restart or rewrite unrelated files. Re-read the cited artifacts and repair them to match the source requirements and "
                            + "remove unsupported candidate choices identified by the review. Make the smallest coherent repair across the statement, solution, template, and tests. Keep every unaffected "
                            + "requirement, API, test, and example. Re-run `sh verify.sh solution` and `sh verify.sh template`, then call submit again.\n\nThe instructor "
                            + "source requirements are:\n" + authoringBrief + specContractSection(specSnapshot.get()) + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
                    continue;
                }
                if (semanticRepairAttempted && semanticMechanicalCorrectionsRemaining == 0) {
                    emit(progress, "The one mechanical correction after semantic repair was not enough; preserving the last mechanically verified candidate instead of starting "
                            + "another open-ended repair cycle.");
                    break;
                }
                if (semanticRepairAttempted) {
                    semanticMechanicalCorrectionsRemaining--;
                    emit(progress, "The semantic repair broke mechanical verification; allowing one narrow mechanical correction without reopening semantic scope.");
                }
                emit(progress, "Verification rejected the exercise; asking the agent to fix the issues and try again.");
                // The hard rejection (must fix) plus the advisory findings, the latter framed so the rejection is prioritised.
                currentPrompt = attemptFraming(attempt) + "Your previous attempt was rejected by the differential verifier:\n" + verification.report()
                        + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, re-run `sh verify.sh solution` and "
                        + "`sh verify.sh template` to confirm, then call submit again. If a reason names a forbidden, duplicate, or abandoned path, delete it; replacing it with a "
                        + "placeholder does not remove the violation. Make the smallest coherent repair, leave unrelated files unchanged, and preserve the source requirements below.\n\n"
                        + authoringBrief + specContractSection(specSnapshot.get()) + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
            }

            // A semantic repair can accidentally break a candidate that already built and graded correctly. Never discard that more useful checkpoint in favour of a later
            // mechanically broken tree; return the last buildable candidate and its unresolved review findings.
            if ((verification == null || !verification.mechanicallyVerified()) && lastMechanicallyVerifiedCandidate != null) {
                emit(progress, "The run used " + totalAgentTurns + " agent turn(s) in total.");
                return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
            }

            emit(progress, "The run used " + totalAgentTurns + " agent turn(s) in total.");
            return new GenerationOutcome(loopResult, verification, sessionId, this, sandbox, producedFilesByType, producedProblemStatement, specFidelityReport,
                    workspaceSeed.repositoryHeads(), readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
        }
        catch (RuntimeException e) {
            // A build interrupted by the cancel hook surfaces as a throw; report it as a clean cancellation.
            if (cancelled.getAsBoolean()) {
                if (lastMechanicallyVerifiedCandidate != null && workspaceSeed != null) {
                    return preserveCandidate(lastMechanicallyVerifiedCandidate, sandbox, sessionId, workspaceSeed);
                }
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }
            if (lastMechanicallyVerifiedCandidate != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while repairing a mechanically verified candidate for exercise {}; preserving the verified checkpoint ({})", exercise.getId(),
                        e.getClass().getSimpleName());
                return new GenerationOutcome(lastMechanicallyVerifiedCandidate.loopResult(), lastMechanicallyVerifiedCandidate.verification(), sessionId, this, sandbox,
                        lastMechanicallyVerifiedCandidate.producedFiles(), lastMechanicallyVerifiedCandidate.problemStatement(), lastMechanicallyVerifiedCandidate.reviewReport(),
                        workspaceSeed.repositoryHeads(), lastMechanicallyVerifiedCandidate.specDocument(), lastMechanicallyVerifiedCandidate.testPlanJson());
            }
            if (lastExtractedCandidate != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while verifying an extracted candidate for exercise {}; preserving the captured work ({})", exercise.getId(),
                        e.getClass().getSimpleName());
                AgentLoopResult stopped = new AgentLoopResult(AgentLoopResult.Status.ERROR, lastExtractedCandidate.loopResult().turns(),
                        "Generation stopped before verification completed.");
                return new GenerationOutcome(stopped, null, sessionId, this, sandbox, lastExtractedCandidate.producedFiles(), lastExtractedCandidate.problemStatement(),
                        SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the captured candidate could be fully verified."), workspaceSeed.repositoryHeads(),
                        readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
            }
            GenerationOutcome diagnosticError = captureUnexpectedFailure(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles,
                    baselineProblemStatement);
            if (diagnosticError != null) {
                log.warn("Exercise generation failed after producing diagnostic artifacts for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
                return diagnosticError;
            }
            // No usable outcome exists for the caller to close.
            destroyQuietly(sandbox, sessionId);
            log.error("Exercise generation failed for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
            throw e;
        }
        finally {
            jobService.deregisterCancelHook(jobId);
        }
    }

    private record CandidateSnapshot(AgentLoopResult loopResult, VerificationResult verification, Map<RepositoryType, Map<String, String>> producedFiles, String problemStatement,
            SpecFidelityReport reviewReport, String specDocument, String testPlanJson) {
    }

    private record ExtractedCandidate(AgentLoopResult loopResult, Map<RepositoryType, Map<String, String>> producedFiles, String problemStatement) {
    }

    /**
     * Frames a repair prompt with where the run stands, so the model can triage: on the final attempt it must prioritise blocking findings instead of treating every attempt
     * identically (previously it had no signal that this was its last chance).
     */
    /**
     * The frozen, gate-approved specification appended to every repair prompt, so a repair under verification pressure faces the behavioural contract it might otherwise
     * silently cut. Empty when no spec was captured (skipped stage or legacy path).
     */
    private static String specContractSection(@Nullable String specSnapshot) {
        if (specSnapshot == null || specSnapshot.isBlank()) {
            return "";
        }
        return "\n\nTHE SPECIFICATION (frozen at the spec gate — the behavioural contract; do not change rules or worked examples unless a verification reason requires it):\n"
                + specSnapshot.strip();
    }

    /**
     * Gives the semantic critic the same monotonic contract the mechanical gates enforce: approved decisions remain binding, while a later clarification may add work. Showing
     * only the live copy let downgrades erase evidence; showing only the approved copy hid legitimate additions discovered during implementation.
     */
    private static String effectiveSpecReviewContext(@Nullable String approvedSpec, @Nullable String liveSpec) {
        String approved = approvedSpec == null ? "" : approvedSpec.strip();
        String live = liveSpec == null ? "" : liveSpec.strip();
        if (approved.isEmpty()) {
            return live;
        }
        if (live.isEmpty() || live.equals(approved)) {
            return approved;
        }
        return "APPROVED SNAPSHOT (all decisions remain binding):\n" + approved
                + "\n\nCURRENT SPECIFICATION (later clarifications may add obligations; any weaker conflicting text does not remove the approved obligation):\n" + live;
    }

    private static String attemptFraming(int attempt) {
        int repairAttempt = attempt;   // the prompt built after attempt N drives attempt N+1
        boolean finalAttempt = repairAttempt + 1 >= MAX_GENERATION_ATTEMPTS;
        return "Repair attempt " + (repairAttempt + 1) + " of " + MAX_GENERATION_ATTEMPTS
                + (finalAttempt ? " — this is the FINAL attempt; prioritise the blocking findings (especially any repeated from earlier reviews) over cosmetic ones. " : ". ");
    }

    private GenerationOutcome preserveCandidate(CandidateSnapshot candidate, InteractiveSandbox sandbox, String sessionId, GenerationWorkspaceService.WorkspaceSeed workspaceSeed) {
        return new GenerationOutcome(candidate.loopResult(), candidate.verification(), sessionId, this, sandbox, candidate.producedFiles(), candidate.problemStatement(),
                candidate.reviewReport(), workspaceSeed.repositoryHeads(), candidate.specDocument(), candidate.testPlanJson());
    }

    private static Map<RepositoryType, Map<String, String>> copyProducedFiles(Map<RepositoryType, Map<String, String>> producedFiles) {
        Map<RepositoryType, Map<String, String>> copy = new EnumMap<>(RepositoryType.class);
        producedFiles.forEach((type, files) -> copy.put(type, Map.copyOf(files)));
        return Map.copyOf(copy);
    }

    private GenerationOutcome stopOrPreserve(InteractiveSandbox sandbox, @Nullable String sessionId, GenerationWorkspaceService.@Nullable WorkspaceSeed workspaceSeed,
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
        boolean statementChanged = !java.util.Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), statement);
        if (!statementChanged && files.isEmpty()) {
            return null;
        }
        AgentLoopResult loopResult = new AgentLoopResult(AgentLoopResult.Status.ERROR, 0, "Generation stopped unexpectedly before verification completed.");
        return new GenerationOutcome(loopResult, null, sessionId, this, sandbox, files, statement,
                SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the candidate could be fully verified."), workspaceSeed.repositoryHeads(),
                readSpecDocument(sandbox, sessionId), readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
    }

    private Map<RepositoryType, Map<String, String>> captureRepositoryFiles(InteractiveSandbox sandbox, String sessionId, GenerationWorkspaceService.WorkspaceSeed workspaceSeed,
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

    private static boolean hasProducedChanges(Map<RepositoryType, Map<String, String>> baselineFiles, Map<RepositoryType, Map<String, String>> producedFiles,
            @Nullable String baselineProblemStatement, String producedProblemStatement) {
        if (!java.util.Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), producedProblemStatement)) {
            return true;
        }
        return List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS).stream()
                .anyMatch(type -> !baselineFiles.getOrDefault(type, Map.of()).equals(producedFiles.getOrDefault(type, Map.of())));
    }

    private static Map<RepositoryType, Map<String, String>> changedCapturedRepositoryFiles(Map<RepositoryType, Map<String, String>> baselineFiles,
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

    /** Records the repository's directory as extraction-failed so the verifier can fail CLOSED on a read-back error (distinct from a genuinely empty repo). */
    private static void addIfExtractionFailed(Set<String> extractionFailed, GenerationWorkspaceService.RepositoryExtraction extraction, RepositoryType type) {
        if (extraction.extractionFailed()) {
            extractionFailed.add(GenerationWorkspaceService.directoryFor(type));
        }
    }

    /** Matches an Artemis {@code [task][Title](testA,testB)} binding, capturing the comma-separated test-name list. */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task]\\[[^]]*]\\(([^)]*)\\)");

    /**
     * Reviews spec fidelity and adaptation scope without allowing reviewer failures to escape the orchestration boundary.
     *
     * @param brief             the instructor brief for this run
     * @param problemStatement  the produced student-facing problem statement
     * @param language          the exercise language (may be {@code null})
     * @param producedArtifacts the mechanically verified solution, template, and tests repositories
     * @param progress          the progress sink for a short transcript line
     * @param previousReport    the immediately preceding attempt's report, threaded in for reviewer continuity; {@code null} on the first attempt
     * @return the report (possibly empty); never {@code null}
     */
    private SpecFidelityReport runSpecFidelityCritic(String brief, String problemStatement, @Nullable ProgrammingLanguage language,
            Map<RepositoryType, Map<String, String>> producedArtifacts, @Nullable String adaptationChanges, Consumer<ChatResponse> usageSink, BooleanSupplier cancelled,
            Consumer<String> progress, @Nullable SpecFidelityReport previousReport, @Nullable String specSnapshot) {
        try {
            List<String> testNames = extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = adaptationChanges == null
                    ? specFidelityCritic.critique(brief, problemStatement, testNames, producedArtifacts, usageSink, cancelled, previousReport, specSnapshot)
                    : specFidelityCritic.critiqueAdaptation(brief, problemStatement, testNames, adaptationChanges, producedArtifacts, usageSink, cancelled, previousReport);
            if (adaptationChanges != null && adaptationChanges.contains(CHANGE_SUMMARY_TRUNCATED)) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(SpecFidelityReport.adaptationScopeUnavailable("The bounded change summary was truncated, so not every changed line could be reviewed.").findings());
                report = new SpecFidelityReport(List.copyOf(combined));
            }
            // Messageless assertions remain advisory and share the same retry/review channel.
            List<SpecFidelityReport.Finding> messageless = specFidelityCritic.detectMessagelessAssertions(language, producedArtifacts.getOrDefault(RepositoryType.TESTS, Map.of()));
            if (!messageless.isEmpty()) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(messageless);
                report = new SpecFidelityReport(combined);
            }
            if (report.hasFindings()) {
                long blockingCount = report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).count();
                long advisoryCount = report.findings().size() - blockingCount;
                String counts = blockingCount > 0 && advisoryCount > 0 ? blockingCount + " blocking and " + advisoryCount + " advisory"
                        : blockingCount > 0 ? blockingCount + " blocking" : advisoryCount + " advisory";
                String gaps = report.findings().size() == 1 ? " exercise-quality gap" : " exercise-quality gaps";
                emit(progress, "The review found " + counts + gaps + (blockingCount > 0 ? " that require instructor attention." : "."));
            }
            return report;
        }
        catch (RuntimeException e) {
            log.warn("Spec-fidelity critic could not run for exercise ({})", e.getClass().getSimpleName());
            return adaptationChanges == null ? SpecFidelityReport.qualityReviewUnavailable("The full-artifact reviewer failed before it could assess the candidate.")
                    : SpecFidelityReport.adaptationScopeUnavailable("The adaptation-scope reviewer failed before it could assess every changed line.");
        }
    }

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

    private static void appendChangedFile(StringBuilder changes, String path, @Nullable String before, @Nullable String after) {
        if (changes.length() >= MAX_ADAPTATION_CHANGE_CHARS || java.util.Objects.equals(before == null ? "" : before, after == null ? "" : after)) {
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

    /**
     * Extracts the test identifiers bound by {@code [task]} lines in the problem statement, deduplicated and trimmed.
     *
     * @param problemStatement the produced problem statement (may be empty)
     * @return the distinct task-bound test names, in encounter order
     */
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

    /**
     * Prepends the seeded-workspace layout snapshot to the user prompt as a delimited observation block. An empty/blank layout returns the prompt unchanged.
     *
     * @param layout     the rendered layout snapshot (may be empty)
     * @param userPrompt the instruction for this run
     * @return the user prompt with the layout block prepended, or the unchanged prompt when there is no layout to show
     */
    static String prependWorkspaceLayout(String layout, String userPrompt) {
        if (layout == null || layout.isBlank()) {
            return userPrompt;
        }
        return "=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\n" + layout.strip() + "\n=== END INITIAL WORKSPACE ===\n\n" + userPrompt;
    }

    private static AgentLoopResult cancelledResult(AgentLoopResult lastResult) {
        return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, lastResult.turns(), lastResult.finalMessage());
    }

    /**
     * The exercise's currently-persisted test names — a conservative superset of the graded coverage the adapt total-wipe gate protects: it reads every persisted case via
     * {@code findByExerciseId} (the same repository production grading uses) rather than re-deriving the active/weighted subset, so it never under-reports the baseline. Returns
     * empty
     * (leaving the gate inert) when the repository is absent (a build-agent-only node) or the exercise has no id yet, so a missing baseline never fabricates a rejection.
     *
     * @return the persisted test names, or an empty set when no authoritative baseline is available
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

    private static GenerationWorkspaceService.RepositoryExtraction replacePlaceholders(GenerationWorkspaceService.RepositoryExtraction extraction,
            Map<String, String> replacements) {
        return new GenerationWorkspaceService.RepositoryExtraction(replacePlaceholders(extraction.files(), replacements), extraction.extractionFailed());
    }

    private static Map<String, String> replacePlaceholders(Map<String, String> files, Map<String, String> replacements) {
        Map<String, String> normalized = new java.util.LinkedHashMap<>();
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

    private VerificationResult verifyWithInfrastructureRetry(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, VerificationRequest request,
            Runnable restoreCandidate, BooleanSupplier cancelled, Consumer<String> progress) {
        try {
            return verifier.verify(sandbox, sessionId, exercise, request, restoreCandidate);
        }
        catch (DifferentialVerificationService.VerificationInfrastructureException exception) {
            if (cancelled.getAsBoolean() || !exception.isRetryableInSameSession()) {
                throw exception;
            }
            log.warn("Exercise verification infrastructure failed once for exercise {} ({}); retrying the same candidate without another provider call", exercise.getId(),
                    exception.getClass().getSimpleName());
            emit(progress, "The verification infrastructure failed; retrying the same exercise without asking the AI to regenerate it.");
            return verifier.verify(sandbox, sessionId, exercise, request, restoreCandidate);
        }
    }

    /**
     * Best-effort, read-once capture of the workspace's {@code SPEC.md} for {@link GenerationOutcome#specDocument()}; {@code null} when the file was never written or
     * could not be read (e.g. the sandbox session no longer exists). Never persisted into any repository.
     */
    @Nullable
    private static String readSpecDocument(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
        return readWorkspaceRootFile(sandbox, sessionId, "SPEC.md");
    }

    @Nullable
    private static String readWorkspaceRootFile(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId, String fileName) {
        if (sandbox == null || sessionId == null) {
            return null;
        }
        try {
            SandboxExecResult result = sandbox.exec(sessionId, SPEC_DOCUMENT_READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/" + fileName);
            return result != null && result.isSuccess() ? result.stdout() : null;
        }
        catch (RuntimeException e) {
            log.debug("Could not read {} after generation for diagnostics: {}", fileName, e.getMessage());
            return null;
        }
    }

    void destroyQuietly(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
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

    private static void emit(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }
}
