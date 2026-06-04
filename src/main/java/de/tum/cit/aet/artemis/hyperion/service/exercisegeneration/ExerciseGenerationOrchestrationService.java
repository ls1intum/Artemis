package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

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
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Top-level driver of agentic exercise generation and adaptation. It owns the lifecycle of a single generation session and ties together the interactive sandbox, the
 * pi-faithful tools, the user-controlled agent loop, and the out-of-band verifier.
 * <p>
 * The flow, identical for both modes (create-from-scratch and adapt-with-feedback), is: create a warm sandbox session, seed it with the exercise's components, run the agent
 * loop until it stops or the budget is reached, then run the differential verifier. The verdict and the produced files are returned to the caller, which decides whether to
 * persist. The session container is always destroyed, even on failure, so nothing is leaked.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class ExerciseGenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationOrchestrationService.class);

    /**
     * Hard cap on agent turns per attempt, configurable via {@code artemis.hyperion.agent.max-turns}. Generous by default so the slower, multi-file languages (C#, Kotlin, Swift,
     * Haskell, R) can finish a real edit/build/test loop within a single attempt rather than being cut off; still bounded to prevent a runaway session. The agent is nudged to
     * converge shortly before the cap, and the verifier-feedback retry loop adds {@link #MAX_GENERATION_ATTEMPTS} attempts on top.
     */
    private final int maxTurns;

    /** How many times to run the agent and verify: the first attempt plus a couple of verifier-feedback-driven fix iterations before giving up. */
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    /** Pipeline id recorded with the LLM token-usage trace for an agentic generation run. */
    private static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    // The interactive sandbox lives on a build agent. On the single-node integrated deployment it is present in the same JVM and injected; on a core-only node it is absent (the
    // multi-node command channel that bridges to a remote build agent is a follow-up). It is injected as an Optional so its absence is reported clearly when a run is attempted,
    // rather than preventing the core node from starting.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final AuthoritativeVerificationService verifier;

    private final AgentSystemPromptService systemPromptFactory;

    private final StructuralOracleSeedingService structuralOracleSeeder;

    // The advisory spec-fidelity / coverage critic — the brief-coverage axis the differential verifier is structurally blind to. Its findings are NON-BLOCKING: they never affect
    // the accept/reject verdict (the verifier remains the sole source of truth), only feed the retry prompt while attempts remain and surface as advisory review comments
    // otherwise.
    private final SpecFidelityCriticService specFidelityCritic;

    // Used only to register a node-local cancel hook that destroys the sandbox session so a cancellation arriving during a long build interrupts it promptly instead of waiting for
    // the next between-turn poll. Injected directly: there is no longer a construction cycle (the job service hands off the run via an event, so it does not depend on this).
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
        return interactiveSandbox.orElseThrow(() -> new IllegalStateException(
                "No interactive sandbox is available on this node. Agentic exercise generation requires a co-located build agent (single-node integrated deployment)."));
    }

    /**
     * Runs one generation/adaptation session for an exercise.
     *
     * @param exercise   the exercise to generate or adapt (its repositories must already be scaffolded)
     * @param user       the instructor performing the generation, recorded with the LLM token-usage trace
     * @param userPrompt the instruction for this run (a generation brief, or the feedback to address)
     * @param jobId      the job id, used to register a node-local cancel hook that destroys the sandbox session to interrupt a long in-flight build on cancellation
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
            emit(progress, "Creating sandbox session");
            sessionId = sandbox.createSession(workspace.sessionSpec(exercise));
            // Register a node-local interrupt: a cancellation arriving during a long build destroys this session so the in-flight exec fails fast instead of waiting for the next
            // between-turn poll. destroySession is idempotent, so this is safe alongside the orchestrator's own teardown.
            String activeSessionId = sessionId;
            jobService.registerCancelHook(jobId, () -> sandbox.destroySession(activeSessionId));

            emit(progress, "Seeding workspace from the exercise repositories");
            // Capture the seeded tests-repo harness snapshot up front so the verifier can reject any later tampering against this exact baseline.
            Map<String, String> testsSeedSnapshot = workspace.seedWorkspace(sandbox, sessionId, exercise);

            String systemPrompt = systemPromptFactory.build(exercise);
            SandboxAgentTools tools = new SandboxAgentTools(sandbox, sessionId);

            // Run the agent, verify, and — if the authoritative verifier rejects — feed its reasons back so the agent can fix the workspace and try again, up to a small bound.
            // The verifier enforces acceptance rules the agent's own verify.sh build cannot show it (e.g. the template must fail a meaningful fraction of tests, and the problem
            // statement must bind tasks), so this loop is what turns a "builds but not quite right" first attempt into an accepted exercise without instructor intervention.
            String currentPrompt = userPrompt;
            AgentLoopResult loopResult = null;
            VerificationResult verification = null;
            // The latest advisory spec-fidelity report (brief-coverage axis the verifier is blind to). Recomputed each attempt against the produced artifacts; the one from the
            // final
            // attempt is attached to the outcome and surfaced as advisory review comments. NEVER consulted by the accept/reject verdict.
            SpecFidelityReport specFidelityReport = SpecFidelityReport.empty();
            for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
                loopResult = agentLoopRunner.run(systemPrompt, currentPrompt, tools, maxTurns, cancelled, usageSink, progress);
                // Log per-attempt turn usage so verifier-feedback thrash (later attempts pinned at the cap) is observable in the logs without re-running.
                log.info("Exercise generation attempt {} took {} turn(s)", attempt, loopResult.turns());

                if (loopResult.status() == AgentLoopResult.Status.CANCELLED) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(loopResult);
                }
                if (loopResult.status() == AgentLoopResult.Status.ERROR) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.error(loopResult);
                }
                // The agent loop only polls cancellation between turns; honour a cancel that arrived while the last turn ran before spending minutes on the verification build.
                if (cancelled.getAsBoolean()) {
                    destroyQuietly(sandbox, sessionId);
                    return GenerationOutcome.cancelled(cancelledResult(loopResult));
                }

                // Additive, best-effort: seed Java structural tests when the produced solution/template structures differ. The returned set is the AUTHORITATIVE list of structural
                // test names just injected; the verifier exempts a [task] bound to one of them from the binding-resolution gate (the agent could not bind tests seeded after it
                // ran),
                // while still requiring each to pass on the solution and fail on the template (W1: kills the structural-binding retry thrash).
                Set<String> seededStructuralTestNames = structuralOracleSeeder.seedIfStructuralDiff(sandbox, sessionId, exercise);

                emit(progress, "Verifying the generated exercise (attempt " + attempt + " of " + MAX_GENERATION_ATTEMPTS + ")");
                // Read the produced repositories back out so the verifier can run the sandbox-free integrity gates (harness immutability against the seed snapshot, and the
                // solution-leak check across the produced template/solution). extractRepository already strips orphan residue, so the gates see the same files that ship. The
                // extraction-failed flag is threaded so the verifier can fail CLOSED on a genuine read-back error (distinct from a genuinely empty repo).
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

                // Advisory spec-fidelity / coverage critic: the verifier proves the exercise is internally consistent but never that it covers the instructor's brief. Run the
                // critic
                // against THIS attempt's produced artifacts so its findings can (a) feed the next retry prompt while attempts remain and (b) become the advisory report on the
                // final
                // outcome. It is best-effort and NON-BLOCKING — it never touches `verification`, so it can never flip the accept/reject verdict; a critic failure yields no
                // findings.
                specFidelityReport = runSpecFidelityCritic(userPrompt, workspace.extractProblemStatement(sandbox, sessionId), producedTests.files(), progress);

                if (verification.accepted() || attempt == MAX_GENERATION_ATTEMPTS) {
                    break;
                }
                emit(progress, "Verification rejected the exercise; asking the agent to fix the issues and try again.");
                // Fold both the authoritative rejection (which the agent MUST fix) and the advisory spec-fidelity findings (which it SHOULD fix) into the next prompt. The
                // spec-fidelity block is clearly framed as advisory so the agent prioritises the hard rejection but still adds the missing tests / cleans leaked phrasing.
                currentPrompt = "Your previous attempt was rejected by the authoritative verifier:\n" + verification.report()
                        + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, re-run `sh verify.sh solution` and "
                        + "`sh verify.sh template` to confirm, then call submit again." + specFidelityCritic.renderForRetryPrompt(specFidelityReport);
            }

            return new GenerationOutcome(loopResult, verification, sessionId, this, sandbox, specFidelityReport);
        }
        catch (RuntimeException e) {
            // Destroy the session here since the caller will not get a usable outcome to close.
            destroyQuietly(sandbox, sessionId);
            // A build interrupted by the cancel hook surfaces as a thrown exception; report it as a clean cancellation rather than an error.
            if (cancelled.getAsBoolean()) {
                return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, ""));
            }
            log.error("Exercise generation failed for exercise {}", exercise.getId(), e);
            throw e;
        }
        finally {
            // The hook holds the session reference; once the run has left the loop the session is either already destroyed or owned by the returned outcome, so drop the hook.
            jobService.deregisterCancelHook(jobId);
        }
    }

    /**
     * Matches an Artemis {@code [task][Title](testA,testB)} binding, capturing the comma-separated test-name list, so the produced test identifiers can be handed to the critic.
     */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task]\\[[^]]*]\\(([^)]*)\\)");

    /**
     * Runs the advisory spec-fidelity critic against one attempt's produced artifacts, never throwing. The critic itself degrades gracefully (a model failure yields no findings),
     * but this wrapper additionally guards against any unexpected failure of the inputs (e.g. a problem-statement read-back error) so the critic can never perturb the run.
     *
     * @param brief            the instructor brief for this run
     * @param problemStatement the produced student-facing problem statement
     * @param producedTests    the produced tests-repo files (path to content), used to derive the test identifiers
     * @param progress         the progress sink for a short transcript line
     * @return the advisory report (possibly empty); never {@code null}
     */
    private SpecFidelityReport runSpecFidelityCritic(String brief, String problemStatement, Map<String, String> producedTests, Consumer<String> progress) {
        try {
            List<String> testNames = extractTaskBoundTestNames(problemStatement);
            SpecFidelityReport report = specFidelityCritic.critique(brief, problemStatement, testNames);
            if (report.hasFindings()) {
                emit(progress, "Spec-fidelity review found " + report.findings().size()
                        + " advisory gap(s) against the brief (these do not affect acceptance; they are surfaced for review).");
            }
            return report;
        }
        catch (RuntimeException e) {
            // Defence in depth: the critic is non-blocking, so any failure here is swallowed and contributes no findings. The verifier's verdict is untouched.
            log.warn("Spec-fidelity critic could not run for exercise; continuing without advisory findings: {}", e.getMessage());
            return SpecFidelityReport.empty();
        }
    }

    /**
     * Extracts the test identifiers bound by {@code [task]} lines in the problem statement, deduplicated and trimmed. These are the exact names the runner reports (the agent
     * copies
     * them verbatim from the verifier), so they are the cheapest faithful source of "which tests exist" without re-reading the verifier's internal build summary.
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
