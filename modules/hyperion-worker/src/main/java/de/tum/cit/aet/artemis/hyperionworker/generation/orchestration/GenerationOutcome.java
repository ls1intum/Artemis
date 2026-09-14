package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;

/** Captured authoring result. Core alone decides whether the immutable candidate may be saved. */
public final class GenerationOutcome {

    private final AgentLoopResult loopResult;

    @Nullable
    private final VerificationResult verification;

    @Nullable
    private final String errorMessage;

    /** An errored run may retain best-effort diagnostics here, but only a mechanically verified outcome is ever persisted. */
    private final Map<RepositoryRole, Map<String, String>> capturedProducedFiles;

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

    GenerationOutcome(AgentLoopResult loopResult, @Nullable VerificationResult verification, Map<RepositoryRole, Map<String, String>> capturedProducedFiles,
            @Nullable String capturedProblemStatement, SpecFidelityReport specFidelityReport, @Nullable String specDocument, @Nullable String testPlanJson) {
        this.loopResult = loopResult;
        this.verification = verification;
        this.errorMessage = null;
        this.capturedProducedFiles = capturedProducedFiles.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        this.capturedProblemStatement = capturedProblemStatement;
        this.specFidelityReport = specFidelityReport;
        this.specDocument = specDocument;
        this.testPlanJson = testPlanJson;
    }

    private GenerationOutcome(AgentLoopResult loopResult, @Nullable String errorMessage) {
        this.loopResult = loopResult;
        this.verification = null;
        this.errorMessage = errorMessage;
        this.capturedProducedFiles = Map.of();
        this.capturedProblemStatement = null;
        this.specFidelityReport = SpecFidelityReport.empty();
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
        return !capturedProducedFiles.isEmpty() || capturedProblemStatement != null;
    }

    @Nullable
    public VerificationResult verification() {
        return verification;
    }

    @Nullable
    public String errorMessage() {
        return errorMessage;
    }

    public Map<String, String> producedFiles(RepositoryRole repositoryType) {
        return capturedProducedFiles.getOrDefault(repositoryType, Map.of());
    }

    public Map<RepositoryRole, Map<String, String>> capturedProducedFiles() {
        return capturedProducedFiles;
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

}
