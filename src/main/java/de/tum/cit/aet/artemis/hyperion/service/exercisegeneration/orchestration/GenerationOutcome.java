package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CrossCheckVerdict;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
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
     * Advisory spec-fidelity / coverage findings (the brief-coverage axis the differential oracle is blind to). Purely advisory: never consulted by {@link #isAccepted()}. Empty
     * when the critic found nothing or was skipped.
     */
    private final SpecFidelityReport specFidelityReport;

    /**
     * The decorrelated cross-check result (an independently-authored suite run against the real solution). {@code null} when the cross-check did not run for this outcome (flag
     * off,
     * language not allowlisted, error/cancelled path). Never consulted by {@link #isAccepted()}.
     */
    @Nullable
    private final CrossCheckVerdict crossCheckVerdict;

    /**
     * Whether the cross-check's contradiction should hard-block persistence (the {@code reject-on-contradiction} flag was on and the cross-check found a contradiction). Layered on
     * top of the oracle's accept decision — it can only make acceptance stricter, never looser — so a proven false-accept is routed to review instead of silently persisted.
     */
    private final boolean hardBlockedByCrossCheck;

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId,
            @Nullable ExerciseGenerationOrchestrationService orchestrator, @Nullable InteractiveSandbox sandbox, SpecFidelityReport specFidelityReport,
            @Nullable CrossCheckVerdict crossCheckVerdict, boolean hardBlockedByCrossCheck) {
        this.loopResult = loopResult;
        this.verification = verification;
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.sandbox = sandbox;
        this.errorMessage = null;
        this.specFidelityReport = specFidelityReport;
        this.crossCheckVerdict = crossCheckVerdict;
        this.hardBlockedByCrossCheck = hardBlockedByCrossCheck;
    }

    private GenerationOutcome(AgentLoopResult loopResult, @Nullable String errorMessage) {
        this.loopResult = loopResult;
        this.verification = null;
        this.sessionId = null;
        this.orchestrator = null;
        this.sandbox = null;
        this.errorMessage = errorMessage;
        this.specFidelityReport = SpecFidelityReport.empty();
        this.crossCheckVerdict = null;
        this.hardBlockedByCrossCheck = false;
    }

    static GenerationOutcome cancelled(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "Generation was cancelled.");
    }

    static GenerationOutcome error(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "The agent loop ended with an error.");
    }

    /**
     * @return the advisory spec-fidelity report (see field Javadoc); never {@code null}
     */
    public SpecFidelityReport specFidelityReport() {
        return specFidelityReport;
    }

    /**
     * @return {@code true} only when verification accepted the exercise; not changed by the cross-check
     */
    public boolean isAccepted() {
        return verification != null && verification.accepted();
    }

    /**
     * @return the decorrelated cross-check result, or {@code null} when it did not run for this outcome
     */
    @Nullable
    public CrossCheckVerdict crossCheckVerdict() {
        return crossCheckVerdict;
    }

    /**
     * Whether persistence must be hard-blocked because the cross-check found a contract contradiction while the {@code reject-on-contradiction} flag was on. Separate from (and
     * layered on top of) {@link #isAccepted()}: an outcome can be differential-accepted yet hard-blocked, which routes it to review instead of a silent persist.
     *
     * @return {@code true} when the accepted exercise must be diverted to review because of a contradiction
     */
    public boolean isHardBlockedByCrossCheck() {
        return hardBlockedByCrossCheck;
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
