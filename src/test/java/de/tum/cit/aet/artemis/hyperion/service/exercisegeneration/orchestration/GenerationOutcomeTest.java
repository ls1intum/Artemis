package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationOutcomeTest {

    @Test
    void projectsFrozenFilesAndKeepsCoreOnlyHeads() {
        var seed = seed(List.of());
        var output = output(List.of(text("template/src/App.java", "class App {}"), text("problem-statement.md", "# Exercise"), text("SPEC.md", "contract")));
        var outcome = GenerationOutcome.received(output, seed, false);
        assertThat(outcome.producedFiles(RepositoryType.TEMPLATE)).containsExactlyEntriesOf(Map.of("src/App.java", "class App {}"));
        assertThat(outcome.producedProblemStatement()).isEqualTo("# Exercise");
        assertThat(outcome.specDocument()).isEqualTo("contract");
        assertThat(outcome.seedRepositoryHeads()).containsExactlyEntriesOf(seed.heads());
        assertThat(outcome.isMechanicallyVerified()).isTrue();
        assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
    }

    @Test
    void explicitCancellationDoesNotTurnVerifiedOutputIntoSuccess() {
        var outcome = GenerationOutcome.received(output(List.of(text("problem-statement.md", "statement"))), seed(List.of()), true);
        assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(outcome.hasCapturedArtifacts()).isTrue();
    }

    @Test
    void keepsCanonicalBinaryOutOfTextPersistence() {
        var binary = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false);
        var outcome = GenerationOutcome.received(output(List.of(binary)), seed(List.of(binary)), false);
        assertThat(outcome.producedFiles(RepositoryType.TESTS)).isEmpty();
        assertThatThrownBy(() -> GenerationOutcome.received(output(List.of()), seed(List.of(binary)), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenerationOutcome.received(output(List.of(binary)), seed(List.of()), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsEarlyUnverifiedFailureWithoutRepositoryCapture() {
        var binary = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false);
        for (var files : List.of(List.<WorkspaceFile>of(), List.of(text("SPEC.md", "unfinished contract")))) {
            var outcome = GenerationOutcome.received(unverifiedOutput(files), seed(List.of(binary)), false);
            assertThat(outcome.isMechanicallyVerified()).isFalse();
            assertThat(outcome.errorMessage()).contains("Specification gate failed");
            assertThat(outcome.capturedProducedFiles()).isEmpty();
            assertThat(outcome.hasCapturedArtifacts()).isEqualTo(!files.isEmpty());
            if (!files.isEmpty()) {
                assertThat(outcome.specDocument()).isEqualTo("unfinished contract");
            }
        }
    }

    @Test
    void unverifiedRepositoryCaptureStillRequiresItsCanonicalBinaries() {
        var binary = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false);
        var output = unverifiedOutput(List.of(text("tests/src/AppTest.java", "class AppTest {}")));
        assertThatThrownBy(() -> GenerationOutcome.received(output, seed(List.of(binary)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical binary scaffolding");
    }

    @Test
    void unverifiedPartialCaptureMayOmitUnchangedRepositories() {
        var binary = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false);
        var files = List.of(text("template/src/App.java", "class App {}"));
        var outcome = GenerationOutcome.received(unverifiedOutput(files), seed(List.of(binary)), false);
        assertThat(outcome.isMechanicallyVerified()).isFalse();
        assertThat(outcome.producedFiles(RepositoryType.TEMPLATE)).containsExactlyEntriesOf(Map.of("src/App.java", "class App {}"));
        assertThat(outcome.producedFiles(RepositoryType.TESTS)).isEmpty();
        assertThatThrownBy(() -> GenerationOutcome.received(output(files), seed(List.of(binary)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical binary scaffolding");
        assertThatThrownBy(() -> GenerationOutcome.received(unverifiedOutput(List.of(binary)), seed(List.of()), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated binaries cannot replace");
    }

    @Test
    void rejectsChangedExecutableMode() {
        var executable = new WorkspaceFile("tests/gradlew", "script".getBytes(StandardCharsets.UTF_8), true);
        assertThatThrownBy(() -> GenerationOutcome.received(output(List.of(executable)), seed(List.of(text("tests/gradlew", "script"))), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidUtf8RatherThanChangingVerifiedBytes() {
        var invalidText = new WorkspaceFile("template/src/App.java", new byte[] { (byte) 0xc3, 0x28 }, false);
        assertThatThrownBy(() -> GenerationOutcome.received(output(List.of(invalidText)), seed(List.of()), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownRepositoryAndRootDocument() {
        for (String path : List.of("scratch/output.java", "unexpected.md")) {
            assertThatThrownBy(() -> GenerationOutcome.received(output(List.of(text(path, "content"))), seed(List.of()), false)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static WorkspaceFile text(String path, String value) {
        return new WorkspaceFile(path, value.getBytes(StandardCharsets.UTF_8), false);
    }

    private static GenerationSeedService.Seed seed(List<WorkspaceFile> files) {
        return new GenerationSeedService.Seed(new WorkspaceSnapshot(files), Map.of(RepositoryType.TEMPLATE, "core-only-head"));
    }

    private static GenerationOutput unverifiedOutput(List<WorkspaceFile> files) {
        var snapshot = new WorkspaceSnapshot(files);
        return new GenerationOutput(snapshot, new VerificationResult(false, false, false, 0, List.of("Specification gate failed")), null, SpecFidelityReport.empty(), "RUN_FAILED",
                null, GenerationOutput.AccountingState.INCOMPLETE, "default");
    }

    private static GenerationOutput output(List<WorkspaceFile> files) {
        var snapshot = new WorkspaceSnapshot(files);
        return new GenerationOutput(snapshot, new VerificationResult(true, true, true, 1, List.of()), snapshot.sha256(), SpecFidelityReport.empty(), "CONVERGED", null,
                GenerationOutput.AccountingState.INCOMPLETE, "default");
    }
}
