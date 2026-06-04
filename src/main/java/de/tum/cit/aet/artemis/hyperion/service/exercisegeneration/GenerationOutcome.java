package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The result of an agentic generation session, returned to the caller (the async task) so it can decide whether to persist and then close the session.
 * <p>
 * It is {@link AutoCloseable}: closing it destroys the underlying sandbox container. The produced files are read lazily from the still-open session (so they are only extracted
 * if the caller actually persists), which is why the outcome holds a reference back to the orchestrator and the session id.
 */
public final class GenerationOutcome implements AutoCloseable {

    private final AgentLoopResult loopResult;

    @Nullable
    private final VerificationResult verification;

    @Nullable
    private final String sessionId;

    @Nullable
    private final ExerciseGenerationOrchestrationService orchestrator;

    @Nullable
    private final InteractiveSandbox sandbox;

    @Nullable
    private final String errorMessage;

    /**
     * Advisory spec-fidelity / coverage findings (the brief-coverage axis the differential oracle is blind to). Never empty-by-contract and never consulted by
     * {@link #isAccepted()}
     * — purely advisory. Empty when the critic found nothing or was skipped.
     */
    private final SpecFidelityReport specFidelityReport;

    /**
     * Per-attempt turn-budget telemetry: the model-turn count of each generation attempt, in order. One entry per agent run, so {@code [12, 30, 30]} means three attempts that took
     * 12, 30, then 30 turns — making verifier-feedback thrash (later attempts pinned at the cap) observable without re-running. Empty for a cancelled/error outcome.
     */
    private final List<Integer> attemptTurnCounts;

    /**
     * Convenience overload without the advisory spec-fidelity report and turn-budget telemetry, defaulting both to empty. Used by tests that construct a controlled outcome and do
     * not exercise the critic / telemetry; the orchestrator always uses the full constructor.
     */
    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId,
            @Nullable ExerciseGenerationOrchestrationService orchestrator, @Nullable InteractiveSandbox sandbox) {
        this(loopResult, verification, sessionId, orchestrator, sandbox, SpecFidelityReport.empty(), List.of());
    }

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId,
            @Nullable ExerciseGenerationOrchestrationService orchestrator, @Nullable InteractiveSandbox sandbox, SpecFidelityReport specFidelityReport,
            List<Integer> attemptTurnCounts) {
        this.loopResult = loopResult;
        this.verification = verification;
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.sandbox = sandbox;
        this.errorMessage = null;
        this.specFidelityReport = specFidelityReport;
        this.attemptTurnCounts = List.copyOf(attemptTurnCounts);
    }

    private GenerationOutcome(AgentLoopResult loopResult, @Nullable String errorMessage) {
        this.loopResult = loopResult;
        this.verification = null;
        this.sessionId = null;
        this.orchestrator = null;
        this.sandbox = null;
        this.errorMessage = errorMessage;
        this.specFidelityReport = SpecFidelityReport.empty();
        this.attemptTurnCounts = List.of();
    }

    static GenerationOutcome cancelled(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "Generation was cancelled.");
    }

    static GenerationOutcome error(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "The agent loop ended with an error.");
    }

    /**
     * @return the advisory spec-fidelity report; never {@code null}. It is purely advisory and is NEVER consulted by {@link #isAccepted()} — an oracle-accepted exercise stays
     *         accepted regardless of what it contains.
     */
    public SpecFidelityReport specFidelityReport() {
        return specFidelityReport;
    }

    /** @return the per-attempt model-turn counts, in order; empty for a cancelled/error outcome. Telemetry only — makes verifier-feedback thrash observable. */
    public List<Integer> attemptTurnCounts() {
        return attemptTurnCounts;
    }

    /**
     * @return {@code true} only when verification accepted the exercise; the caller persists exactly in this case
     */
    public boolean isAccepted() {
        return verification != null && verification.accepted();
    }

    public AgentLoopResult loopResult() {
        return loopResult;
    }

    @Nullable
    public VerificationResult verification() {
        return verification;
    }

    @Nullable
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * Reads the produced files for a repository type out of the (still-open) session. Only valid before {@link #close()}.
     *
     * @param repositoryType the repository whose produced files to read
     * @return the produced files (path to content), or an empty map if the session is no longer available
     */
    public Map<String, String> producedFiles(RepositoryType repositoryType) {
        if (orchestrator == null || sessionId == null || sandbox == null) {
            return Map.of();
        }
        return orchestrator.workspace().extractRepositoryFiles(sandbox, sessionId, repositoryType);
    }

    /**
     * Reads the produced problem statement out of the (still-open) session. Only valid before {@link #close()}.
     *
     * @return the produced problem statement, or an empty string if unavailable
     */
    public String producedProblemStatement() {
        if (orchestrator == null || sessionId == null || sandbox == null) {
            return "";
        }
        return orchestrator.workspace().extractProblemStatement(sandbox, sessionId);
    }

    @Override
    public void close() {
        if (orchestrator != null) {
            orchestrator.destroyQuietly(sandbox, sessionId);
        }
    }
}
