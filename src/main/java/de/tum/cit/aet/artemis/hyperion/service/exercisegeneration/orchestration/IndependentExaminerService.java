package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ExaminerAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CrossCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CrossCheckVerdict;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Drives the DECORRELATED test-author (independent examiner) agent for the cross-check, in its OWN fresh sandbox container that is seeded WITHOUT the reference
 * solution
 * ({@link GenerationWorkspaceService#seedExaminerWorkspace}) — so the examiner provably cannot read {@code solution/}. The examiner authors a test suite that pins the problem
 * statement's stated contract, using the restricted {@link ExaminerAgentTools} (no {@code verify} tool), and iterates only to make the suite compile against the template.
 * <p>
 * The authored suite (the tests-repo working tree, read back out of the examiner container) is then run against the REAL solution/template via the {@link CrossCheckService};
 * {@link #crossCheck} bundles both halves so the orchestrator's cross-check step is a single call. It is only invoked when the cross-check is enabled and the language is
 * allowlisted.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class IndependentExaminerService {

    private static final Logger log = LoggerFactory.getLogger(IndependentExaminerService.class);

    private static final String EXAMINER_USER_PROMPT = "Author an independent test suite that pins the STATED contract in problem-statement.md. For each stated postcondition, "
            + "invariant, and error/edge case, write the minimal test that would falsify a subtly-wrong implementation, and assert the exact stated result. Make the suite compile "
            + "against the template with `sh verify.sh template`, then submit. Do not try to make the tests pass — you have no reference solution.";

    // Optional so a core-only node (no co-located build agent) still starts; the cross-check is only invoked where a sandbox is available.
    private final Optional<InteractiveSandbox> interactiveSandbox;

    private final GenerationWorkspaceService workspace;

    private final AgentLoopRunner agentLoopRunner;

    private final AgentSystemPromptService systemPromptService;

    private final CrossCheckService crossCheckService;

    /** The examiner has a bounded job (author tests, make them compile), so a smaller turn budget than the main author is enough. */
    private final int maxTurns;

    public IndependentExaminerService(Optional<InteractiveSandbox> interactiveSandbox, GenerationWorkspaceService workspace, AgentLoopRunner agentLoopRunner,
            AgentSystemPromptService systemPromptService, CrossCheckService crossCheckService, @Value("${artemis.hyperion.crosscheck.examiner-max-turns:40}") int maxTurns) {
        this.interactiveSandbox = interactiveSandbox;
        this.workspace = workspace;
        this.agentLoopRunner = agentLoopRunner;
        this.systemPromptService = systemPromptService;
        this.crossCheckService = crossCheckService;
        this.maxTurns = maxTurns;
    }

    /**
     * Authors a decorrelated shadow suite ({@link #authorShadowSuite}) and evaluates it against the real solution via the {@link CrossCheckService}. Bundling the two halves —
     * authoring the independent suite and running it — behind one call keeps the orchestrator's cross-check step a single, intention-revealing statement and shares the examiner's
     * one attribution/{@code usageSink} path.
     *
     * @param sandbox               the live generation sandbox the shadow suite is run against (holds the real produced solution/template)
     * @param sessionId             the generation session id in that sandbox
     * @param exercise              the accepted exercise being cross-checked
     * @param producedTemplateFiles the produced TEMPLATE files the examiner writes tests against
     * @param producedTestsFiles    the produced TESTS files (sample sources stripped before seeding)
     * @param cancelled             polled cooperatively between turns; a {@code true} aborts the examiner loop
     * @param usageSink             receives each model call's response for token-usage tracking; may be {@code null}
     * @param progress              short human-readable progress lines for the live transcript; may be {@code null}
     * @return the cross-check verdict (an empty authored suite fails open to a non-contradiction verdict)
     */
    public CrossCheckVerdict crossCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> producedTemplateFiles,
            Map<String, String> producedTestsFiles, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress) {
        Map<String, String> shadowSuite = authorShadowSuite(exercise, producedTemplateFiles, producedTestsFiles, cancelled, usageSink, progress);
        return crossCheckService.runAgainstShadowSuite(sandbox, sessionId, exercise, shadowSuite);
    }

    /**
     * Runs the independent examiner in a fresh, solution-free sandbox and returns the authored shadow suite (the tests-repo working tree read back out). The examiner is seeded
     * from
     * the caller's PRODUCED template + tests maps (not a fresh git checkout of the stale pre-generation scaffold), so it authors tests against the API the agent actually produced
     * —
     * the shadow suite then compiles against the real solution and a solution-side failure is a genuine contradiction rather than an against-the-wrong-API compile error.
     *
     * @param exercise              the exercise whose statement the examiner tests against
     * @param producedTemplateFiles the produced TEMPLATE files (repository-relative path to content) — the real public API the examiner writes tests against
     * @param producedTestsFiles    the produced TESTS files (repository-relative path to content); the sample test sources are stripped before seeding
     * @param cancelled             polled cooperatively between turns; a {@code true} aborts the examiner loop
     * @param usageSink             receives each model call's response for token-usage tracking; may be {@code null}
     * @param progress              short human-readable progress lines for the live transcript; may be {@code null}
     * @return the authored suite (repository-relative path to content); empty if no sandbox is available or nothing usable was produced
     */
    public Map<String, String> authorShadowSuite(ProgrammingExercise exercise, Map<String, String> producedTemplateFiles, Map<String, String> producedTestsFiles,
            BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress) {
        if (interactiveSandbox.isEmpty()) {
            log.warn("No interactive sandbox available; skipping the independent-examiner cross-check for exercise {}", exercise.getId());
            return Map.of();
        }
        InteractiveSandbox sandbox = interactiveSandbox.get();
        String sessionId = null;
        try {
            if (progress != null) {
                progress.accept("Running an independent examiner to cross-check the exercise");
            }
            sessionId = sandbox.createSession(workspace.sessionSpec(exercise));
            // Decorrelation by ABSENCE: the examiner's container is seeded with the statement + PRODUCED template + stripped PRODUCED tests, and NEVER the solution or reference
            // sample.
            workspace.seedExaminerWorkspace(sandbox, sessionId, exercise, producedTemplateFiles, producedTestsFiles);
            ExaminerAgentTools tools = new ExaminerAgentTools(sandbox, sessionId);
            agentLoopRunner.run(systemPromptService.buildExaminerPrompt(exercise), EXAMINER_USER_PROMPT, tools, maxTurns, cancelled, usageSink, progress);
            // Read the authored suite back out (best-effort: even a partial suite lets the cross-check run and fail-open if it does not compile).
            return workspace.extractRepositoryFiles(sandbox, sessionId, RepositoryType.TESTS);
        }
        catch (RuntimeException e) {
            // Advisory-by-default: an examiner failure must never perturb the main run; return no suite so the cross-check is skipped.
            log.warn("Independent-examiner agent failed for exercise {}; skipping the cross-check: {}", exercise.getId(), e.getMessage());
            return Map.of();
        }
        finally {
            destroyQuietly(sandbox, sessionId);
        }
    }

    private void destroyQuietly(InteractiveSandbox sandbox, @Nullable String sessionId) {
        if (sessionId != null) {
            try {
                sandbox.destroySession(sessionId);
            }
            catch (RuntimeException e) {
                log.warn("Failed to destroy examiner sandbox session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}
