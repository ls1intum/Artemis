package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The one guarded read-back of a run's sandbox workspace, and the run's most recent authoring-stage snapshot.
 * <p>
 * Two things are needed for a failing run to retain anything. {@link SandboxSessionLifecycle} keeps a cancellation from destroying the session while a copy-out is in flight;
 * this type is what still has something to hand back when the session is genuinely gone — the agent lost, the container out of memory — because a dead session cannot be read
 * at all. Before it existed, the only durable snapshot of a run was the mechanically verified checkpoint, taken after verification passes, so a run that died during its first
 * attempt's authoring had never captured anything and retained nothing.
 * <p>
 * The snapshot is taken at authoring-stage boundaries (SPEC / TESTS / STATEMENT), not per turn: a per-turn copy-out of three repositories would add a relay round trip to every
 * turn to capture states that the next stage boundary captures anyway. Only the latest snapshot is kept, and it is held in this run's memory rather than in the distributed
 * replay evidence — it is read back by the same thread that owns the run, and it reaches an instructor only through {@code RetainedArtifacts}, whose file, size, and secret
 * screening bounds it exactly as they bound a candidate captured live.
 */
final class AuthoringStageCapture {

    private static final Logger log = LoggerFactory.getLogger(AuthoringStageCapture.class);

    /** The order the candidate repositories are read back in, kept stable so a run's copy-out sequence does not depend on map iteration. */
    private static final List<RepositoryType> CANDIDATE_REPOSITORIES = List.of(RepositoryType.TESTS, RepositoryType.TEMPLATE, RepositoryType.SOLUTION);

    /** A run's workspace as it stood at one point in time, either read live or replayed from the last authoring-stage boundary. */
    record CapturedWorkspace(Map<RepositoryType, Map<String, String>> files, String problemStatement, @Nullable String specDocument, @Nullable String testPlanJson,
            @Nullable GenerationStage fromStage) {
    }

    /** One guarded read of the live session covering everything verification decides on: the three repositories, the statement, and the grading plan. */
    record CandidateReadBack(Map<RepositoryType, GenerationWorkspaceService.RepositoryExtraction> repositories, String problemStatement, @Nullable String testPlanJson) {
    }

    private final GenerationOrchestrationService service;

    private final GenerationWorkspaceService workspace;

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    private final SandboxSessionLifecycle lifecycle;

    private final GenerationWorkspaceService.WorkspaceSeed workspaceSeed;

    private final Map<String, String> placeholderReplacements;

    private final Map<RepositoryType, Map<String, String>> baselineRepositoryFiles;

    @Nullable
    private final String baselineProblemStatement;

    /** Written by the generation thread at a stage boundary and read by it again on the failure paths; volatile so a read on any other thread still sees the latest. */
    @Nullable
    private volatile CapturedWorkspace latestStageSnapshot;

    AuthoringStageCapture(GenerationOrchestrationService service, GenerationWorkspaceService workspace, InteractiveSandbox sandbox, String sessionId,
            SandboxSessionLifecycle lifecycle, GenerationWorkspaceService.WorkspaceSeed workspaceSeed, Map<String, String> placeholderReplacements,
            Map<RepositoryType, Map<String, String>> baselineRepositoryFiles, @Nullable String baselineProblemStatement) {
        this.service = service;
        this.workspace = workspace;
        this.sandbox = sandbox;
        this.sessionId = sessionId;
        this.lifecycle = lifecycle;
        this.workspaceSeed = workspaceSeed;
        this.placeholderReplacements = placeholderReplacements;
        this.baselineRepositoryFiles = baselineRepositoryFiles;
        this.baselineProblemStatement = baselineProblemStatement;
    }

    SandboxSessionLifecycle lifecycle() {
        return lifecycle;
    }

    /** The last authoring-stage boundary snapshot, or {@code null} when no stage has completed with anything in it yet. Package-private for tests. */
    @Nullable
    CapturedWorkspace latestStageSnapshot() {
        return latestStageSnapshot;
    }

    /**
     * Snapshots the repositories and the statement at the end of one authoring stage, replacing the previous snapshot. Best-effort throughout: a stage boundary is not a
     * checkpoint the run depends on, so a failed read leaves the previous snapshot in place rather than failing the stage.
     *
     * @param stage the authoring stage that just finished
     */
    void recordStageBoundary(GenerationStage stage) {
        if (!lifecycle.beginCapture()) {
            return;
        }
        try {
            Map<RepositoryType, Map<String, String>> files = GenerationOrchestrationService.changedCapturedRepositoryFiles(baselineRepositoryFiles,
                    service.captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
            String statement = problemStatementOrBaseline();
            if (files.isEmpty() && statement.isBlank()) {
                return;
            }
            latestStageSnapshot = new CapturedWorkspace(files, statement, GenerationOrchestrationService.readSpecDocument(sandbox, sessionId),
                    GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"), stage);
            log.debug("Snapshotted {} repository/repositories at the end of authoring stage {} for session {}", files.size(), stage, sessionId);
        }
        catch (RuntimeException e) {
            log.debug("Could not snapshot the workspace at the end of authoring stage {}: {}", stage, e.getMessage());
        }
        finally {
            lifecycle.endCapture();
        }
    }

    /**
     * Reads every candidate artifact back for verification, under the same teardown gate as every other capture. Extraction failures are preserved rather than swallowed so
     * verification can tell an unreadable repository apart from an empty one.
     *
     * @return the read-back candidate
     * @throws IllegalStateException when a cancellation already destroyed the session. Throwing rather than issuing copy-outs that are guaranteed to fail hands the run to its
     *                                   cancellation path, which retains the last authoring-stage snapshot instead of a tree of empty extractions
     */
    CandidateReadBack readBackCandidate() {
        if (!lifecycle.beginCapture()) {
            throw new IllegalStateException("The sandbox session was destroyed before the candidate could be read back for verification");
        }
        try {
            Map<RepositoryType, GenerationWorkspaceService.RepositoryExtraction> repositories = new EnumMap<>(RepositoryType.class);
            for (RepositoryType type : CANDIDATE_REPOSITORIES) {
                GenerationWorkspaceService.RepositoryExtraction extraction = workspace.extractRepository(sandbox, sessionId, type,
                        workspaceSeed.repositoryMetadata().getOrDefault(type, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY));
                repositories.put(type, GenerationOrchestrationService.replacePlaceholders(extraction, placeholderReplacements));
            }
            // Persistence trims the statement before saving. Canonicalize it here so verification and persistence consume the same value, and capture the grading plan with the
            // repositories so both stages decide on the same immutable candidate.
            return new CandidateReadBack(repositories, workspace.extractProblemStatement(sandbox, sessionId).trim(),
                    GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"));
        }
        finally {
            lifecycle.endCapture();
        }
    }

    /**
     * The best account of what this run produced: the live workspace when the session can still be read, and otherwise the last authoring-stage snapshot.
     *
     * @return the captured workspace, or {@code null} when the run produced nothing worth retaining
     */
    @Nullable
    CapturedWorkspace captureNow() {
        if (!lifecycle.beginCapture()) {
            // The session is already gone, so every copy-out below would fail. Skipping them is the point: issuing them would only add "Could not extract … files" warnings to
            // the log and still return nothing.
            log.info("Sandbox session {} was destroyed before its work could be copied out; falling back to the last authoring-stage snapshot", sessionId);
            return latestStageSnapshot;
        }
        try {
            try {
                workspace.cleanTransientBuildOutputs(sandbox, sessionId);
            }
            catch (RuntimeException cleanupFailure) {
                log.debug("Could not clean transient outputs before diagnostic capture: {}", cleanupFailure.getMessage());
            }
            Map<RepositoryType, Map<String, String>> files = Map.of();
            try {
                files = GenerationOrchestrationService.changedCapturedRepositoryFiles(baselineRepositoryFiles,
                        service.captureRepositoryFiles(sandbox, sessionId, workspaceSeed, placeholderReplacements));
            }
            catch (RuntimeException extractionFailure) {
                log.debug("Could not extract every repository after generation failed: {}", extractionFailure.getMessage());
            }
            String statement = problemStatementOrBaseline();
            boolean statementChanged = !Objects.equals(normalizedBaselineProblemStatement(), statement);
            if (!statementChanged && files.isEmpty()) {
                return latestStageSnapshot;
            }
            return new CapturedWorkspace(files, statement, GenerationOrchestrationService.readSpecDocument(sandbox, sessionId),
                    GenerationOrchestrationService.readWorkspaceRootFile(sandbox, sessionId, "test-plan.json"), null);
        }
        finally {
            lifecycle.endCapture();
        }
    }

    /**
     * The partial candidate a run that stopped before verification leaves behind, built from {@link #captureNow()}.
     *
     * @param loopResult the loop result to report with the outcome
     * @param reviewNote why the quality review is unavailable for this candidate
     * @return the outcome, or {@code null} when nothing was captured and the caller should tear the session down instead
     */
    @Nullable
    GenerationOutcome partialOutcome(AgentLoopResult loopResult, String reviewNote) {
        CapturedWorkspace captured = captureNow();
        if (captured == null) {
            return null;
        }
        if (captured.fromStage() != null) {
            log.info("Retaining the snapshot taken at the end of authoring stage {} for session {}; the live workspace could not be read back", captured.fromStage(), sessionId);
        }
        return new GenerationOutcome(loopResult, null, sessionId, service, sandbox, captured.files(), captured.problemStatement(),
                SpecFidelityReport.qualityReviewUnavailable(reviewNote), workspaceSeed.repositoryHeads(), captured.specDocument(), captured.testPlanJson());
    }

    private String problemStatementOrBaseline() {
        try {
            return workspace.extractProblemStatement(sandbox, sessionId).trim();
        }
        catch (RuntimeException extractionFailure) {
            return normalizedBaselineProblemStatement();
        }
    }

    private String normalizedBaselineProblemStatement() {
        return baselineProblemStatement == null ? "" : baselineProblemStatement.trim();
    }
}
