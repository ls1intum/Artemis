package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The result of an agentic Hyperion sandbox, returned to the caller (the async task) so it can decide whether to persist and then close the session.
 * <p>
 * It is {@link AutoCloseable}: closing it destroys the underlying sandbox container. Verification already reads each repository's produced files (and the problem statement) out of
 * the session to run the integrity gates, so those extractions are captured on the outcome and reused at persist — no second full-repo read of the sandbox. Persist only runs on a
 * mechanically verified outcome, so the accessors return the captured value directly (empty when absent); the outcome holds a reference back to the orchestrator and the session
 * id solely so {@link #close()} can destroy the sandbox container.
 */
public final class GenerationOutcome implements AutoCloseable {

    private final AgentLoopResult loopResult;

    @Nullable
    private final VerificationResult verification;

    @Nullable
    private final String sessionId;

    @Nullable
    private final GenerationOrchestrationService orchestrator;

    @Nullable
    private final InteractiveSandbox sandbox;

    @Nullable
    private final String errorMessage;

    /**
     * Produced files captured from the sandbox. Verified outcomes reuse the verification extraction; an errored run may retain best-effort diagnostics, but unverified artifacts
     * are never persisted. Cancelled and artifact-less errored outcomes keep this empty.
     */
    private final Map<RepositoryType, Map<String, String>> capturedProducedFiles;

    private final Map<RepositoryType, String> seedRepositoryHeads;

    /**
     * The produced problem statement captured to avoid a later sandbox read. It may also be present on an errored run for diagnostics.
     */
    @Nullable
    private final String capturedProblemStatement;

    /**
     * Full-artifact review findings. Blocking findings require instructor review of the saved mechanically valid exercise; presentation findings are advisory.
     */
    private final SpecFidelityReport specFidelityReport;

    private final AtomicBoolean closed = new AtomicBoolean();

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId, @Nullable GenerationOrchestrationService orchestrator,
            @Nullable InteractiveSandbox sandbox, Map<RepositoryType, Map<String, String>> capturedProducedFiles, @Nullable String capturedProblemStatement,
            SpecFidelityReport specFidelityReport, Map<RepositoryType, String> seedRepositoryHeads) {
        this.loopResult = loopResult;
        this.verification = verification;
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.sandbox = sandbox;
        this.errorMessage = null;
        this.capturedProducedFiles = Map.copyOf(capturedProducedFiles);
        this.capturedProblemStatement = capturedProblemStatement;
        this.specFidelityReport = specFidelityReport;
        this.seedRepositoryHeads = Map.copyOf(seedRepositoryHeads);
    }

    private GenerationOutcome(AgentLoopResult loopResult, @Nullable String errorMessage) {
        this.loopResult = loopResult;
        this.verification = null;
        this.sessionId = null;
        this.orchestrator = null;
        this.sandbox = null;
        this.errorMessage = errorMessage;
        this.capturedProducedFiles = Map.of();
        this.capturedProblemStatement = null;
        this.specFidelityReport = SpecFidelityReport.empty();
        this.seedRepositoryHeads = Map.of();
    }

    static GenerationOutcome cancelled(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "Generation was cancelled.");
    }

    static GenerationOutcome error(AgentLoopResult loopResult) {
        return new GenerationOutcome(loopResult, "The agent loop ended with an error.");
    }

    static GenerationOutcome error(AgentLoopResult loopResult, String errorMessage) {
        return new GenerationOutcome(loopResult, errorMessage);
    }

    /**
     * @return the spec-fidelity and adaptation-scope report; never {@code null}
     */
    public SpecFidelityReport specFidelityReport() {
        return specFidelityReport;
    }

    /** @return whether the generated exercise passed the authoritative mechanical verification */
    public boolean isMechanicallyVerified() {
        return verification != null && verification.mechanicallyVerified();
    }

    public AgentLoopResult loopResult() {
        return loopResult;
    }

    /** @return whether an errored run captured workspace artifacts for diagnostics */
    public boolean hasCapturedArtifacts() {
        return sessionId != null && (!capturedProducedFiles.isEmpty() || capturedProblemStatement != null);
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
     * The produced files for a repository type — the extraction verification already performed and captured on this outcome. Only mechanically verified outcomes are persisted,
     * so the capture is always present for the repositories persist reads.
     *
     * @param repositoryType the repository whose produced files to read
     * @return the produced files (path to content), or an empty map if that repository produced none
     */
    public Map<String, String> producedFiles(RepositoryType repositoryType) {
        return capturedProducedFiles.getOrDefault(repositoryType, Map.of());
    }

    public Map<RepositoryType, String> seedRepositoryHeads() {
        return seedRepositoryHeads;
    }

    /**
     * The produced problem statement verification already read and captured on this outcome.
     *
     * @return the produced problem statement, or an empty string when none was captured
     */
    public String producedProblemStatement() {
        return capturedProblemStatement != null ? capturedProblemStatement : "";
    }

    @Override
    public void close() {
        if (orchestrator != null && closed.compareAndSet(false, true)) {
            orchestrator.destroyQuietly(sandbox, sessionId);
        }
    }
}
