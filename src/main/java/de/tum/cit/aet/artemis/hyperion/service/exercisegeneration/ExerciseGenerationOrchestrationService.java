package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Top-level driver of agentic exercise generation and adaptation. Owns one generation session: create a sandbox, seed it with the exercise's components, run the agent loop, then
 * run the differential verifier. The verdict and produced files are returned to the caller, which decides whether to persist. The session container is always destroyed, even on
 * failure.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class ExerciseGenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationOrchestrationService.class);

    /**
     * Hard cap on agent turns per attempt ({@code artemis.hyperion.agent.max-turns}); generous so slow multi-file languages finish in one attempt, still bounded against runaways.
     */
    private final int maxTurns;

    /** First attempt plus a couple of verifier-feedback-driven fix iterations before giving up. */
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    // Optional so a core-only node (where no build agent is co-located to host the sandbox) still starts; absence is reported only when a run is attempted.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final AuthoritativeVerificationService verifier;

    private final AgentSystemPromptService systemPromptFactory;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // Advisory critic for the brief-coverage axis the differential verifier is blind to. NON-BLOCKING: never affects the verdict (verifier is the sole truth); only feeds the retry
    // prompt while attempts remain and surfaces as advisory review comments otherwise.
    private final SpecFidelityCriticService specFidelityCritic;

    // Used to register a node-local cancel hook that destroys the sandbox session, so a cancellation during a long build interrupts promptly rather than at the next between-turn
    // poll.
    private final ExerciseGenerationJobService jobService;

    private final LLMTokenUsageService llmTokenUsageService;

    public ExerciseGenerationOrchestrationService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            AuthoritativeVerificationService verifier, AgentSystemPromptService systemPromptFactory, StructuralOracleSeedingService structuralOracleSeeder,
            SpecFidelityCriticService specFidelityCritic, ExerciseGenerationJobService jobService, LLMTokenUsageService llmTokenUsageService,
            @Value("${artemis.hyperion.agent.max-turns:100}") int maxTurns) {
        this.maxTurns = maxTurns;
        this.interactiveSandbox = interactiveSandbox;
        this.workspace = workspace;
        this.agentLoopRunner = agentLoopRunner;
        this.verifier = verifier;
        this.systemPromptFactory = systemPromptFactory;
        this.structuralOracleSeeder = structuralOracleSeeder;
        this.specFidelityCritic = specFidelityCritic;
        this.jobService = jobService;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    private InteractiveSandbox requireSandbox() {
        return interactiveSandbox.orElseThrow(
                () -> new IllegalStateException("No interactive sandbox is available on this node. Agentic exercise generation requires either a co-located build agent or a "
                        + "reachable build agent in the cluster to host the sandbox container."));
    }

    /**
     * Runs one generation/adaptation session for an exercise.
     *
     * @param exercise   the exercise to generate or adapt (its repositories must already be scaffolded)
     * @param user       the instructor performing the generation, recorded with the LLM token-usage trace
     * @param userPrompt the instruction for this run (a generation brief, or the feedback to address)
     * @param jobId      the job id, used to register a node-local cancel hook
     * @param cancelled  polled cooperatively; if it returns {@code true} the session is aborted
     * @param progress   receives short human-readable progress lines for the live transcript; may be {@code null}
     * @return the outcome including the verification verdict and the produced files
     */
    public GenerationOutcome generate(ProgrammingExercise exercise, User user, String userPrompt, String jobId, BooleanSupplier cancelled, Consumer<String> progress) {
        InteractiveSandbox sandbox = requireSandbox();
        String sessionId = null;
        Long courseId = courseIdOf(exercise);
        Consumer<ChatResponse> usageSink = chatResponse -> llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                builder -> builder.withCourse(courseId).withExercise(exercise.getId()).withUser(user.getId()));
        try {
            emit(progress, "Setting up the build environment");
            sessionId = sandbox.createSession(workspace.sessionSpec(exercise));
            // Node-local interrupt so a cancellation during a long build aborts the in-flight exec; destroySession is idempotent, safe alongside the orchestrator's own teardown.
            String activeSessionId = sessionId;
            jobService.registerCancelHook(jobId, () -> sandbox.destroySession(activeSessionId));

            emit(progress, "Loading the example exercise");
            // Snapshot the seeded tests-repo harness so the verifier can reject later tampering against this exact baseline.
            Map<String, String> testsSeedSnapshot = workspace.seedWorkspace(sandbox, sessionId, exercise);

            String systemPrompt = systemPromptFactory.build(exercise);
            // The agent's `verify` tool runs the SAME differential as the post-loop gate so it sees the verdict in-loop (pass/fail tests, exact [task] names); post-loop
            // verify(...)
            // below stays the sole acceptance truth.
            SandboxAgentTools tools = new SandboxAgentTools(sandbox, sessionId, verifier, exercise);

            // Free turn-0 observation of the seeded layout so the agent need not `ls -R`. Best-effort (empty probe leaves the prompt unchanged) and first-attempt only — retries
            // already operate on a workspace the agent has explored.
            String firstPrompt = prependWorkspaceLayout(workspace.probeWorkspaceLayout(sandbox, sessionId), userPrompt);

            // On rejection, feed the verifier's reasons back and retry up to a small bound. The verifier enforces rules the agent's own verify.sh cannot show (template must fail a
            // meaningful fraction; problem statement must bind tasks), so this loop turns a "builds but not quite right" first attempt into an accepted exercise.
            String currentPrompt = firstPrompt;
            AgentLoopResult loopResult = null;
            VerificationResult verification = null;
            // Recomputed each attempt; the final attempt's report rides the outcome. Advisory only.
            SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();
            for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
                loopResult = agentLoopRunner.run(systemPrompt, currentPrompt, tools, maxTurns, cancelled, usageSink, progress);
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

                // Seed Java structural tests when the produced solution/template structures differ. The returned set is the AUTHORITATIVE list of names just injected; the verifier
                // exempts a [task] bound to one from the binding-resolution gate (the agent could not bind tests seeded after it ran) while still requiring
                // solution-pass/template-fail.
                Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);

                emit(progress, "Checking the exercise builds and grades (attempt " + attempt + " of " + MAX_GENERATION_ATTEMPTS + ")");
                // Read the produced repos back for the sandbox-free integrity gates (harness immutability vs the seed snapshot, solution-leak across template/solution). The
                // extraction-failed flag lets the verifier fail CLOSED on a read-back error, distinct from a genuinely empty repo.
                GenerationWorkspaceService.RepositoryExtraction producedTests = workspace.extractRepository(sandbox, sessionId, RepositoryType.TESTS);
                GenerationWorkspaceService.RepositoryExtraction producedTemplate = workspace.extractRepository(sandbox, sessionId, RepositoryType.TEMPLATE);
                GenerationWorkspaceService.RepositoryExtraction producedSolution = workspace.extractRepository(sandbox, sessionId, RepositoryType.SOLUTION);
                Set<String> extractionFailed = new LinkedHashSet<>();
                if (producedTests.extractionFailed()) {
                    extractionFailed.add(GenerationWorkspaceService.directoryFor(RepositoryType.TESTS));
                }
                if (producedTemplate.extractionFailed()) {
                    extractionFailed.add(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
                }
                if (producedSolution.extractionFailed()) {
                    extractionFailed.add(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
                }
                verification = verifier.verify(sandbox, sessionId, exercise, testsSeedSnapshot, producedTests.files(), producedTemplate.files(), producedSolution.files(),
                        extractionFailed, seededStructuralTestNames);
                emit(progress, verification.report());

                // Advisory critic against this attempt's artifacts; never touches `verification`.
                specFidelityReport = runSpecFidelityCritic(userPrompt, workspace.extractProblemStatement(sandbox, sessionId), exercise.getProgrammingLanguage(),
                        producedTests.files(), progress);

                if (verification.accepted() || attempt == MAX_GENERATION_ATTEMPTS) {
                    break;
                }
                emit(progress, "Verification rejected the exercise; asking the agent to fix the issues and try again.");
                // The hard rejection (must fix) plus the advisory findings, the latter framed so the rejection is prioritised.
                currentPrompt = "Your previous attempt was rejected by the authoritative verifier:\n" + verification.report()
                        + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, re-run `sh verify.sh solution` and "
                        + "`sh verify.sh template` to confirm, then call submit again." + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
            }

            return new GenerationOutcome(loopResult, verification, sessionId, this, sandbox, specFidelityReport);
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
            Consumer<String> progress) {
        try {
            List<String> testNames = extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = specFidelityCritic.critique(brief, problemStatement, testNames);
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

    @Nullable
    private static Long courseIdOf(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course == null ? null : course.getId();
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
