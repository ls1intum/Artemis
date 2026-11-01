package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

class AuthoringStageCaptureTest {

    private static final String SESSION = "session";

    private static final Map<RepositoryRole, Map<String, String>> BASELINE = Map.of(RepositoryRole.SOLUTION, Map.of("src/Existing.java", "class Existing {}"));

    private final InteractiveSandbox sandbox = mock(InteractiveSandbox.class);

    private final GenerationWorkspace workspace = mock(GenerationWorkspace.class);

    private final GenerationOrchestrator service = mock(GenerationOrchestrator.class);

    private final GenerationWorkspace.WorkspaceSeed seed = new GenerationWorkspace.WorkspaceSeed(Map.of(), Map.of(), BASELINE, Map.of());

    private final Map<String, String> rootDocuments = new HashMap<>();

    private SandboxSessionLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new SandboxSessionLifecycle(SESSION, () -> {
        });
        when(service.captureRepositoryFiles(sandbox, SESSION, seed, Map.of())).thenReturn(BASELINE);
        when(sandbox.copyOut(eq(SESSION), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            String name = path.substring(path.lastIndexOf('/') + 1);
            Map<String, String> files = rootDocuments.containsKey(name) ? Map.of(name, rootDocuments.get(name)) : Map.of();
            return new TarArchiveInputStream(WorkspaceArchive.buildFilesTarStream(files, Map.of(), Set.of()));
        });
    }

    private AuthoringStageCapture capture(String baselineStatement) {
        when(workspace.extractProblemStatement(sandbox, SESSION)).thenReturn(baselineStatement);
        return new AuthoringStageCapture(service, workspace, sandbox, SESSION, lifecycle, seed, Map.of(), BASELINE, baselineStatement);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SPEC.md", "test-plan.json" })
    void retainsAnAuthoredRootDocumentWithoutRepositoryOrStatementChanges(String fileName) {
        rootDocuments.put(fileName, "authored diagnostic document");
        AuthoringStageCapture capture = capture("Existing statement");

        GenerationOutcome outcome = capture.partialOutcome(AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Provider stopped"), "Not verified");

        assertThat(outcome).isNotNull();
        assertThat(outcome.capturedProducedFiles()).isEmpty();
        assertThat(outcome.verification()).isNull();
        assertThat(fileName.equals("SPEC.md") ? outcome.specDocument() : outcome.testPlanJson()).isEqualTo("authored diagnostic document");
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SPEC.md", "test-plan.json" })
    void retainsTheRootOnlyStageSnapshotAfterTheSandboxIsDestroyed(String fileName) {
        rootDocuments.put(fileName, "authored stage document");
        AuthoringStageCapture capture = capture("");
        capture.recordStageBoundary(GenerationStage.SPEC);
        assertThat(capture.latestStageSnapshot()).isNotNull();
        lifecycle.requestDestroy();

        AuthoringStageCapture.CapturedWorkspace retained = capture.captureNow();

        assertThat(retained).isSameAs(capture.latestStageSnapshot());
        assertThat(retained.fromStage()).isEqualTo(GenerationStage.SPEC);
        assertThat(fileName.equals("SPEC.md") ? retained.specDocument() : retained.testPlanJson()).isEqualTo("authored stage document");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "Existing unchanged statement" })
    void doesNotRetainUnchangedSeedContentOrBlankRootDocuments(String baselineStatement) {
        rootDocuments.put("SPEC.md", " \n");
        rootDocuments.put("test-plan.json", " ");
        AuthoringStageCapture capture = capture(baselineStatement);

        capture.recordStageBoundary(GenerationStage.SPEC);

        assertThat(capture.latestStageSnapshot()).isNull();
        assertThat(capture.captureNow()).isNull();
    }

    @Test
    void failedRootDocumentReadsDoNotInventArtifactsOrVerification() {
        AuthoringStageCapture capture = capture("Existing unchanged statement");
        when(sandbox.copyOut(eq(SESSION), anyString())).thenThrow(new IllegalStateException("Session unavailable"));

        assertThat(capture.partialOutcome(AgentLoopResult.outsideSession(AgentLoopResult.Status.ERROR, "Provider stopped"), "Not verified")).isNull();
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.ACTIVE);
    }

    @Test
    void captureAfterDestructionNeverAccessesTheSandbox() {
        AuthoringStageCapture capture = capture("");
        lifecycle.requestDestroy();

        assertThat(capture.captureNow()).isNull();

        verifyNoInteractions(sandbox);
    }
}
