package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The result of an agentic generation session, returned to the caller (the async task) so it can decide whether to persist and then close the session.
 * <p>
 * It is {@link AutoCloseable}: closing it destroys the underlying sandbox container. Verification already reads each repository's produced files (and the problem statement) out of
 * the session to run the integrity gates, so those extractions are captured on the outcome and reused at persist — no second full-repo read of the sandbox. The lazy read from the
 * still-open session is kept only as a fallback for an outcome that was never verified (so a hypothetical persist without verification still works), which is why the outcome holds
 * a reference back to the orchestrator and the session id.
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
     * The verification-time produced files per repository, captured so persist reuses them instead of re-reading the sandbox. Empty when verification never ran (cancelled/errored
     * outcomes), in which case {@link #producedFiles} falls back to a lazy session read.
     */
    private final Map<RepositoryType, Map<String, String>> capturedProducedFiles;

    /** The verification-time produced problem statement, captured to avoid re-reading it at persist. {@code null} when verification never ran (then a lazy read is used). */
    @Nullable
    private final String capturedProblemStatement;

    /**
     * Advisory spec-fidelity / coverage findings (the brief-coverage axis the differential oracle is blind to). Purely advisory: never consulted by {@link #isAccepted()}. Empty
     * when the critic found nothing or was skipped.
     */
    private final SpecFidelityReport specFidelityReport;

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, @Nullable String sessionId, @Nullable GenerationOrchestrationService orchestrator,
            @Nullable InteractiveSandbox sandbox, Map<RepositoryType, Map<String, String>> capturedProducedFiles, @Nullable String capturedProblemStatement,
            SpecFidelityReport specFidelityReport) {
        this.loopResult = loopResult;
        this.verification = verification;
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.sandbox = sandbox;
        this.errorMessage = null;
        this.capturedProducedFiles = Map.copyOf(capturedProducedFiles);
        this.capturedProblemStatement = capturedProblemStatement;
        this.specFidelityReport = specFidelityReport;
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
     * @return {@code true} only when verification accepted the exercise
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
     * The produced files for a repository type: the extraction verification already performed, or — only when the outcome was never verified — a lazy read from the still-open
     * session (valid before {@link #close()}).
     *
     * @param repositoryType the repository whose produced files to read
     * @return the produced files (path to content), or an empty map if neither a captured extraction nor a live session is available
     */
    public Map<String, String> producedFiles(RepositoryType repositoryType) {
        Map<String, String> captured = capturedProducedFiles.get(repositoryType);
        if (captured != null) {
            return captured;
        }
        if (orchestrator == null || sessionId == null || sandbox == null) {
            return Map.of();
        }
        return orchestrator.workspace().extractRepositoryFiles(sandbox, sessionId, repositoryType);
    }

    /**
     * The produced problem statement: the one verification already read, or — only when the outcome was never verified — a lazy read from the still-open session (valid before
     * {@link #close()}).
     *
     * @return the produced problem statement, or an empty string if unavailable
     */
    public String producedProblemStatement() {
        if (capturedProblemStatement != null) {
            return capturedProblemStatement;
        }
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
