package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The result of an agentic Hyperion sandbox, returned to the caller (the async task) so it can decide whether to persist and then close the session.
 * <p>
 * Closing destroys the underlying sandbox container. Verification already reads each repository's produced files (and the problem statement) out of the session to run the
 * integrity gates, so those extractions are captured here and reused at persist rather than read from the sandbox a second time.
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

    /** An errored run may retain best-effort diagnostics here, but only a mechanically verified outcome is ever persisted. */
    private final Map<RepositoryType, Map<String, String>> capturedProducedFiles;

    private final Map<RepositoryType, String> seedRepositoryHeads;

    @Nullable
    private final String capturedProblemStatement;

    /** Blocking findings require instructor review of the saved mechanically valid exercise; presentation findings are advisory. */
    private final SpecFidelityReport specFidelityReport;

    /** The workspace's {@code SPEC.md}, the agent's planning artifact. Surfaced for observability and never persisted into any repository. */
    @Nullable
    private final String specDocument;

    /** The workspace's {@code test-plan.json}, the TESTS stage's grading plan. */
    @Nullable
    private final String testPlanJson;

    /**
     * Why the run stopped producing candidates. Purely observational: nothing in persistence or the verdict reads it. Stamped on the single thread that builds and returns this
     * outcome, before it is handed to the caller, so it needs no synchronization.
     */
    @Nullable
    private TerminationReason terminationReason;

    private final AtomicBoolean closed = new AtomicBoolean();

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId, @Nullable GenerationOrchestrationService orchestrator,
            @Nullable InteractiveSandbox sandbox, Map<RepositoryType, Map<String, String>> capturedProducedFiles, @Nullable String capturedProblemStatement,
            SpecFidelityReport specFidelityReport, Map<RepositoryType, String> seedRepositoryHeads) {
        this(loopResult, verification, sessionId, orchestrator, sandbox, capturedProducedFiles, capturedProblemStatement, specFidelityReport, seedRepositoryHeads, null, null);
    }

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId, @Nullable GenerationOrchestrationService orchestrator,
            @Nullable InteractiveSandbox sandbox, Map<RepositoryType, Map<String, String>> capturedProducedFiles, @Nullable String capturedProblemStatement,
            SpecFidelityReport specFidelityReport, Map<RepositoryType, String> seedRepositoryHeads, @Nullable String specDocument, @Nullable String testPlanJson) {
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
        this.specDocument = specDocument;
        this.testPlanJson = testPlanJson;
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
        this.specDocument = null;
        this.testPlanJson = null;
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
     * Records why the run ended and returns this outcome. The first reason wins, so a wrapping path that adds context cannot overwrite the precise reason the exit itself
     * recorded.
     */
    GenerationOutcome withTermination(@Nullable TerminationReason reason) {
        if (this.terminationReason == null) {
            this.terminationReason = reason;
        }
        return this;
    }

    /** @return why the run stopped, or {@code null} when the outcome was produced outside the attempt loop and nothing stamped it */
    @Nullable
    public TerminationReason terminationReason() {
        return terminationReason;
    }

    public SpecFidelityReport specFidelityReport() {
        return specFidelityReport;
    }

    public boolean isMechanicallyVerified() {
        return verification != null && verification.mechanicallyVerified();
    }

    public AgentLoopResult loopResult() {
        return loopResult;
    }

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

    public Map<String, String> producedFiles(RepositoryType repositoryType) {
        return capturedProducedFiles.getOrDefault(repositoryType, Map.of());
    }

    public Map<RepositoryType, Map<String, String>> capturedProducedFiles() {
        return capturedProducedFiles;
    }

    public Map<RepositoryType, String> seedRepositoryHeads() {
        return seedRepositoryHeads;
    }

    @Nullable
    public String specDocument() {
        return specDocument;
    }

    @Nullable
    public String testPlanJson() {
        return testPlanJson;
    }

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
