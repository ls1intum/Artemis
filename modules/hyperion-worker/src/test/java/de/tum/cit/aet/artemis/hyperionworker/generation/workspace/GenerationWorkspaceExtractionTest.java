package de.tum.cit.aet.artemis.hyperionworker.generation.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService.RepositorySeedMetadata;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;

class GenerationWorkspaceExtractionTest {

    private final GenerationResources resources = new GenerationResources();

    private final GenerationWorkspaceService workspace = new GenerationWorkspaceService(new SandboxBuildCommandService(resources), resources);

    private final InteractiveSandbox sandbox = mock(InteractiveSandbox.class);

    private void returnedArchive(Map<String, String> text, Map<String, byte[]> binary, Set<String> executable) {
        when(sandbox.copyOut(anyString(), anyString())).thenAnswer(_ -> new TarArchiveInputStream(WorkspaceArchive.buildFilesTarStream(text, binary, executable)));
    }

    @Test
    void preservesTextWhileCheckingSeededBinaryDigestsAndExecutableModes() {
        byte[] wrapper = { 0, 1, (byte) 0xff };
        returnedArchive(Map.of("solution/src/Stack.java", "class Stack {}", "solution/gradlew", "#!/bin/sh"), Map.of("solution/wrapper.jar", wrapper), Set.of("solution/gradlew"));
        var metadata = new RepositorySeedMetadata(Map.of("wrapper.jar", WorkspaceArchive.sha256(wrapper)), Set.of("gradlew"));

        var result = workspace.extractRepository(sandbox, "session", RepositoryRole.SOLUTION, metadata);

        assertThat(result.extractionFailed()).isFalse();
        assertThat(result.files()).containsExactlyInAnyOrderEntriesOf(Map.of("src/Stack.java", "class Stack {}", "gradlew", "#!/bin/sh"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aChangedBinaryOrLostExecutableModeInvalidatesTheExtraction(boolean binaryChanged) {
        byte[] wrapper = { 0, 1, (byte) 0xff };
        returnedArchive(Map.of("tests/gradlew", "#!/bin/sh"), Map.of("tests/wrapper.jar", binaryChanged ? new byte[] { 0, 2 } : wrapper),
                binaryChanged ? Set.of("tests/gradlew") : Set.of());
        var metadata = new RepositorySeedMetadata(Map.of("wrapper.jar", WorkspaceArchive.sha256(wrapper)), Set.of("gradlew"));

        assertThat(workspace.extractRepository(sandbox, "session", RepositoryRole.TESTS, metadata).extractionFailed()).isTrue();
    }

    @Test
    void nestedRepositoryResidueCannotBecomeCanonicalStudentSources() {
        returnedArchive(Map.of("template/src/Stack.java", "class Stack {}", "template/solution/src/Secret.java", "class Secret {}"), Map.of(), Set.of());

        var result = workspace.extractRepository(sandbox, "session", RepositoryRole.TEMPLATE, RepositorySeedMetadata.EMPTY);

        assertThat(result.extractionFailed()).isTrue();
        assertThat(result.files()).containsOnlyKeys("src/Stack.java");
    }

    @Test
    void unreadableRepositoryIsNotMistakenForAnEmptySuccessfulExtraction() {
        when(sandbox.copyOut(anyString(), anyString())).thenThrow(new IllegalStateException("transport unavailable"));

        var result = workspace.extractRepository(sandbox, "session", RepositoryRole.TESTS, RepositorySeedMetadata.EMPTY);

        assertThat(result.extractionFailed()).isTrue();
        assertThat(result.files()).isEmpty();
    }

    @Test
    void readsTheWholeStatementFromTheArchiveInsteadOfTruncatedExecOutput() {
        String statement = "Teaching text.\n".repeat(10_000);
        returnedArchive(Map.of("problem-statement.md", statement), Map.of(), Set.of());

        assertThat(workspace.extractProblemStatement(sandbox, "session")).isEqualTo(statement);
    }

    @Test
    void missingStatementFailsRatherThanReturningASeeminglyAuthoredEmptyStatement() {
        returnedArchive(Map.of("unrelated.md", "not the statement"), Map.of(), Set.of());

        assertThatThrownBy(() -> workspace.extractProblemStatement(sandbox, "session")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not extract the generated problem statement");
    }
}
