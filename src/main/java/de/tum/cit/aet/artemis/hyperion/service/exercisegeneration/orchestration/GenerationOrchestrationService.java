package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.FileSnapshotEmittingAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
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

    /** First attempt plus three bounded repair iterations, leaving room for a semantic repair after mechanical setup problems. */
    private static final int MAX_GENERATION_ATTEMPTS = 4;

    // Optional so a core-only node (where no build agent is co-located to host the sandbox) still starts; absence is reported only when a run is attempted.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final DifferentialVerificationService verifier;

    private final AgentSystemPromptService systemPromptService;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // Reviews brief coverage and, for adaptations, blocks acceptance when unrelated changes cannot be ruled out.
    private final SpecFidelityCriticService specFidelityCritic;

    // Used to register a node-local cancel hook that destroys the sandbox session, so a cancellation during a long build interrupts promptly rather than at the next between-turn
    // poll.
    private final GenerationJobService jobService;

    // Source of the pre-adapt graded test names (the adapt total-wipe gate's baseline). Optional because it is a core-profile repository, absent on a build-agent-only node; when
    // absent the baseline is empty and the total-wipe gate stays inert (fail-open), consistent with every other doubt-on-read-back gate.
    private final Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository;

    public GenerationOrchestrationService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            DifferentialVerificationService verifier, AgentSystemPromptService systemPromptService, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, GenerationJobService jobService, Optional<ProgrammingExerciseTestCaseRepository> testCaseRepository,
            @Value("${artemis.hyperion.agent.max-turns:40}") int maxTurns) {
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
    }

    private InteractiveSandbox requireSandbox() {
        return interactiveSandbox.orElseThrow(
                () -> new IllegalStateException("No interactive sandbox is available on this node. Agentic exercise generation requires either a co-located build agent or a "
                        + "reachable build agent in the cluster to host the sandbox container."));
    }

    /**
     * Runs one generation/adaptation session, streaming a whole-file snapshot to {@code fileSnapshotSink} on every successful {@code write_file}/{@code edit_file} so the
     * triggering
     * instructor's editor can render a live preview of what the agent produces.
     *
     * @param exercise         the exercise to generate or adapt (its repositories must already be scaffolded)
     * @param user             the instructor performing the generation, recorded with the LLM token-usage trace
     * @param userPrompt       the instruction for this run (a generation brief, or the feedback to address)
     * @param jobId            the job id, used to register a node-local cancel hook
     * @param mode             the explicit run intent (generate vs. adapt)
     * @param cancelled        polled cooperatively; if it returns {@code true} the session is aborted
     * @param progress         receives short human-readable progress lines for the live transcript; may be {@code null}
     * @param fileSnapshotSink receives a whole-file snapshot on every successful write for live streaming; {@code null} disables snapshot streaming
     * @param usageSink        receives token usage for every model call; {@code null} uses the default persisted run sink
     * @return the outcome including the verification verdict and the produced files
     */
    public GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, GenerationMode mode, BooleanSupplier cancelled,
            Consumer<String> progress, @Nullable Consumer<ExerciseGenerationFileSnapshotDTO> fileSnapshotSink, @Nullable Consumer<ChatResponse> usageSink) {
        // Snapshot the pre-adapt graded test names so the verifier can reject a destructive total wipe (an adapt that retains none of them = a from-scratch regeneration mislabeled
        // as an adapt). Empty for GENERATE, which leaves the total-wipe gate inert.
        Set<String> baselineGradedTestNames = mode == GenerationMode.ADAPT ? captureBaselineGradedTestNames(exercise) : Set.of();
        String baselineProblemStatement = exercise.getProblemStatement();
        String sourceBrief = renderReviewBrief(mode, userPrompt, baselineProblemStatement);
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
            workspaceSeed = workspace.seedWorkspace(sandbox, sessionId, exercise, mode);
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

            String systemPrompt = systemPromptService.build(exercise, mode);
            // The agent's `verify` tool runs the same differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); the post-loop
            // verify(...) below stays the acceptance decision.
            SandboxAgentTools baseTools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise, testsSeedSnapshot, mode == GenerationMode.ADAPT);
            // Wrap the tools in the snapshot-emitting decorator when a sink is supplied, so each successful write streams the whole file to the instructor's editor. The decorator
            // re-exposes the same @Tool surface (the model sees the same tools) and only adds emission.
            Object tools = fileSnapshotSink != null ? new FileSnapshotEmittingAgentTools(baseTools, fileSnapshotSink) : baseTools;

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (empty probe leaves the prompt unchanged) and first-attempt only — retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), authoringBrief);

            // On rejection, feed the verifier's reasons back and retry up to a small bound. The verifier enforces rules the agent's own verify.sh cannot show (template must fail a
            // meaningful fraction; problem statement must bind tasks), so this loop turns a "builds but not quite right" first attempt into an accepted exercise.
            String currentPrompt = firstPrompt;
            AgentLoopResult loopResult = null;
            VerificationResult verification = null;
            // The final attempt's produced files and problem statement ride the outcome so persist reuses them instead of re-reading the sandbox (verification already extracted
            // them for the integrity gates). Overwritten each attempt so the outcome carries the last (accepted or exhausted) attempt's tree.
            Map<RepositoryType, Map<String, String>> producedFilesByType = new EnumMap<>(RepositoryType.class);
            String producedProblemStatement = "";
            // Recomputed each attempt; the final attempt's report rides the outcome.
            SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();
            for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
                loopResult = agentLoopRunner.run(systemPrompt, currentPrompt, tools, maxTurns, cancelled, effectiveUsageSink, progress);
                log.info("Exercise generation attempt {} took {} turn(s)", attempt, loopResult.turns());

                if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement, loopResult);
                }
                if (loopResult.status() == AgentLoopResult.Status.ERROR) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return new GenerationOutcome(lastMechanicallyVerifiedCandidate.loopResult(), lastMechanicallyVerifiedCandidate.verification(), sessionId, this, sandbox,
                                lastMechanicallyVerifiedCandidate.producedFiles(), lastMechanicallyVerifiedCandidate.problemStatement(),
                                lastMechanicallyVerifiedCandidate.reviewReport(), workspaceSeed.repositoryHeads());
                    }
                    Map<RepositoryType, Map<String, String>> erroredFiles = changedCapturedRepositoryFiles(baselineRepositoryFiles,
                            captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
                    String erroredStatement = workspace.extractProblemStatement(sandbox, sessionId).trim();
                    boolean statementChanged = !java.util.Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), erroredStatement);
                    if (statementChanged || !erroredFiles.isEmpty()) {
                        return new GenerationOutcome(loopResult, null, sessionId, this, sandbox, erroredFiles, erroredStatement,
                                SpecFidelityReport.qualityReviewUnavailable("The agent stopped before verification; the partial candidate requires manual review."),
                                workspaceSeed.repositoryHeads());
                    }
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.error(loopResult);
                }
                // The loop only polls cancellation between turns; honour a cancel that arrived during the last turn before spending minutes on the verification build.
                if (cancelled.getAsBoolean()) {
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }

                // Seed Java structural tests when the produced solution/template structures differ. The returned set is the list of names just injected; the verifier exempts a
                // [task] bound to one from the binding-resolution gate (the agent could not bind tests seeded after it ran) while still requiring solution-pass/template-fail.
                Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);
                if (cancelled.getAsBoolean()) {
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
                if (extractionFailed.isEmpty()) {
                    workspace.materializeRepositoryFiles(sandbox, sessionId, producedFilesByType, workspaceSeed.repositoryMetadata());
                    if (hasProducedChanges(baselineRepositoryFiles, producedFilesByType, baselineProblemStatement, producedProblemStatement)) {
                        lastExtractedCandidate = new ExtractedCandidate(loopResult, copyProducedFiles(producedFilesByType), producedProblemStatement);
                    }
                }
                VerificationRequest verificationRequest = new VerificationRequest(testsSeedSnapshot, baselineRepositoryFiles.getOrDefault(RepositoryType.TEMPLATE, Map.of()),
                        baselineRepositoryFiles.getOrDefault(RepositoryType.SOLUTION, Map.of()), producedTests.files(), producedTemplate.files(), producedSolution.files(),
                        extractionFailed, seededStructuralTestNames, baselineGradedTestNames, producedProblemStatement, mode == GenerationMode.ADAPT);
                if (cancelled.getAsBoolean()) {
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }
                // The authoritative pass re-seeds its pristine script, discards old reports, and builds from fresh temporary directories before parsing the result independently.
                verification = verifyWithInfrastructureRetry(sandbox, sessionId, exercise, verificationRequest, cancelled, progress);
                emit(progress, verification.report());
                if (verification.accepted()) {
                    lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                            SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the mechanically verified candidate received its full-artifact review."));
                }
                if (cancelled.getAsBoolean()) {
                    if (lastMechanicallyVerifiedCandidate != null) {
                        return new GenerationOutcome(lastMechanicallyVerifiedCandidate.loopResult(), lastMechanicallyVerifiedCandidate.verification(), sessionId, this, sandbox,
                                lastMechanicallyVerifiedCandidate.producedFiles(), lastMechanicallyVerifiedCandidate.problemStatement(),
                                lastMechanicallyVerifiedCandidate.reviewReport(), workspaceSeed.repositoryHeads());
                    }
                    return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                            cancelledResult(loopResult));
                }

                // Run the expensive semantic review only after the deterministic mechanical gate passes. Reviewing a candidate that cannot build or grade wastes provider quota and
                // produces findings against artifacts the next attempt must replace anyway.
                if (verification.accepted()) {
                    @Nullable
                    String adaptationChanges = mode == GenerationMode.ADAPT
                            ? renderAdaptationChanges(baselineProblemStatement, producedProblemStatement, baselineRepositoryFiles, producedFilesByType)
                            : null;
                    specFidelityReport = runSpecFidelityCritic(reviewBrief, producedProblemStatement, exercise.getProgrammingLanguage(), producedFilesByType, adaptationChanges,
                            effectiveUsageSink, progress);
                    lastMechanicallyVerifiedCandidate = new CandidateSnapshot(loopResult, verification, copyProducedFiles(producedFilesByType), producedProblemStatement,
                            specFidelityReport);
                    if (cancelled.getAsBoolean()) {
                        return new GenerationOutcome(lastMechanicallyVerifiedCandidate.loopResult(), lastMechanicallyVerifiedCandidate.verification(), sessionId, this, sandbox,
                                lastMechanicallyVerifiedCandidate.producedFiles(), lastMechanicallyVerifiedCandidate.problemStatement(),
                                lastMechanicallyVerifiedCandidate.reviewReport(), workspaceSeed.repositoryHeads());
                    }
                }
                else {
                    specFidelityReport = SpecFidelityReport.empty();
                }

                if (verification.accepted() && !specFidelityReport.hasBlockingFindings()) {
                    break;
                }
                if (attempt == MAX_GENERATION_ATTEMPTS) {
                    break;
                }
                if (verification.accepted()) {
                    boolean reviewUnavailable = specFidelityReport.findings().stream()
                            .anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE
                                    || finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
                    boolean hasActionableReviewFinding = specFidelityReport.findings().stream()
                            .anyMatch(finding -> finding.isBlocking() && finding.kind() != SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE
                                    && finding.kind() != SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
                    if (reviewUnavailable && !hasActionableReviewFinding) {
                        break;
                    }
                    emit(progress, "Mechanical verification passed, but the exercise review found requirements or quality issues; asking the AI to correct them.");
                    String scopeGuidance = mode == GenerationMode.ADAPT ? " Preserve all content outside the requested adaptation." : "";
                    currentPrompt = "Your previous attempt passed mechanical verification, but the automated full-artifact review found acceptance blockers." + scopeGuidance
                            + " Preserve the mechanically correct work: do not restart or rewrite unrelated files. Re-read the cited artifacts and repair them to match the source requirements and "
                            + "remove unsupported candidate choices identified by the review. Make the smallest coherent repair across the statement, solution, template, and tests. Keep every unaffected "
                            + "requirement, API, test, and example. Re-run `sh verify.sh solution` and `sh verify.sh template`, then call submit again.\n\nThe instructor "
                            + "source requirements are:\n" + authoringBrief + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
                    continue;
                }
                emit(progress, "Verification rejected the exercise; asking the agent to fix the issues and try again.");
                // The hard rejection (must fix) plus the advisory findings, the latter framed so the rejection is prioritised.
                currentPrompt = "Your previous attempt was rejected by the differential verifier:\n" + verification.report()
                        + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, re-run `sh verify.sh solution` and "
                        + "`sh verify.sh template` to confirm, then call submit again. If a reason names a forbidden, duplicate, or abandoned path, delete it; replacing it with a "
                        + "placeholder does not remove the violation. Make the smallest coherent repair, leave unrelated files unchanged, and preserve the source requirements below.\n\n"
                        + authoringBrief + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
            }

            // A semantic repair can accidentally break a candidate that already built and graded correctly. Never discard that more useful checkpoint in favour of a later
            // mechanically broken tree; recovery should expose the last buildable candidate and the review findings that still prevented live persistence.
            if ((verification == null || !verification.accepted()) && lastMechanicallyVerifiedCandidate != null) {
                loopResult = lastMechanicallyVerifiedCandidate.loopResult();
                verification = lastMechanicallyVerifiedCandidate.verification();
                producedFilesByType = lastMechanicallyVerifiedCandidate.producedFiles();
                producedProblemStatement = lastMechanicallyVerifiedCandidate.problemStatement();
                specFidelityReport = lastMechanicallyVerifiedCandidate.reviewReport();
            }

            return new GenerationOutcome(loopResult, verification, sessionId, this, sandbox, producedFilesByType, producedProblemStatement, specFidelityReport,
                    workspaceSeed.repositoryHeads());
        }
        catch (RuntimeException e) {
            // A build interrupted by the cancel hook surfaces as a throw; report it as a clean cancellation.
            if (cancelled.getAsBoolean()) {
                return stopOrPreserve(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement,
                        new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }
            if (lastMechanicallyVerifiedCandidate != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while repairing a mechanically verified candidate for exercise {}; preserving the verified checkpoint ({})", exercise.getId(),
                        e.getClass().getSimpleName());
                return new GenerationOutcome(lastMechanicallyVerifiedCandidate.loopResult(), lastMechanicallyVerifiedCandidate.verification(), sessionId, this, sandbox,
                        lastMechanicallyVerifiedCandidate.producedFiles(), lastMechanicallyVerifiedCandidate.problemStatement(), lastMechanicallyVerifiedCandidate.reviewReport(),
                        workspaceSeed.repositoryHeads());
            }
            if (lastExtractedCandidate != null && workspaceSeed != null) {
                log.warn("Exercise generation failed while verifying an extracted candidate for exercise {}; preserving the captured work ({})", exercise.getId(),
                        e.getClass().getSimpleName());
                AgentLoopResult stopped = new AgentLoopResult(AgentLoopResult.Status.ERROR, lastExtractedCandidate.loopResult().turns(),
                        "Generation stopped before verification completed.");
                return new GenerationOutcome(stopped, null, sessionId, this, sandbox, lastExtractedCandidate.producedFiles(), lastExtractedCandidate.problemStatement(),
                        SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the captured candidate could be fully verified."), workspaceSeed.repositoryHeads());
            }
            GenerationOutcome recoveredError = captureUnexpectedFailure(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles,
                    baselineProblemStatement);
            if (recoveredError != null) {
                log.warn("Exercise generation failed after producing recoverable work for exercise {}; preserving it as an isolated review draft ({})", exercise.getId(),
                        e.getClass().getSimpleName());
                return recoveredError;
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
            SpecFidelityReport reviewReport) {
    }

    private record ExtractedCandidate(AgentLoopResult loopResult, Map<RepositoryType, Map<String, String>> producedFiles, String problemStatement) {
    }

    private static Map<RepositoryType, Map<String, String>> copyProducedFiles(Map<RepositoryType, Map<String, String>> producedFiles) {
        Map<RepositoryType, Map<String, String>> copy = new EnumMap<>(RepositoryType.class);
        producedFiles.forEach((type, files) -> copy.put(type, Map.copyOf(files)));
        return Map.copyOf(copy);
    }

    private GenerationOutcome stopOrPreserve(InteractiveSandbox sandbox, @Nullable String sessionId, GenerationWorkspaceService.@Nullable WorkspaceSeed workspaceSeed,
            Map<String, String> placeholderReplacements, Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement,
            AgentLoopResult cancelledResult) {
        GenerationOutcome recoverable = captureUnexpectedFailure(sandbox, sessionId, workspaceSeed, placeholderReplacements, baselineRepositoryFiles, baselineProblemStatement);
        if (recoverable != null) {
            return recoverable;
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
            log.debug("Could not clean transient outputs before generation recovery: {}", cleanupFailure.getMessage());
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
                SpecFidelityReport.qualityReviewUnavailable("Generation stopped before the candidate could be fully verified."), workspaceSeed.repositoryHeads());
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
     * @return the report (possibly empty); never {@code null}
     */
    private SpecFidelityReport runSpecFidelityCritic(String brief, String problemStatement, @Nullable ProgrammingLanguage language,
            Map<RepositoryType, Map<String, String>> producedArtifacts, @Nullable String adaptationChanges, Consumer<ChatResponse> usageSink, Consumer<String> progress) {
        try {
            List<String> testNames = extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = adaptationChanges == null ? specFidelityCritic.critique(brief, problemStatement, testNames, producedArtifacts, usageSink)
                    : specFidelityCritic.critiqueAdaptation(brief, problemStatement, testNames, adaptationChanges, producedArtifacts, usageSink);
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
                emit(progress, "The review found " + counts + gaps + (blockingCount > 0 ? " that must be resolved before live persistence." : "."));
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
            BooleanSupplier cancelled, Consumer<String> progress) {
        try {
            return verifier.verify(sandbox, sessionId, exercise, request);
        }
        catch (DifferentialVerificationService.VerificationInfrastructureException exception) {
            if (cancelled.getAsBoolean() || !exception.isRetryableInSameSession()) {
                throw exception;
            }
            log.warn("Exercise verification infrastructure failed once for exercise {} ({}); retrying the same candidate without another provider call", exercise.getId(),
                    exception.getClass().getSimpleName());
            emit(progress, "The verification infrastructure failed; retrying the same exercise without asking the AI to regenerate it.");
            return verifier.verify(sandbox, sessionId, exercise, request);
        }
    }

    void destroyQuietly(@Nullable InteractiveSandbox sandbox, @Nullable String sessionId) {
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
