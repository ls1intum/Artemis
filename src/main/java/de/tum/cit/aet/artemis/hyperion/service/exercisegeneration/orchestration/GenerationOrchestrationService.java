package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
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

    private static final long VERIFY_WORKSPACE_MAX_FILE_BYTES = 64L * 1024 * 1024;

    private static final long VERIFY_WORKSPACE_MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    /**
     * Hard cap on agent turns per attempt ({@code artemis.hyperion.agent.max-turns}); generous so slow multi-file languages finish in one attempt, still bounded against runaways.
     */
    private final int maxTurns;

    /** First attempt plus a couple of verifier-feedback-driven fix iterations before giving up. */
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    // Optional so a core-only node (where no build agent is co-located to host the sandbox) still starts; absence is reported only when a run is attempted.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final DifferentialVerificationService verifier;

    private final AgentSystemPromptService systemPromptService;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // Advisory critic for the brief-coverage axis the differential verifier is blind to. Non-blocking: never affects the final verdict; while attempts remain it can still trigger
    // a polish retry for an otherwise accepted exercise, and any remaining findings surface as advisory review comments.
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
        InteractiveSandbox sandbox = requireSandbox();
        String sessionId = null;
        Long courseId = courseIdOf(exercise);
        Consumer<ChatResponse> effectiveUsageSink = usageSink != null ? usageSink : jobService.tokenUsageSink(courseId, exercise.getId(), user.getId());
        try {
            emit(progress, "Setting up the build environment");
            sessionId = sandbox.createSession(workspace.sessionSpec(exercise));
            // Node-local interrupt so a cancellation during a long build aborts the in-flight exec; destroySession is idempotent, safe alongside the orchestrator's own teardown.
            String activeSessionId = sessionId;
            jobService.registerCancelHook(jobId, () -> sandbox.destroySession(activeSessionId));

            emit(progress, "Loading the example exercise");
            // Snapshot the seeded tests-repo harness so the verifier can reject later tampering against this exact baseline.
            GenerationWorkspaceService.WorkspaceSeed workspaceSeed = workspace.seedWorkspace(sandbox, sessionId, exercise);
            Map<String, String> testsSeedSnapshot = workspaceSeed.testsSeedSnapshot();

            String systemPrompt = systemPromptService.build(exercise, mode);
            // The agent's `verify` tool runs the same differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); the post-loop
            // verify(...) below stays the acceptance decision.
            SandboxAgentTools baseTools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise);
            // Wrap the tools in the snapshot-emitting decorator when a sink is supplied, so each successful write streams the whole file to the instructor's editor. The decorator
            // re-exposes the same @Tool surface (the model sees the same tools) and only adds emission.
            Object tools = fileSnapshotSink != null ? new FileSnapshotEmittingAgentTools(baseTools, fileSnapshotSink) : baseTools;

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (empty probe leaves the prompt unchanged) and first-attempt only — retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), userPrompt);

            // On rejection, feed the verifier's reasons back and retry up to a small bound. The verifier enforces rules the agent's own verify.sh cannot show (template must fail a
            // meaningful fraction; problem statement must bind tasks), so this loop turns a "builds but not quite right" first attempt into an accepted exercise.
            String currentPrompt = firstPrompt;
            AgentLoopResult loopResult = null;
            VerificationResult verification = null;
            // The final attempt's produced files and problem statement ride the outcome so persist reuses them instead of re-reading the sandbox (verification already extracted
            // them for the integrity gates). Overwritten each attempt so the outcome carries the last (accepted or exhausted) attempt's tree.
            Map<RepositoryType, Map<String, String>> producedFilesByType = new EnumMap<>(RepositoryType.class);
            String producedProblemStatement = "";
            // Recomputed each attempt; the final attempt's report rides the outcome. Advisory only.
            SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();
            for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
                loopResult = agentLoopRunner.run(systemPrompt, currentPrompt, tools, maxTurns, cancelled, effectiveUsageSink, progress);
                log.info("Exercise generation attempt {} took {} turn(s)", attempt, loopResult.turns());

                if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(loopResult);
                }
                if (loopResult.status() == AgentLoopResult.Status.ERROR) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.error(loopResult);
                }
                // The loop only polls cancellation between turns; honour a cancel that arrived during the last turn before spending minutes on the verification build.
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }

                // Seed Java structural tests when the produced solution/template structures differ. The returned set is the list of names just injected; the verifier exempts a
                // [task] bound to one from the binding-resolution gate (the agent could not bind tests seeded after it ran) while still requiring solution-pass/template-fail.
                Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }

                emit(progress, "Checking the exercise builds and grades (attempt " + attempt + " of " + MAX_GENERATION_ATTEMPTS + ")");
                // Read the produced repos back for the sandbox-free integrity gates (harness immutability vs the seed snapshot, solution-leak across template/solution). The
                // extraction-failed flag lets the verifier fail closed on a read-back error, distinct from an empty repo.
                GenerationWorkspaceService.RepositoryExtraction producedTests = workspace.extractRepository(sandbox, sessionId, RepositoryType.TESTS);
                GenerationWorkspaceService.RepositoryExtraction producedTemplate = workspace.extractRepository(sandbox, sessionId, RepositoryType.TEMPLATE);
                GenerationWorkspaceService.RepositoryExtraction producedSolution = workspace.extractRepository(sandbox, sessionId, RepositoryType.SOLUTION);
                // Capture this attempt's extraction so persist reuses it — the same full-repo read verification needs for the integrity gates, not a second sandbox round-trip.
                producedFilesByType.put(RepositoryType.TESTS, producedTests.files());
                producedFilesByType.put(RepositoryType.TEMPLATE, producedTemplate.files());
                producedFilesByType.put(RepositoryType.SOLUTION, producedSolution.files());
                producedProblemStatement = workspace.extractProblemStatement(sandbox, sessionId);
                Set<String> extractionFailed = new LinkedHashSet<>();
                addIfExtractionFailed(extractionFailed, producedTests, RepositoryType.TESTS);
                addIfExtractionFailed(extractionFailed, producedTemplate, RepositoryType.TEMPLATE);
                addIfExtractionFailed(extractionFailed, producedSolution, RepositoryType.SOLUTION);
                VerificationRequest verificationRequest = new VerificationRequest(testsSeedSnapshot, producedTests.files(), producedTemplate.files(), producedSolution.files(),
                        extractionFailed, seededStructuralTestNames, baselineGradedTestNames);
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }
                // The sole-acceptance verification runs in a FRESH sandbox session against the exact produced tree, so no agent-spawned background process or file planted during
                // the
                // in-session loop can overwrite the pristine verify.sh or forge a report between seed and copyOut. The in-loop self-check (the agent's `verify` tool) stays
                // in-session
                // and advisory; only WHERE this authoritative verify runs changed — the differential logic is unchanged.
                verification = verifyInFreshSession(exercise, sandbox, sessionId, verificationRequest, jobId);
                emit(progress, verification.report());
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }

                // Advisory critic against this attempt's artifacts; never touches `verification`. Shares the run's usage sink so the critic's LLM call is counted, not dropped.
                specFidelityReport = runSpecFidelityCritic(userPrompt, producedProblemStatement, exercise.getProgrammingLanguage(), producedTests.files(), effectiveUsageSink,
                        progress);
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }

                if (verification.accepted() && !specFidelityReport.hasFindings() || attempt == MAX_GENERATION_ATTEMPTS) {
                    break;
                }
                if (verification.accepted()) {
                    emit(progress, "Verification accepted the exercise, but the spec-fidelity review found quality gaps; asking the agent to polish the exercise.");
                    currentPrompt = "Your previous attempt passed differential verification, but the spec-fidelity review found quality gaps. Keep the accepted behaviour intact, "
                            + "fix only these quality gaps, re-run `sh verify.sh solution` and `sh verify.sh template`, then call submit again."
                            + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
                    continue;
                }
                emit(progress, "Verification rejected the exercise; asking the agent to fix the issues and try again.");
                // The hard rejection (must fix) plus the advisory findings, the latter framed so the rejection is prioritised.
                currentPrompt = "Your previous attempt was rejected by the differential verifier:\n" + verification.report()
                        + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, re-run `sh verify.sh solution` and "
                        + "`sh verify.sh template` to confirm, then call submit again." + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
            }

            return new GenerationOutcome(loopResult, verification, sessionId, this, sandbox, producedFilesByType, producedProblemStatement, specFidelityReport,
                    workspaceSeed.repositoryHeads());
        }
        catch (RuntimeException e) {
            // The caller gets no usable outcome to close, so tear down here.
            destroyQuietly(sandbox, sessionId);
            // A build interrupted by the cancel hook surfaces as a throw; report it as a clean cancellation.
            if (cancelled.getAsBoolean()) {
                return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }
            log.error("Exercise generation failed for exercise {}", exercise.getId(), e);
            throw e;
        }
        finally {
            jobService.deregisterCancelHook(jobId);
        }
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
     * Runs the advisory spec-fidelity critic against one attempt's produced artifacts, never throwing, so the critic can never perturb the run.
     *
     * @param brief            the instructor brief for this run
     * @param problemStatement the produced student-facing problem statement
     * @param language         the exercise language (may be {@code null})
     * @param producedTests    the produced tests-repo files (path to content), used to derive the test identifiers
     * @param progress         the progress sink for a short transcript line
     * @return the advisory report (possibly empty); never {@code null}
     */
    private SpecFidelityReport runSpecFidelityCritic(String brief, String problemStatement, @Nullable ProgrammingLanguage language, Map<String, String> producedTests,
            Consumer<ChatResponse> usageSink, Consumer<String> progress) {
        try {
            List<String> testNames = extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = specFidelityCritic.critique(brief, problemStatement, testNames, usageSink);
            // Merge the model-free messageless-assertion check into the same advisory report (folds into the retry prompt / review comments, never affects acceptance).
            List<SpecFidelityReport.Finding> messageless = specFidelityCritic.detectMessagelessAssertions(language, producedTests);
            if (!messageless.isEmpty()) {
                List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
                combined.addAll(messageless);
                report = new SpecFidelityReport(combined);
            }
            if (report.hasFindings()) {
                emit(progress, "Spec-fidelity review found " + report.findings().size()
                        + " advisory gap(s) against the brief (these do not affect acceptance; they are surfaced for review).");
            }
            return report;
        }
        catch (RuntimeException e) {
            log.warn("Spec-fidelity critic could not run for exercise; continuing without advisory findings: {}", e.getMessage());
            return SpecFidelityReport.empty();
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

    /**
     * Runs the authoritative differential verification in a fresh sandbox session so it cannot be tampered with by anything the agent loop left behind in the in-session container.
     * Creates a clean container, copies the exact produced {@code /workspace} tree into it, runs the pristine differential there, and always destroys it in the {@code finally} —
     * so
     * no agent-spawned process or planted file can follow the verdict. The differential logic itself is unchanged (same {@link DifferentialVerificationService#verify}).
     *
     * @param exercise      the exercise being verified (selects the container image and the per-language build recipe)
     * @param sandbox       the sandbox instance (one per node; the fresh session is a new container on it)
     * @param loopSessionId the agent-loop session whose produced {@code /workspace} tree is copied into the fresh session
     * @param request       the produced artifacts and integrity-gate inputs to decide on
     * @return the acceptance verdict from the fresh, untamperable session
     */
    private VerificationResult verifyInFreshSession(ProgrammingExercise exercise, InteractiveSandbox sandbox, String loopSessionId, VerificationRequest request, String jobId) {
        String verifySessionId = null;
        try {
            verifySessionId = sandbox.createVerificationSession(workspace.sessionSpec(exercise), loopSessionId);
            String activeVerifySessionId = verifySessionId;
            jobService.registerCancelHook(jobId, () -> {
                destroyQuietly(sandbox, loopSessionId);
                destroyQuietly(sandbox, activeVerifySessionId);
            });
            copyWorkspaceInto(sandbox, loopSessionId, verifySessionId);
            return verifier.verify(sandbox, verifySessionId, exercise, request);
        }
        finally {
            // The fresh verification session is never returned to the caller, so it is torn down here on every path.
            destroyQuietly(sandbox, verifySessionId);
        }
    }

    /** Copies the regular-file {@code /workspace} tree into the fresh verification session. */
    private static void copyWorkspaceInto(InteractiveSandbox sandbox, String fromSessionId, String toSessionId) {
        byte[] repacked;
        try (TarArchiveInputStream tar = sandbox.copyOut(fromSessionId, GenerationWorkspaceService.WORKSPACE)) {
            // copyOut prefixes entries with "workspace/", so copying the repacked archive in at "/" restores them under /workspace.
            repacked = repackTar(tar);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not copy the produced workspace into the fresh verification session", e);
        }
        sandbox.copyIn(toSessionId, "/", new ByteArrayInputStream(repacked));
    }

    /**
     * Repacks the copied-out workspace into a fresh archive for {@code copyIn}. The stream is untrusted: reject path escapes, links, special entries, and oversized content before
     * materializing bytes for the verification sandbox.
     */
    private static byte[] repackTar(TarArchiveInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long[] totalBytes = { 0 };
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = verifiedWorkspaceEntryName(entry);
                if (entry.isDirectory()) {
                    TarArchiveEntry directory = new TarArchiveEntry(name.endsWith("/") ? name : name + "/");
                    directory.setMode(entry.getMode());
                    tar.putArchiveEntry(directory);
                    tar.closeArchiveEntry();
                    continue;
                }
                if (!entry.isFile() || entry.isSymbolicLink() || entry.isLink() || entry.isFIFO() || entry.isCharacterDevice() || entry.isBlockDevice()) {
                    throw new IOException("Refusing non-regular workspace entry in verifier copy: " + entry.getName());
                }
                byte[] content = readBoundedEntry(in, entry.getSize(), totalBytes);
                TarArchiveEntry copy = new TarArchiveEntry(name);
                copy.setMode(entry.getMode());
                copy.setSize(content.length);
                tar.putArchiveEntry(copy);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    private static String verifiedWorkspaceEntryName(TarArchiveEntry entry) throws IOException {
        String name = entry.getName();
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith("/") || name.equals("..") || name.startsWith("../") || name.endsWith("/..") || name.contains("/../") || !name.startsWith("workspace/")) {
            throw new IOException("Refusing workspace entry outside /workspace in verifier copy: " + entry.getName());
        }
        return name;
    }

    private static byte[] readBoundedEntry(TarArchiveInputStream in, long declaredSize, long[] totalBytes) throws IOException {
        if (declaredSize > VERIFY_WORKSPACE_MAX_FILE_BYTES) {
            throw new IOException("Refusing oversized workspace entry in verifier copy: " + declaredSize + " bytes");
        }
        ByteArrayOutputStream content = new ByteArrayOutputStream(declaredSize > 0 && declaredSize <= Integer.MAX_VALUE ? (int) declaredSize : 8192);
        byte[] buffer = new byte[8192];
        long entryBytes = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            entryBytes += read;
            totalBytes[0] += read;
            if (entryBytes > VERIFY_WORKSPACE_MAX_FILE_BYTES || totalBytes[0] > VERIFY_WORKSPACE_MAX_TOTAL_BYTES) {
                throw new IOException("Refusing oversized workspace archive in verifier copy");
            }
            content.write(buffer, 0, read);
        }
        return content.toByteArray();
    }

    GenerationWorkspaceService workspace() {
        return workspace;
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
